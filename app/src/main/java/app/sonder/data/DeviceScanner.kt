package app.sonder.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Read-only discovery of Android-indexed shared media. Never traverses private app storage. */
class DeviceScanner(private val context:Context) {
    data class File(val uri:Uri,val name:String,val size:Long,val duration:Long,val path:String,val volume:String,val suggested:Boolean,val imported:Boolean=false) {
        val folderKey get()="mediafolder:$volume:$path"
        fun input()=Importer.Input(uri,name,size,folderKey,path.trimEnd('/').substringAfterLast('/'))
    }
    data class Result(val files:List<File>,val limited:Boolean=false,val includesVideo:Boolean=false)
    companion object {
        const val MAX_FILES=20000
        fun requiredPermissions(includeVideo:Boolean):Array<String> = if(Build.VERSION.SDK_INT>=33) {
            if(includeVideo) arrayOf(Manifest.permission.READ_MEDIA_AUDIO,Manifest.permission.READ_MEDIA_VIDEO) else arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        fun likelyAudiobook(name:String,path:String,flag:Boolean=false):Boolean = flag || name.substringAfterLast('.').equals("m4b",true) || Regex("audio[ _-]?books?|spoken[ _-]?word",RegexOption.IGNORE_CASE).containsMatchIn(path)
    }
    private fun allowed(permission:String)=ContextCompat.checkSelfPermission(context,permission)==PackageManager.PERMISSION_GRANTED
    suspend fun scan(includeVideo:Boolean,known:Set<String>):Result = withContext(Dispatchers.IO) {
        val audioAllowed=allowed(if(Build.VERSION.SDK_INT>=33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
        require(audioAllowed) { "Allow audio access to scan your device. You can still choose files or a folder without this permission." }
        val videoAllowed=includeVideo && (Build.VERSION.SDK_INT<33 || allowed(Manifest.permission.READ_MEDIA_VIDEO))
        val files=mutableListOf<File>();var limited=false
        val knownKeys=known.map { MediaIdentity.of(context,Uri.parse(it)) }.toSet()
        val collections=buildList {
            add(if(Build.VERSION.SDK_INT>=29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            if(videoAllowed) add(if(Build.VERSION.SDK_INT>=29) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }
        for(collection in collections) {
            currentCoroutineContext().ensureActive()
            val audio=collection.path?.contains("/audio/")==true
            val projection=mutableListOf(MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATA,"duration")
            if(Build.VERSION.SDK_INT>=29) { projection+=MediaStore.MediaColumns.RELATIVE_PATH;projection+=MediaStore.MediaColumns.VOLUME_NAME;if(audio) projection+=MediaStore.Audio.Media.IS_AUDIOBOOK }
            context.contentResolver.query(collection,projection.toTypedArray(),null,null,"${MediaStore.MediaColumns.DISPLAY_NAME} ASC")?.use { c ->
                fun string(column:String)=c.getColumnIndex(column).takeIf { it>=0 }?.let { c.getString(it) }.orEmpty()
                fun number(column:String)=c.getColumnIndex(column).takeIf { it>=0 }?.let { c.getLong(it) } ?: 0
                while(c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val name=string(MediaStore.MediaColumns.DISPLAY_NAME)
                    if(name.substringAfterLast('.').lowercase() !in Importer.extensions) continue
                    if(files.size>=MAX_FILES) { limited=true;break }
                    val uri=ContentUris.withAppendedId(collection,number(MediaStore.MediaColumns._ID))
                    val path=if(Build.VERSION.SDK_INT>=29) string(MediaStore.MediaColumns.RELATIVE_PATH) else string(MediaStore.MediaColumns.DATA).substringBeforeLast('/',"")
                    val volume=if(Build.VERSION.SDK_INT>=29) string(MediaStore.MediaColumns.VOLUME_NAME) else "external"
                    files+=File(uri,name,number(MediaStore.MediaColumns.SIZE),number("duration"),path,volume,likelyAudiobook(name,path,audio && Build.VERSION.SDK_INT>=29 && number(MediaStore.Audio.Media.IS_AUDIOBOOK)==1L),(MediaIdentity.fromPath(string(MediaStore.MediaColumns.DATA)) ?: uri.toString()) in knownKeys)
                }
            }
            if(limited) break
        }
        Result(files.sortedWith(compareByDescending<File> { it.suggested }.thenBy { it.path }.thenBy { it.name }),limited,videoAllowed)
    }
}
