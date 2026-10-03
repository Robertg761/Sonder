package app.sonder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import app.sonder.data.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable fun BookmarkScreen(library:Library,onOpen:(Book,Long)->Unit,onDelete:(Long)->Unit,onEdit:(Long,String)->Unit,onOptions:(Book)->Unit) {
    var edit by remember { mutableStateOf<Bookmark?>(null) };var note by remember { mutableStateOf("") }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Text("Your bookmarks",style=MaterialTheme.typography.headlineLarge);Text("The moments worth coming back to.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp)) }
        if(library.bookmarks.isEmpty()) item { EmptyState(Icons.Rounded.BookmarkBorder,"A place for the good parts","Tap Bookmark in the player to save a passage and add your own note.") }
        items(library.bookmarks,key={ it.id }) { mark ->
            val book=library.books.firstOrNull { it.id==mark.bookId }
            if(book!=null) Surface(Modifier.fillMaxWidth().combinedClickable(onClick={ onOpen(book,mark.position) },onLongClickLabel="Book options",onLongClick={ onOptions(book) }),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Cover(book,Modifier.width(38.dp).height(50.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text(book.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text(clock(mark.position),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall) };IconAction(Icons.Rounded.Edit,"Edit bookmark",{ edit=mark;note=mark.note });IconAction(Icons.Rounded.DeleteOutline,"Delete bookmark",{ onDelete(mark.id) }) }
                    Spacer(Modifier.height(14.dp));Text(mark.note,style=MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
    edit?.let { mark -> AlertDialog(onDismissRequest={ edit=null },title={ Text("Edit bookmark") },text={ OutlinedTextField(note,{ note=it },minLines=3,label={ Text("Note") }) },confirmButton={ TextButton({ onEdit(mark.id,note.ifBlank { "Bookmark" });edit=null }) { Text("Save") } },dismissButton={ TextButton({ edit=null }) { Text("Cancel") } }) }
}
@Composable fun InsightsScreen(library:Library,goal:Int) {
    val today=LocalDate.now();val days=(6 downTo 0).map { today.minusDays(it.toLong()) }
    val todayMs=library.daily[today.toString()] ?: 0
    val week=days.sumOf { library.daily[it.toString()] ?: 0 }
    val max=days.maxOf { library.daily[it.toString()] ?: 0 }.coerceAtLeast(goal*60000L)
    var streak=0;var day=if(todayMs>0) today else today.minusDays(1)
    while((library.daily[day.toString()] ?: 0)>0 && streak<10000) { streak++;day=day.minusDays(1) }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { Text("Your listening life",style=MaterialTheme.typography.headlineLarge);Text("One chapter at a time.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp)) }
        item { Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer) { Column(Modifier.fillMaxWidth().padding(24.dp)) { Text("TODAY",style=MaterialTheme.typography.labelSmall,letterSpacing=1.sp);Spacer(Modifier.height(12.dp));Text("${todayMs/60000} minutes",style=MaterialTheme.typography.headlineLarge);Spacer(Modifier.height(16.dp));LinearProgressIndicator(progress={ (todayMs.toFloat()/(goal*60000)).coerceIn(0f,1f) },modifier=Modifier.fillMaxWidth().height(6.dp),drawStopIndicator={});Text("Your daily goal is $goal minutes",modifier=Modifier.padding(top=10.dp),style=MaterialTheme.typography.bodyMedium) } } }
        item { SectionTitle("This week",duration(week));Row(Modifier.fillMaxWidth().height(180.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Bottom) { days.forEach { date ->
            val value=library.daily[date.toString()] ?: 0
            Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.weight(1f).semantics { contentDescription="${date.dayOfWeek}: ${value/60000} minutes listened" }) {
                Text("${value/60000}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(6.dp));Box(Modifier.width(24.dp).height(((value.toFloat()/max)*120).coerceAtLeast(4f).dp).clip(RoundedCornerShape(6.dp)).background(if(date==today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer));Spacer(Modifier.height(8.dp));Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW,Locale.getDefault()),style=MaterialTheme.typography.bodySmall)
            }
        } } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { InsightCard("${library.books.count { it.finished }}","Books finished",Modifier.weight(1f));InsightCard("$streak","Day streak",Modifier.weight(1f)) };Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { InsightCard(duration(library.daily.values.sum()),"Total listening",Modifier.weight(1f));InsightCard("${library.bookmarks.size}","Saved moments",Modifier.weight(1f)) } }
        item { Text("Listening time counts time actually spent playing, including background playback. Skipping and seeking do not add time.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable private fun InsightCard(value:String,label:String,modifier:Modifier) { Surface(modifier,shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceVariant) { Column(Modifier.padding(18.dp)) { Text(value,style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(4.dp));Text(label,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } } }

@UnstableApi
@Composable fun SettingsScreen(settings:Settings,vm:LibraryViewModel,library:Library,onFiles:()->Unit,onFolder:()->Unit,onExport:()->Unit,onRestore:()->Unit,onScan:()->Unit,onUpdate:()->Unit) {
    var option by remember { mutableStateOf("") };var restoreConfirm by remember { mutableStateOf(false) };var licenses by remember { mutableStateOf(false) }
    val updater by vm.updater.state.collectAsStateWithLifecycle()
    val update=vm.preferences::update
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        item { SectionTitle("Make yourself comfortable");Text("Appearance",style=MaterialTheme.typography.titleSmall);Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { listOf("System","Light","Dark").forEach { t -> FilterChip(selected=settings.theme==t,onClick={ update(settings.copy(theme=t)) },label={ Text(t) }) } } }
        item { SectionTitle("Playback");SettingSwitch("Skip silence","Skip quiet gaps in narration",settings.skipSilence,{ update(settings.copy(skipSilence=it)) });SettingSwitch("Preserve voice pitch","Keep narration natural at faster speeds",settings.preservePitch,{ update(settings.copy(preservePitch=it)) }) }
        item { SettingAction("Rewind button","${settings.rewind} seconds",Icons.Rounded.Replay,{ option="Rewind" });SettingAction("Forward button","${settings.forward} seconds",Icons.Rounded.Forward30,{ option="Forward" });SettingAction("Smart rewind","${settings.smartRewind} seconds when opening a book",Icons.Rounded.History,{ option="Smart rewind" }) }
        item { SectionTitle("Listening goal");SettingAction("Daily goal","${settings.dailyGoal} minutes",Icons.Rounded.Flag,{ option="Daily goal" }) }
        item { SectionTitle("Library & files");SettingAction("Add audio files","Import files from your phone or storage provider",Icons.Rounded.AudioFile,onFiles);SettingAction("Add a folder","Group audio tracks and read chapter files",Icons.Rounded.FolderOpen,onFolder);SettingAction("Scan device for audiobooks","Find shared audio without choosing a folder",Icons.Rounded.Search,onScan);if(library.folders.isNotEmpty()) SettingAction("Rescan saved folders","Add new files without importing duplicates",Icons.Rounded.Refresh,vm::rescan) }
        items(library.folders) { uri -> Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.Folder,null,tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(12.dp));Text(android.net.Uri.decode(uri.substringAfterLast('/')),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis);IconAction(Icons.Rounded.Close,"Stop watching this folder",{ vm.forgetFolder(uri) }) } }
        item { SectionTitle("Backup & restore");SettingAction("Export library backup","Save playback data, bookmarks, and reading history",Icons.Rounded.FileUpload,onExport);SettingAction("Restore a backup","Restore saved information for imported books",Icons.Rounded.FileDownload,{ restoreConfirm=true });Text("Backups include reading history, but do not contain audio or cover images. Reading records restore without media. Import original files to restore playback data. Folder permissions must be granted again on a new phone.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(vertical=8.dp)) }
        item { SectionTitle("App updates");SettingSwitch("Automatic update checks","Check GitHub when you open Sonder, at most once a day",updater.automatic,vm.updater::automatic);SettingAction(if(updater.checking) "Checking for updates…" else "Check for updates","Installed version ${app.sonder.BuildConfig.VERSION_NAME}",Icons.Rounded.SystemUpdate,{ vm.updater.check() });if(updater.available!=null) SettingAction("Update to ${updater.available!!.version}",if(updater.phase=="ready") "Downloaded and ready to install" else "View release notes and download",Icons.Rounded.Download,onUpdate);if(updater.message.isNotBlank()) Text(updater.message,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(vertical=8.dp)) }
        item { SectionTitle("About Sonder");Text("Sonder ${app.sonder.BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.titleMedium);Spacer(Modifier.height(10.dp));Text("Your books stay on your device. Sonder has no account, advertising, or analytics. Internet access is used only to check and download updates from GitHub.",style=MaterialTheme.typography.bodyLarge);Spacer(Modifier.height(16.dp));Text("Supported files",style=MaterialTheme.typography.titleSmall);Text("MP4, M4B, M4A, MP3, AAC, FLAC, OGG, OPUS, WAV, WebM, Matroska, AMR, and 3GP. Playback depends on the file's audio codec and the device decoder. MP4 video is played as audio.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp));Spacer(Modifier.height(12.dp));Text("Chapters are read from MP4/M4B chapter lists and QuickTime chapter tracks, MP3 ID3 CHAP tags, Vorbis chapter comments, and folder CUE sheets. Files without chapter metadata become track chapters. DRM-protected AA, AAX, AAXC, and unsupported WMA/AIFF files need conversion to an unprotected supported format.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(12.dp));SettingAction("Open-source licenses","The libraries behind Sonder",Icons.Rounded.Info,{ licenses=true });Spacer(Modifier.height(24.dp)) }
    }
    if(licenses) {
        val context=LocalContext.current
        val text by produceState("Loading licenses…") { value=withContext(Dispatchers.IO) { context.assets.open("notices.txt").bufferedReader().use { it.readText() }+"\n\n"+context.assets.open("apache-2.0.txt").bufferedReader().use { it.readText() } } }
        AlertDialog(onDismissRequest={ licenses=false },title={ Text("Open-source licenses") },text={ Text(text,modifier=Modifier.verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall) },confirmButton={ TextButton({ licenses=false }) { Text("Close") } })
    }
    if(option.isNotEmpty()) {
        val values=when(option) { "Daily goal" -> listOf(10,15,20,30,45,60,90,120);"Smart rewind" -> listOf(0,3,5,10,15,30);else -> listOf(5,10,15,20,30,45,60) }
        AlertDialog(onDismissRequest={ option="" },title={ Text(option) },text={ Column { values.forEach { n -> TextButton({ update(when(option) { "Daily goal" -> settings.copy(dailyGoal=n);"Smart rewind" -> settings.copy(smartRewind=n);"Rewind" -> settings.copy(rewind=n);else -> settings.copy(forward=n) });option="" },modifier=Modifier.fillMaxWidth()) { Text("$n ${if(option=="Daily goal") "minutes" else "seconds"}") } } } },confirmButton={},dismissButton={ TextButton({ option="" }) { Text("Cancel") } })
    }
    if(restoreConfirm) AlertDialog(onDismissRequest={ restoreConfirm=false },title={ Text("Restore your library information?") },text={ Text("Matching library books receive the backup's metadata, listening progress, and bookmarks. Current bookmarks on those books are replaced. Reading history is merged by entry ID and restores without audio. Export a backup first to keep the current state.") },confirmButton={ TextButton({ restoreConfirm=false;onRestore() }) { Text("Choose backup") } },dismissButton={ TextButton({ restoreConfirm=false }) { Text("Cancel") } })
}
@Composable private fun SettingSwitch(title:String,subtitle:String,checked:Boolean,onChecked:(Boolean)->Unit) { Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) };Spacer(Modifier.width(12.dp));Switch(checked,onChecked) } }
@Composable private fun SettingAction(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) { Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) { Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleMedium);Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) };Spacer(Modifier.width(8.dp));Icon(Icons.Rounded.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant) } }
