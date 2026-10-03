package app.sonder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import app.sonder.media.ChapterParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.UUID

class Importer(private val context:Context,private val store:LibraryStore) {
    private val mutex=Mutex()
    private val state=MutableStateFlow(ImportProgress())
    val progress=state.asStateFlow()
    companion object { val extensions=setOf("mp3","m4b","m4a","mp4","aac","flac","ogg","opus","wav","wave","webm","mka","mkv","amr","3gp") }
    data class Input(val uri:Uri,val name:String,val size:Long,val folder:String="",val folderName:String="",val siblings:List<DocumentFile> = emptyList())
    private data class Info(val input:Input,val title:String,val album:String,val author:String,val narrator:String,val genre:String,val duration:Long,val cover:String,val chapters:List<Chapter>,val description:String="",val series:String="")
    suspend fun files(uris:List<Uri>) = runImport {
        uris.mapNotNull { uri -> DocumentFile.fromSingleUri(context,uri)?.let { Input(uri,it.name ?: "Untitled",it.length()) } }
    }
    suspend fun discovered(files:List<DeviceScanner.File>) = runImport { files.map { it.input() } }
    suspend fun folder(uri:Uri) = runImport {
        val root=DocumentFile.fromTreeUri(context,uri) ?: error("Cannot open this folder")
        val files=mutableListOf<Input>();val seen=mutableSetOf<String>()
        suspend fun visit(dir:DocumentFile,depth:Int) {
            currentCoroutineContext().ensureActive()
            require(depth<32) { "Folder nesting is too deep" }
            if(!seen.add(dir.uri.toString())) return
            val children=dir.listFiles()
            children.forEach { file ->
                currentCoroutineContext().ensureActive()
                require(files.size<20000) { "Import at most 20,000 files at a time" }
                if(file.isDirectory) visit(file,depth+1)
                else if(file.isFile && file.name?.substringAfterLast('.',"")?.lowercase() in extensions) files+=Input(file.uri,file.name ?: "Untitled",file.length(),dir.uri.toString(),dir.name.orEmpty(),children.toList())
            }
        }
        visit(root,0);store.rememberFolder(uri.toString());files
    }
    private suspend fun runImport(inputs:suspend () -> List<Input>) = withContext(Dispatchers.IO) { mutex.withLock {
        state.value=ImportProgress(true,"Scanning files")
        val errors=mutableListOf<String>();val temporaryCovers=mutableSetOf<String>();var done=0
        try {
            val known=store.knownUris().map { identity(Uri.parse(it)) }.toSet();val candidates=inputs().filter { identity(it.uri) !in known }.distinctBy { identity(it.uri) }
            if(candidates.isEmpty()) { state.value=ImportProgress(errors=listOf("No new supported audio files found."));return@withLock }
            state.value=ImportProgress(true,"Reading metadata",0,candidates.size)
            val info=mutableListOf<Info>()
            for(input in candidates) {
                currentCoroutineContext().ensureActive()
                state.value=state.value.copy(current=input.name,done=done++)
                runCatching { inspect(input) }.onSuccess { info+=it;if(it.cover.isNotBlank()) temporaryCovers+=it.cover }.onFailure { errors+="${input.name}: ${it.message ?: "Could not read audio"}" }
            }
            // Album tags identify books across picker methods. Untagged whole-book containers stay separate.
            val groups=info.groupBy {
                when {
                    it.album.isNotBlank() -> "album:${it.album.trim().lowercase()}:${it.author.trim().lowercase()}"
                    it.input.name.substringAfterLast('.').lowercase() in setOf("m4b","mp4") || it.chapters.size>1 -> "file:${identity(it.input.uri)}"
                    it.input.folder.isNotEmpty() -> "folder:${identity(Uri.parse(it.input.folder))}"
                    else -> "file:${identity(it.input.uri)}"
                }
            }
            var imported=0
            for((source,group) in groups) {
                currentCoroutineContext().ensureActive()
                val ordered=group.sortedWith { a,b -> ChapterParser.naturalComparator.compare(a.input.name,b.input.name) }
                val first=ordered.first();var offset=0L
                val tracks=ordered.mapIndexed { i,item -> Track(uri=item.input.uri.toString(),name=item.title.ifBlank { item.input.name.substringBeforeLast('.') },duration=item.duration,offset=offset,ordinal=i,sortName=item.input.name).also { offset+=item.duration } }
                var chapters=ordered.flatMapIndexed { i,item ->
                    if(item.chapters.isNotEmpty()) item.chapters.map { it.copy(start=it.start+tracks[i].offset,end=if(it.end>0) it.end+tracks[i].offset else 0) }
                    else listOf(Chapter(tracks[i].name,tracks[i].offset))
                }
                val cue=first.input.siblings.firstOrNull { it.name?.endsWith(".cue",true)==true }
                if(cue!=null) runCatching {
                    val text=context.contentResolver.openInputStream(cue.uri)?.use { it.readBounded(1024*1024).toString(Charsets.UTF_8) } ?: ""
                    val cued=ChapterParser.cue(text).mapNotNull { c -> tracks.firstOrNull { it.name.equals(c.file.substringBeforeLast('.'),true) || ordered[it.ordinal].input.name.equals(c.file,true) }?.let { Chapter(c.title,it.offset+c.position) } }
                    if(cued.isNotEmpty()) chapters=cued
                }
                chapters=chapters.filter { it.start in 0 until offset }.distinctBy { it.start }.sortedBy { it.start }
                chapters=chapters.mapIndexed { i,c -> c.copy(end=chapters.getOrNull(i+1)?.start ?: offset) }
                val existingCover=store.library.value.books.firstOrNull { it.source==source }?.cover.orEmpty()
                var coverPath=existingCover.ifBlank { ordered.firstOrNull { it.cover.isNotBlank() }?.cover.orEmpty() }
                if(coverPath.isBlank()) first.input.siblings.firstOrNull { it.name?.lowercase() in setOf("cover.jpg","cover.png","folder.jpg","folder.png") }?.let { cover -> runCatching { context.contentResolver.openInputStream(cover.uri)?.use { coverPath=saveCover(it.readBounded(8*1024*1024));if(coverPath.isNotBlank()) temporaryCovers+=coverPath } } }
                val parentName=first.input.folderName.ifBlank { null }
                val title=first.album.ifBlank { if(source.startsWith("folder:")) parentName ?: first.title else first.title }.ifBlank { first.input.name.substringBeforeLast('.') }
                runCatching { store.add(Book(title=title,author=first.author,narrator=first.narrator,genre=first.genre,cover=coverPath,source=source,duration=offset,description=first.description,collection=first.series,format=ordered.map { it.input.name.substringAfterLast('.').uppercase() }.distinct().joinToString(" / "),size=ordered.sumOf { it.input.size }),tracks,chapters) }.onSuccess { imported++ }.onFailure { errors+="$title: ${it.message}";if(coverPath.isNotEmpty() && coverPath!=existingCover) File(coverPath).delete() }
            }
            state.value=ImportProgress(false,"Imported $imported ${if(imported==1) "book" else "books"}",candidates.size,candidates.size,errors)
        } catch(e:kotlinx.coroutines.CancellationException) { state.value=ImportProgress(errors=listOf("Import stopped. Completed books are in your library."));throw e }
        catch(e:Exception) { state.value=ImportProgress(errors=listOf(e.message ?: "Import failed. Check folder access and try again.")) }
        finally { val used=store.library.value.books.map { it.cover }.toSet();temporaryCovers.filter { it !in used }.forEach { File(it).delete() } }
    } }
    private fun identity(uri:Uri):String = MediaIdentity.of(context,uri)
    private fun inspect(input:Input):Info {
        require(input.name.substringAfterLast('.',"").lowercase() in extensions) { "Unsupported format. Choose an unprotected audio file." }
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(context,input.uri)
            fun tag(key:Int)=retriever.extractMetadata(key)?.trim().orEmpty()
            val duration=tag(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0
            require(duration>0) { "No playable audio found, or this file uses an unsupported codec" }
            val parsed=runCatching { context.contentResolver.openFileDescriptor(input.uri,"r")?.use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { val source=ChapterParser.ChannelSource(it.channel);val ext=input.name.substringAfterLast('.');ChapterParser.read(source,ext) to ChapterParser.tags(source,ext) } } ?: (emptyList<Chapter>() to emptyMap<String,String>()) }.getOrDefault(emptyList<Chapter>() to emptyMap())
            val extra=parsed.second
            return Info(input,tag(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { input.name.substringBeforeLast('.') },tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),extra["author"] ?: tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).ifBlank { tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) }.ifBlank { "Unknown author" },extra["narrator"].orEmpty(),tag(MediaMetadataRetriever.METADATA_KEY_GENRE),duration,retriever.embeddedPicture?.takeIf { it.size<=16*1024*1024 }?.let { saveCover(it) }.orEmpty(),parsed.first,extra["description"].orEmpty(),extra["series"].orEmpty())
        } finally { retriever.release() }
    }
    internal fun saveCover(bytes:ByteArray):String {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth<=0 || bounds.outHeight<=0) return ""
        val options=BitmapFactory.Options().apply { inSampleSize=generateSequence(1) { it*2 }.first { bounds.outWidth/it<=1000 && bounds.outHeight/it<=1000 } }
        val bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options) ?: return ""
        val folder=File(context.filesDir,"covers").apply { mkdirs() };val file=File(folder,"${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,88,it) };bitmap.recycle();return file.absolutePath
    }
}
