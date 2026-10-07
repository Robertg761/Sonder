package app.sonder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontFamily
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
    LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,top=16.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Bookmarks","The moments worth coming back to.");Spacer(Modifier.height(8.dp)) }
        if(library.bookmarks.isEmpty()) item { EmptyState(Icons.Rounded.BookmarkBorder,"A place for the good parts","Tap Bookmark in the player to save a passage and add your own note.") }
        items(library.bookmarks,key={ it.id }) { mark ->
            val book=library.books.firstOrNull { it.id==mark.bookId }
            if(book!=null) Surface(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).combinedClickable(onClick={ onOpen(book,mark.position) },onLongClickLabel="Book options",onLongClick={ onOptions(book) }),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(start=16.dp,end=6.dp,top=14.dp,bottom=16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Cover(book,Modifier.width(36.dp).height(48.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text(book.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.Bookmark,null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(4.dp));Text(clock(mark.position),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium) } };IconAction(Icons.Rounded.Edit,"Edit bookmark",{ edit=mark;note=mark.note },tint=MaterialTheme.colorScheme.onSurfaceVariant);IconAction(Icons.Rounded.DeleteOutline,"Delete bookmark",{ onDelete(mark.id) },tint=MaterialTheme.colorScheme.onSurfaceVariant) }
                    // The note reads as a pull quote: a slim accent rule beside serif text.
                    Row(Modifier.padding(top=12.dp,end=10.dp).height(IntrinsicSize.Min)) { Box(Modifier.width(3.dp).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha=.5f)));Spacer(Modifier.width(12.dp));Text(mark.note,style=MaterialTheme.typography.bodyLarge.copy(fontFamily=FontFamily.Serif)) }
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
    LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,top=16.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { PageHeader("Insights","Your listening life, one chapter at a time.");Spacer(Modifier.height(8.dp)) }
        item { Surface(shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.primaryContainer) { Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text("TODAY",style=MaterialTheme.typography.labelMedium,letterSpacing=1.5.sp,color=MaterialTheme.colorScheme.onPrimaryContainer,modifier=Modifier.weight(1f));Text("${(todayMs*100/(goal*60000L).coerceAtLeast(1)).coerceAtMost(999)}% of goal",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.height(10.dp));Row(verticalAlignment=Alignment.Bottom) { Text("${todayMs/60000}",style=MaterialTheme.typography.displaySmall,color=MaterialTheme.colorScheme.onPrimaryContainer);Text(" / $goal min",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha=.7f),modifier=Modifier.padding(bottom=6.dp)) }
            Spacer(Modifier.height(14.dp));ProgressBar(todayMs.toFloat()/(goal*60000),height=8.dp)
        } } }
        item { Surface(shape=MaterialTheme.shapes.extraLarge,color=MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.fillMaxWidth().padding(start=20.dp,end=20.dp,top=8.dp,bottom=18.dp)) {
            SectionTitle("This week",duration(week))
            Row(Modifier.fillMaxWidth().height(150.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.Bottom) { days.forEach { date ->
                val value=library.daily[date.toString()] ?: 0
                Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.weight(1f).semantics { contentDescription="${date.dayOfWeek}: ${value/60000} minutes listened" }) {
                    Text(if(value>0) "${value/60000}" else "–",style=MaterialTheme.typography.labelSmall,color=if(date==today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(6.dp))
                    // Each bar sits in a faint full-height track so quiet days still read as days.
                    Box(Modifier.width(22.dp).height(100.dp).clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.onSurface.copy(alpha=.06f)),contentAlignment=Alignment.BottomCenter) { if(value>0) Box(Modifier.fillMaxWidth().fillMaxHeight((value.toFloat()/max).coerceIn(.04f,1f)).clip(MaterialTheme.shapes.extraSmall).background(if(date==today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha=.45f))) }
                    Spacer(Modifier.height(8.dp));Text(date.dayOfWeek.getDisplayName(TextStyle.NARROW,Locale.getDefault()),style=MaterialTheme.typography.labelMedium,color=if(date==today) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } }
        } } }
        item { Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { StatCard("${library.books.count { it.finished }}","Books finished",Modifier.weight(1f));StatCard("$streak","Day streak",Modifier.weight(1f)) };Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { StatCard(duration(library.daily.values.sum()),"Total listening",Modifier.weight(1f));StatCard("${library.bookmarks.size}","Saved moments",Modifier.weight(1f)) } }
        item { Text("Listening time counts time actually spent playing, including background playback. Skipping and seeking do not add time.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp)) }
    }
}

@UnstableApi
@Composable fun SettingsScreen(settings:Settings,vm:LibraryViewModel,library:Library,onFiles:()->Unit,onFolder:()->Unit,onExport:()->Unit,onRestore:()->Unit,onScan:()->Unit,onUpdate:()->Unit,onDownloadFolder:()->Unit,onFind:()->Unit) {
    var option by remember { mutableStateOf("") };var restoreConfirm by remember { mutableStateOf(false) };var licenses by remember { mutableStateOf(false) }
    var tokenOpen by remember { mutableStateOf(false) };var siteOpen by remember { mutableStateOf(false) }
    val updater by vm.updater.state.collectAsStateWithLifecycle()
    val downloads by vm.downloads.settings.collectAsStateWithLifecycle()
    val account by vm.account.collectAsStateWithLifecycle()
    LaunchedEffect(downloads.token) { if(downloads.token.isNotBlank() && account.user==null && account.error.isBlank()) vm.connectRealDebrid() }
    val update=vm.preferences::update
    LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=4.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        item { SettingsGroup("Appearance") { Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("System","Light","Dark").forEach { t -> ChoiceChip(t,settings.theme==t,{ update(settings.copy(theme=t)) }) } } } }
        item { SettingsGroup("Playback") { SettingSwitch("Skip silence","Skip quiet gaps in narration",settings.skipSilence,{ update(settings.copy(skipSilence=it)) });SettingSwitch("Preserve voice pitch","Keep narration natural at faster speeds",settings.preservePitch,{ update(settings.copy(preservePitch=it)) });SettingAction("Rewind button","${settings.rewind} seconds",Icons.Rounded.Replay,{ option="Rewind" });SettingAction("Forward button","${settings.forward} seconds",Icons.Rounded.Forward30,{ option="Forward" });SettingAction("Smart rewind",if(settings.smartRewind==0) "Off" else "${settings.smartRewind} seconds when opening a book",Icons.Rounded.History,{ option="Smart rewind" }) } }
        item { SettingsGroup("Listening goal") { SettingAction("Daily goal","${settings.dailyGoal} minutes",Icons.Rounded.Flag,{ option="Daily goal" }) } }
        item { SettingsGroup("Library & files") {
            SettingAction("Add audio files","Import files from your phone or storage provider",Icons.Rounded.AudioFile,onFiles);SettingAction("Add a folder","Group audio tracks and read chapter files",Icons.Rounded.FolderOpen,onFolder);SettingAction("Scan device for audiobooks","Find shared audio without choosing a folder",Icons.Rounded.Search,onScan);if(library.folders.isNotEmpty()) SettingAction("Rescan saved folders","Add new files without importing duplicates",Icons.Rounded.Refresh,vm::rescan)
            library.folders.forEach { uri -> Row(Modifier.fillMaxWidth().padding(start=16.dp,end=4.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.Folder,null,tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(16.dp));Text(android.net.Uri.decode(uri.substringAfterLast('/')),modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis);IconAction(Icons.Rounded.Close,"Stop watching this folder",{ vm.forgetFolder(uri) },tint=MaterialTheme.colorScheme.onSurfaceVariant) } }
        } }
        item { SettingsGroup("AudioBookBay downloads",footer="Search words go to AudioBookBay. The book's magnet link goes to Real-Debrid, which downloads it for you. Your API token stays on this device.") {
            val user=account.user
            SettingAction("Real-Debrid",when { downloads.token.isBlank() -> "Add your API token to download books";account.checking -> "Checking your account…";account.error.isNotBlank() -> account.error;user==null -> "Token saved";!user.premium -> "${user.name} · Free account. Torrents need premium.";else -> "${user.name} · Premium until ${user.expiration.take(10)}" },Icons.Rounded.Key,{ tokenOpen=true })
            SettingAction("Download folder",if(downloads.folder.isBlank()) "Choose where downloaded books are saved" else android.net.Uri.decode(downloads.folder.substringAfterLast('/')).substringAfter(':').ifBlank { "Internal storage" },Icons.Rounded.CreateNewFolder,onDownloadFolder)
            SettingAction("AudioBookBay address",downloads.site.removePrefix("https://"),Icons.Rounded.Language,{ siteOpen=true })
            if(downloads.ready) SettingAction("Find audiobooks","Search and download into your library",Icons.Rounded.TravelExplore,onFind)
        } }
        item { SettingsGroup("Backup & restore",footer="Backups include reading history, but do not contain audio or cover images. Reading records restore without media. Import original files to restore playback data. Folder permissions must be granted again on a new phone.") { SettingAction("Export library backup","Save playback data, bookmarks, and reading history",Icons.Rounded.FileUpload,onExport);SettingAction("Restore a backup","Restore saved information for imported books",Icons.Rounded.FileDownload,{ restoreConfirm=true }) } }
        item { SettingsGroup("App updates",footer=updater.message) { SettingSwitch("Automatic update checks","Check GitHub when you open Sonder, at most once a day",updater.automatic,vm.updater::automatic);SettingAction(if(updater.checking) "Checking for updates…" else "Check for updates","Installed version ${app.sonder.BuildConfig.VERSION_NAME}",Icons.Rounded.SystemUpdate,{ vm.updater.check() });if(updater.available!=null) SettingAction("Update to ${updater.available!!.version}",if(updater.phase=="ready") "Downloaded and ready to install" else "View release notes and download",Icons.Rounded.Download,onUpdate) } }
        item { SettingsGroup("About Sonder") {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) { SonderMark(Modifier.size(52.dp));Spacer(Modifier.width(14.dp));Column { Text("Sonder",style=MaterialTheme.typography.titleLarge);Text("Version ${app.sonder.BuildConfig.VERSION_NAME}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } };Spacer(Modifier.height(14.dp));Text("Your books stay on your device. Sonder has no account, advertising, or analytics. Internet access is used to check and download updates from GitHub and, if you set up downloads, to search AudioBookBay and download through Real-Debrid.",style=MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp));Text("Supported files",style=MaterialTheme.typography.titleSmall);Text("MP4, M4B, M4A, MP3, AAC, FLAC, OGG, OPUS, WAV, WebM, Matroska, AMR, and 3GP. Playback depends on the file's audio codec and the device decoder. MP4 video is played as audio.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
                Spacer(Modifier.height(12.dp));Text("Chapters",style=MaterialTheme.typography.titleSmall);Text("Chapters are read from MP4/M4B chapter lists and QuickTime chapter tracks, MP3 ID3 CHAP tags, Vorbis chapter comments, and folder CUE sheets. Files without chapter metadata become track chapters. DRM-protected AA, AAX, AAXC, and unsupported WMA/AIFF files need conversion to an unprotected supported format.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
            }
            SettingAction("Open-source licenses","The libraries behind Sonder",Icons.Rounded.Info,{ licenses=true })
        } }
    }
    if(licenses) {
        val context=LocalContext.current
        val text by produceState("Loading licenses…") { value=withContext(Dispatchers.IO) { context.assets.open("notices.txt").bufferedReader().use { it.readText() }+"\n\n"+context.assets.open("apache-2.0.txt").bufferedReader().use { it.readText() } } }
        AlertDialog(onDismissRequest={ licenses=false },title={ Text("Open-source licenses") },text={ Text(text,modifier=Modifier.verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall) },confirmButton={ TextButton({ licenses=false }) { Text("Close") } })
    }
    if(option.isNotEmpty()) {
        val values=when(option) { "Daily goal" -> listOf(10,15,20,30,45,60,90,120);"Smart rewind" -> listOf(0,3,5,10,15,30);else -> listOf(5,10,15,20,30,45,60) }
        val current=when(option) { "Daily goal" -> settings.dailyGoal;"Smart rewind" -> settings.smartRewind;"Rewind" -> settings.rewind;else -> settings.forward }
        AlertDialog(onDismissRequest={ option="" },title={ Text(option) },text={ Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) { values.forEach { n ->
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).selectable(selected=n==current,role=Role.RadioButton,onClick={ update(when(option) { "Daily goal" -> settings.copy(dailyGoal=n);"Smart rewind" -> settings.copy(smartRewind=n);"Rewind" -> settings.copy(rewind=n);else -> settings.copy(forward=n) });option="" }).padding(vertical=10.dp,horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) { RadioButton(selected=n==current,onClick=null);Spacer(Modifier.width(14.dp));Text(if(n==0) "Off" else "$n ${if(option=="Daily goal") "minutes" else "seconds"}",style=MaterialTheme.typography.bodyLarge) }
        } } },confirmButton={},dismissButton={ TextButton({ option="" }) { Text("Cancel") } })
    }
    if(tokenOpen) {
        var token by remember { mutableStateOf("") }
        val uri=androidx.compose.ui.platform.LocalUriHandler.current
        AlertDialog(onDismissRequest={ tokenOpen=false },title={ Text("Real-Debrid API token") },
            text={ Column {
                Text("Sign in at real-debrid.com, open your API token page, and paste the token here. Downloading torrents needs a premium account.",style=MaterialTheme.typography.bodyMedium)
                TextButton({ uri.openUri("https://real-debrid.com/apitoken") },contentPadding=PaddingValues(0.dp)) { Text("Open real-debrid.com/apitoken") }
                OutlinedTextField(token,{ token=it.trim() },label={ Text(if(downloads.token.isBlank()) "API token" else "New API token") },singleLine=true,visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
                if(account.checking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp))
                if(account.error.isNotBlank() && token.isNotBlank()) Text(account.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))
            } },
            confirmButton={ TextButton({ vm.connectRealDebrid(token) { tokenOpen=false } },enabled=token.isNotBlank() && !account.checking) { Text("Connect") } },
            dismissButton={ Row { if(downloads.token.isNotBlank()) TextButton({ vm.disconnectRealDebrid();tokenOpen=false }) { Text("Remove token") };TextButton({ tokenOpen=false }) { Text("Cancel") } } })
    }
    if(siteOpen) {
        var site by remember { mutableStateOf(downloads.site.removePrefix("https://")) }
        AlertDialog(onDismissRequest={ siteOpen=false },title={ Text("AudioBookBay address") },
            text={ Column { Text("AudioBookBay moves between addresses. If searches stop working, enter the address that works in your browser.",style=MaterialTheme.typography.bodyMedium);Spacer(Modifier.height(12.dp));OutlinedTextField(site,{ site=it.trim() },label={ Text("Address") },singleLine=true,modifier=Modifier.fillMaxWidth()) } },
            confirmButton={ TextButton({ if(vm.setDownloadSite(site)) siteOpen=false },enabled=site.isNotBlank()) { Text("Save") } },
            dismissButton={ Row { TextButton({ vm.setDownloadSite(app.sonder.download.AudioBookBay.DEFAULT_SITE);siteOpen=false }) { Text("Reset") };TextButton({ siteOpen=false }) { Text("Cancel") } } })
    }
    if(restoreConfirm) AlertDialog(onDismissRequest={ restoreConfirm=false },title={ Text("Restore your library information?") },text={ Text("Matching library books receive the backup's metadata, listening progress, and bookmarks. Current bookmarks on those books are replaced. Reading history is merged by entry ID and restores without audio. Export a backup first to keep the current state.") },confirmButton={ TextButton({ restoreConfirm=false;onRestore() }) { Text("Choose backup") } },dismissButton={ TextButton({ restoreConfirm=false }) { Text("Cancel") } })
}
@Composable private fun SettingsGroup(title:String,footer:String="",content:@Composable ColumnScope.()->Unit) {
    Column {
        Text(title,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(start=4.dp,bottom=8.dp))
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.fillMaxWidth().padding(vertical=4.dp),content=content) }
        if(footer.isNotBlank()) Text(footer,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(start=4.dp,end=4.dp,top=8.dp))
    }
}
@Composable private fun SettingSwitch(title:String,subtitle:String,checked:Boolean,onChecked:(Boolean)->Unit) { Row(Modifier.fillMaxWidth().clickable { onChecked(!checked) }.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleSmall);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };Spacer(Modifier.width(12.dp));Switch(checked,onChecked) } }
@Composable private fun SettingAction(title:String,subtitle:String,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) { Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically) { Icon(icon,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleSmall);Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };Spacer(Modifier.width(8.dp));Icon(Icons.Rounded.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant) } }
