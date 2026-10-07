package app.sonder.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import app.sonder.SonderApp
import app.sonder.data.*
import app.sonder.download.AudioBookBay
import app.sonder.download.RealDebrid
import app.sonder.media.PlaybackService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class DeviceScanState(val running:Boolean=false,val completed:Boolean=false,val result:DeviceScanner.Result=DeviceScanner.Result(emptyList()),val error:String="")
data class UndoNotice(val message:String,val undo:()->Unit)
data class Playback(val bookId:Long=0,val position:Long=0,val playing:Boolean=false,val buffering:Boolean=false,val speed:Float=1f,val error:String="")
data class FindState(val query:String="",val loading:Boolean=false,val results:List<AudioBookBay.Listing> = emptyList(),val plan:AudioBookBay.Plan?=null,val page:Int=0,val next:Boolean=false,val error:String="",val searched:Boolean=false)
data class FindDetails(val listing:AudioBookBay.Listing,val details:AudioBookBay.Details?=null,val error:String="")
/** Shared files Android must confirm before deleting, waiting for the activity to show the system dialog. */
data class PendingDelete(val book:Book,val sender:android.content.IntentSender,val deleted:Int,val confirm:Int,val failed:Int,val launched:Boolean=false)
data class AccountState(val checking:Boolean=false,val user:RealDebrid.User?=null,val error:String="")
@UnstableApi
class LibraryViewModel(application:Application,private val saved:androidx.lifecycle.SavedStateHandle):AndroidViewModel(application) {
    private val app=application as SonderApp
    val updater=app.updater
    val store=app.store
    val preferences=app.preferences
    val library=store.library
    val settings=preferences.settings
    val importer=app.importer
    val importProgress=importer.progress
    val sleep=PlaybackService.timer
    private val playerState=MutableStateFlow(Playback())
    val playback=playerState.asStateFlow()
    private val noticeState=MutableStateFlow("")
    val notice=noticeState.asStateFlow()
    private val errorHandler=CoroutineExceptionHandler { _,error -> android.util.Log.e("Sonder","Library operation failed",error);notice("Could not complete this action. Check file access and available storage, then try again.") }
    private var controller:MediaController?=null
    private val controllerFuture=MediaController.Builder(app,SessionToken(app,ComponentName(app,PlaybackService::class.java))).buildAsync()
    private var importJob:Job?=null
    private var scanJob:Job?=null
    private val scanState=MutableStateFlow(DeviceScanState())
    val deviceScan=scanState.asStateFlow()
    fun scanDevice(includeVideo:Boolean) {
        if(scanJob?.isActive==true) return
        scanJob=viewModelScope.launch(errorHandler) {
            scanState.value=DeviceScanState(running=true)
            try {
                val result=DeviceScanner(app).scan(includeVideo,store.knownUris())
                scanState.value=DeviceScanState(result=result,completed=true)
            } catch(e:CancellationException) { scanState.value=DeviceScanState();throw e }
            catch(e:Exception) { scanState.value=DeviceScanState(error=e.message ?: "Could not scan shared media. Try choosing a folder.") }
        }
    }
    fun cancelScan() { scanJob?.cancel() }
    fun importDiscovered(files:List<DeviceScanner.File>) {
        if(importJob?.isActive==true) { notice("An import is already running.");return }
        importJob=viewModelScope.launch(errorHandler) { importer.discovered(files);refreshActiveQueue()
            val known=withContext(Dispatchers.IO) { store.knownUris().map { MediaIdentity.of(app,Uri.parse(it)) }.toSet() }
            val updated=withContext(Dispatchers.IO) { scanState.value.result.files.map { it.copy(imported=MediaIdentity.of(app,it.uri) in known) } }
            scanState.value=scanState.value.copy(result=scanState.value.result.copy(files=updated)) }
    }
    private var loadedTracks:List<Track> = emptyList()
    private var playingJob:Job?=null
    init {
        perform { store.refresh() }
        viewModelScope.launch(errorHandler) { app.storageError.collect { if(it.isNotBlank()) notice(it) } }
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.onSuccess { c ->
                controller=c
                c.addListener(object:Player.Listener {
                    override fun onEvents(player:Player,events:Player.Events) { updatePlayback() }
                    override fun onPlayerError(error:PlaybackException) { playerState.value=playerState.value.copy(error="Cannot play this file. Check its folder access or audio codec.");notice("Playback failed. Try reselecting the source folder.") }
                })
                viewModelScope.launch(errorHandler) {
                    while(isActive) { updatePlayback();delay(300) }
                }
            }.onFailure { notice("Player unavailable. Close and reopen Sonder.") }
        },androidx.core.content.ContextCompat.getMainExecutor(app))
    }
    private fun updatePlayback() {
        val c=controller ?: return
        val extras=c.currentMediaItem?.mediaMetadata?.extras
        val id=extras?.getLong("bookId") ?: 0
        playerState.value=Playback(id,(extras?.getLong("offset") ?: 0)+c.currentPosition.coerceAtLeast(0),c.isPlaying,c.playbackState==Player.STATE_BUFFERING,c.playbackParameters.speed,if(c.playerError==null) "" else playerState.value.error)
    }
    fun play(book:Book,position:Long?=null,forceReload:Boolean=false) {
        playingJob?.cancel()
        playingJob=viewModelScope.launch(errorHandler) {
            val c=controller ?: run { notice("Player is connecting. Try again in a moment.");return@launch }
            if(!forceReload && playback.value.bookId==book.id && c.mediaItemCount>0) { if(position!=null) { seek(position);c.play() } else toggle();return@launch }
            loadedTracks=store.tracks(book.id)
            if(loadedTracks.isEmpty()) { notice("No audio tracks are linked to this book.");return@launch }
            val pos=position ?: if(book.finished) 0 else (book.position-preferences.settings.value.smartRewind*1000).coerceAtLeast(0)
            val index=loadedTracks.indexOfLast { it.offset<=pos }.coerceAtLeast(0)
            val items=loadedTracks.map { t ->
                val extras=Bundle().apply { putLong("bookId",book.id);putLong("offset",t.offset);putLong("progressRevision",book.progressRevision) }
                val metadata=MediaMetadata.Builder().setTitle(book.title).setArtist(book.author).setAlbumTitle(t.name).setExtras(extras)
                if(book.cover.isNotEmpty()) metadata.setArtworkUri(Uri.fromFile(File(book.cover)))
                MediaItem.Builder().setMediaId("${book.id}:${t.id}").setUri(Uri.parse(t.uri)).setMediaMetadata(metadata.build()).build()
            }
            store.startListening(book.id)
            c.setMediaItems(items,index,(pos-loadedTracks[index].offset).coerceAtLeast(0));c.setPlaybackParameters(PlaybackParameters(book.speed,if(settings.value.preservePitch) 1f else book.speed));c.prepare();c.play()

        }
    }
    fun toggle() { controller?.let { if(it.isPlaying) it.pause() else { if(it.playbackState==Player.STATE_ENDED) it.seekTo(0,0);it.play() } } }
    fun seek(position:Long) { viewModelScope.launch(errorHandler) {
        val c=controller ?: return@launch
        val tracks=if(loadedTracks.firstOrNull()?.bookId==playback.value.bookId) loadedTracks else store.tracks(playback.value.bookId).also { loadedTracks=it }
        if(tracks.isEmpty()) return@launch
        val total=tracks.last().offset+tracks.last().duration
        val pos=position.coerceIn(0,(total-1).coerceAtLeast(0));val index=tracks.indexOfLast { it.offset<=pos }.coerceAtLeast(0)
        c.seekTo(index,pos-tracks[index].offset);store.progress(playback.value.bookId,pos,0)
    } }
    fun skip(seconds:Int) = seek(playback.value.position+seconds*1000L)
    fun speed(value:Float) { controller?.setPlaybackParameters(PlaybackParameters(value,if(settings.value.preservePitch) 1f else value)) }
    fun timer(minutes:Int) { controller?.sendCustomCommand(SessionCommand(PlaybackService.TIMER,Bundle.EMPTY),Bundle().apply { putInt("minutes",minutes) }) }
    fun importFiles(uris:List<Uri>) { if(importJob?.isActive==true) { notice("An import is already running.");return };importJob=viewModelScope.launch(errorHandler) { importer.files(uris);refreshActiveQueue() } }
    fun importFolder(uri:Uri) { if(importJob?.isActive==true) { notice("An import is already running.");return };importJob=viewModelScope.launch(errorHandler) { importer.folder(uri);refreshActiveQueue() } }
    private suspend fun refreshActiveQueue() {
        val before=playback.value
        val book=library.value.books.firstOrNull { it.id==before.bookId } ?: return
        val tracks=store.tracks(book.id)
        val old=loadedTracks
        if(old.map { it.uri }==tracks.map { it.uri }) return
        val currentUri=controller?.currentMediaItem?.localConfiguration?.uri?.toString()
        val track=tracks.firstOrNull { it.uri==currentUri }
        val pos=if(track!=null) track.offset+(controller?.currentPosition ?: 0) else book.position
        play(book,pos,forceReload=true);playingJob?.join()
        if(!before.playing) controller?.pause()
    }
    // Stop cancels the import on screen, which may belong to a download; a stopped download can be retried.
    fun cancelImport() { importer.cancelRunning() }
    fun forgetFolder(uri:String) { perform { store.forgetFolder(uri) } }
    fun rescan() { if(importJob?.isActive==true) return;importJob=viewModelScope.launch(errorHandler) { library.value.folders.forEach { importer.folder(Uri.parse(it)) };refreshActiveQueue();notice("Folder scan finished.") } }
    fun changeCover(id:Long,uri:Uri) { viewModelScope.launch(errorHandler) {
        runCatching {
            val path=withContext(Dispatchers.IO) {
                val bytes=app.contentResolver.openInputStream(uri)?.use { it.readBounded(16*1024*1024+1) } ?: error("Cannot open image")
                require(bytes.size<=16*1024*1024) { "Choose an image smaller than 16 MB" }
                importer.saveCover(bytes).also { require(it.isNotBlank()) { "Could not decode this image" } }
            }
            val book=library.value.books.firstOrNull { it.id==id } ?: run { File(path).delete();return@launch }
            store.update(book.copy(cover=path))
            if(book.cover.isNotBlank()) withContext(Dispatchers.IO) { File(book.cover).delete() }
        }.onSuccess { notice("Cover updated.") }.onFailure { notice(it.message ?: "Could not update cover.") }
    } }
    private val historySavingState=MutableStateFlow(false)
    val historySaving=historySavingState.asStateFlow()
    fun saveReading(entry:ReadingEntry,promptId:String?=null,onSaved:()->Unit) {
        if(historySavingState.value) return
        historySavingState.value=true
        viewModelScope.launch(errorHandler) {
            try { store.saveReading(entry,promptId);onSaved();notice("Reading history saved.") }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { notice(e.message ?: "Could not save reading history. Check available storage.") }
            finally { historySavingState.value=false }
        }
    }
    fun deleteReading(id:String) {
        val entry=library.value.readingHistory.firstOrNull { it.id==id }
        perform { store.deleteReading(id);if(entry!=null) offerUndo("Reading entry deleted") { perform { store.saveReading(entry) } } else notice("Removed this reading history entry.") }
    }
    fun dismissCompletion(id:String) { perform { store.dismissCompletion(id) } }
    private fun perform(block:suspend ()->Unit) { viewModelScope.launch(errorHandler) { try { block() } catch(e:CancellationException) { throw e } catch(e:Exception) { notice("Could not save the library. Check available storage and try again.") } } }
    fun edit(book:Book) { perform { store.update(book) } }
    fun markStatus(book:Book,status:ListeningStatus) {
        playingJob?.cancel()
        val before=library.value.books.firstOrNull { it.id==book.id } ?: book
        perform {
            store.markStatus(book.id,status)
            if(playback.value.bookId==book.id) {
                controller?.pause();controller?.stop();controller?.clearMediaItems();loadedTracks=emptyList();updatePlayback();timer(0)
            }
            offerUndo("Marked as ${status.label.lowercase()}") { perform { store.restoreStatus(before) } }
        }
    }
    private suspend fun removeBook(book:Book) { if(playback.value.bookId==book.id) { controller?.stop();controller?.clearMediaItems() };store.remove(book.id);if(book.cover.isNotEmpty()) withContext(Dispatchers.IO) { File(book.cover).delete() } }
    fun remove(book:Book) { perform { removeBook(book);notice("Removed from library. The original audio files are unchanged.") } }
    private val deleteState=MutableStateFlow<PendingDelete?>(null)
    val pendingDelete=deleteState.asStateFlow()
    /** Removes the book from the library and deletes its audio from the phone. */
    fun deleteFromPhone(book:Book) { perform {
        if(playback.value.bookId==book.id) { controller?.stop();controller?.clearMediaItems() }
        val uris=store.tracks(book.id).map { Uri.parse(it.uri) }
        val others=(store.knownUris()-uris.map { it.toString() }.toSet()).map { Uri.parse(it) }
        // A Sonder download is deleted as its whole folder, cover and chapter sheet included, unless other books share it.
        if(withContext(Dispatchers.IO) { downloads.deleteBookFolder(uris,others) }) { removeBook(book);notice("Deleted ${book.title} from your phone.");return@perform }
        val result=withContext(Dispatchers.IO) { MediaFiles.delete(app,uris) }
        val sender=withContext(Dispatchers.IO) { runCatching { MediaFiles.confirmation(app,result.confirm) }.getOrNull() }
        if(sender!=null) {
            // Saved so the outcome is still applied if Android stops Sonder while its confirmation is open.
            saved["deleteBook"]=book.id;saved["deleteCounts"]=intArrayOf(result.deleted,result.confirm.size,result.failed)
            deleteState.value=PendingDelete(book,sender,result.deleted,result.confirm.size,result.failed)
        }
        else finishDelete(book,result.deleted,result.failed+result.confirm.size,cancelled=false)
    } }
    fun deleteLaunched() { deleteState.value=deleteState.value?.copy(launched=true) }
    fun deleteConfirmed(confirmed:Boolean) {
        val pending=deleteState.value
        val id=pending?.book?.id ?: saved.get<Long>("deleteBook") ?: return
        val counts=pending?.let { intArrayOf(it.deleted,it.confirm,it.failed) } ?: saved.get<IntArray>("deleteCounts") ?: return
        deleteState.value=null;saved.remove<Long>("deleteBook");saved.remove<IntArray>("deleteCounts")
        perform {
            if(pending==null) store.refresh()
            val book=pending?.book ?: library.value.books.firstOrNull { it.id==id } ?: return@perform
            val (deleted,confirm,failed)=counts
            finishDelete(book,deleted+if(confirmed) confirm else 0,failed+if(confirmed) 0 else confirm,cancelled=!confirmed)
        }
    }
    private suspend fun finishDelete(book:Book,deleted:Int,failed:Int,cancelled:Boolean) {
        // Keep the book when nothing was deleted, so its files stay playable.
        if(deleted==0 && (failed>0 || cancelled)) { notice(if(cancelled) "Nothing was deleted." else "Android didn't let Sonder delete these files. Delete them with your file manager, then remove the book.");return }
        removeBook(book)
        notice(if(failed==0) "Deleted ${book.title} from your phone." else "Deleted ${book.title}, but $failed ${if(failed==1) "file" else "files"} couldn't be deleted. Remove ${if(failed==1) "it" else "them"} with your file manager.")
    }
    fun bookmark(note:String,position:Long?=null,bookId:Long?=null) { val p=playback.value;val id=bookId ?: p.bookId; if(id>0) perform { store.addBookmark(id,position ?: p.position,note.ifBlank { "Bookmark" });notice("Bookmark saved.") } }
    fun deleteBookmark(id:Long) {
        val mark=library.value.bookmarks.firstOrNull { it.id==id }
        perform { store.removeBookmark(id);if(mark!=null) offerUndo("Bookmark deleted") { perform { store.restoreBookmark(mark) } } }
    }
    fun editBookmark(id:Long,note:String) { perform { store.editBookmark(id,note) } }
    fun export(uri:Uri) { viewModelScope.launch(errorHandler) {
        runCatching { val json=store.export();withContext(Dispatchers.IO) { app.contentResolver.openOutputStream(uri,"wt")?.use { it.write(json.toByteArray()) } ?: error("Cannot write backup") } }.onSuccess { notice("Backup saved. Audio files are not included.") }.onFailure { notice(it.message ?: "Backup could not be saved.") }
    } }
    fun restore(uri:Uri) { viewModelScope.launch(errorHandler) {
        runCatching { val text=withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)?.use { val bytes=it.readBounded(8_000_001);require(bytes.size<=8_000_000);bytes.toString(Charsets.UTF_8) } ?: error("Cannot open backup") };store.restore(text) }.onSuccess { controller?.pause();controller?.stop();controller?.clearMediaItems();loadedTracks=emptyList();updatePlayback();notice("Restored $it library books. ${library.value.readingHistory.size} reading history entries available.") }.onFailure { notice("Could not restore this backup. Check the file and version.") }
    } }
    val downloads=app.downloads
    private val findState=MutableStateFlow(FindState())
    val find=findState.asStateFlow()
    private val detailState=MutableStateFlow<FindDetails?>(null)
    val findDetails=detailState.asStateFlow()
    private var findJob:Job?=null
    private var detailJob:Job?=null
    fun search(query:String,more:Boolean=false) {
        val current=findState.value
        if(query.isBlank() || more && (current.loading || !current.next)) return
        findJob?.cancel()
        findState.value=if(more) current.copy(loading=true,error="") else FindState(query.trim(),loading=true,searched=true)
        findJob=viewModelScope.launch(errorHandler) {
            try {
                val site=downloads.settings.value.site
                if(more && current.plan!=null) {
                    // More pages come from the search that first found something, ranked against what was typed.
                    val page=withContext(Dispatchers.IO) { AudioBookBay.search(site,current.plan.query,current.page+1,current.plan.titles) }
                    findState.value=findState.value.copy(loading=false,results=(findState.value.results+AudioBookBay.rank(page.results,query)).distinctBy { it.url },page=page.page,next=page.next && page.results.isNotEmpty())
                } else {
                    val found=withContext(Dispatchers.IO) { AudioBookBay.find(site,query) }
                    findState.value=findState.value.copy(loading=false,results=found.results,plan=found.more,page=found.page,next=found.next)
                }
            } catch(e:CancellationException) { throw e }
            catch(e:Exception) { findState.value=findState.value.copy(loading=false,error=e.message ?: "Search failed. Try again.") }
        }
    }
    fun openListing(listing:AudioBookBay.Listing) {
        detailJob?.cancel();detailState.value=FindDetails(listing)
        detailJob=viewModelScope.launch(errorHandler) {
            try { val details=withContext(Dispatchers.IO) { AudioBookBay.details(downloads.settings.value.site,listing) };detailState.value=detailState.value?.takeIf { it.listing==listing }?.copy(details=details) }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { detailState.value=detailState.value?.takeIf { it.listing==listing }?.copy(error=e.message ?: "Couldn't open this book. Try again.") }
        }
    }
    fun closeListing() { detailJob?.cancel();detailState.value=null }
    fun download(details:AudioBookBay.Details) {
        runCatching { downloads.enqueue(details) }.onSuccess { notice(it ?: "Downloading ${details.title}. It will appear in your library when it's ready.") }
            .onFailure { android.util.Log.e("Sonder","Could not start download",it);notice("Couldn't start the download. Open Sonder and try again.") }
    }
    private val accountState=MutableStateFlow(AccountState())
    val account=accountState.asStateFlow()
    /** Checks a token with Real-Debrid. A blank token checks the saved one. Valid tokens are saved. */
    fun connectRealDebrid(token:String="",onSaved:()->Unit={}) {
        val value=token.trim().ifBlank { downloads.settings.value.token }
        if(value.isBlank() || accountState.value.checking) return
        accountState.value=AccountState(checking=true)
        verifyJob=viewModelScope.launch(errorHandler) {
            try { val user=downloads.verify(value);downloads.saveToken(value);accountState.value=AccountState(user=user);onSaved() }
            catch(e:CancellationException) { accountState.value=AccountState();throw e }
            catch(e:Exception) { accountState.value=AccountState(error=e.message ?: "Couldn't check this token.") }
        }
    }
    // Cancel a check in flight so its success can't save the token again.
    fun disconnectRealDebrid() { verifyJob?.cancel();downloads.saveToken("");accountState.value=AccountState() }
    private var verifyJob:Job?=null
    fun setDownloadSite(value:String):Boolean = runCatching { downloads.saveSite(value) }.onFailure { notice(it.message ?: "Enter a web address like audiobookbay.lu") }.isSuccess
    fun notice(text:String) { noticeState.value=text }
    private val undoState=MutableStateFlow<UndoNotice?>(null)
    val undo=undoState.asStateFlow()
    private fun offerUndo(message:String,undo:()->Unit) { undoState.value=UndoNotice(message,undo) }
    fun clearUndo(notice:UndoNotice) { undoState.compareAndSet(notice,null) }
    fun clearNotice() { noticeState.value="" }
    override fun onCleared() { MediaController.releaseFuture(controllerFuture);super.onCleared() }
}
