package app.sonder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sonder.data.Book
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34],qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookOptionsSheetTest {
    @get:Rule val compose=createComposeRule()
    private val book=Book(id=7,title="Project Hail Mary",author="Andy Weir")

    @Test fun deleteFromPhoneAsksFirstThenDeletes() {
        var deletes=0
        compose.setContent { SonderTheme("Light") { BookOptionsSheet(book,onDismiss={},onStatus={},onFavorite={},onDetails={},onLog={},onDelete={ deletes++ }) } }
        compose.waitForIdle()
        compose.screenshot("book-options")
        compose.onNodeWithText("Delete from phone").assertIsDisplayed().performClick()
        compose.onNodeWithText("Delete from your phone?").assertIsDisplayed()
        compose.screenshot("book-options-delete-confirm")
        assertEquals("Nothing is deleted before confirming",0,deletes)
        compose.onNodeWithText("Delete").performClick()
        assertEquals(1,deletes)
    }
    @Test fun cancelDoesNotDelete() {
        var deletes=0
        compose.setContent { SonderTheme("Light") { BookOptionsSheet(book,onDismiss={},onStatus={},onFavorite={},onDetails={},onLog={},onDelete={ deletes++ }) } }
        compose.onNodeWithText("Delete from phone").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete from your phone?").assertDoesNotExist()
        assertEquals(0,deletes)
    }
}
