package app.sonder

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sonder.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusAndDiscoveryTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    @Test fun statusResetRejectsOldPlaybackSavesAndRetainsBookmarks() = runBlocking {
        val name="status-test.db";context.deleteDatabase(name)
        val store=LibraryStore(context,name)
        try {
            val id=store.add(Book(title="Status test",source="status-test",duration=30000,format="MP3"),listOf(Track(uri="content://test/status",name="Track",duration=30000)),listOf(Chapter("Track",0,30000)))
            store.progress(id,12000,1000);store.addBookmark(id,10000,"Keep this")
            val revision=store.library.value.books.single().progressRevision
            store.markStatus(id,ListeningStatus.NOT_STARTED)
            store.trackProgress(id,"content://test/status",15000,0,true,revision)
            var book=store.library.value.books.single()
            assertEquals(0,book.position);assertEquals(0,book.lastPlayed);assertFalse(book.inProgress);assertFalse(book.finished)
            assertEquals("Keep this",store.library.value.bookmarks.single().note)
            assertEquals(1000,store.library.value.daily.values.sum())
            store.markStatus(id,ListeningStatus.IN_PROGRESS);book=store.library.value.books.single();assertTrue(book.inProgress);assertEquals(0,book.position)
            val backup=store.export();store.markStatus(id,ListeningStatus.FINISHED);assertTrue(store.library.value.books.single().finished)
            store.restore(backup);assertTrue(store.library.value.books.single().inProgress)
            store.close()
            val reopened=LibraryStore(context,name);reopened.refresh();assertTrue(reopened.library.value.books.single().inProgress);reopened.close()
        } finally { store.close();context.deleteDatabase(name) }
    }
    @Test fun migratesVersionTwoLibraryWithoutLosingProgress() = runBlocking {
        val name="migration-test.db";context.deleteDatabase(name)
        val store=LibraryStore(context,name)
        // Construct the actual previous schema by removing only the new columns.
        val legacy=SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null)
        try {
            val freshName="fresh-schema-test.db";context.deleteDatabase(freshName)
            val fresh=LibraryStore(context,freshName)
            val schema=fresh.readableDatabase.rawQuery("SELECT sql FROM sqlite_master WHERE type IN ('table','index') AND sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name!='android_metadata' AND name NOT LIKE 'reading_%'",null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
            fresh.close();context.deleteDatabase(freshName)
            schema.forEach { sql -> legacy.execSQL(sql.replace(", started INTEGER NOT NULL DEFAULT 0, progressRevision INTEGER NOT NULL DEFAULT 0","")) }
            legacy.execSQL("INSERT INTO books(title,author,narrator,description,genre,cover,source,duration,position,added,format,size) VALUES('Existing book','Author','','','','','legacy',30000,12000,1,'MP3',100)")
            legacy.version=2
        } finally { legacy.close() }
        try { store.refresh();val book=store.library.value.books.single();assertEquals("Existing book",book.title);assertEquals(12000,book.position);assertTrue(book.inProgress);assertEquals(4,store.readableDatabase.version) } finally { store.close();context.deleteDatabase(name) }
    }
    @Test fun scansRealMediaStoreImportsSelectedFilesAndDeduplicatesAcrossPickerIdentity() = runBlocking {
        for(permission in DeviceScanner.requiredPermissions(true)) instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} $permission").close()
        val resolver=context.contentResolver
        val path="Audiobooks/Sonder discovery tests/"
        fun insert(name:String,mime:String,collection:android.net.Uri,asset:String):android.net.Uri {
            val uri=resolver.insert(collection,ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,mime);put(MediaStore.MediaColumns.RELATIVE_PATH,if(mime.startsWith("video/")) "Movies/Audiobooks/Sonder discovery tests/" else path);put(MediaStore.MediaColumns.IS_PENDING,1) })!!
            instrumentation.context.assets.open(asset).use { input -> resolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
            resolver.update(uri,ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            return uri
        }
        resolver.delete(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),"${MediaStore.MediaColumns.DISPLAY_NAME}=?",arrayOf("Discovery.m4b"))
        val audio=insert("Discovery.m4b","audio/mp4",MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),"chapters.m4b")
        val video=insert("Discovery video.mp4","video/mp4",MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),"chapters.mp4")
        val name="discovery-test.db";context.deleteDatabase(name);val store=LibraryStore(context,name)
        try {
            val scanner=DeviceScanner(context)
            val result=scanner.scan(false,emptySet())
            val found=result.files.single { it.name=="Discovery.m4b" };assertTrue(found.suggested);assertFalse(result.files.any { it.name=="Discovery video.mp4" })
            val withVideo=scanner.scan(true,emptySet());assertTrue(withVideo.includesVideo);assertTrue(withVideo.files.any { it.name=="Discovery video.mp4" })
            Importer(context,store).discovered(listOf(found));assertEquals(1,store.library.value.books.size)
            assertEquals(3,store.chapters(store.library.value.books.single().id).size)
            val next=scanner.scan(false,store.knownUris());assertTrue(next.files.single { it.name==found.name }.imported)
            Importer(context,store).discovered(listOf(found));assertEquals(1,store.library.value.books.size)
            val doc=DocumentsContract.buildDocumentUri("com.android.externalstorage.documents","primary:${path}Discovery.m4b")
            assertEquals(MediaIdentity.of(context,found.uri),MediaIdentity.of(context,doc))
        } finally { store.close();context.deleteDatabase(name);resolver.delete(audio,null,null);resolver.delete(video,null,null) }
    }
}
