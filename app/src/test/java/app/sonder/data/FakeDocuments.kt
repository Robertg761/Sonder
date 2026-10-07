package app.sonder.data

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.io.FileNotFoundException

/**
 * Stands in for Android's storage providers. Documents in [writable] can be deleted (like a folder Sonder may
 * write to); documents in [readOnly] exist but refuse deletion (like a file chosen with the picker).
 */
class FakeDocuments:ContentProvider() {
    val writable=mutableSetOf<String>();val readOnly=mutableSetOf<String>();val deleted=mutableListOf<String>()
    private val file by lazy { File.createTempFile("track",".mp3").apply { writeText("audio") } }
    private fun id(uri:Uri)=if(uri.authority==AUTHORITY) DocumentsContract.getDocumentId(uri) else uri.lastPathSegment.orEmpty()
    override fun call(method:String,arg:String?,extras:Bundle?):Bundle? {
        check(method=="android:deleteDocument") { "Unexpected $method" }
        @Suppress("DEPRECATION") val id=id(extras!!.getParcelable("uri")!!)
        // DocumentsContract passes the document under its hidden EXTRA_URI key, "uri". Deleting a folder deletes everything inside it, as the real providers do.
        if(writable.none { it==id || it.startsWith("$id/") }) throw SecurityException("Permission denied for $id")
        writable.removeAll { it==id || it.startsWith("$id/") };deleted+=id
        return Bundle()
    }
    override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
        if(id(uri) !in writable+readOnly) throw FileNotFoundException("Missing ${id(uri)}")
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun onCreate()=true
    /** Lists a folder's direct children, as DocumentsContract's child-documents query does. */
    override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor? {
        if(uri.pathSegments.lastOrNull()!="children") return null
        val parent=uri.pathSegments[uri.pathSegments.size-2]
        return android.database.MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)).apply {
            (writable+readOnly).filter { it.startsWith("$parent/") && '/' !in it.removePrefix("$parent/") }.forEach { addRow(arrayOf(it)) }
        }
    }
    override fun getType(uri:Uri):String?=null
    override fun insert(uri:Uri,values:ContentValues?):Uri?=null
    override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
    override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    companion object {
        const val AUTHORITY="app.sonder.test.documents"
        /** Registers the provider and tells Android it is a documents provider, as DocumentsContract checks. */
        fun install(context:Context,authority:String=AUTHORITY,documents:Boolean=true):FakeDocuments {
            val info=ProviderInfo().apply { this.authority=authority;packageName=context.packageName;name=FakeDocuments::class.java.name;exported=true;grantUriPermissions=true }
            val provider=Robolectric.buildContentProvider(FakeDocuments::class.java).create(info).get()
            if(documents) shadowOf(context.packageManager).addResolveInfoForIntent(Intent(DocumentsContract.PROVIDER_INTERFACE),ResolveInfo().apply { providerInfo=info })
            return provider
        }
        val tree:Uri=DocumentsContract.buildTreeDocumentUri(AUTHORITY,"primary:Audiobooks")
        fun document(id:String):Uri=DocumentsContract.buildDocumentUriUsingTree(tree,id)
    }
}
