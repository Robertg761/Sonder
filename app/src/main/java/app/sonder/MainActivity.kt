package app.sonder

import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import app.sonder.ui.LibraryViewModel
import app.sonder.ui.SonderRoot

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onStart() { super.onStart();(application as SonderApp).updater.foreground() }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        setContent {
            val vm:LibraryViewModel=viewModel()
            val appearance by vm.settings.collectAsStateWithLifecycle()
            val dark=appearance.theme=="Dark" || appearance.theme=="System" && isSystemInDarkTheme()
            SideEffect {
                WindowInsetsControllerCompat(window,window.decorView).apply { isAppearanceLightStatusBars=!dark;isAppearanceLightNavigationBars=!dark }
            }
            var coverBook by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(0) }
            val cover=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.changeCover(coverBook,uri) }
            val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            var scanVideo by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
            val scanPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.scanDevice(scanVideo) }
            val files=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                uris.forEach { uri -> runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) }.onFailure { vm.notice("This provider did not grant lasting access. Keep the file available and reimport it if needed.") } }
                if(uris.isNotEmpty()) vm.importFiles(uris)
            }
            val folder=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                if(uri!=null) runCatching { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);vm.importFolder(uri) }.onFailure { vm.notice("Folder access was not granted. Choose another folder.") }
            }
            val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null) vm.export(uri) }
            val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) vm.restore(uri) }
            SonderRoot(vm,onFiles={ files.launch(arrayOf("audio/*","video/mp4","video/x-matroska","application/octet-stream")) },onFolder={ folder.launch(null) },onExport={ export.launch("sonder-backup-${java.time.LocalDate.now()}.json") },onRestore={ restore.launch(arrayOf("application/json","text/plain","application/octet-stream")) },onScan={ includeVideo -> scanVideo=includeVideo;val permissions=app.sonder.data.DeviceScanner.requiredPermissions(includeVideo);if(permissions.all { androidx.core.content.ContextCompat.checkSelfPermission(this,it)==android.content.pm.PackageManager.PERMISSION_GRANTED }) vm.scanDevice(includeVideo) else scanPermission.launch(permissions) },onNotification={ if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) },onCover={ coverBook=it.id;cover.launch(arrayOf("image/*")) })
        }
    }
}
