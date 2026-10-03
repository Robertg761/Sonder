package app.sonder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sonder.data.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable fun SonderRoot(vm:LibraryViewModel,onFiles:()->Unit,onFolder:()->Unit,onExport:()->Unit,onRestore:()->Unit,onNotification:()->Unit,onScan:(Boolean)->Unit,onCover:(Book)->Unit) {
    val updates by vm.updater.state.collectAsStateWithLifecycle()
    var updateOpen by rememberSaveable { mutableStateOf(false) }
    val library by vm.library.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val import by vm.importProgress.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val historySaving by vm.historySaving.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableLongStateOf(0) }
    var player by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var importOpen by rememberSaveable { mutableStateOf(false) }
    var optionsBook by rememberSaveable { mutableLongStateOf(0) }
    var scanOpen by rememberSaveable { mutableStateOf(false) }
    var historyDraft by rememberSaveable(stateSaver=ReadingDraftSaver) { mutableStateOf<ReadingEntry?>(null) }
    var historyPromptId by rememberSaveable { mutableStateOf("") }
    var collectionFilter by rememberSaveable { mutableStateOf("") }
    val snackbar=remember { SnackbarHostState() }
    val scope=rememberCoroutineScope()
    val current=library.books.firstOrNull { it.id==playback.bookId }
    val detail=library.books.firstOrNull { it.id==selected }
    LaunchedEffect(updates.available?.version) { if(updates.available!=null) { if(snackbar.showSnackbar("Sonder ${updates.available!!.version} is available",actionLabel="Update",withDismissAction=true)==SnackbarResult.ActionPerformed) updateOpen=true } }
    LaunchedEffect(notice) { if(notice.isNotEmpty()) { snackbar.showSnackbar(notice);vm.clearNotice() } }
    LaunchedEffect(import.running,import.current,import.errors) { if(!import.running && import.current.isNotBlank()) { vm.notice(import.current+if(import.errors.isNotEmpty()) " · ${import.errors.size} files need attention" else "") } else if(!import.running && import.errors.isNotEmpty()) vm.notice(import.errors.first()) }
    BackHandler(player || settingsOpen || selected>0 || collectionFilter.isNotBlank()) { when { player -> player=false;settingsOpen -> settingsOpen=false;selected>0 -> selected=0;else -> collectionFilter="" } }
    SonderTheme(settings.theme) {
        Surface(Modifier.fillMaxSize()) {
            if(player && current!=null) PlayerScreen(vm,current,playback,onBack={ player=false },onDetails={ selected=current.id;player=false },onOptions={ optionsBook=current.id })
            else Scaffold(
                containerColor=MaterialTheme.colorScheme.background,
                snackbarHost={ SnackbarHost(snackbar) },
                topBar={
                    if(settingsOpen || detail!=null) TopAppBar(title={ Text(if(settingsOpen) "Settings" else "Book details",style=MaterialTheme.typography.titleLarge) },navigationIcon={ IconAction(Icons.AutoMirrored.Rounded.ArrowBack,"Back",{ settingsOpen=false;selected=0 }) },colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
                    else Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start=24.dp,end=14.dp,top=12.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Headphones,null,Modifier.size(27.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(9.dp));Text("sonder",fontFamily=FontFamily.Serif,fontSize=30.sp,letterSpacing=(-1).sp,modifier=Modifier.weight(1f))
                        IconAction(Icons.Rounded.Add,"Import audiobooks",{ importOpen=true });IconAction(Icons.Rounded.Tune,"Settings",{ settingsOpen=true })
                    }
                },
                bottomBar={ Column {
                    if(current!=null) MiniPlayer(current,playback,{ player=true },vm::toggle,{ optionsBook=current.id })
                    if(!settingsOpen && detail==null) NavigationBar(containerColor=MaterialTheme.colorScheme.background,tonalElevation=0.dp) {
                        val labels=listOf("Library","Collections","Bookmarks","History","Insights")
                        val icons=listOf(Icons.Rounded.LibraryBooks,Icons.Rounded.FolderOpen,Icons.Rounded.Bookmarks,Icons.Rounded.HistoryEdu,Icons.Rounded.BarChart)
                        labels.forEachIndexed { i,label -> NavigationBarItem(selected=tab==i,onClick={ tab=i;collectionFilter="" },icon={ Icon(icons[i],null) },label={ Text(label,maxLines=1,overflow=TextOverflow.Ellipsis) },colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer)) }
                    }
                } }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize(),contentAlignment=Alignment.TopCenter) {
                    Box(Modifier.widthIn(max=900.dp).fillMaxSize()) {
                        when {
                            settingsOpen -> SettingsScreen(settings,vm,library,onFiles,onFolder,onExport,onRestore,onScan={ scanOpen=true },onUpdate={ updateOpen=true })
                            detail!=null -> BookDetails(detail,vm,library,onPlay={ vm.play(detail);onNotification();player=true },onRemoved={ selected=0 },onCover={ onCover(detail) },onOptions={ optionsBook=detail.id })
                            tab==0 || collectionFilter.isNotEmpty() -> LibraryScreen(library.books,settings.grid,collectionFilter,onClearCollection={ collectionFilter="" },onGrid={ vm.preferences.update(settings.copy(grid=!settings.grid)) },onBook={ selected=it.id },onPlay={ vm.play(it);onNotification();player=true },onImport={ importOpen=true },onOptions={ optionsBook=it.id })
                            tab==1 -> CollectionsScreen(library.books,onCollection={ collectionFilter=it },onEdit=vm::edit)
                            tab==2 -> BookmarkScreen(library,onOpen={ b,pos -> vm.play(b,pos);onNotification();player=true },onDelete=vm::deleteBookmark,onEdit=vm::editBookmark,onOptions={ optionsBook=it.id })
                            tab==3 -> ReadingHistoryScreen(library.readingHistory,onAdd={ historyDraft=ReadingEntry() },onEdit={ historyDraft=it },onDelete=vm::deleteReading)
                            else -> InsightsScreen(library,settings.dailyGoal)
                        }
                        if(import.running) Surface(modifier=Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant,shadowElevation=4.dp) {
                            Column(Modifier.padding(16.dp)) { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Adding to your library",style=MaterialTheme.typography.titleSmall);Text(import.current,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis) };TextButton(onClick=vm::cancelImport) { Text("Stop") } };Spacer(Modifier.height(8.dp));if(import.total>0) LinearProgressIndicator(progress={ import.done.toFloat()/import.total },modifier=Modifier.fillMaxWidth()) else LinearProgressIndicator(modifier=Modifier.fillMaxWidth()) }
                        }
                    }
                }
            }
            if(updateOpen && updates.available!=null) UpdateSheet(vm.updater,onDismiss={ updateOpen=false })
            val options=library.books.firstOrNull { it.id==optionsBook }
            if(options!=null) BookOptionsSheet(options,onDismiss={ optionsBook=0 },onStatus={ status -> optionsBook=0;if(playback.bookId==options.id) player=false;vm.markStatus(options,status) },onFavorite={ vm.edit(options.copy(favorite=!options.favorite));optionsBook=0 },onDetails={ selected=options.id;player=false;settingsOpen=false;optionsBook=0 },onLog={ val pending=library.completionPrompts.firstOrNull { it.bookId==options.id };historyDraft=pending?.entry ?: ReadingEntry.fromBook(options);historyPromptId=pending?.entry?.id.orEmpty();optionsBook=0 })
            val completion=library.completionPrompts.firstOrNull()
            if(completion!=null && historyDraft==null && options==null && !scanOpen && !importOpen) AlertDialog(
                onDismissRequest={ vm.dismissCompletion(completion.entry.id) },
                title={ Text("Add this book to reading history?") },
                text={ Text("You finished ${completion.entry.title}. Save a reading record with the date, progress, and any notes you'd like to keep.") },
                confirmButton={ TextButton({ historyDraft=completion.entry;historyPromptId=completion.entry.id }) { Text("Add to history") } },
                dismissButton={ TextButton({ vm.dismissCompletion(completion.entry.id) }) { Text("No thanks") } }
            )
            historyDraft?.let { draft -> ReadingEntryEditor(draft,library.books,historySaving,onDismiss={ if(historyPromptId.isNotBlank()) vm.dismissCompletion(historyPromptId);historyDraft=null;historyPromptId="" },onSave={ entry -> vm.saveReading(entry,historyPromptId.ifBlank { null }) { historyDraft=null;historyPromptId="";player=false;settingsOpen=false;selected=0;collectionFilter="";tab=3 } }) }
            if(scanOpen) DeviceScanSheet(vm,onDismiss={ scanOpen=false;vm.cancelScan() },onScan=onScan,onImport={ vm.importDiscovered(it);scanOpen=false })
            if(importOpen) ModalBottomSheet(onDismissRequest={ importOpen=false },containerColor=MaterialTheme.colorScheme.background) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=30.dp)) {
                    Text("Add your next listen",style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(12.dp));Text("Choose files or a folder. Sonder reads cover art, titles, authors, and embedded chapters.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(24.dp))
                    Button(onClick={ importOpen=false;onFiles() },modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.AudioFile,null);Spacer(Modifier.width(10.dp));Text("Choose audio files") }
                    Spacer(Modifier.height(12.dp));OutlinedButton(onClick={ importOpen=false;onFolder() },modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.FolderOpen,null);Spacer(Modifier.width(10.dp));Text("Choose a folder") }
                    Spacer(Modifier.height(12.dp));OutlinedButton(onClick={ importOpen=false;scanOpen=true },modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.Search,null);Spacer(Modifier.width(10.dp));Text("Scan device for audiobooks") }
                    Spacer(Modifier.height(20.dp));Text("MP4 · M4B · MP3 · M4A · AAC · FLAC · OGG · OPUS · WAV",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(8.dp));Text("Files stay in their original location. Keep access to the folder. Protected Audible files are not supported.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(import.errors.isNotEmpty()) { Spacer(Modifier.height(16.dp));Text("Last import",style=MaterialTheme.typography.titleSmall);import.errors.take(5).forEach { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error) } }
                }
            }
        }
    }
}

