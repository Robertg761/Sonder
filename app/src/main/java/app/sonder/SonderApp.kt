package app.sonder
import android.app.Application
import app.sonder.data.LibraryStore
import app.sonder.data.Preferences
class SonderApp : Application() {
    val storageError = kotlinx.coroutines.flow.MutableStateFlow("")
    val backgroundScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, error -> android.util.Log.e("Sonder", "Library save failed", error);storageError.value="Could not save listening progress. Check available storage and reopen Sonder." })
    val updater by lazy { app.sonder.update.AppUpdater(this) }
    val store by lazy { LibraryStore(this) }
    val preferences by lazy { Preferences(this) }
    // One importer for the app, so downloads and manual imports share its lock and progress.
    val importer by lazy { app.sonder.data.Importer(this, store) }
    val downloads by lazy { app.sonder.download.Downloads(this, importer) }
}
