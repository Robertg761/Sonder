package app.sonder.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import app.sonder.data.Importer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

data class DownloadSettings(val token:String="",val folder:String="",val site:String=AudioBookBay.DEFAULT_SITE) { val ready:Boolean get()=token.isNotBlank() && folder.isNotBlank() }

data class DownloadJob(
    val id:String=UUID.randomUUID().toString(),val title:String,val author:String="",val page:String="",val hash:String,val magnet:String,val cover:String="",
    val state:State=State.QUEUED,val message:String="Waiting to start",val progress:Float=-1f,val bytes:Long=0,val total:Long=0,
    val torrent:String="",val folder:String="",val added:Long=System.currentTimeMillis(),
    /** Set once importing starts. From then on the library may point at the folder, so it is never deleted. */
    val imported:Boolean=false
) {
    enum class State { QUEUED,WORKING,DONE,FAILED }
    val active:Boolean get()=state==State.QUEUED || state==State.WORKING
    fun json():JSONObject=JSONObject().put("id",id).put("title",title).put("author",author).put("page",page).put("hash",hash).put("magnet",magnet).put("cover",cover)
        .put("state",state.name).put("message",message).put("torrent",torrent).put("folder",folder).put("added",added).put("imported",imported)
    companion object {
        fun fromJson(j:JSONObject)=DownloadJob(j.getString("id"),j.getString("title"),j.optString("author"),j.optString("page"),j.getString("hash"),j.getString("magnet"),j.optString("cover"),
            runCatching { State.valueOf(j.getString("state")) }.getOrDefault(State.FAILED),j.optString("message"),torrent=j.optString("torrent"),folder=j.optString("folder"),added=j.optLong("added"),imported=j.optBoolean("imported"))
    }
}

/**
 * AudioBookBay → Real-Debrid → download folder → library. Jobs run one at a time inside [DownloadService],
 * which keeps the process alive. The job list is saved so failures and finished downloads survive restarts.
 */
class Downloads(private val context:Context,private val importer:Importer) {
    private companion object {
        const val IMPORTING="Adding to your library"
        /** How long [preflight] watches a new torrent for signs that anyone is sharing it. */
        const val CHECK_TIME=60_000L
        const val NO_SEEDERS="Real-Debrid couldn't find anyone sharing this upload right now, so it won't download. Try again another time, or pick another upload."
    }
    private val prefs=context.getSharedPreferences("downloads",Context.MODE_PRIVATE)
    private val settingsState=MutableStateFlow(DownloadSettings(prefs.getString("token","").orEmpty(),prefs.getString("folder","").orEmpty(),prefs.getString("site",AudioBookBay.DEFAULT_SITE).orEmpty()))
    val settings=settingsState.asStateFlow()
    private val jobState=MutableStateFlow(load())
    val jobs=jobState.asStateFlow()
    // Downloads from before folders were recorded separately seed the record from the download list.
    init { if(!prefs.contains("bookFolders")) prefs.edit().putStringSet("bookFolders",jobState.value.map { it.folder }.filter { it.isNotBlank() }.toSet()).apply() }
    private val lock=Any()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private var current:Job?=null
    private var currentId:String?=null

    fun saveToken(token:String) { prefs.edit().putString("token",token.trim()).apply();settingsState.value=settingsState.value.copy(token=token.trim()) }
    fun saveFolder(uri:String) { prefs.edit().putString("folder",uri).apply();settingsState.value=settingsState.value.copy(folder=uri) }
    fun saveSite(site:String) { val value=AudioBookBay.site(site);prefs.edit().putString("site",value).apply();settingsState.value=settingsState.value.copy(site=value) }
    suspend fun verify(token:String):RealDebrid.User = withContext(Dispatchers.IO) { RealDebrid(token.trim()).user() }

