package app.sonder.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import app.sonder.data.Book
import app.sonder.data.readBounded
import app.sonder.download.AudioBookBay
import app.sonder.download.DownloadJob
import app.sonder.download.DownloadPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@UnstableApi
@Composable fun FindScreen(vm:LibraryViewModel,onNotification:()->Unit) {
    val state by vm.find.collectAsStateWithLifecycle()
    val detail by vm.findDetails.collectAsStateWithLifecycle()
    val jobs by vm.downloads.jobs.collectAsStateWithLifecycle()
    detail?.let { ListingDetails(it,jobs,onDownload={ d -> onNotification();vm.download(d) },onRetry={ vm.openListing(it.listing) });return }
    var query by rememberSaveable { mutableStateOf(state.query) }
    val focus=LocalFocusManager.current
    LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,top=16.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        item {
            PageHeader("Find","Search AudioBookBay. Books download through Real-Debrid straight into your library.")
            Spacer(Modifier.height(16.dp))
            SearchField(query,{ query=it },"Search titles and authors","Clear search",onSearch={ focus.clearFocus();vm.search(query) })
        }
        if(jobs.isNotEmpty()) {
            item { Row(verticalAlignment=Alignment.CenterVertically) { Box(Modifier.weight(1f)) { SectionTitle("Downloads") };if(jobs.any { it.state==DownloadJob.State.DONE }) TextButton(vm.downloads::clearFinished) { Text("Clear finished") } } }
            items(jobs,key={ "job-${it.id}" }) { job -> DownloadRow(job,onRetry={ onNotification();vm.downloads.retry(job.id) },onRemove={ vm.downloads.remove(job.id) }) }
        }
        if(state.searched) item { SectionTitle(if(state.loading && state.results.isEmpty()) "Searching…" else "Results",if(state.results.isNotEmpty()) "${state.results.size}${if(state.next) "+" else ""}" else "") }
        items(state.results,key={ "result-${it.url}" }) { listing -> ListingRow(listing) { vm.openListing(listing) } }
        item {
            when {
                state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=16.dp).clip(MaterialTheme.shapes.extraLarge))
                state.error.isNotBlank() -> Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) { Text(state.error,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium);TextButton({ vm.search(state.query,more=state.results.isNotEmpty()) }) { Text("Try again") } }
                state.next -> OutlinedButton({ vm.search(state.query,more=true) },Modifier.fillMaxWidth().padding(top=8.dp)) { Text("Show more results") }
                state.searched && state.results.isEmpty() -> EmptyState(Icons.Rounded.SearchOff,"No books found","Try fewer words, or search for the author's name.")
                !state.searched && jobs.isEmpty() -> EmptyState(Icons.Rounded.TravelExplore,"Find your next listen","Search for a title or author, then download it straight into your library.")
            }
        }
    }
}

