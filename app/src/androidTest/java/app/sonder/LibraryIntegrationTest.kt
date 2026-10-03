package app.sonder

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sonder.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibraryIntegrationTest {
    private val target get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val app get()=target.applicationContext as SonderApp
    private fun fixture(name:String):android.net.Uri {
        val dir=File(target.filesDir,"fixtures").apply { mkdirs() };val file=File(dir,name)
        InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
        return FileProvider.getUriForFile(target,"app.sonder.audiobooks.testfiles",file)
    }
    private suspend fun clear() { app.store.refresh();app.store.library.value.books.forEach { app.store.remove(it.id) } }
    @Test fun importsMp4MetadataChaptersAndDoesNotDuplicate() = runBlocking {
        clear();val uri=fixture("chapters.mp4");val importer=Importer(target,app.store)
        importer.files(listOf(uri));val book=app.store.library.value.books.single()
        assertEquals("The listening test",book.title);assertEquals("Sonder test studio",book.author);assertTrue(book.duration>=29000)
        assertEquals(listOf("Opening","The middle","Closing"),app.store.chapters(book.id).map { it.title })
        importer.files(listOf(uri));assertEquals(1,app.store.library.value.books.size)
        app.store.progress(book.id,12500,3000);assertEquals(12500,app.store.library.value.books.single().position)
        app.store.addBookmark(book.id,12500,"A useful moment")
        val backup=app.store.export();app.store.update(app.store.library.value.books.single().copy(title="Changed"))
        assertEquals(1,app.store.restore(backup));assertEquals("The listening test",app.store.library.value.books.single().title)
        assertEquals("A useful moment",app.store.library.value.bookmarks.single().note)
        app.store.remove(book.id);assertTrue(app.store.library.value.bookmarks.isEmpty());assertTrue(app.store.tracks(book.id).isEmpty())
    }
    @Test fun importsCommonUnprotectedFormats() = runBlocking {
        clear();val importer=Importer(target,app.store)
        val formats=listOf("mp3","flac","ogg","opus","wav","m4a","aac","m4b")
        val uris=formats.map { fixture(if(it=="m4b") "chapters.m4b" else "format.$it") }
        importer.files(uris)
        assertEquals(importer.progress.value.errors.toString(),formats.size,app.store.library.value.books.size)
        assertTrue(app.store.library.value.books.all { it.duration>=4900 })
        app.store.library.value.books.filter { it.format.lowercase() in setOf("flac","ogg","opus") }.forEach { book ->
            assertEquals(listOf(0L,2500L),app.store.chapters(book.id).map { it.start })
            assertEquals("Test narrator",book.narrator)
        }
        clear()
    }
    @Test fun rescansMergeTracksAndPreserveBookmarkTrackPosition() = runBlocking {
        clear();val store=app.store
        val id=store.add(Book(title="A folder book",source="folder:test",duration=10000,format="MP3"),listOf(Track(uri="content://test/t2",name="02",duration=10000)),listOf(Chapter("02",0,10000)))
        store.progress(id,5000,0);store.addBookmark(id,5000,"Track two")
        store.add(Book(title="A folder book",source="folder:test",duration=10000,format="MP3"),listOf(Track(uri="content://test/t1",name="01",duration=10000)),listOf(Chapter("01",0,10000)))
        assertEquals(1,store.library.value.books.size);assertEquals(20000,store.library.value.books.single().duration)
        assertEquals(15000,store.library.value.books.single().position);assertEquals(15000,store.library.value.bookmarks.single().position)
        assertEquals(listOf("01","02"),store.tracks(id).map { it.name });assertEquals(listOf(0L,10000L),store.chapters(id).map { it.start })
        clear()
    }
    @Test fun mixedFlatFolderKeepsSeparateBooksSeparate() = runBlocking {
        clear()
        val root=File(target.filesDir,"saf-fixtures").apply { deleteRecursively();mkdirs() }
        listOf("format.mp3" to "One book.mp3","chapters.m4b" to "Another book.m4b").forEach { (asset,name) -> InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use { input -> File(root,name).outputStream().use { input.copyTo(it) } } }
        val uri=android.provider.DocumentsContract.buildTreeDocumentUri("app.sonder.audiobooks.testdocs","root")
        val importer=Importer(target,app.store);importer.folder(uri)
        assertEquals(importer.progress.value.errors.toString(),2,app.store.library.value.books.size)
        assertTrue(app.store.library.value.books.all { app.store.tracks(it.id).size==1 })
        clear()
    }
    @Test fun scansNestedSafFoldersCueChaptersAndMergesNewTracks() = runBlocking {
        clear()
        val root=File(target.filesDir,"saf-fixtures").apply { deleteRecursively();mkdirs() }
        val bookFolder=File(root,"Nested book").apply { mkdirs() }
        fun audio(name:String) { InstrumentationRegistry.getInstrumentation().context.assets.open("format.wav").use { input -> File(bookFolder,name).outputStream().use { input.copyTo(it) } } }
        audio("02.wav");audio("10.wav")
        File(bookFolder,"chapters.cue").writeText("FILE \"02.wav\" WAVE\n TRACK 01 AUDIO\n TITLE \"First passage\"\n INDEX 01 00:00:00\nFILE \"10.wav\" WAVE\n TRACK 02 AUDIO\n TITLE \"Second passage\"\n INDEX 01 00:00:00")
        val uri=android.provider.DocumentsContract.buildTreeDocumentUri("app.sonder.audiobooks.testdocs","root")
        val importer=Importer(target,app.store);importer.folder(uri)
        assertEquals(importer.progress.value.errors.toString(),1,app.store.library.value.books.size)
        val b=app.store.library.value.books.single();assertEquals(listOf("02.wav","10.wav"),app.store.tracks(b.id).map { it.sortName })
        assertEquals(listOf("First passage","Second passage"),app.store.chapters(b.id).map { it.title })
        val singleUri=android.provider.DocumentsContract.buildDocumentUri("app.sonder.audiobooks.testdocs","root/Nested book/02.wav")
        importer.files(listOf(singleUri));assertEquals(1,app.store.library.value.books.size);assertEquals(2,app.store.tracks(b.id).size)
        app.store.progress(b.id,2000,0);app.store.addBookmark(b.id,2000,"Track two")
        audio("01.wav");importer.folder(uri)
        assertEquals(1,app.store.library.value.books.size)
        assertEquals(listOf("01.wav","02.wav","10.wav"),app.store.tracks(b.id).map { it.sortName })
        assertEquals(7000,app.store.library.value.books.single().position)
        assertEquals(7000,app.store.library.value.bookmarks.single().position)
        importer.folder(uri);assertEquals(3,app.store.tracks(b.id).size)
        clear()
    }
    @Test fun failedDuplicateInsertRollsBackWholeBook() = runBlocking {
        clear();val store=app.store
        store.add(Book(title="First",source="first",duration=1000,format="MP3"),listOf(Track(uri="content://test/same",name="First",duration=1000)),listOf(Chapter("First",0,1000)))
        assertTrue(runCatching { store.add(Book(title="Second",source="second",duration=1000,format="MP3"),listOf(Track(uri="content://test/same",name="Second",duration=1000)),emptyList()) }.isFailure)
        store.refresh();assertEquals(1,store.library.value.books.size);assertEquals("First",store.library.value.books.single().title)
        clear()
    }
}
