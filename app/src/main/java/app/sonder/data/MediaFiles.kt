package app.sonder.data

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore

/** Deletes a book's audio from the phone, with whatever access Android gave Sonder for each file. */
object MediaFiles {
    /** [confirm] holds shared-media files Android must ask the person about; [failed] could not be deleted at all. */
    data class Result(val deleted:Int,val confirm:List<Uri>,val failed:Int)
    fun delete(context:Context,uris:List<Uri>):Result {
        var deleted=0;var failed=0;val confirm=mutableListOf<Uri>()
        for(uri in uris.distinct()) {
            // Folder grants with write access (such as the download folder) can delete directly.
            val direct=DocumentsContract.isDocumentUri(context,uri) && runCatching { DocumentsContract.deleteDocument(context.contentResolver,uri) }.getOrDefault(false)
            val media=if(direct) null else if(uri.authority==MediaStore.AUTHORITY) uri else mediaUri(context,uri)
            when {
                direct || missing(context,media ?: uri) -> deleted++
                media!=null -> confirm+=media
                else -> failed++
            }
        }
        return Result(deleted,confirm,failed)
    }
    /** Android 11+ shows one system dialog to delete shared media that Sonder only has read access to. */
    fun confirmation(context:Context,uris:List<Uri>):IntentSender? = if(Build.VERSION.SDK_INT>=30 && uris.isNotEmpty()) MediaStore.createDeleteRequest(context.contentResolver,uris).intentSender else null
    /** Only a definite "not found" counts as already gone; a file Sonder lost access to is not. */
    private fun missing(context:Context,uri:Uri)=try { context.contentResolver.openFileDescriptor(uri,"r")?.close();false } catch(e:java.io.FileNotFoundException) { true } catch(e:Exception) { false }
    private fun mediaUri(context:Context,uri:Uri):Uri? = if(Build.VERSION.SDK_INT>=29) runCatching { MediaStore.getMediaUri(context,uri) }.getOrNull() else null
}
