package app.sonder.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Android 9: no MediaStore document mapping, so files Sonder can't delete are reported, not confirmed.
@RunWith(AndroidJUnit4::class)
@Config(sdk=[28])
class MediaFilesTest {
    private val context=ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test fun deletesWritableFilesAndReportsReadOnlyOnes() {
        val docs=FakeDocuments.install(context).apply { writable+="primary:Audiobooks/Book/01.mp3";readOnly+="primary:Music/02.mp3" }
        val result=MediaFiles.delete(context,listOf(FakeDocuments.document("primary:Audiobooks/Book/01.mp3"),FakeDocuments.document("primary:Music/02.mp3")))
        assertEquals(MediaFiles.Result(deleted=1,confirm=emptyList(),failed=1),result)
        assertEquals(listOf("primary:Audiobooks/Book/01.mp3"),docs.deleted)
    }
    @Test fun aFileThatIsAlreadyGoneCountsAsDeleted() {
        FakeDocuments.install(context)
        assertEquals(MediaFiles.Result(1,emptyList(),0),MediaFiles.delete(context,listOf(FakeDocuments.document("primary:Audiobooks/Gone.mp3"))))
    }
    @Test fun sharedMediaIsHandedToAndroidsConfirmation() {
        FakeDocuments.install(context,authority="media",documents=false).apply { readOnly+="7" }
        val track=Uri.parse("content://media/external/audio/media/7")
        assertEquals(MediaFiles.Result(0,listOf(track),0),MediaFiles.delete(context,listOf(track,track)))
    }
}
