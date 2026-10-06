package app.sonder

import android.content.ComponentName
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sonder.data.Book
import app.sonder.data.Chapter
import app.sonder.data.Track
import app.sonder.media.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackResumeTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val app get()=context.applicationContext as SonderApp
    private val audioManager get()=context.getSystemService(AudioManager::class.java)
    private lateinit var controller:MediaController
    private var focusRequest:AudioFocusRequest?=null
    private val seeks=CopyOnWriteArrayList<Pair<Int,Long>>()

    private fun <T:Any> main(action:()->T):T {
        var result:T?=null
        instrumentation.runOnMainSync { result=action() }
        return result!!
    }
    private fun await(message:String,condition:()->Boolean) {
        val deadline=System.currentTimeMillis()+15000
        while(System.currentTimeMillis()<deadline) {
            if(main(condition)) return
            Thread.sleep(25)
        }
        assertTrue(message,main(condition))
    }

    @Before fun connect() {
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        controller=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync().get(20,TimeUnit.SECONDS)
        main {
            controller.stop();controller.clearMediaItems()
            controller.setPlaybackSpeed(1f)
            controller.addListener(object:Player.Listener {
                override fun onPositionDiscontinuity(oldPosition:Player.PositionInfo,newPosition:Player.PositionInfo,reason:Int) {
                    if(reason==Player.DISCONTINUITY_REASON_SEEK) seeks.add(newPosition.mediaItemIndex to newPosition.positionMs)
                }
            })
        }
        runBlocking { app.store.refresh();app.store.library.value.books.forEach { app.store.remove(it.id) } }
    }
    @After fun disconnect() {
        abandonFocus()
        if(::controller.isInitialized) main { controller.stop();controller.clearMediaItems();controller.release() }
    }

    private fun addBook(title:String="Resume test",trackCount:Int=1):Book=runBlocking {
        val dir=File(context.filesDir,"fixtures").apply { mkdirs() }
        val tracks=(0 until trackCount).map { index ->
            val file=File(dir,"$title-$index.mp4")
            instrumentation.context.assets.open("chapters.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            Track(uri=FileProvider.getUriForFile(context,"${context.packageName}.testfiles",file).toString(),name="Track $index",duration=30000,offset=index*30000L,ordinal=index)
        }
        val id=app.store.add(Book(title=title,source=title,duration=trackCount*30000L),tracks,tracks.map { Chapter(it.name,it.offset,it.offset+it.duration) })
        app.store.library.value.books.first { it.id==id }
    }
    private fun start(trackCount:Int=1):Book {
        val book=addBook(trackCount=trackCount)
        compose.waitUntil(15000) { compose.onAllNodesWithTag("shelfBook-${book.id}").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shelfBook-${book.id}").performClick()
        compose.onNodeWithText("Start listening").performScrollTo().performClick()
        await("Initial playback should start") { controller.isPlaying }
        assertTrue("Opening a new book must not trigger a resume seek",seeks.isEmpty())
        return book
    }
    private fun seek(index:Int=0,position:Long=12000) {
        main { controller.seekTo(index,position) }
        await("Seek should finish") { controller.isPlaying && controller.currentMediaItemIndex==index && controller.currentPosition>=position }
        seeks.clear()
    }
    private fun pause():Long {
        main { controller.pause() }
        await("Playback should pause") { !controller.isPlaying && !controller.playWhenReady }
        Thread.sleep(100)
        return main { controller.currentPosition }
    }
    private fun expectRewind(index:Int,position:Long) {
        await("Resuming should seek backwards") { seeks.isNotEmpty() }
        await("Playback should resume") { controller.isPlaying }
        Thread.sleep(150)
        assertEquals("Rewind must happen only once",1,seeks.size)
        assertEquals(index,seeks.single().first)
        assertTrue("Expected $position ms, got ${seeks.single().second}",kotlin.math.abs(position-seeks.single().second)<300)
    }
    private fun requestFocus(gain:Int=AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) {
        val request=AudioFocusRequest.Builder(gain)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setOnAudioFocusChangeListener { }
            .build()
        focusRequest=request
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED,main { audioManager.requestAudioFocus(request) })
    }
    private fun abandonFocus() {
        focusRequest?.let { request -> main { audioManager.abandonAudioFocusRequest(request) } }
        focusRequest=null
    }

    @Test fun appPauseAndPlayRewindsFiveSeconds() {
        val book=start();seek()
        compose.onNodeWithContentDescription("Pause").performClick()
        await("App pause should reach the service") { !controller.isPlaying && !controller.playWhenReady }
        val paused=main { controller.currentPosition }
        Thread.sleep(200)
        assertEquals("Position should stay put while paused",paused,main { controller.currentPosition })
        compose.onNodeWithContentDescription("Play").performClick()
        expectRewind(0,paused-5000)
        compose.waitUntil(10000) { app.store.library.value.books.first { it.id==book.id }.position in (paused-5300)..(paused-3000) }
    }

    @Test fun mediaCommandsAndRepeatedPlayRewindOnlyOnce() {
        start();seek()
        val paused=pause()
        main { controller.pause();controller.playWhenReady=true;controller.play() }
        expectRewind(0,paused-5000)
        main { controller.play() }
        Thread.sleep(200)
        assertEquals(1,seeks.size)
    }

    @Test fun notificationDuckRequestPausesInBackgroundAndAutomaticallyResumesEarlier() {
        start();seek()
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        try {
            requestFocus()
            await("A notification must pause speech instead of ducking") { !controller.isPlaying && controller.playbackSuppressionReason==Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS }
            val paused=main { controller.currentPosition }
            Thread.sleep(300)
            assertEquals(paused,main { controller.currentPosition })
            assertTrue(main { controller.playWhenReady })
            abandonFocus()
            expectRewind(0,paused-5000)
        } finally { compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED) }
    }

    @Test fun transientInterruptionResumesEarlierButManualPauseDuringItStaysPaused() {
        start();seek()
        requestFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        await("Transient focus should pause") { !controller.isPlaying && controller.playbackSuppressionReason==Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS }
        val paused=pause()
        abandonFocus()
        Thread.sleep(500)
        assertFalse("Focus returning must respect a manual pause",main { controller.isPlaying })
        assertTrue(seeks.isEmpty())
        main { controller.play() }
        expectRewind(0,paused-5000)
    }

    @Test fun permanentFocusLossWaitsForManualResumeAndRewinds() {
        start();seek()
        requestFocus(AudioManager.AUDIOFOCUS_GAIN)
        await("Permanent focus loss should pause") { !controller.isPlaying && !controller.playWhenReady }
        val paused=main { controller.currentPosition }
        abandonFocus()
        Thread.sleep(300)
        assertFalse(main { controller.isPlaying })
        main { controller.play() }
        expectRewind(0,paused-5000)
    }

    @Test fun rewindCrossesTrackFilesAndClampsAtBookStart() {
        start(trackCount=2);seek(1,2000)
        val paused=pause()
        main { controller.play() }
        expectRewind(0,30000+paused-5000)
        seek(0,2000)
        pause();main { controller.play() }
        expectRewind(0,0)
    }

    @Test fun explicitSeekWhilePausedPreservesChosenPosition() {
        start();seek();pause()
        main { controller.seekTo(0,18000) }
        await("Paused seek should arrive") { controller.currentPosition==18000L }
        // MediaController masks seek state before the service's acknowledgement arrives.
        Thread.sleep(200)
        assertTrue("Only the explicit seek should be reported: $seeks",seeks.isNotEmpty() && seeks.all { it==0 to 18000L })
        seeks.clear()
        main { controller.play() }
        await("Playback should start at chosen position") { controller.isPlaying }
        Thread.sleep(150)
        assertTrue("Play should not add a seek: $seeks",seeks.isEmpty())
        assertTrue(main { controller.currentPosition>=18000 })
    }

    @Test fun replacingPausedQueueDoesNotApplyAnotherRewind() {
        start();seek();pause()
        val book=addBook("Another book")
        val tracks=runBlocking { app.store.tracks(book.id) }
        val items=tracks.map { track -> MediaItem.Builder().setUri(track.uri).setMediaId("${book.id}:${track.id}")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(book.title).setExtras(Bundle().apply { putLong("bookId",book.id);putLong("offset",track.offset);putLong("progressRevision",book.progressRevision) }).build()).build() }
        main { controller.setMediaItems(items,0,12000);controller.prepare();controller.play() }
        await("Replacement book should play") { controller.isPlaying && controller.currentMediaItem?.mediaMetadata?.extras?.getLong("bookId")==book.id }
        assertTrue(seeks.isEmpty())
        assertTrue(main { controller.currentPosition>=12000 })
    }
}
