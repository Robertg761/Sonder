package app.sonder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.sonder.update.AppUpdater
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun UpdateSheet(updater:AppUpdater,onDismiss:()->Unit) {
    val state by updater.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var launching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val info=state.available ?: return
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("A new chapter for Sonder",style=MaterialTheme.typography.headlineMedium)
            Text("Version ${info.version} · ${"%.1f".format(info.size/1_000_000.0)} MB",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)
            if(info.notes.isNotBlank()) Text(info.notes,style=MaterialTheme.typography.bodyMedium)
            Text("The update comes from Sonder's GitHub releases. Your library and reading history stay saved. Android will ask you to confirm installation.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.phase=="downloading" || state.phase=="verifying") {
                LinearProgressIndicator(progress={ state.progress },modifier=Modifier.fillMaxWidth())
                Text(if(state.phase=="verifying") "Verifying the update…" else "Downloading… ${(state.progress*100).toInt()}%")
                TextButton({ updater.cancel() }) { Text("Cancel download") }
            } else if(state.phase=="ready") {
                Text("Ready to install. If Android asks, allow installs from Sonder, return here, then tap Install update again.",style=MaterialTheme.typography.bodyMedium)
                Button(onClick={ launching=true;error="";scope.launch { try { context.startActivity(updater.installIntent()) } catch(e:Exception) { error=e.message ?: "Android couldn't open the installer." } finally { launching=false } } },enabled=!launching,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(launching) "Opening installer…" else "Install update") }
            } else Button(onClick=updater::download,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(if(state.phase=="error") "Retry download" else "Download update") }
            if(state.message.isNotBlank()) Text(state.message,color=MaterialTheme.colorScheme.error)
            if(error.isNotBlank()) Text(error,color=MaterialTheme.colorScheme.error)
            TextButton(onDismiss,modifier=Modifier.fillMaxWidth()) { Text("Later") }
        }
    }
}
