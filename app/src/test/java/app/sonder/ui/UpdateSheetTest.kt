package app.sonder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.sonder.update.ReleaseInfo
import app.sonder.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// A small phone, so long release notes overflow the screen.
@RunWith(AndroidJUnit4::class)
@Config(sdk=[34],qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateSheetTest {
    @get:Rule val compose=createComposeRule()
    private val notes=(1..18).joinToString("\n") { "- Release note line $it, long enough to wrap onto a second line on a narrow phone screen." }
    private val info=ReleaseInfo("1.7.2","https://github.com/Robertg761/Sonder/releases/download/v1.7.2/Sonder-1.7.2.apk",4_900_000,"a".repeat(64),notes)

    @Test fun downloadAndLaterStayVisibleWithLongNotes() {
        var downloads=0;var dismissed=0
        compose.setContent { SonderTheme("Light") { UpdateSheetContent(info,UpdateState(available=info),launching=false,error="",onDownload={ downloads++ },onCancel={},onInstall={},onDismiss={ dismissed++ }) } }
        compose.waitForIdle()
        compose.screenshot("update-sheet-long-notes")
        compose.onNodeWithText("Download update").assertIsDisplayed().performClick()
        compose.onNodeWithText("Later").assertIsDisplayed()
        assertEquals(1,downloads)
    }
    @Test fun installButtonIsVisibleWhenReady() {
        var installs=0
        compose.setContent { SonderTheme("Dark") { UpdateSheetContent(info,UpdateState(available=info,phase="ready",progress=1f),launching=false,error="",onDownload={},onCancel={},onInstall={ installs++ },onDismiss={}) } }
        compose.waitForIdle()
        compose.screenshot("update-sheet-ready")
        compose.onNodeWithText("Install update").assertIsDisplayed().performClick()
        assertEquals(1,installs)
    }
    @Test fun progressAndCancelAreVisibleWhileDownloading() {
        var cancels=0
        compose.setContent { SonderTheme("Light") { UpdateSheetContent(info,UpdateState(available=info,phase="downloading",progress=.4f),launching=false,error="",onDownload={},onCancel={ cancels++ },onInstall={},onDismiss={}) } }
        compose.waitForIdle()
        compose.onNodeWithText("Downloading… 40%").assertIsDisplayed()
        compose.onNodeWithText("Cancel download").assertIsDisplayed().performClick()
        assertEquals(1,cancels)
    }
}
