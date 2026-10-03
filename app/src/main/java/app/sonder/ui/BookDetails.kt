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
    LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally) {
                Cover(book,Modifier.width(196.dp).height(262.dp).combinedClickable(onClick=onOptions,onLongClickLabel="Book options",onLongClick=onOptions),large=true);Spacer(Modifier.height(24.dp));Text(book.title,style=MaterialTheme.typography.headlineMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center);Spacer(Modifier.height(8.dp));Text(book.author,style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                if(book.narrator.isNotBlank()) Text("Narrated by ${book.narrator}",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=4.dp))
                TextButton(onOptions) { Icon(Icons.Rounded.MoreHoriz,null);Spacer(Modifier.width(6.dp));Text("Book options") }
                Spacer(Modifier.height(20.dp));FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick={},label={ Text(duration(book.duration)) },leadingIcon={ Icon(Icons.Rounded.Schedule,null,Modifier.size(16.dp)) });AssistChip(onClick={},label={ Text("${chapters.size} chapters") });AssistChip(onClick={},label={ Text(book.format) })
                }
                Spacer(Modifier.height(16.dp));Button(onClick=onPlay,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),shape=androidx.compose.foundation.shape.RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(if(book.inProgress) "Continue listening" else "Start listening") }
                TextButton(onCover) { Text("Change cover") }
                if(book.position>0) { Spacer(Modifier.height(12.dp));LinearProgressIndicator(progress={ book.progress },modifier=Modifier.fillMaxWidth(),drawStopIndicator={});Text("${(book.progress*100).toInt()}% complete · ${duration(((book.duration-book.position)/book.speed.toDouble().coerceAtLeast(.5)).toLong())} left at ${book.speed}×",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp)) }
                Row(Modifier.fillMaxWidth().padding(top=12.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                    TextButton({ vm.edit(book.copy(favorite=!book.favorite)) }) { Icon(if(book.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text(if(book.favorite) "Saved" else "Favorite") }
                    TextButton({ editing=true }) { Icon(Icons.Rounded.Edit,null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text("Edit") }
                    TextButton({ vm.markStatus(book,if(book.finished) ListeningStatus.IN_PROGRESS else ListeningStatus.FINISHED) }) { Icon(Icons.Rounded.CheckCircleOutline,null,Modifier.size(18.dp));Spacer(Modifier.width(5.dp));Text(if(book.finished) "Unfinish" else "Finish") }
                }
            }
        }
        if(book.description.isNotBlank()) item { SectionTitle("About this book");Text(book.description,style=MaterialTheme.typography.bodyLarge) }
        item { SectionTitle("Chapters","${chapters.size}") }
        itemsIndexed(chapters,key={ _,c -> c.start }) { i,c -> ChapterRow(i,c,book.position>=c.start && book.position<c.end,onClick={ vm.play(book,c.start) }) }
        item { SectionTitle("File details");DetailLine("Format",book.format);DetailLine("Tracks","${tracks.size}");DetailLine("Size","%.1f MB".format(book.size/1048576.0));if(book.genre.isNotBlank()) DetailLine("Genre",book.genre);if(book.collection.isNotBlank()) DetailLine("Collection",book.collection);DetailLine("Added",java.time.Instant.ofEpochMilli(book.added).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()) }
        item { TextButton({ remove=true },modifier=Modifier.fillMaxWidth()) { Icon(Icons.Rounded.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text("Remove from library",color=MaterialTheme.colorScheme.error) } }
    }
    if(editing) EditBookDialog(book,onDismiss={ editing=false },onSave={ vm.edit(it);editing=false })
    if(remove) AlertDialog(onDismissRequest={ remove=false },title={ Text("Remove this audiobook?") },text={ Text("The book, progress, and bookmarks will be removed from Sonder. The original audio files will stay on your phone.") },confirmButton={ TextButton({ vm.remove(book);remove=false;onRemoved() }) { Text("Remove",color=MaterialTheme.colorScheme.error) } },dismissButton={ TextButton({ remove=false }) { Text("Cancel") } })
}
@Composable fun ChapterRow(index:Int,chapter:Chapter,active:Boolean,onClick:()->Unit) {
    Surface(color=if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,shape=androidx.compose.foundation.shape.RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().clickable(onClick=onClick)) {
        Row(Modifier.padding(horizontal=12.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
            if(active) Icon(Icons.Rounded.GraphicEq,"Current chapter",Modifier.width(32.dp),tint=MaterialTheme.colorScheme.primary) else Text("%02d".format(index+1),modifier=Modifier.width(32.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text(chapter.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis);Text(clock(chapter.start),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) };Text(duration(chapter.end-chapter.start),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable fun DetailLine(label:String,value:String) { Row(Modifier.fillMaxWidth().padding(vertical=6.dp)) { Text(label,modifier=Modifier.weight(.4f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,modifier=Modifier.weight(.6f),style=MaterialTheme.typography.bodyMedium) } }
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
