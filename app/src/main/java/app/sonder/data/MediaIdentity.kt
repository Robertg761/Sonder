package app.sonder.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.util.Locale

object MediaIdentity {
    fun fromPath(path:String):String? {
        if(path.startsWith("/storage/emulated/0/")) return "storage:primary:${path.removePrefix("/storage/emulated/0/")}"
        if(path.startsWith("/storage/")) { val relative=path.removePrefix("/storage/");return "storage:${relative.substringBefore('/').lowercase(Locale.ROOT)}:${relative.substringAfter('/')}" }
        return null
    }
    fun of(context:Context,uri:Uri):String {
        val documentId=runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        if(uri.authority=="com.android.externalstorage.documents" && documentId!=null) return "storage:${documentId.substringBefore(':').lowercase(Locale.ROOT)}:${documentId.substringAfter(':')}"
        val media=if(uri.authority=="media") uri else if(Build.VERSION.SDK_INT>=29 && DocumentsContract.isDocumentUri(context,uri)) runCatching { MediaStore.getMediaUri(context,uri) }.getOrNull() else null
        if(media!=null) {
            runCatching { context.contentResolver.query(media,arrayOf(MediaStore.MediaColumns.DATA),null,null,null)?.use { c -> if(c.moveToFirst()) c.getString(0) else null } }.getOrNull()?.let { path ->
                fromPath(path)?.let { return it }
            }
            return media.toString()
        }
        return if(documentId!=null) "document:${uri.authority}:$documentId" else uri.toString()
    }
}
