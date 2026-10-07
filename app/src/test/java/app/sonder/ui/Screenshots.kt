package app.sonder.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.io.File

/**
 * Saves the topmost window (a sheet or dialog when one is open) to app/build/screenshots for review.
 * Draws the window directly because captureToImage can't capture dialog windows under Robolectric.
 */
fun ComposeContentTestRule.screenshot(name:String) {
    waitForIdle()
    val view=runOnIdle { (onAllNodes(isRoot()).fetchSemanticsNodes().last().root as ViewRootForTest).view.rootView }
    val image=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
    runOnIdle { view.draw(Canvas(image)) }
    val file=File("build/screenshots/$name.png").apply { parentFile?.mkdirs() }
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
}
