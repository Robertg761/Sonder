package app.sonder.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk=[34],qualifiers="w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReturnBannerTest {
    @get:Rule val compose=createComposeRule()

    @Test fun showsWhereListeningWasAndReturns() {
        var returns=0;var dismissals=0
        compose.setContent { SonderTheme("Light") { ReturnBanner("Chapter 18",1_421_000,behind=false,onReturn={ returns++ },onDismiss={ dismissals++ },Modifier.padding(16.dp)) } }
        compose.onNodeWithText("Skipped back by mistake?").assertIsDisplayed()
        compose.onNodeWithText("You were 23:41 into Chapter 18").assertIsDisplayed()
        compose.screenshot("return-banner")
        compose.onNodeWithText("Return").performClick()
        assertEquals(1,returns)
        compose.onNodeWithContentDescription("Dismiss").performClick()
        assertEquals(1,dismissals)
    }
    @Test fun saysSkippedAheadWhenTheSkipWentForward() {
        compose.setContent { SonderTheme("Light") { ReturnBanner("The Storm",65_000,behind=true,onReturn={},onDismiss={}) } }
        compose.onNodeWithText("Skipped ahead by mistake?").assertIsDisplayed()
        compose.onNodeWithText("You were 1:05 into The Storm").assertIsDisplayed()
    }
}