@Composable private fun LibraryScreen(books:List<Book>,grid:Boolean,collection:String,onClearCollection:()->Unit,onGrid:()->Unit,onBook:(Book)->Unit,onPlay:(Book)->Unit,onImport:()->Unit,onOptions:(Book)->Unit) {
    var query by rememberSaveable { mutableStateOf("") };var filter by rememberSaveable { mutableStateOf("All books") };var sort by rememberSaveable { mutableStateOf("Recently added") };var sortOpen by remember { mutableStateOf(false) }
    val filtered=books.filter { (collection.isEmpty() || if(collection=="Favorites") it.favorite else it.collection==collection) && (query.isBlank() || listOf(it.title,it.author,it.narrator,it.genre,it.collection).any { v -> v.contains(query,true) }) && when(filter) { "In progress" -> it.inProgress;"Unstarted" -> !it.inProgress && !it.finished;"Finished" -> it.finished;else -> true } }.let { list -> when(sort) { "Title" -> list.sortedBy { it.title.lowercase() };"Author" -> list.sortedBy { it.author.lowercase() };"Duration" -> list.sortedBy { it.duration };"Last listened" -> list.sortedByDescending { it.lastPlayed };else -> list.sortedByDescending { it.added } } }
    val recent=books.filter { it.inProgress }.maxByOrNull { it.lastPlayed }
    val header:@Composable (Modifier)->Unit = { modifier ->
        Column(modifier) {
            Row(verticalAlignment=Alignment.CenterVertically) { Text(if(collection.isBlank()) "Your library" else collection,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.weight(1f));if(collection.isNotEmpty()) IconAction(Icons.Rounded.Close,"Clear collection filter",onClearCollection) else Text("${books.size} ${if(books.size==1) "book" else "books"}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(18.dp));OutlinedTextField(query,{ query=it },placeholder={ Text("Search books, authors, narrators") },leadingIcon={ Icon(Icons.Rounded.Search,null) },trailingIcon={ if(query.isNotEmpty()) IconAction(Icons.Rounded.Close,"Clear search",{ query="" }) },singleLine=true,shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth(),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=MaterialTheme.colorScheme.outlineVariant))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("All books","In progress","Unstarted","Finished").forEach { label -> FilterChip(selected=filter==label,onClick={ filter=label },label={ Text(label) },border=FilterChipDefaults.filterChipBorder(enabled=true,selected=filter==label,borderColor=MaterialTheme.colorScheme.outlineVariant)) } }
        }
    }
    Column(Modifier.fillMaxSize()) {
        if(books.isEmpty()) LazyColumn(Modifier.weight(1f)) { item { header(Modifier.padding(horizontal=24.dp)) }; item {
            Column(Modifier.fillMaxWidth().padding(top=24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                ShelfIllustration()
                EmptyState(Icons.Rounded.Headphones,"Make room for a good story","Bring your audiobooks together. Pick a book, press play, and settle in.",action={ Button(onImport,shape=RoundedCornerShape(16.dp),modifier=Modifier.heightIn(min=52.dp)) { Icon(Icons.Rounded.Add,null);Spacer(Modifier.width(8.dp));Text("Add your first audiobook") } })
            }
        } }
        else LazyVerticalGrid(columns=if(grid) GridCells.Adaptive(145.dp) else GridCells.Fixed(1),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=24.dp),horizontalArrangement=Arrangement.spacedBy(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.weight(1f).testTag("libraryGrid")) {
            item(span={ GridItemSpan(maxLineSpan) }) { header(Modifier) }
            if(recent!=null && query.isBlank() && filter=="All books" && collection.isBlank()) item(span={ GridItemSpan(maxLineSpan) }) {
                Column { SectionTitle("Continue listening");Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().combinedClickable(onClick={ onPlay(recent) },onLongClickLabel="Book options",onLongClick={ onOptions(recent) }).testTag("continueBook-${recent.id}")) {
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically) { Cover(recent,Modifier.width(74.dp).height(100.dp));Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)) { Text(recent.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);Text(recent.author,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onPrimaryContainer);Spacer(Modifier.height(12.dp));LinearProgressIndicator(progress={ recent.progress },modifier=Modifier.fillMaxWidth().height(4.dp),drawStopIndicator={});Spacer(Modifier.height(6.dp));Text("${duration(recent.duration-recent.position)} left · ${(recent.progress*100).toInt()}%",style=MaterialTheme.typography.bodySmall) };Spacer(Modifier.width(12.dp));FilledIconButton({ onPlay(recent) },Modifier.size(44.dp)) { Icon(Icons.Rounded.PlayArrow,"Resume ${recent.title}") } }
                } }
            }
            item(span={ GridItemSpan(maxLineSpan) }) { Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically) { Text(if(query.isNotEmpty()) "${filtered.size} results" else "On your shelf",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));Box { TextButton({ sortOpen=true }) { Icon(Icons.Rounded.Sort,null,Modifier.size(18.dp));Spacer(Modifier.width(6.dp));Text(sort,style=MaterialTheme.typography.bodySmall) };DropdownMenu(sortOpen,{ sortOpen=false }) { listOf("Recently added","Last listened","Title","Author","Duration").forEach { s -> DropdownMenuItem(text={ Text(s) },onClick={ sort=s;sortOpen=false }) } } };IconAction(if(grid) Icons.Rounded.ViewList else Icons.Rounded.GridView,"Change library layout",onGrid) } }
            if(filtered.isEmpty()) item(span={ GridItemSpan(maxLineSpan) }) { EmptyState(Icons.Rounded.SearchOff,"No matching books","Try another search or filter.") }
            items(filtered,key={ it.id }) { b ->
                if(grid) Column(Modifier.fillMaxWidth().combinedClickable(onClick={ onBook(b) },onLongClickLabel="Book options",onLongClick={ onOptions(b) }).testTag("shelfBook-${b.id}")) {
                    Box { Cover(b,Modifier.fillMaxWidth().aspectRatio(.74f));if(b.favorite) Surface(Modifier.align(Alignment.TopEnd).padding(8.dp),color=MaterialTheme.colorScheme.background,shape=RoundedCornerShape(20.dp)) { Icon(Icons.Rounded.Favorite,null,Modifier.padding(5.dp).size(14.dp),tint=MaterialTheme.colorScheme.primary) } }
                    Spacer(Modifier.height(9.dp));Text(b.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis);Text(b.author,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis);Spacer(Modifier.height(6.dp));Text(if(b.finished) "Finished" else if(b.inProgress) "${(b.progress*100).toInt()}% · ${duration(b.duration-b.position)} left" else "${duration(b.duration)} · ${b.format}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                    if(b.inProgress) LinearProgressIndicator(progress={ b.progress },modifier=Modifier.padding(top=8.dp).fillMaxWidth().height(3.dp),drawStopIndicator={})
                } else BookRow(b,{ onBook(b) },onOptions={ onOptions(b) })
            }
        }
    }
}
@Composable private fun ShelfIllustration() {
    Row(Modifier.widthIn(max=280.dp).height(140.dp).padding(horizontal=16.dp),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf(Forest,Gold,androidx.compose.ui.graphics.Color(0xFF9B6C62)).forEachIndexed { i,color ->
            Box(Modifier.weight(1f).height(listOf(118,140,102)[i].dp).clip(RoundedCornerShape(topStart=8.dp,topEnd=8.dp)).background(color),contentAlignment=Alignment.Center) {
                Column(horizontalAlignment=Alignment.CenterHorizontally) { Icon(listOf(Icons.Rounded.NightsStay,Icons.Rounded.WbSunny,Icons.Rounded.Spa)[i],null,Modifier.size(30.dp),tint=Cream.copy(alpha=.8f));Spacer(Modifier.height(18.dp));Box(Modifier.width(28.dp).height(2.dp).background(Cream.copy(alpha=.5f))) }
            }
        }
    }
    HorizontalDivider(Modifier.width(280.dp),thickness=5.dp,color=MaterialTheme.colorScheme.outlineVariant)
}

