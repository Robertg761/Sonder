package app.sonder

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.sonder.data.Importer
import app.sonder.data.ListeningStatus
import app.sonder.media.PlaybackService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@UnstableApi
@RunWith(AndroidJUnit4::class)
class PlaybackUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val app get()=context.applicationContext as SonderApp
    private fun screenshot(name:String) { instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(context.filesDir,name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle() } }
    private fun seedBook() {
        runBlocking {
            app.store.refresh();app.store.library.value.books.forEach { app.store.remove(it.id) }
            val dir=File(context.filesDir,"fixtures").apply { mkdirs() };val file=File(dir,"chapters.mp4")
            instrumentation.context.assets.open("chapters.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
            Importer(context,app.store).files(listOf(FileProvider.getUriForFile(context,"app.sonder.audiobooks.testfiles",file)))
        }
    }
    @Test fun importsPlaysInBackgroundSavesProgressAndPausesAtChapterEnd() {
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        seedBook()
        compose.waitUntil(15000) { compose.onAllNodesWithText("The listening test").fetchSemanticsNodes().isNotEmpty() }
        screenshot("library.png")
        compose.onNodeWithTag("shelfBook-${app.store.library.value.books.single().id}").performClick()
        compose.onNodeWithText("Start listening").performScrollTo().performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty() }
        screenshot("player.png")
        compose.waitUntil(20000) { app.store.library.value.books.firstOrNull()?.position?.let { it>2000 }==true }
        compose.onNodeWithContentDescription("Add bookmark").performClick()
        compose.onNodeWithText("Add a note").performTextInput("Remember this passage")
        compose.onNodeWithText("Save bookmark").performClick()
        compose.waitUntil(10000) { app.store.library.value.bookmarks.any { it.note=="Remember this passage" } }
        val notificationManager=context.getSystemService(NotificationManager::class.java)
        assertTrue(notificationManager.activeNotifications.any { it.notification.category==Notification.CATEGORY_TRANSPORT })
        val before=app.store.library.value.books.single().position
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        compose.waitUntil(15000) { app.store.library.value.books.single().position>before+2000 }
        val controller=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync().get(20,TimeUnit.SECONDS)
        instrumentation.runOnMainSync {
            controller.seekTo(0,5000)
            controller.setPlaybackSpeed(1.5f)
            controller.sendCustomCommand(SessionCommand(PlaybackService.TIMER,Bundle.EMPTY),Bundle().apply { putInt("minutes",-1) })
            controller.seekTo(0,9000)
        }
        val deadline=System.currentTimeMillis()+10000
        var playing=true
        while(playing && System.currentTimeMillis()<deadline) { Thread.sleep(200);instrumentation.runOnMainSync { playing=controller.isPlaying } }
        assertFalse("End-of-chapter timer must pause playback",playing)
        runBlocking { app.store.refresh() }
        compose.waitUntil(15000) { app.store.library.value.books.single().position>=10000 }
        assertEquals(1.5f,app.store.library.value.books.single().speed)
        // Relaunching a fresh Activity reads the persistent library rather than recreating a demo.
        compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithText("The listening test").fetchSemanticsNodes().isNotEmpty() }
        instrumentation.runOnMainSync { controller.stop();controller.clearMediaItems();controller.release() }
    }
    @Test fun libraryRemainsUsableInLandscapeAndLargeText() {
        seedBook()
        try {
            compose.activityRule.scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            compose.waitUntil(15000) { compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithTag("libraryGrid").performScrollToNode(hasText("The listening test"))
            compose.onNodeWithText("The listening test").assertIsDisplayed()
            screenshot("landscape.png")
            compose.activityRule.scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 2.0")).use { it.readBytes() }
            compose.waitUntil(15000) { compose.activity.resources.configuration.fontScale>=1.9f && compose.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT }
            compose.onNodeWithTag("libraryGrid").performScrollToNode(hasText("The listening test"))
            compose.onNodeWithText("The listening test").assertIsDisplayed()
            screenshot("large-text.png")
        } finally {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("settings put system font_scale 1.0")).use { it.readBytes() }
            compose.activityRule.scenario.onActivity { it.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        }
    }

    @Test fun longPressChangesContinueListeningGridAndListStatus() {
        seedBook()
        val id=app.store.library.value.books.single().id
        runBlocking { app.store.progress(id,12000,0);app.store.addBookmark(id,5000,"Keep bookmark") }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("continueBook-$id").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("continueBook-$id").performTouchInput { longClick() }
        compose.onNodeWithText("Mark as not started").performClick()
        compose.onNodeWithText("Mark not started").performClick()
        compose.waitUntil(10000) { !app.store.library.value.books.single().inProgress }
        assertEquals(0,app.store.library.value.books.single().position)
        assertEquals("Keep bookmark",app.store.library.value.bookmarks.single().note)
        compose.onAllNodesWithTag("continueBook-$id").assertCountEquals(0)
        compose.onNodeWithTag("libraryGrid").performScrollToNode(hasTestTag("shelfBook-$id"))
        compose.onNodeWithTag("shelfBook-$id").performTouchInput { longClick() }
        compose.onNodeWithText("Mark as in progress").performClick()
        compose.waitUntil(10000) { app.store.library.value.books.single().inProgress }
        compose.onNodeWithContentDescription("Change library layout").performClick()
        compose.onNodeWithTag("libraryGrid").performScrollToNode(hasTestTag("shelfBook-$id"))
        compose.onNodeWithTag("shelfBook-$id").performTouchInput { longClick() }
        compose.onNodeWithText("Mark as finished").performClick()
        compose.waitUntil(10000) { app.store.library.value.books.single().finished }
        compose.onAllNodesWithTag("continueBook-$id").assertCountEquals(0)
    }

    @Test fun markingPlayingBookNotStartedStopsPlaybackAndStaysReset() {
        seedBook()
        compose.waitUntil(15000) { compose.onAllNodesWithText("The listening test").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shelfBook-${app.store.library.value.books.single().id}").performClick()
        compose.onNodeWithText("Start listening").performScrollTo().performClick()
        compose.waitUntil(20000) { app.store.library.value.books.firstOrNull()?.position?.let { it>2000 }==true }
        compose.onNodeWithContentDescription("Cover of The listening test").performTouchInput { longClick() }
        compose.onNodeWithText("Mark as not started").performClick()
        compose.onNodeWithText("Mark not started").performClick()
        compose.waitUntil(10000) { app.store.library.value.books.single().position==0L && !app.store.library.value.books.single().inProgress }
        // Let a complete autosave interval pass so late service callbacks cannot overwrite the reset.
        Thread.sleep(4000)
        assertEquals(0,app.store.library.value.books.single().position)
        assertFalse(app.store.library.value.books.single().inProgress)
        val controller=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync().get(20,TimeUnit.SECONDS)
        instrumentation.runOnMainSync { assertFalse(controller.isPlaying);assertEquals(0,controller.mediaItemCount);controller.release() }
    }
    @Test fun deviceScanSheetFindsAndImportsSelectedAudio() {
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.READ_MEDIA_AUDIO").close()
        runBlocking { app.store.refresh();app.store.library.value.books.forEach { app.store.remove(it.id) } }
        val resolver=context.contentResolver
        val uri=resolver.insert(android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY),android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"UI discovery test.m4b");put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"audio/mp4");put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,"Audiobooks/Sonder UI tests/");put(android.provider.MediaStore.MediaColumns.IS_PENDING,1)
        })!!
        try {
            instrumentation.context.assets.open("chapters.m4b").use { input -> resolver.openOutputStream(uri)!!.use { input.copyTo(it) } }
            resolver.update(uri,android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING,0) },null,null)
            compose.onNodeWithContentDescription("Import audiobooks").performClick()
            compose.onNodeWithText("Scan device for audiobooks").performClick()
            compose.onNodeWithText("Scan device").performClick()
            compose.waitUntil(20000) { compose.onAllNodesWithText("UI discovery test.m4b").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("UI discovery test.m4b").assertIsDisplayed()
            screenshot("discovery.png")
            compose.onNodeWithText("Clear").performClick()
            compose.onNodeWithText("UI discovery test.m4b").performClick()
            compose.onNodeWithText("Import 1 selected file").performClick()
            compose.waitUntil(15000) { app.store.library.value.books.any { it.title=="The listening test" } }
            assertEquals(3,runBlocking { app.store.chapters(app.store.library.value.books.single().id) }.size)
        } finally { resolver.delete(uri,null,null) }
    }

    private fun clearReadingHistory() { runBlocking { app.store.library.value.readingHistory.forEach { app.store.deleteReading(it.id) } } }
    @Test fun manualReadingEntryCanRecordPartialProgressEditAndDeleteWithoutMedia() {
        clearReadingHistory();runBlocking { app.store.refresh();app.store.library.value.books.forEach { app.store.remove(it.id) } }
        compose.onNodeWithText("History").performClick()
        compose.onNodeWithContentDescription("Add reading history").performClick()
        compose.onNodeWithText("Book title").performTextInput("A paper book")
        compose.onNodeWithText("Author").performScrollTo().performTextInput("A writer")
        compose.onNodeWithTag("readingPercent").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(42f) }
        compose.onNodeWithTag("readingEditorForm").performScrollToNode(hasText("Reading notes"))
        compose.onNodeWithText("Reading notes").performTextInput("Stopped at chapter six")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Save reading entry").performClick()
        compose.waitUntil(10000) { app.store.library.value.readingHistory.any { it.title=="A paper book" } }
        assertTrue(app.store.library.value.books.isEmpty());assertEquals(42,app.store.library.value.readingHistory.single().percent)
        assertEquals("Stopped at chapter six",app.store.library.value.readingHistory.single().notes)
        compose.onNodeWithText("42% read").assertIsDisplayed();screenshot("reading-history.png")
        compose.onNodeWithContentDescription("Edit reading entry: A paper book").performClick()
        compose.onNodeWithTag("readingEditorForm").performScrollToNode(hasTestTag("markReadingCompleted"))
        compose.onNodeWithTag("markReadingCompleted").performClick()
        compose.onNodeWithText("Save reading entry").performClick()
        compose.waitUntil(10000) { app.store.library.value.readingHistory.single().percent==100 }
        compose.onNodeWithContentDescription("Delete reading entry: A paper book").performClick()
        compose.onNodeWithText("Delete entry").performClick()
        compose.waitUntil(10000) { app.store.library.value.readingHistory.isEmpty() }
    }
    @Test fun finishingInBackgroundAsksToLogAndDeclineDoesNotRecordHistory() {
        clearReadingHistory();seedBook()
        compose.waitUntil(15000) { compose.onAllNodesWithText("The listening test").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("shelfBook-${app.store.library.value.books.single().id}").performClick()
        compose.onNodeWithText("Start listening").performScrollTo().performClick()
        compose.waitUntil(20000) { compose.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty() }
        val controller=MediaController.Builder(context,SessionToken(context,ComponentName(context,PlaybackService::class.java))).buildAsync().get(20,TimeUnit.SECONDS)
        try {
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            instrumentation.runOnMainSync { controller.seekTo(0,29000);controller.play() }
            compose.waitUntil(15000) { app.store.library.value.completionPrompts.isNotEmpty() }
            assertTrue(app.store.library.value.readingHistory.isEmpty())
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Add this book to reading history?").assertIsDisplayed()
            compose.onNodeWithText("No thanks").performClick()
            compose.waitUntil(10000) { app.store.library.value.completionPrompts.isEmpty() }
            assertTrue(app.store.library.value.readingHistory.isEmpty())
            val id=app.store.library.value.books.single().id
            runBlocking { app.store.startListening(id) }
            instrumentation.runOnMainSync { controller.seekTo(0,29000);controller.play() }
            compose.waitUntil(15000) { app.store.library.value.completionPrompts.isNotEmpty() }
            compose.onNodeWithText("Add to history").performClick()
            compose.onNodeWithText("Reading notes").performScrollTo().performTextInput("Finished after bedtime")
            compose.onNodeWithText("Save reading entry").performClick()
            compose.waitUntil(10000) { app.store.library.value.readingHistory.size==1 }
            assertEquals(100,app.store.library.value.readingHistory.single().percent)
            assertTrue(app.store.library.value.completionPrompts.isEmpty())
            runBlocking { app.store.markStatus(id,ListeningStatus.NOT_STARTED) }
            compose.onNodeWithText("Completed · 100% read").assertIsDisplayed()
            assertEquals(0,app.store.library.value.books.single().position)
        } finally { instrumentation.runOnMainSync { controller.stop();controller.clearMediaItems();controller.release() };clearReadingHistory() }
    }

}
