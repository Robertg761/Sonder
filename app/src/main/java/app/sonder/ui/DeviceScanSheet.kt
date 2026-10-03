package app.sonder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sonder.data.DeviceScanner

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@UnstableApi
@Composable fun DeviceScanSheet(vm:LibraryViewModel,onDismiss:()->Unit,onScan:(Boolean)->Unit,onImport:(List<DeviceScanner.File>)->Unit) {
    val scan by vm.deviceScan.collectAsStateWithLifecycle()
    var includeVideo by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(scan.result) { selected=scan.result.files.filter { it.suggested && !it.imported }.map { it.uri.toString() }.toSet() }
    val files=scan.result.files.filter { query.isBlank() || it.name.contains(query,true) || it.path.contains(query,true) }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal=24.dp)) {
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                item {
                    Text("Find your audiobooks",style=MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(12.dp));Text("Scan shared audio on your phone and available SD cards, then choose what to add. Android will ask for audio access. Private app folders and unindexed files still need the file or folder picker.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Include MP4 and other videos",style=MaterialTheme.typography.titleSmall);Text("Also asks for video access. Video is played as audio.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };Switch(includeVideo,{ includeVideo=it },enabled=!scan.running) }
                    Button(onClick={ onScan(includeVideo) },enabled=!scan.running,modifier=Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Search,null);Spacer(Modifier.width(8.dp));Text(if(scan.completed) "Scan again" else "Scan device") }
                    if(scan.running) { Spacer(Modifier.height(12.dp));LinearProgressIndicator(Modifier.fillMaxWidth());Text("Searching shared media…",modifier=Modifier.padding(top=8.dp)) }
                    if(scan.error.isNotBlank()) Text(scan.error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(vertical=12.dp))
                    if(scan.completed) {
                        Spacer(Modifier.height(16.dp));Text("${scan.result.files.size} supported ${if(scan.result.files.size==1) "file" else "files"} found",style=MaterialTheme.typography.titleMedium)
                        Text("Audiobook tags, M4B files, and audiobook folders are suggested. Review the selection; other audio may be music.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(includeVideo && !scan.result.includesVideo) Text("Video access wasn't granted. These results contain audio only.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(scan.result.limited) Text("Showing the first ${DeviceScanner.MAX_FILES} files. Use a folder import to find more.",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp));OutlinedTextField(query,{ query=it },modifier=Modifier.fillMaxWidth(),singleLine=true,label={ Text("Search filenames or folders") },leadingIcon={ Icon(Icons.Rounded.Search,null) })
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton({ selected=selected+files.filter { !it.imported }.map { it.uri.toString() } }) { Text("Select shown") }
                            TextButton({ selected=scan.result.files.filter { it.suggested && !it.imported }.map { it.uri.toString() }.toSet() }) { Text("Suggested only") }
                            TextButton({ selected=emptySet() }) { Text("Clear") }
                        }
                        if(scan.result.files.isEmpty()) Text("No supported media was indexed by Android. Try Choose a folder or Choose audio files.",modifier=Modifier.padding(vertical=12.dp))
                    }
                }
                items(files,key={ it.uri.toString() }) { file ->
                    val key=file.uri.toString()
                    Row(Modifier.fillMaxWidth().clickable(enabled=!file.imported) { selected=if(key in selected) selected-key else selected+key }.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(key in selected,{ checked -> selected=if(checked) selected+key else selected-key },enabled=!file.imported)
                        Column(Modifier.weight(1f)) {
                            Text(file.name,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(file.path.ifBlank { "Shared media" },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(if(file.imported) "Already in your library" else "${duration(file.duration)}${if(file.suggested) " · Suggested audiobook" else ""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Button(onClick={ onImport(scan.result.files.filter { it.uri.toString() in selected && !it.imported }) },enabled=selected.isNotEmpty() && !scan.running,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp).heightIn(min=52.dp)) { Text("Import ${selected.size} selected ${if(selected.size==1) "file" else "files"}") }
        }
    }
}