@Composable private fun CollectionsScreen(books:List<Book>,onCollection:(String)->Unit,onEdit:(Book)->Unit) {
    var create by remember { mutableStateOf(false) };var name by remember { mutableStateOf("") };var chosen by remember { mutableStateOf(setOf<Long>()) }
    val groups=books.filter { it.collection.isNotBlank() }.groupBy { it.collection }
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment=Alignment.CenterVertically) { Text("Collections",style=MaterialTheme.typography.headlineLarge,modifier=Modifier.weight(1f));IconAction(Icons.Rounded.CreateNewFolder,"New collection",{ create=true }) };Text("A shelf for every mood.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp)) }
        item { CollectionCard("Favorites",books.filter { it.favorite },Icons.Rounded.Favorite,{ onCollection("Favorites") }) }
        items(groups.toList(),key={ it.first }) { (title,items) -> CollectionCard(title,items,Icons.Rounded.FolderOpen,{ onCollection(title) }) }
        if(groups.isEmpty()) item { EmptyState(Icons.Rounded.FolderOpen,"Give your books a home","Create a collection for a series, an author, or whatever you want to listen to next.",action={ OutlinedButton({ create=true }) { Text("New collection") } }) }
    }
    if(create) AlertDialog(onDismissRequest={ create=false },title={ Text("New collection") },text={ Column { OutlinedTextField(name,{ name=it },label={ Text("Collection name") },singleLine=true);Spacer(Modifier.height(12.dp));Text("Choose books to add",style=MaterialTheme.typography.titleSmall);LazyColumn(Modifier.heightIn(max=280.dp)) { items(books) { b -> Row(Modifier.fillMaxWidth().clickable { chosen=if(b.id in chosen) chosen-b.id else chosen+b.id },verticalAlignment=Alignment.CenterVertically) { Checkbox(b.id in chosen,{ checked -> chosen=if(checked) chosen+b.id else chosen-b.id });Text(b.title,style=MaterialTheme.typography.bodyMedium) } } };if(books.isEmpty()) Text("Import a book first to create a collection.",style=MaterialTheme.typography.bodyMedium) } },confirmButton={ TextButton(onClick={ books.filter { it.id in chosen }.forEach { onEdit(it.copy(collection=name.trim())) };create=false;name="";chosen=emptySet() },enabled=name.isNotBlank() && chosen.isNotEmpty()) { Text("Create") } },dismissButton={ TextButton({ create=false }) { Text("Cancel") } })
}
@Composable private fun CollectionCard(title:String,books:List<Book>,icon:androidx.compose.ui.graphics.vector.ImageVector,onClick:()->Unit) {
    Surface(modifier=Modifier.fillMaxWidth().clickable(onClick=onClick),shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically) { Icon(icon,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(18.dp));Column(Modifier.weight(1f)) { Text(title,style=MaterialTheme.typography.titleLarge);Text("${books.size} books · ${duration(books.sumOf { it.duration })}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) };if(books.isNotEmpty()) Cover(books.first(),Modifier.width(45.dp).height(62.dp)) else Icon(Icons.Rounded.ChevronRight,null) }
    }
}
