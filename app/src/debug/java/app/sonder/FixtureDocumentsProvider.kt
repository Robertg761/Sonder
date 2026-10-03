package app.sonder

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

/** Android Storage Access Framework fixture provider. Excluded from every release build. */
class FixtureDocumentsProvider:DocumentsProvider() {
    private val root get()=File(context!!.filesDir,"saf-fixtures").apply { mkdirs() }
    private val columns=arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    override fun onCreate()=true
    private fun file(id:String):File {
        val target=if(id=="root") root else File(root,id.removePrefix("root/"))
        require(target.canonicalPath==root.canonicalPath || target.canonicalPath.startsWith(root.canonicalPath+"/"))
        return target
    }
    private fun MatrixCursor.add(id:String) {
        val f=file(id)
        newRow().add(DocumentsContract.Document.COLUMN_DOCUMENT_ID,id).add(DocumentsContract.Document.COLUMN_DISPLAY_NAME,if(id=="root") "Sonder test files" else f.name).add(DocumentsContract.Document.COLUMN_MIME_TYPE,if(f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else when(f.extension) { "cue" -> "text/plain";"mp3" -> "audio/mpeg";"wav" -> "audio/wav";else -> "application/octet-stream" }).add(DocumentsContract.Document.COLUMN_FLAGS,if(f.isDirectory) DocumentsContract.Document.FLAG_DIR_PREFERS_LAST_MODIFIED else 0).add(DocumentsContract.Document.COLUMN_SIZE,f.length()).add(DocumentsContract.Document.COLUMN_LAST_MODIFIED,f.lastModified())
    }
    override fun queryRoots(projection:Array<out String>?):Cursor {
        val cols=projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID,DocumentsContract.Root.COLUMN_DOCUMENT_ID,DocumentsContract.Root.COLUMN_TITLE,DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.COLUMN_AVAILABLE_BYTES)
        return MatrixCursor(cols).apply { newRow().add(DocumentsContract.Root.COLUMN_ROOT_ID,"sonder-test").add(DocumentsContract.Root.COLUMN_DOCUMENT_ID,"root").add(DocumentsContract.Root.COLUMN_TITLE,"Sonder test files").add(DocumentsContract.Root.COLUMN_FLAGS,DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD).add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES,root.usableSpace) }
    }
    override fun queryDocument(documentId:String,projection:Array<out String>?)=MatrixCursor(projection ?: columns).apply { add(documentId) }
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?)=MatrixCursor(projection ?: columns).apply { file(parentDocumentId).listFiles()?.sortedBy { it.name }?.forEach { f -> add("$parentDocumentId/${f.name}") } }
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor=ParcelFileDescriptor.open(file(documentId),ParcelFileDescriptor.MODE_READ_ONLY)
    override fun isChildDocument(parentDocumentId:String,documentId:String)=documentId.startsWith("$parentDocumentId/")
}
