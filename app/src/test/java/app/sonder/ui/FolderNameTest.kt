package app.sonder.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34])
class FolderNameTest {
    @Test fun showsFolderWithoutStorageVolume() {
        assertEquals("Books",folderName("content://com.android.externalstorage.documents/tree/primary%3ABooks"))
        assertEquals("Audio/Books",folderName("content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AAudio%2FBooks"))
        assertEquals("Internal storage",folderName("content://com.android.externalstorage.documents/tree/primary%3A"))
    }
}
