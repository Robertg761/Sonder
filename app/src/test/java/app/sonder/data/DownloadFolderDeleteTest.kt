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
    @Test fun leavesBooksThatWerentDownloadedBySonder() {
        val docs=FakeDocuments.install(context).apply { writable+="primary:Music/Other/01.mp3" }
        val downloads=downloads(job(DownloadJob.State.DONE))
        assertFalse(downloads.deleteBookFolder(listOf(FakeDocuments.document("primary:Music/Other/01.mp3"))))
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
