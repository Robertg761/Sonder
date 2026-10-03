package app.sonder.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.sonder.data.*

@UnstableApi
@OptIn(ExperimentalLayoutApi::class)
@Composable fun BookDetails(book:Book,vm:LibraryViewModel,library:Library,onPlay:()->Unit,onRemoved:()->Unit,onCover:()->Unit,onOptions:()->Unit) {
    var chapters by remember(book.id) { mutableStateOf<List<Chapter>>(emptyList()) }
    var tracks by remember(book.id) { mutableStateOf<List<Track>>(emptyList()) }
    var editing by remember { mutableStateOf(false) };var remove by remember { mutableStateOf(false) }
    LaunchedEffect(book.id,book.duration) { chapters=vm.store.chapters(book.id);tracks=vm.store.tracks(book.id) }
    LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        item {
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                Cover(book,Modifier.width(188.dp).height(254.dp).shadow(16.dp,RoundedCornerShape(14.dp),ambientColor=Color.Black.copy(alpha=.3f),spotColor=Color.Black.copy(alpha=.3f)).combinedClickable(onClick=onOptions,onLongClickLabel="Book options",onLongClick=onOptions),large=true);Spacer(Modifier.height(24.dp));Text(book.title,style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center);Spacer(Modifier.height(6.dp));Text(book.author,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center)
                if(book.narrator.isNotBlank()) Text("Narrated by ${book.narrator}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,modifier=Modifier.padding(top=2.dp))
                Spacer(Modifier.height(12.dp));Text(listOf(duration(book.duration),"${chapters.size} ${if(chapters.size==1) "chapter" else "chapters"}",book.format).filter { it.isNotBlank() }.joinToString("  ·  "),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                if(book.position>0) { Spacer(Modifier.height(20.dp));ProgressBar(book.progress,height=6.dp);Text("${(book.progress*100).toInt()}% complete · ${duration(((book.duration-book.position)/book.speed.toDouble().coerceAtLeast(.5)).toLong())} left at ${book.speed}×",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp)) }
                Spacer(Modifier.height(20.dp));Button(onClick=onPlay,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=MaterialTheme.shapes.medium) { Icon(Icons.Rounded.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(if(book.inProgress) "Continue listening" else "Start listening",style=MaterialTheme.typography.titleSmall) }
                Spacer(Modifier.height(12.dp));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    ActionTile(if(book.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,if(book.favorite) "Saved" else "Favorite",{ vm.edit(book.copy(favorite=!book.favorite)) },Modifier.weight(1f),active=book.favorite)
                    ActionTile(Icons.Rounded.Edit,"Edit",{ editing=true },Modifier.weight(1f))
                    ActionTile(Icons.Rounded.Image,"Cover",onCover,Modifier.weight(1f),"Change cover")
                    ActionTile(Icons.Rounded.CheckCircleOutline,if(book.finished) "Unfinish" else "Finish",{ vm.markStatus(book,if(book.finished) ListeningStatus.IN_PROGRESS else ListeningStatus.FINISHED) },Modifier.weight(1f),active=book.finished)
                }
            }
        }
        if(book.description.isNotBlank()) item { SectionTitle("About this book");Text(book.description,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.85f)) }
        item { SectionTitle("Chapters","${chapters.size}") }
        itemsIndexed(chapters,key={ _,c -> c.start }) { i,c -> ChapterRow(i,c,book.position>=c.start && book.position<c.end,onClick={ vm.play(book,c.start) }) }
        item { SectionTitle("File details");Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp)) { DetailLine("Format",book.format);DetailLine("Tracks","${tracks.size}");DetailLine("Size","%.1f MB".format(book.size/1048576.0));if(book.genre.isNotBlank()) DetailLine("Genre",book.genre);if(book.collection.isNotBlank()) DetailLine("Collection",book.collection);DetailLine("Added",java.time.Instant.ofEpochMilli(book.added).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))) } } }
        item { TextButton({ remove=true },modifier=Modifier.fillMaxWidth().padding(top=8.dp),colors=ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Icon(Icons.Rounded.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text("Remove from library") } }
    }
    if(editing) EditBookDialog(book,onDismiss={ editing=false },onSave={ vm.edit(it);editing=false })
    if(remove) AlertDialog(onDismissRequest={ remove=false },title={ Text("Remove this audiobook?") },text={ Text("The book, progress, and bookmarks will be removed from Sonder. The original audio files will stay on your phone.") },confirmButton={ TextButton({ vm.remove(book);remove=false;onRemoved() }) { Text("Remove",color=MaterialTheme.colorScheme.error) } },dismissButton={ TextButton({ remove=false }) { Text("Cancel") } })
}
@Composable fun ChapterRow(index:Int,chapter:Chapter,active:Boolean,onClick:()->Unit) {
    Surface(onClick=onClick,color=if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal=12.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
            if(active) Icon(Icons.Rounded.GraphicEq,"Current chapter",Modifier.width(32.dp),tint=MaterialTheme.colorScheme.primary) else Text("%02d".format(index+1),modifier=Modifier.width(32.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text(chapter.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis);Text(clock(chapter.start),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };Text(duration(chapter.end-chapter.start),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun DetailLine(label:String,value:String) { Row(Modifier.fillMaxWidth().padding(vertical=8.dp)) { Text(label,modifier=Modifier.weight(.4f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,modifier=Modifier.weight(.6f),style=MaterialTheme.typography.bodyMedium) } }
@Composable private fun EditBookDialog(book:Book,onDismiss:()->Unit,onSave:(Book)->Unit) {
    var title by remember { mutableStateOf(book.title) };var author by remember { mutableStateOf(book.author) };var narrator by remember { mutableStateOf(book.narrator) };var description by remember { mutableStateOf(book.description) };var genre by remember { mutableStateOf(book.genre) };var collection by remember { mutableStateOf(book.collection) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text("Edit book") },text={ LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { OutlinedTextField(title,{ title=it.take(1000) },label={ Text("Title") },modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(author,{ author=it.take(1000) },label={ Text("Author") },modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(narrator,{ narrator=it.take(1000) },label={ Text("Narrator") },modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(genre,{ genre=it.take(1000) },label={ Text("Genre") },modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(collection,{ collection=it.take(1000) },label={ Text("Collection") },modifier=Modifier.fillMaxWidth()) }
        item { OutlinedTextField(description,{ description=it.take(20000) },label={ Text("Description") },modifier=Modifier.fillMaxWidth(),minLines=3) }
    } },confirmButton={ TextButton(onClick={ onSave(book.copy(title=title.trim(),author=author.trim().ifBlank { "Unknown author" },narrator=narrator.trim(),genre=genre.trim(),collection=collection.trim(),description=description.trim())) },enabled=title.isNotBlank()) { Text("Save") } },dismissButton={ TextButton(onDismiss) { Text("Cancel") } })
}
