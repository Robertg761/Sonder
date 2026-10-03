package app.sonder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sonder.data.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@UnstableApi
@Composable fun PlayerScreen(vm:LibraryViewModel,book:Book,playback:Playback,onBack:()->Unit,onDetails:()->Unit,onOptions:()->Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val timer by vm.sleep.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    var chapters by remember(book.id) { mutableStateOf<List<Chapter>>(emptyList()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var speedOpen by remember { mutableStateOf(false) };var timerOpen by remember { mutableStateOf(false) };var bookmarkOpen by remember { mutableStateOf(false) };var note by remember { mutableStateOf("") };var bookmarkPosition by rememberSaveable { mutableLongStateOf(0) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(book.id,book.duration) { chapters=vm.store.chapters(book.id) }
    val position=drag?.toLong() ?: playback.position
    val currentIndex=chapters.indexOfLast { it.start<=position }.coerceAtLeast(0)
    val current=chapters.getOrNull(currentIndex)
    val bookmarks=library.bookmarks.filter { it.bookId==book.id }
    Scaffold(containerColor=MaterialTheme.colorScheme.background,topBar={
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            IconAction(Icons.Rounded.KeyboardArrowDown,"Close player",onBack);Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally) { Text("NOW LISTENING",style=MaterialTheme.typography.labelSmall,letterSpacing=2.sp,color=MaterialTheme.colorScheme.primary);Text(book.collection.ifBlank { "Your library" },style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };IconAction(Icons.Rounded.MoreHoriz,"Book details",onDetails)
        }
    }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).widthIn(max=650.dp),contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            item {
                Spacer(Modifier.height(8.dp));Cover(book,Modifier.widthIn(max=280.dp).fillMaxWidth(.72f).aspectRatio(.74f).shadow(18.dp,RoundedCornerShape(14.dp),ambientColor=Color.Black.copy(alpha=.35f),spotColor=Color.Black.copy(alpha=.35f)).combinedClickable(onClick=onOptions,onLongClickLabel="Book options",onLongClick=onOptions),large=true)
                Spacer(Modifier.height(28.dp));Text(book.title,style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(4.dp));Text(book.author,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyLarge,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
                if(book.narrator.isNotBlank()) Text("Narrated by ${book.narrator}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth().padding(top=4.dp))
                Spacer(Modifier.height(24.dp));Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(current?.title ?: "Audiobook",style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis);Text("Chapter ${currentIndex+1} of ${chapters.size.coerceAtLeast(1)}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Slider(value=position.toFloat().coerceIn(0f,book.duration.toFloat().coerceAtLeast(1f)),onValueChange={ drag=it },onValueChangeFinished={ drag?.let { vm.seek(it.toLong()) };drag=null },valueRange=0f..book.duration.toFloat().coerceAtLeast(1f),modifier=Modifier.fillMaxWidth(),colors=SliderDefaults.colors(inactiveTrackColor=MaterialTheme.colorScheme.onSurface.copy(alpha=.1f)))
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(clock(position),style=MaterialTheme.typography.labelMedium);Text("−${clock(book.duration-position)}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(18.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    IconAction(Icons.Rounded.SkipPrevious,"Previous chapter",{ vm.seek(chapters.getOrNull(if(position-(current?.start ?: 0)>3000) currentIndex else currentIndex-1)?.start ?: 0) })
                    Column(horizontalAlignment=Alignment.CenterHorizontally) { IconAction(Icons.Rounded.Replay,"Rewind ${settings.rewind} seconds",{ vm.skip(-settings.rewind) });Text("${settings.rewind}s",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    FilledIconButton(vm::toggle,modifier=Modifier.size(72.dp),shape=CircleShape) { if(playback.buffering) CircularProgressIndicator(Modifier.size(26.dp),color=MaterialTheme.colorScheme.onPrimary,strokeWidth=2.dp) else Icon(if(playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(playback.playing) "Pause" else "Play",Modifier.size(36.dp)) }
                    Column(horizontalAlignment=Alignment.CenterHorizontally) { IconAction(Icons.Rounded.Replay,"Forward ${settings.forward} seconds",{ vm.skip(settings.forward) },Modifier.graphicsLayer(scaleX=-1f));Text("${settings.forward}s",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    IconAction(Icons.Rounded.SkipNext,"Next chapter",{ chapters.getOrNull(currentIndex+1)?.let { vm.seek(it.start) } })
                }
                Spacer(Modifier.height(24.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    ActionTile(Icons.Rounded.Speed,"${playback.speed}×",{ speedOpen=true },Modifier.weight(1f),"Playback speed",active=playback.speed!=1f)
                    ActionTile(Icons.Rounded.Bedtime,if(timer<0) "Chapter end" else if(timer>0) clock(timer) else "Sleep timer",{ timerOpen=true },Modifier.weight(1f),"Sleep timer",active=timer!=0L)
                    ActionTile(Icons.Rounded.BookmarkAdd,"Bookmark",{ bookmarkPosition=playback.position;bookmarkOpen=true },Modifier.weight(1f),"Add bookmark")
                }
                if(playback.error.isNotBlank()) Text(playback.error,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(top=16.dp),style=MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp));PrimaryTabRow(selectedTabIndex=tab,containerColor=MaterialTheme.colorScheme.background,divider={ HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant) }) { listOf("Chapters","Bookmarks").forEachIndexed { i,title -> Tab(selected=tab==i,onClick={ tab=i },text={ Text(title) },unselectedContentColor=MaterialTheme.colorScheme.onSurfaceVariant) } };Spacer(Modifier.height(12.dp))
            }
            if(tab==0) itemsIndexed(chapters,key={ _,c -> c.start }) { i,c -> ChapterRow(i,c,i==currentIndex,{ vm.seek(c.start) }) }
            else if(bookmarks.isEmpty()) item { EmptyState(Icons.Rounded.BookmarkBorder,"Keep the good parts","Save a moment with a note so you can return to it later.") }
            else itemsIndexed(bookmarks,key={ _,b -> b.id }) { _,b -> Row(Modifier.fillMaxWidth().clickable { vm.seek(b.position) }.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.Bookmark,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f)) { Text(b.note,style=MaterialTheme.typography.bodyLarge);Text(clock(b.position),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary) };IconAction(Icons.Rounded.DeleteOutline,"Delete bookmark",{ vm.deleteBookmark(b.id) },tint=MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
    if(bookmarkOpen) AlertDialog(onDismissRequest={ bookmarkOpen=false },title={ Text("Bookmark at ${clock(bookmarkPosition)}") },text={ OutlinedTextField(note,{ note=it },label={ Text("Add a note") },minLines=3,modifier=Modifier.fillMaxWidth()) },confirmButton={ TextButton({ vm.bookmark(note,bookmarkPosition,book.id);note="";bookmarkOpen=false;tab=1 }) { Text("Save bookmark") } },dismissButton={ TextButton({ bookmarkOpen=false }) { Text("Cancel") } })
    if(speedOpen) ModalBottomSheet(onDismissRequest={ speedOpen=false },containerColor=MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal=24.dp).padding(bottom=32.dp)) {
            Text("At your own pace",style=MaterialTheme.typography.headlineMedium);Spacer(Modifier.height(8.dp));Text("${playback.speed}× · ${duration(((book.duration-playback.position)/playback.speed).toLong())} remaining",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(playback.speed,{ vm.speed((it*20).toInt()/20f) },valueRange=.5f..3f,steps=49)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf(.75f,1f,1.25f,1.5f,2f).forEach { value -> ChoiceChip("${value}×",playback.speed==value,{ vm.speed(value) }) } }
            Row(Modifier.fillMaxWidth().padding(top=16.dp),verticalAlignment=Alignment.CenterVertically) { Text("Preserve voice pitch",modifier=Modifier.weight(1f));Switch(settings.preservePitch,{ vm.preferences.update(settings.copy(preservePitch=it)) }) }
        }
    }
    if(timerOpen) ModalBottomSheet(onDismissRequest={ timerOpen=false },containerColor=MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(horizontal=24.dp).padding(bottom=32.dp)) {
            Text("Drift off to a story",style=MaterialTheme.typography.headlineMedium);Text("Playback pauses when the timer ends.",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=20.dp))
            listOf(15,30,45,60,90).forEach { minutes -> TimerOption("$minutes minutes",{ vm.timer(minutes);timerOpen=false }) }
            TimerOption("End of this chapter",{ vm.timer(-1);timerOpen=false },selected=timer<0)
            if(timer!=0L) TextButton({ vm.timer(0);timerOpen=false },Modifier.fillMaxWidth()) { Text("Turn off timer",color=MaterialTheme.colorScheme.error) }
        }
    }
}
@Composable private fun TimerOption(label:String,onClick:()->Unit,selected:Boolean=false) { Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick=onClick).padding(horizontal=4.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) { Text(label,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.weight(1f));if(selected) Icon(Icons.Rounded.Check,"Selected",tint=MaterialTheme.colorScheme.primary) } }
