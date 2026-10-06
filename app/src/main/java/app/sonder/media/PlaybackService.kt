package app.sonder.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import app.sonder.R
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import app.sonder.MainActivity
import app.sonder.SonderApp
import app.sonder.data.Chapter
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
class PlaybackService : MediaSessionService() {
    private lateinit var player:ExoPlayer
    private var session:MediaSession?=null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val app get()=application as SonderApp
    private var wasPlaying=false
    private var accumulated=0L
    private var lastWall=0L
    private var lastSaved=0L
    private var lastPosition=0L
    private var lastUri=""
    private var lastRelative=0L
    private var lastBook=0L
    private var lastRevision=0L
    private var chapters:List<Chapter> = emptyList()
    private var timerDeadline=0L
    private var chapterDeadline:Long?=null
    private var resumeRewindPending=false
    private var playedCurrentQueue=false
    companion object {
        const val TIMER="app.sonder.SLEEP_TIMER"
        private val timerState=MutableStateFlow(0L)
        val timer=timerState.asStateFlow() // -1 means end of chapter; positive means remaining milliseconds.
        private const val RESUME_REWIND_MS=5000L
    }
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(DefaultMediaNotificationProvider.Builder(this).build().also { it.setSmallIcon(R.drawable.ic_stat_sonder) })
        player=ExoPlayer.Builder(this).setSeekBackIncrementMs(app.preferences.settings.value.rewind*1000L).setSeekForwardIncrementMs(app.preferences.settings.value.forward*1000L).build().apply {
            // Speech makes Media3 pause for notification focus requests, including requests to duck.
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),true)
            setHandleAudioBecomingNoisy(true);setWakeMode(C.WAKE_MODE_LOCAL)
            trackSelectionParameters=trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO,true).build()
        }
        val intent=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val sessionPlayer=object:ForwardingPlayer(player) {
            override fun getSeekBackIncrement()=app.preferences.settings.value.rewind*1000L
            override fun getSeekForwardIncrement()=app.preferences.settings.value.forward*1000L
            override fun seekBack() { seekTo((currentPosition-seekBackIncrement).coerceAtLeast(0)) }
            override fun seekForward() { seekTo((currentPosition+seekForwardIncrement).coerceAtMost(duration.takeIf { it>0 } ?: Long.MAX_VALUE)) }
        }
        session=MediaSession.Builder(this,sessionPlayer).setSessionActivity(intent).setCallback(object:MediaSession.Callback {
            override fun onConnect(session:MediaSession,controller:MediaSession.ControllerInfo):MediaSession.ConnectionResult {
                if(controller.packageName!=packageName && !controller.isTrusted) return MediaSession.ConnectionResult.reject()
                val commands=MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().add(SessionCommand(TIMER,Bundle.EMPTY)).build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(commands).build()
            }
            override fun onCustomCommand(session:MediaSession,controller:MediaSession.ControllerInfo,customCommand:SessionCommand,args:Bundle):ListenableFuture<SessionResult> {
                if(customCommand.customAction!=TIMER) return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
                val minutes=args.getInt("minutes",0).coerceIn(-1,180)
                timerDeadline=if(minutes>0) SystemClock.elapsedRealtime()+minutes*60000L else 0
                chapterDeadline=if(minutes==-1) chapters.firstOrNull { globalPosition()<it.end }?.end else null
                timerState.value=when { chapterDeadline!=null -> -1;timerDeadline>0 -> minutes*60000L;else -> 0 }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }).build()
        player.addListener(object:Player.Listener {
            override fun onMediaItemTransition(mediaItem:androidx.media3.common.MediaItem?,reason:Int) {
                save()
                val id=mediaItem?.mediaMetadata?.extras?.getLong("bookId") ?: 0
                if(id!=lastBook || reason==Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) clearResumeRewind()
                if(id!=lastBook) { lastBook=id;accumulated=0;if(chapterDeadline!=null) timerState.value=0;chapterDeadline=null }
                scope.launch { runCatching { app.store.chapters(id) }.onSuccess { chapters=it }.onFailure { android.util.Log.e("Sonder","Chapter lookup failed",it);chapters=emptyList() } }
                lastRevision=mediaItem?.mediaMetadata?.extras?.getLong("progressRevision") ?: 0
                snapshot();lastWall=SystemClock.elapsedRealtime()
            }
            override fun onPlayWhenReadyChanged(playWhenReady:Boolean,reason:Int) {
                if(!playWhenReady && playedCurrentQueue && player.playbackState!=Player.STATE_IDLE && player.playbackState!=Player.STATE_ENDED) resumeRewindPending=true
                if(playWhenReady) rewindAfterPause()
            }
            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason:Int) {
                if(playbackSuppressionReason==Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS && player.playWhenReady && playedCurrentQueue) resumeRewindPending=true
                if(playbackSuppressionReason==Player.PLAYBACK_SUPPRESSION_REASON_NONE) rewindAfterPause()
            }
            override fun onIsPlayingChanged(isPlaying:Boolean) {
                if(isPlaying) { rewindAfterPause();playedCurrentQueue=true }
                account();snapshot();save()
            }
            override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int) {
                // An explicit seek or bookmark jump while paused should keep the chosen position.
                if(reason==Player.DISCONTINUITY_REASON_SEEK) resumeRewindPending=false
                if(player.currentMediaItem?.mediaMetadata?.extras?.getLong("bookId")==lastBook) { snapshot();save() }
            }
            override fun onPlaybackStateChanged(playbackState:Int) {
                if(playbackState==Player.STATE_IDLE || playbackState==Player.STATE_ENDED) clearResumeRewind()
                if(playbackState==Player.STATE_ENDED) save(true)
            }
            override fun onPlaybackParametersChanged(playbackParameters:PlaybackParameters) {
                val id=player.currentMediaItem?.mediaMetadata?.extras?.getLong("bookId") ?: return
                app.backgroundScope.launch { app.store.setSpeed(id,playbackParameters.speed) }
            }
        })
        scope.launch {
            app.preferences.settings.collect { settings ->
                player.skipSilenceEnabled=settings.skipSilence
                val speed=player.playbackParameters.speed
                player.playbackParameters=PlaybackParameters(speed,if(settings.preservePitch) 1f else speed)
            }
        }
        scope.launch {
            lastWall=SystemClock.elapsedRealtime()
            while(isActive) {
                delay(1000);account();snapshot()
                if(SystemClock.elapsedRealtime()-lastSaved>=3000) save()
                if(timerDeadline>0) {
                    timerState.value=(timerDeadline-SystemClock.elapsedRealtime()).coerceAtLeast(0)
                    if(timerState.value==0L) { player.pause();timerDeadline=0 }
                }
                if(chapterDeadline?.let { globalPosition()>=it }==true) { player.pause();chapterDeadline=null;timerState.value=0 }
            }
        }
    }
    private fun clearResumeRewind() { resumeRewindPending=false;playedCurrentQueue=false }
    private fun rewindAfterPause() {
        if(!resumeRewindPending || !player.playWhenReady || player.playbackSuppressionReason!=Player.PLAYBACK_SUPPRESSION_REASON_NONE || player.mediaItemCount==0) return
        // Consume first: seeking emits more player callbacks and must not rewind twice.
        resumeRewindPending=false
        val bookId=player.currentMediaItem?.mediaMetadata?.extras?.getLong("bookId") ?: return
        val target=(globalPosition()-RESUME_REWIND_MS).coerceAtLeast(0)
        var index=player.currentMediaItemIndex
        // Rewind across track files belonging to the same book.
        while(index>0 && (player.getMediaItemAt(index).mediaMetadata.extras?.getLong("offset") ?: 0)>target) {
            if(player.getMediaItemAt(index-1).mediaMetadata.extras?.getLong("bookId")!=bookId) break
            index--
        }
        val offset=player.getMediaItemAt(index).mediaMetadata.extras?.getLong("offset") ?: 0
        player.seekTo(index,(target-offset).coerceAtLeast(0))
    }
    private fun account() {
        val now=SystemClock.elapsedRealtime()
        if(wasPlaying && lastWall>0) accumulated+=(now-lastWall).coerceIn(0,2000)
        lastWall=now;wasPlaying=player.isPlaying
    }
    private fun snapshot() {
        lastPosition=globalPosition()
        if(player.currentMediaItem?.mediaMetadata?.extras?.getLong("bookId")==lastBook) {
            lastUri=player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
            lastRelative=player.currentPosition.coerceAtLeast(0)
        }
    }
    private fun globalPosition():Long=(player.currentMediaItem?.mediaMetadata?.extras?.getLong("offset") ?: 0)+player.currentPosition.coerceAtLeast(0)
    private fun save(finished:Boolean=false) {
        val id=lastBook
        if(id<=0) return
        if(finished) snapshot()
        val uri=lastUri;val relative=lastRelative;val revision=lastRevision
        val listened=accumulated;accumulated=0;lastSaved=SystemClock.elapsedRealtime()
        app.backgroundScope.launch { app.store.trackProgress(id,uri,relative,listened,finished,revision) }
    }
    override fun onGetSession(controllerInfo:MediaSession.ControllerInfo)=session
    override fun onTaskRemoved(rootIntent:Intent?) { if(!player.playWhenReady || player.mediaItemCount==0 || player.playbackState==Player.STATE_ENDED) stopSelf() }
    override fun onDestroy() { account();snapshot();save();scope.cancel();session?.release();player.release();timerState.value=0;super.onDestroy() }
}
