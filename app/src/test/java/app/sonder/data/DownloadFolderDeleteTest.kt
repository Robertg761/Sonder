package app.sonder.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sonder.download.DownloadJob
import app.sonder.download.Downloads
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34])
class DownloadFolderDeleteTest {
    private val context=ApplicationProvider.getApplicationContext<android.app.Application>()
    private val folder=FakeDocuments.document("primary:Audiobooks/Project Hail Mary")
    private fun downloads(vararg jobs:DownloadJob):Downloads {
        context.getSharedPreferences("downloads",Context.MODE_PRIVATE).edit().putString("jobs",JSONArray().apply { jobs.forEach { put(it.json()) } }.toString()).commit()
        return Downloads(context,Importer(context,LibraryStore(context,"test-${System.nanoTime()}.db")))
    }
    private fun job(state:DownloadJob.State)=DownloadJob(title="Project Hail Mary",hash="ad5f",magnet="magnet:?xt=urn:btih:ad5f",state=state,folder=folder.toString())

    @Test fun deletesTheWholeDownloadFolderAndForgetsTheDownload() {
        val docs=FakeDocuments.install(context).apply { writable+=listOf("primary:Audiobooks/Project Hail Mary","primary:Audiobooks/Project Hail Mary/01.m4b","primary:Audiobooks/Project Hail Mary/cover.jpg") }
        val downloads=downloads(job(DownloadJob.State.DONE))
        assertTrue(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary/01.m4b"))))
        assertEquals("The folder goes, cover included",listOf("primary:Audiobooks/Project Hail Mary"),docs.deleted)
        assertTrue(docs.writable.isEmpty())
        assertTrue("The download can be found and downloaded again",downloads.jobs.value.isEmpty())
    }
    @Test fun stillDeletesTheWholeFolderAfterFinishedDownloadsAreCleared() {
        val docs=FakeDocuments.install(context).apply { writable+=listOf("primary:Audiobooks/Project Hail Mary","primary:Audiobooks/Project Hail Mary/01.m4b","primary:Audiobooks/Project Hail Mary/cover.jpg") }
        val downloads=downloads(job(DownloadJob.State.DONE))
        downloads.clearFinished()
        assertTrue(downloads.jobs.value.isEmpty())
        assertTrue(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary/01.m4b"))))
        assertEquals(listOf("primary:Audiobooks/Project Hail Mary"),docs.deleted)
    }
    @Test fun trustsTheProvidersFolderListingNotDocumentIds() {
        // Opaque IDs: the track's ID says nothing about its folder, but the provider lists it as a child.
        val docs=FakeDocuments.install(context).apply { writable+=listOf("primary:Audiobooks/Project Hail Mary","primary:Audiobooks/Project Hail Mary/7f3a") }
        val downloads=downloads(job(DownloadJob.State.DONE))
        assertTrue(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary/7f3a"))))
        assertEquals(listOf("primary:Audiobooks/Project Hail Mary"),docs.deleted)
    }
    @Test fun ignoresIdsThatOnlyLookLikeTheyAreInTheFolder() {
        // A deeper path the provider doesn't list as a direct child of the book folder is not one of its tracks.
        val docs=FakeDocuments.install(context).apply { writable+=listOf("primary:Audiobooks/Project Hail Mary","primary:Audiobooks/Project Hail Mary/Other/01.mp3") }
        val downloads=downloads(job(DownloadJob.State.DONE))
        assertFalse(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary/Other/01.mp3"))))
        assertTrue(docs.deleted.isEmpty())
    }
    @Test fun keepsAFolderAnotherBookStillUses() {
        val docs=FakeDocuments.install(context).apply { writable+=listOf("primary:Audiobooks/Project Hail Mary/Book 1.m4b","primary:Audiobooks/Project Hail Mary/Book 2.m4b") }
        val downloads=downloads(job(DownloadJob.State.DONE))
        // One upload imported as two books: deleting book 1 must not take book 2's audio with it.
        assertFalse(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary/Book 1.m4b")),others=listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary/Book 2.m4b"))))
        assertTrue(docs.deleted.isEmpty());assertEquals(1,downloads.jobs.value.size)
        // Its files are then deleted one by one instead, leaving book 2 alone.
        assertEquals(MediaFiles.Result(1,emptyList(),0),MediaFiles.delete(context,listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary/Book 1.m4b"))))
        assertEquals(setOf("primary:Audiobooks/Project Hail Mary/Book 2.m4b"),docs.writable)
    }
    @Test fun leavesBooksThatWerentDownloadedBySonder() {
        val docs=FakeDocuments.install(context).apply { writable+="primary:Music/Other/01.mp3" }
        val downloads=downloads(job(DownloadJob.State.DONE))
        assertFalse(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Music/Other/01.mp3"))))
        // The same document ID from another provider is a different file.
        val elsewhere=android.provider.DocumentsContract.buildDocumentUri("com.example.cloud","primary:Audiobooks/Project Hail Mary/01.m4b")
        assertFalse(downloads.deleteBookFolder(listOf(elsewhere)))
        // A folder whose name starts the same is a different folder.
        assertFalse(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary 2/01.mp3"))))
        assertTrue(docs.deleted.isEmpty());assertEquals(1,downloads.jobs.value.size)
    }
    @Test fun neverDeletesTheFolderOfADownloadThatIsStillRunning() {
        val docs=FakeDocuments.install(context).apply { writable+="primary:Audiobooks/Project Hail Mary/01.m4b" }
        // Loading the queue marks interrupted jobs as failed, which is not active.
        val downloads=downloads(job(DownloadJob.State.FAILED))
        assertTrue(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Audiobooks/Project Hail Mary/01.m4b"))))
        assertEquals(listOf("primary:Audiobooks/Project Hail Mary"),docs.deleted)
    }
}
