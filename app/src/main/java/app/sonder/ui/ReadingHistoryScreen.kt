package app.sonder.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.sonder.data.Book
import app.sonder.data.ReadingEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.json.JSONObject
import kotlin.math.roundToInt

val ReadingDraftSaver=Saver<ReadingEntry?,String>(save={ it?.json()?.toString() ?: "" },restore={ if(it.isBlank()) null else runCatching { ReadingEntry.fromJson(JSONObject(it)) }.getOrNull() })

@OptIn(ExperimentalLayoutApi::class)
@Composable fun ReadingHistoryScreen(entries:List<ReadingEntry>,onAdd:()->Unit,onEdit:(ReadingEntry)->Unit,onDelete:(String)->Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All reads") }
    var deleting by rememberSaveable { mutableStateOf("") }
    val filtered=entries.filter { (query.isBlank() || listOf(it.title,it.author,it.notes).any { value -> value.contains(query,true) }) && when(filter) { "Completed" -> it.percent==100;"Partial" -> it.percent<100;else -> true } }
    LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,top=16.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            PageHeader("Reading history","Log finished books and partial reads. Entries stay saved when you reset or remove library books.") { PageAction(Icons.Rounded.EditNote,"Add reading history",onAdd) }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                StatCard(entries.count { it.percent==100 }.toString(),"Completed reads",Modifier.weight(1f),highlight=true)
                StatCard(entries.count { it.percent<100 }.toString(),"Partial reads",Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp));SearchField(query,{ query=it },"Search your reading history","Clear history search")
            Row(Modifier.bleed(24.dp).horizontalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(top=12.dp,bottom=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("All reads","Completed","Partial").forEach { value -> ChoiceChip(value,filter==value,{ filter=value }) } }
        }
        if(filtered.isEmpty()) item { EmptyState(Icons.Rounded.HistoryEdu,if(entries.isEmpty()) "Keep a record of your reading" else "No matching entries",if(entries.isEmpty()) "Add a finished book or record how far you got. You can log books even without an audio file." else "Try another search or filter.",action={ OutlinedButton(onAdd) { Text("Log a book") } }) }
        items(filtered,key={ it.id }) { entry ->
            Surface(onClick={ onEdit(entry) },modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(start=16.dp,end=6.dp,top=14.dp,bottom=16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Cover(Book(title=entry.title,author=entry.author),Modifier.width(44.dp).height(60.dp))
                        Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)) { Text(entry.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);if(entry.author.isNotBlank()) Text(entry.author,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis);Text(LocalDate.parse(entry.loggedOn).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=2.dp)) }
                        IconAction(Icons.Rounded.Edit,"Edit reading entry: ${entry.title}",{ onEdit(entry) },tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        IconAction(Icons.Rounded.DeleteOutline,"Delete reading entry: ${entry.title}",{ deleting=entry.id },tint=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.padding(end=10.dp)) {
                        Spacer(Modifier.height(14.dp));Row(verticalAlignment=Alignment.CenterVertically) { if(entry.percent==100) { Icon(Icons.Rounded.CheckCircle,null,Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(6.dp)) };Text(if(entry.percent==100) "Completed · 100% read" else "${entry.percent}% read",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.weight(1f));if(entry.duration>0) Text("${duration(entry.position)} of ${duration(entry.duration)}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                        ProgressBar(entry.percent/100f,Modifier.padding(top=8.dp))
                        if(entry.notes.isNotBlank()) Text(entry.notes,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurface.copy(alpha=.85f),maxLines=3,overflow=TextOverflow.Ellipsis,modifier=Modifier.padding(top=12.dp))
                    }
                }
            }
        }
    }
    entries.firstOrNull { it.id==deleting }?.let { entry -> AlertDialog(onDismissRequest={ deleting="" },title={ Text("Delete this reading entry?") },text={ Text("Remove the history record for ${entry.title}? Your library book and its listening progress stay saved.") },confirmButton={ TextButton({ onDelete(entry.id);deleting="" }) { Text("Delete entry") } },dismissButton={ TextButton({ deleting="" }) { Text("Cancel") } }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReadingEntryEditor(entry:ReadingEntry,books:List<Book>,saving:Boolean,onDismiss:()->Unit,onSave:(ReadingEntry)->Unit) {
    var title by rememberSaveable(entry.id) { mutableStateOf(entry.title) }
    var author by rememberSaveable(entry.id) { mutableStateOf(entry.author) }
    var percent by rememberSaveable(entry.id) { mutableIntStateOf(entry.percent) }
    var date by rememberSaveable(entry.id) { mutableStateOf(entry.loggedOn) }
    var notes by rememberSaveable(entry.id) { mutableStateOf(entry.notes) }
    var source by rememberSaveable(entry.id) { mutableStateOf(entry.source) }
    var audioDuration by rememberSaveable(entry.id) { mutableLongStateOf(entry.duration) }
    var bookPicker by rememberSaveable { mutableStateOf(false) }
    var bookQuery by rememberSaveable { mutableStateOf("") }
    val validDate=runCatching { Regex("\\d{4}-\\d{2}-\\d{2}").matches(date) && !LocalDate.parse(date).isAfter(LocalDate.now()) }.getOrDefault(false)
    ModalBottomSheet(onDismissRequest={ if(!saving) onDismiss() },sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxHeight(.92f).fillMaxWidth().imePadding().padding(horizontal=24.dp)) {
            LazyColumn(Modifier.weight(1f).testTag("readingEditorForm"),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=16.dp)) {
                item { Text("Log your reading",style=MaterialTheme.typography.headlineMedium);Text("Save the progress you want to remember. This entry won't change the library's listening status.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp)) }
                if(books.isNotEmpty()) item { OutlinedButton({ bookPicker=true },enabled=!saving,modifier=Modifier.fillMaxWidth().testTag("readingPercent")) { Icon(Icons.Rounded.LibraryBooks,null);Spacer(Modifier.width(10.dp));Text("Choose a library book") } }
                item { OutlinedTextField(title,{ title=it.take(1000) },modifier=Modifier.fillMaxWidth(),label={ Text("Book title") },singleLine=true,enabled=!saving) }
                item { OutlinedTextField(author,{ author=it.take(1000) },modifier=Modifier.fillMaxWidth(),label={ Text("Author") },singleLine=true,enabled=!saving) }
                item {
                    Text("${percent}% read",style=MaterialTheme.typography.headlineSmall)
                    Slider(percent.toFloat(),{ percent=it.roundToInt() },valueRange=0f..100f,steps=99,enabled=!saving,modifier=Modifier.fillMaxWidth().testTag("readingPercent"))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { TextButton({ percent=0 },enabled=!saving) { Text("Not read") };TextButton({ percent=100 },modifier=Modifier.testTag("markReadingCompleted"),enabled=!saving) { Text("Completed") } }
                }
                item { OutlinedTextField(date,{ date=it.take(10) },modifier=Modifier.fillMaxWidth(),label={ Text(if(percent==100) "Finished on" else "Logged on") },placeholder={ Text("YYYY-MM-DD") },singleLine=true,isError=!validDate,enabled=!saving,supportingText={ Text(if(validDate) "Date format: YYYY-MM-DD" else "Choose a valid date, today or earlier") }) }
                item { OutlinedTextField(notes,{ notes=it.take(10000) },modifier=Modifier.fillMaxWidth(),label={ Text("Reading notes") },minLines=3,maxLines=6,enabled=!saving) }
            }
            Button(onClick={ onSave(entry.copy(title=title.trim(),author=author.trim(),percent=percent,loggedOn=date,notes=notes,source=source,duration=audioDuration)) },enabled=title.isNotBlank() && validDate && !saving,modifier=Modifier.fillMaxWidth().padding(vertical=12.dp).heightIn(min=52.dp)) { Text(if(saving) "Saving…" else "Save reading entry") }
        }
    }
    if(bookPicker) AlertDialog(onDismissRequest={ bookPicker=false },title={ Text("Choose a library book") },text={ Column {
        OutlinedTextField(bookQuery,{ bookQuery=it },label={ Text("Search library books") },singleLine=true)
        LazyColumn(Modifier.heightIn(max=320.dp)) { items(books.filter { bookQuery.isBlank() || it.title.contains(bookQuery,true) || it.author.contains(bookQuery,true) },key={ it.id }) { book -> TextButton({ val chosen=ReadingEntry.fromBook(book);title=chosen.title;author=chosen.author;percent=chosen.percent;source=chosen.source;audioDuration=chosen.duration;bookPicker=false },modifier=Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) { Text(book.title);Text(book.author,style=MaterialTheme.typography.bodySmall) } } } }
    } },confirmButton={},dismissButton={ TextButton({ bookPicker=false }) { Text("Cancel") } })
}