@Composable private fun ListingRow(listing:AudioBookBay.Listing,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        RemoteCover(listing.cover,listing.title,Modifier.width(56.dp).height(76.dp));Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(listing.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(listOf(listing.format,listing.bitrate,listing.size).filter { it.isNotBlank() }.joinToString(" · "),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=4.dp))
            Text(listOf(listing.posted,listing.language).filter { it.isNotBlank() }.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun DownloadRow(job:DownloadJob,onRetry:()->Unit,onRemove:()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(start=16.dp,end=4.dp,top=12.dp,bottom=12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                RemoteCover(job.cover,job.title,Modifier.width(36.dp).height(48.dp));Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.title,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    val detail=if(job.total>0 && job.state==DownloadJob.State.WORKING) " · ${bytes(job.bytes)} of ${bytes(job.total)}" else ""
                    Text(job.message+detail,style=MaterialTheme.typography.bodySmall,color=if(job.state==DownloadJob.State.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if(job.state==DownloadJob.State.FAILED) IconAction(Icons.Rounded.Refresh,"Retry download",onRetry,tint=MaterialTheme.colorScheme.primary)
                IconAction(if(job.active) Icons.Rounded.Close else Icons.Rounded.DeleteOutline,if(job.active) "Cancel download" else "Remove from downloads",onRemove,tint=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(job.state==DownloadJob.State.WORKING) Box(Modifier.padding(top=10.dp,end=12.dp)) { if(job.progress>=0) ProgressBar(job.progress) else LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(MaterialTheme.shapes.extraLarge)) }
        }
    }
}

@Composable private fun ListingDetails(state:FindDetails,jobs:List<DownloadJob>,onDownload:(AudioBookBay.Details)->Unit,onRetry:()->Unit) {
    val details=state.details
    val listing=state.listing
    LazyColumn(contentPadding=PaddingValues(start=24.dp,end=24.dp,top=4.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            Row {
                RemoteCover(details?.cover?.ifBlank { null } ?: listing.cover,listing.title,Modifier.width(120.dp).height(164.dp),large=true);Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    Text(details?.title ?: listing.title,style=MaterialTheme.typography.headlineSmall)
                    details?.author?.takeIf { it.isNotBlank() }?.let { Text("By $it",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp)) }
                    details?.narrator?.takeIf { it.isNotBlank() }?.let { Text("Narrated by $it",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(listOf(details?.format?.ifBlank { null } ?: listing.format,details?.bitrate?.ifBlank { null } ?: listing.bitrate,details?.size?.ifBlank { null } ?: listing.size).filter { it.isNotBlank() }.joinToString(" · "),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=8.dp))
                    if(listing.posted.isNotBlank()) Text("Posted ${listing.posted}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            when {
                state.error.isNotBlank() -> Column { Text(state.error,color=MaterialTheme.colorScheme.error);TextButton(onRetry) { Text("Try again") } }
                details==null -> LinearProgressIndicator(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraLarge))
                else -> {
                    val job=jobs.firstOrNull { it.hash==details.hash }
                    Button({ onDownload(details) },Modifier.fillMaxWidth().heightIn(min=54.dp),enabled=details.playable && job?.active!=true && job?.state!=DownloadJob.State.DONE,shape=MaterialTheme.shapes.medium) {
                        Icon(Icons.Rounded.Download,null);Spacer(Modifier.width(10.dp))
                        Text(when { job?.active==true -> "Downloading…";job?.state==DownloadJob.State.DONE -> "Downloaded";job?.state==DownloadJob.State.FAILED -> "Retry download";else -> "Download to library" })
                    }
                }
            }
        }
        if(details!=null) {
            if(!details.playable) item { Notice("This upload has no audio files Sonder can play.") }
            if(details.suspicious.isNotEmpty()) item { Notice("This upload includes programs (${details.suspicious.take(3).joinToString { DownloadPlan.extension(it) }}). Audiobooks don't need them, so it may be fake. Sonder only downloads audio, chapter sheets, and a cover image.") }
            if(details.files.isNotEmpty()) item {
                Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainer) { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("${details.files.size} ${if(details.files.size==1) "file" else "files"}",style=MaterialTheme.typography.titleSmall)
                    details.files.take(12).forEach { f -> Row(Modifier.padding(top=6.dp)) { Text(f.name,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis,color=if(DownloadPlan.isAudio(f.name)) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.width(12.dp));Text(f.size,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
                    if(details.files.size>12) Text("and ${details.files.size-12} more",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
                } }
            }
            if(details.description.isNotBlank()) item { SelectionContainer { Text(details.description,style=MaterialTheme.typography.bodyMedium) } }
        }
    }
}
@Composable private fun Notice(text:String) {
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.errorContainer) { Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Rounded.WarningAmber,null,tint=MaterialTheme.colorScheme.onErrorContainer);Spacer(Modifier.width(12.dp));Text(text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onErrorContainer) } }
}

// Sized by decoded bytes: a 600×600 cover is about 1.4 MB.
private object RemoteImages { val cache=object:LruCache<String,ImageBitmap>(24*1024*1024) { override fun sizeOf(key:String,value:ImageBitmap)=value.width*value.height*4 } }
/** A cover from the web, falling back to Sonder's drawn cover while loading or when the image is unavailable. */
@Composable fun RemoteCover(url:String,title:String,modifier:Modifier=Modifier,large:Boolean=false) {
    val image by produceState(RemoteImages.cache.get(url),url) {
        if(value!=null || !url.startsWith("https://")) return@produceState
        value=withContext(Dispatchers.IO) { runCatching {
            val bytes=app.sonder.download.Http.open(url).let { c -> try { if(c.responseCode==200) c.inputStream.use { it.readBounded(6*1024*1024) } else null } finally { c.disconnect() } } ?: return@runCatching null
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
            val options=BitmapFactory.Options().apply { inSampleSize=generateSequence(1) { it*2 }.first { bounds.outWidth/it<=600 && bounds.outHeight/it<=600 } }
            BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?.asImageBitmap()?.also { RemoteImages.cache.put(url,it) }
        }.getOrNull() }
    }
    if(image!=null) Image(image!!,"Cover of $title",modifier.clip(MaterialTheme.shapes.small),contentScale=ContentScale.Crop)
    else Cover(Book(title=title.substringBefore(" - ").ifBlank { title },author=title.substringAfter(" - ","")),modifier,large)
}
private fun bytes(value:Long):String = when {
    value>=1L shl 30 -> "%.1f GB".format(value/(1L shl 30).toDouble())
    value>=1L shl 20 -> "%.0f MB".format(value/(1L shl 20).toDouble())
    else -> "${value/1024} KB"
}
