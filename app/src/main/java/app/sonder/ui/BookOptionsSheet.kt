package app.sonder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.sonder.data.Book
import app.sonder.data.ListeningStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BookOptionsSheet(book:Book,onDismiss:()->Unit,onStatus:(ListeningStatus)->Unit,onFavorite:()->Unit,onDetails:()->Unit,onLog:()->Unit) {
    var resetConfirm by remember(book.id) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,bottom=30.dp)) {
            item { Text(book.title,style=MaterialTheme.typography.headlineSmall);Text(book.author,color=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(20.dp));Text("Listening status",style=MaterialTheme.typography.titleSmall) }
            ListeningStatus.entries.forEach { status -> item {
                val selected=when(status) { ListeningStatus.NOT_STARTED -> !book.inProgress && !book.finished;ListeningStatus.IN_PROGRESS -> book.inProgress;ListeningStatus.FINISHED -> book.finished }
                TextButton(onClick={ if(status==ListeningStatus.NOT_STARTED && book.position>0) resetConfirm=true else onStatus(status) },modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                    Icon(when(status) { ListeningStatus.NOT_STARTED -> Icons.Rounded.NewReleases;ListeningStatus.IN_PROGRESS -> Icons.Rounded.Headphones;ListeningStatus.FINISHED -> Icons.Rounded.CheckCircleOutline },null)
                    Spacer(Modifier.width(16.dp));Column(Modifier.weight(1f),horizontalAlignment=Alignment.Start) {
                        Text("Mark as ${status.label.lowercase()}")
                        if(status==ListeningStatus.NOT_STARTED) Text("Start from the beginning. Keep bookmarks.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if(selected) Icon(Icons.Rounded.Check,"Current status")
                }
            } }
            item { HorizontalDivider();TextButton(onLog,Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.HistoryEdu,null);Spacer(Modifier.width(16.dp));Text("Add to reading history",Modifier.weight(1f)) };TextButton(onFavorite,Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(if(book.favorite) Icons.Rounded.FavoriteBorder else Icons.Rounded.Favorite,null);Spacer(Modifier.width(16.dp));Text(if(book.favorite) "Remove from favorites" else "Add to favorites",Modifier.weight(1f)) };TextButton(onDetails,Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Rounded.Info,null);Spacer(Modifier.width(16.dp));Text("View book details",Modifier.weight(1f)) } }
        }
    }
    if(resetConfirm) AlertDialog(onDismissRequest={ resetConfirm=false },title={ Text("Mark as not started?") },text={ Text("This resets the listening position to the beginning and removes this book from Continue Listening. Your bookmarks and listening history stay saved.") },confirmButton={ TextButton({ resetConfirm=false;onStatus(ListeningStatus.NOT_STARTED) }) { Text("Mark not started") } },dismissButton={ TextButton({ resetConfirm=false }) { Text("Cancel") } })
}