    /** Why a book can't be downloaded again, or null when it can. */
    fun blocked(hash:String):String? = when(jobState.value.firstOrNull { it.hash==hash }?.state) {
        DownloadJob.State.QUEUED,DownloadJob.State.WORKING -> "This book is already downloading."
        DownloadJob.State.DONE -> "You've already downloaded this book. Remove it from Downloads to get it again."
        else -> null
    }
    /**
     * Queues a download, on the Real-Debrid [torrent] that [preflight] found usable when there is one.
     * Returns a message when the book is already queued or downloaded.
     */
    fun enqueue(details:AudioBookBay.Details,torrent:String=""):String? {
        synchronized(lock) {
            val existing=jobState.value.firstOrNull { it.hash==details.hash }
            blocked(details.hash)?.let { message -> if(torrent.isNotBlank() && torrent!=existing?.torrent) forget(torrent);return message }
            if(existing!=null) {
                // A failed download starts again, moving to the checked torrent if it has a new one.
                if(torrent.isNotBlank() && torrent!=existing.torrent) { if(existing.torrent.isNotBlank()) forget(existing.torrent);update(existing.id) { it.copy(torrent=torrent) } }
                retry(existing.id);return null
            }
            publish(listOf(DownloadJob(title=details.title,author=details.author,page=details.url,hash=details.hash,magnet=details.magnet,cover=details.cover,torrent=torrent))+jobState.value)
        }
        start();return null
    }
    /**
     * Makes sure Real-Debrid can actually get a book before it's queued: adds the magnet (or reuses a failed
     * download's torrent), chooses its files, and watches for up to a minute for anyone sharing it. Returns the
     * torrent for [enqueue]. Throws with a message for the person when the upload can't download now. The torrent
     * is deleted then, and when the check is cancelled, so a failed check leaves nothing running on the account.
     */
    suspend fun preflight(details:AudioBookBay.Details):String = withContext(Dispatchers.IO) {
        val token=settingsState.value.token
        check(token.isNotBlank()) { "Add your Real-Debrid API token in Settings." }
        val rd=RealDebrid(token)
        val earlier=jobState.value.firstOrNull { it.hash==details.hash && !it.active }?.torrent.orEmpty()
        val id=earlier.takeIf { it.isNotBlank() && runCatching { rd.torrent(it) }.isSuccess } ?: rd.addMagnet(details.magnet)
        var usable=false
        try {
            val started=SystemClock.elapsedRealtime();var selected=0L
            while(SystemClock.elapsedRealtime()-started<CHECK_TIME) {
                val t=rd.torrent(id)
                when {
                    RealDebrid.alive(t) -> { usable=true;return@withContext id }
                    t.status in RealDebrid.failed -> error(RealDebrid.describe(t.status))
                    t.status=="waiting_files_selection" -> {
                        val wanted=DownloadPlan.wanted(t.files)
                        check(wanted.isNotEmpty()) { unplayable(t.files) }
                        // As in the download itself, a partial selection that doesn't take is widened to every file.
                        val now=SystemClock.elapsedRealtime()
                        if(selected==0L || now-selected>20_000) { rd.select(t.id,if(selected==0L) wanted.map { it.id } else null);selected=now }
                    }
                }
                delay(2000)
            }
            error(NO_SEEDERS)
        } finally { if(!usable) withContext(NonCancellable) { runCatching { rd.delete(id) } } }
    }
    private fun unplayable(files:List<RealDebrid.File>)=if(files.any { DownloadPlan.extension(it.path) in DownloadPlan.archives }) "This upload is packed in an archive, which Sonder can't open." else "This upload has no audio files Sonder can play."
    /** Deletes a torrent from Real-Debrid in the background so it stops using the account. */
    private fun forget(torrent:String) {
        val token=settingsState.value.token
        if(token.isNotBlank()) scope.launch { runCatching { RealDebrid(token).delete(torrent) }.onFailure { android.util.Log.w("Sonder","Could not delete Real-Debrid torrent",it) } }
    }
    fun retry(id:String) { update(id) { if(it.active) it else it.copy(state=DownloadJob.State.QUEUED,message="Waiting to start",progress=-1f) };start() }
    /**
     * Stops and forgets a download. Unfinished downloads also lose their partial files and their Real-Debrid
     * torrent, so the transfer stops using the account. Finished books stay in the library.
     */
    fun remove(id:String) {
        val job=synchronized(lock) {
            val job=jobState.value.firstOrNull { it.id==id } ?: return
            publish(jobState.value.filterNot { it.id==id })
            job to current.takeIf { currentId==id }
        }
        scope.launch {
            job.second?.cancelAndJoin()
            // Keep files the library may already point to.
            if(job.first.state!=DownloadJob.State.DONE && !job.first.imported && job.first.folder.isNotBlank()) runCatching { if(DocumentFile.fromTreeUri(context,Uri.parse(job.first.folder))?.delete()==true) keepFolder(job.first.folder,false) }
            if(job.first.state!=DownloadJob.State.DONE && job.first.torrent.isNotBlank()) forget(job.first.torrent)
        }
    }
    /**
     * Book folders Sonder created, kept apart from the visible download list so clearing finished downloads
     * doesn't forget which folders Delete from phone may remove whole.
     */
    private fun bookFolders():Set<String> = prefs.getStringSet("bookFolders",null).orEmpty().toSet()
    private fun keepFolder(uri:String,keep:Boolean) { synchronized(lock) { prefs.edit().putStringSet("bookFolders",if(keep) bookFolders()+uri else bookFolders()-uri).apply() } }
    /**
     * Deletes the book folder a download created when [tracks] all live in it, and forgets it and its download so
     * the book can be downloaded again. Returns false when the tracks didn't come from a Sonder download, when that
     * download is still running, or when [others] (other books' tracks) also live in the folder, as when one upload
     * held several books.
     */
    fun deleteBookFolder(tracks:List<Uri>,others:List<Uri> = emptyList()):Boolean {
        if(tracks.isEmpty()) return false
        // Document IDs are opaque, so membership comes from the provider's own child listing. Downloads are
        // flattened, so every track is a direct child of its book folder.
        fun key(uri:Uri)=runCatching { "${uri.authority}|${android.provider.DocumentsContract.getDocumentId(uri)}" }.getOrNull()
        val ids=tracks.map { key(it) ?: return false }.toSet()
        val running=jobState.value.filter { it.active }.map { it.folder }.toSet()
        val candidates=bookFolders().filter { it !in running && Uri.parse(it).authority==tracks.first().authority }
        val (folder,children)=candidates.firstNotNullOfOrNull { uri -> children(Uri.parse(uri))?.takeIf { it.containsAll(ids) }?.let { uri to it } } ?: return false
        if(others.any { key(it) in children }) return false
        val deleted=runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver,Uri.parse(folder)) }.getOrDefault(false)
        if(deleted) { keepFolder(folder,false);synchronized(lock) { publish(jobState.value.filterNot { it.folder==folder }) } }
        return deleted
    }
    private fun children(folder:Uri):Set<String>? = runCatching {
        val provider=android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(folder,android.provider.DocumentsContract.getDocumentId(folder))
        context.contentResolver.query(provider,arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID),null,null,null)?.use { c -> buildSet { while(c.moveToNext()) add("${folder.authority}|${c.getString(0)}") } }
    }.getOrNull()
    fun clearFinished() { synchronized(lock) { publish(jobState.value.filterNot { it.state==DownloadJob.State.DONE }) } }
    fun pending()=jobState.value.any { it.state==DownloadJob.State.QUEUED }
    private fun start() {
        try { ContextCompat.startForegroundService(context,Intent(context,DownloadService::class.java)) }
        catch(e:IllegalStateException) {
            // Android won't start the service from the background, as when a check finishes after Sonder was left. Nothing runs queued jobs then.
            synchronized(lock) { publish(jobState.value.map { if(it.state==DownloadJob.State.QUEUED) it.copy(state=DownloadJob.State.FAILED,message="Sonder was in the background, so this download didn't start. Tap Retry.") else it }) }
            throw e
        }
    }

    /** Runs queued jobs until none remain. Called by [DownloadService]. */
    suspend fun work() = coroutineScope {
        while(isActive) {
            val job=synchronized(lock) {
                val next=jobState.value.lastOrNull { it.state==DownloadJob.State.QUEUED } ?: return@coroutineScope
                currentId=next.id
                launch { process(next.id) }.also { current=it }
            }
            job.join()
            synchronized(lock) { current=null;currentId=null }
        }
    }

    private suspend fun process(id:String) {
        fun status(message:String,progress:Float=-1f,bytes:Long=0,total:Long=0)=update(id) { it.copy(state=DownloadJob.State.WORKING,message=message,progress=progress,bytes=bytes,total=total) }
        try {
            status("Starting")
            val config=settingsState.value
            check(config.token.isNotBlank()) { "Add your Real-Debrid API token in Settings." }
            val root=config.folder.takeIf { it.isNotBlank() }?.let { runCatching { DocumentFile.fromTreeUri(context,Uri.parse(it)) }.getOrNull() }?.takeIf { it.isDirectory && it.canWrite() }
                ?: error("Choose a download folder in Settings, then tap Retry.")
            val rd=RealDebrid(config.token)
            val torrent=cached(rd,id,::status)
            val selected=torrent.files.filter { it.selected }.sortedBy { it.id }
            val wanted=DownloadPlan.wanted(selected)
            val names=DownloadPlan.names(wanted)
            // Links follow the selected files in order. If Real-Debrid returns a different count, trust its file names instead.
            val planned:List<Pair<String,RealDebrid.File?>> = if(selected.size==torrent.links.size) torrent.links.zip(selected).filter { it.second in wanted } else torrent.links.map { it to null }
            check(planned.isNotEmpty()) { "Real-Debrid didn't return any audio for this upload." }
            val dir=folder(root,id)
            val total=planned.sumOf { it.second?.bytes ?: 0L }.takeIf { planned.all { p -> p.second!=null } } ?: 0L
            val used=names.values.map { it.lowercase() }.toMutableSet()
            var done=0L
            for((index,item) in planned.withIndex()) {
                val (link,file)=item
                val label="Downloading ${index+1} of ${planned.size}"
                status(label,if(total>0) done.toFloat()/total else -1f,done,total)
                val direct=rd.unrestrict(link)
                val name=if(file!=null) names.getValue(file.id) else {
                    val ext=DownloadPlan.extension(direct.name)
                    check(ext !in DownloadPlan.archives) { "Real-Debrid packed these files into an archive, which Sonder can't open." }
                    if(!DownloadPlan.isAudio(direct.name) && ext!="cue") continue
                    DownloadPlan.unique(DownloadPlan.sanitize(direct.name),used)
                }
                val size=direct.size.takeIf { it>0 } ?: file?.bytes ?: 0L
                var last=0L
                fetch(direct.url,dir,name,size) { written ->
                    val now=SystemClock.elapsedRealtime()
                    if(now-last>500) { last=now;status(label,if(total>0) (done+written).toFloat()/total else -1f,done+written,total) }
                }
                done+=size
            }
            update(id) { it.copy(state=DownloadJob.State.WORKING,message=IMPORTING,progress=1f,bytes=done,total=total,imported=true) }
            val result=importer.downloaded(dir.uri)
            val added=Regex("Imported (\\d+)").find(result.current)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            if(added==0 && result.errors.firstOrNull()!="No new supported audio files found.") error(result.errors.firstOrNull() ?: "Sonder couldn't add this download to your library.")
            update(id) { it.copy(state=DownloadJob.State.DONE,message="In your library",progress=1f) }
        } catch(e:CancellationException) {
            update(id) { it.copy(state=DownloadJob.State.FAILED,message="Stopped. Tap Retry to continue.",progress=-1f) };throw e
        } catch(e:Exception) {
            android.util.Log.w("Sonder","Download failed",e)
            update(id) { it.copy(state=DownloadJob.State.FAILED,message=e.message ?: "Download failed. Tap Retry.",progress=-1f) }
        }
    }

    /** Adds the magnet if needed, chooses files, and waits until Real-Debrid has the whole upload. */
    private suspend fun cached(rd:RealDebrid,id:String,status:(String,Float,Long,Long)->Any?):RealDebrid.Torrent {
        // A torrent that sits in one state with no progress for too long is stuck, whatever the state is.
        var seen="";var changed=SystemClock.elapsedRealtime();var selected=0L
        while(true) {
            currentCoroutineContext().ensureActive()
            var job=find(id)
            if(job.torrent.isBlank()) { status("Sending to Real-Debrid",-1f,0,0);val torrent=withContext(Dispatchers.IO) { rd.addMagnet(job.magnet) };job=update(id) { it.copy(torrent=torrent) } ?: run { runCatching { rd.delete(torrent) };throw CancellationException("Download removed") } }
            val t=try { withContext(Dispatchers.IO) { rd.torrent(job.torrent) } }
                catch(e:RealDebrid.Error) { if(e.status==404) { update(id) { it.copy(torrent="") };seen="";changed=SystemClock.elapsedRealtime();selected=0L;continue } else throw e }
            val now=SystemClock.elapsedRealtime()
            if("${t.status}:${t.progress}"!=seen) { seen="${t.status}:${t.progress}";changed=now }
            val waited=(now-changed)/60000
            when {
                t.status=="downloaded" -> return t
                t.status in RealDebrid.failed -> error(RealDebrid.describe(t.status))
                t.status=="waiting_files_selection" -> {
                    val wanted=DownloadPlan.wanted(t.files)
                    check(wanted.isNotEmpty()) { unplayable(t.files) }
                    check(now-changed<5*60*1000L) { "Real-Debrid didn't start this torrent after Sonder chose its ${wanted.size} files. Remove the download and try again, or try another upload." }
                    // Some uploads stay waiting after a partial selection, so after 45 seconds every file is selected.
                    // Only the wanted files are still downloaded to the phone; the rest stay on Real-Debrid.
                    if(selected==0L || now-selected>45_000) { withContext(Dispatchers.IO) { rd.select(t.id,if(selected==0L) wanted.map { it.id } else null) };selected=now }
                    status("Asking Real-Debrid to fetch ${wanted.size} ${if(wanted.size==1) "file" else "files"}"+if(waited>0) " · $waited min" else "",-1f,0,0)
                }
                else -> {
                    check(t.status!="downloading" || t.seeders>0 || now-changed<20*60*1000L) { "Everyone sharing this upload went offline, so Real-Debrid can't finish it. Try again another time, or pick another upload." }
                    check(now-changed<60*60*1000L) { "Real-Debrid hasn't made progress in an hour (${t.status.replace('_',' ')}). The upload may have no seeders. Try again later." }
                    val seeders=if(t.status=="downloading") " · ${t.seeders} ${if(t.seeders==1) "seeder" else "seeders"}" else ""
                    status(RealDebrid.describe(t.status)+seeders+if(waited>0 && t.status!="downloading") " · $waited min" else "",if(t.status=="downloading") t.progress/100f else -1f,0,0)
                }
            }
            delay(if(t.status=="downloading") 5000 else 2500)
        }
    }

    /** The job's own book folder. A new folder never reuses an existing one, because removing the job deletes it. */
    private fun folder(root:DocumentFile,id:String):DocumentFile {
        val job=find(id)
        if(job.folder.isNotBlank()) runCatching { DocumentFile.fromTreeUri(context,Uri.parse(job.folder)) }.getOrNull()?.takeIf { it.exists() && it.isDirectory }?.let { return it }
        val base=DownloadPlan.folderName(job.title);var name=base;var n=1
        while(root.findFile(name)!=null) name="$base (${++n})"
        val dir=root.createDirectory(name) ?: error("Couldn't create a folder for this book. Choose the download folder again in Settings.")
        keepFolder(dir.uri.toString(),true)
        update(id) { it.copy(folder=dir.uri.toString()) }
        return dir
    }

    /** Streams one file into the folder. Partial files resume with a range request; network drops retry a few times. */
    private suspend fun fetch(url:String,dir:DocumentFile,name:String,size:Long,onProgress:(Long)->Unit) = withContext(Dispatchers.IO) {
        var attempt=0
        while(true) {
            try { fetchOnce(url,dir,name,size,onProgress);return@withContext }
            catch(e:IOException) {
                if(++attempt>=3) throw if(e is Problem) e else IOException("The connection dropped while downloading. Tap Retry to continue.",e)
                delay(5000L*attempt)
            }
        }
    }
    private suspend fun fetchOnce(url:String,dir:DocumentFile,name:String,size:Long,onProgress:(Long)->Unit) {
        var file=dir.findFile(name)
        var offset=file?.takeIf { it.isFile }?.length() ?: 0L
        if(file!=null && size>0 && offset==size) { onProgress(size);return }
        if(file!=null && (!file.isFile || size<=0 || offset>size)) { file.delete();file=null;offset=0 }
        val target=file ?: dir.createFile("application/octet-stream",name) ?: throw Problem("Couldn't create $name in the download folder.")
        val connection=Http.open(url,headers=if(offset>0) mapOf("Range" to "bytes=$offset-") else emptyMap())
        try {
            val code=connection.responseCode
            val append=offset>0 && code==206
            if(!append && code!=200) { if(offset>0) target.delete();throw Problem("The file host returned an error ($code). Tap Retry to get a fresh link.") }
            if(!append) offset=0
            val output=try { context.contentResolver.openOutputStream(target.uri,if(append) "wa" else "wt") } catch(e:Exception) { null }
                ?: if(append) { target.delete();throw IOException("Restarting $name") } else throw Problem("Couldn't write $name to the download folder.")
            output.use { out -> connection.inputStream.use { input ->
                val buffer=ByteArray(256*1024);var written=offset
                while(true) {
                    currentCoroutineContext().ensureActive()
                    val count=input.read(buffer);if(count<0) break
                    out.write(buffer,0,count);written+=count;onProgress(written)
                }
            } }
            if(size>0 && target.length()!=size) throw IOException("$name didn't finish downloading.")
        } finally { connection.disconnect() }
    }

    /** A failure with a message meant for the person, not a dropped connection. */
    private class Problem(message:String):IOException(message)
    private fun find(id:String)=jobState.value.firstOrNull { it.id==id } ?: throw CancellationException("Download removed")
    private fun update(id:String,change:(DownloadJob)->DownloadJob):DownloadJob? = synchronized(lock) {
        val old=jobState.value.firstOrNull { it.id==id } ?: return null
        val new=change(old)
        if(new!=old) {
            val list=jobState.value.map { if(it.id==id) new else it }
            // Progress ticks change often and are not worth saving; everything else is.
            if(new.copy(progress=0f,bytes=0)!=old.copy(progress=0f,bytes=0)) publish(list) else jobState.value=list
        }
        new
    }
    private fun publish(list:List<DownloadJob>) {
        val kept=list.filterIndexed { i,job -> job.active || i<50 }
        jobState.value=kept
        prefs.edit().putString("jobs",JSONArray().apply { kept.forEach { put(it.json()) } }.toString()).apply()
    }
    private fun load():List<DownloadJob> = runCatching {
        val array=JSONArray(prefs.getString("jobs","[]"))
        // Nothing runs before this object exists, so anything that was running was interrupted.
        (0 until array.length()).map { DownloadJob.fromJson(array.getJSONObject(it)) }.map { if(it.active) it.copy(state=DownloadJob.State.FAILED,message="Stopped. Tap Retry to continue.") else it }
    }.getOrDefault(emptyList())
}
