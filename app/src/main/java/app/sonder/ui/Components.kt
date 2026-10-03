package app.sonder.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonder.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@Composable fun Cover(book:Book,modifier:Modifier=Modifier,large:Boolean=false) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,book.cover) { value=withContext(Dispatchers.IO) { if(book.cover.isBlank()) null else runCatching { BitmapFactory.decodeFile(book.cover)?.asImageBitmap() }.getOrNull() } }
    val palette=listOf(Color(0xFF334F48) to Color(0xFFE1C298),Color(0xFF7E462D) to Color(0xFFF3D4A3),Color(0xFF394664) to Color(0xFFCFDBDD),Color(0xFF77545F) to Color(0xFFE9C7B4),Color(0xFF5A634A) to Color(0xFFE4DBB1))
    val (base,accent)=palette[(book.title.hashCode().toLong().absoluteValue%palette.size).toInt()]
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(base).semantics { contentDescription="Cover of ${book.title}" }) {
        if(bitmap!=null) Image(bitmap!!,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(base,base.copy(red=(base.red+.12f).coerceAtMost(1f)))))
                drawCircle(accent.copy(alpha=.24f),size.width*.52f,Offset(size.width*.78f,size.height*.72f))
                drawCircle(base,size.width*.4f,Offset(size.width*.92f,size.height*.78f))
                drawCircle(accent.copy(alpha=.4f),size.width*.14f,Offset(size.width*.23f,size.height*.7f))
                drawLine(accent.copy(alpha=.35f),Offset(size.width*.08f,0f),Offset(size.width*.08f,size.height),size.width*.018f)
            }
            Column(Modifier.fillMaxSize().padding(if(large) 25.dp else 14.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Column { Text(book.author.uppercase(),color=accent,fontSize=if(large) 12.sp else 10.sp,letterSpacing=1.4.sp,maxLines=2);Spacer(Modifier.height(if(large) 18.dp else 10.dp));Text(book.title,color=Color(0xFFFFF7E7),fontFamily=FontFamily.Serif,fontSize=if(large) 29.sp else 20.sp,lineHeight=if(large) 33.sp else 24.sp,maxLines=if(large) 5 else 4,overflow=TextOverflow.Ellipsis) }
                Text("S O N D E R",color=accent,fontSize=if(large) 10.sp else 8.sp,letterSpacing=1.sp)
            }
        }
    }
}
@Composable fun IconAction(icon:ImageVector,label:String,onClick:()->Unit,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.onSurface) { IconButton(onClick,modifier) { Icon(icon,label,tint=tint) } }
@Composable fun EmptyState(icon:ImageVector,title:String,body:String,modifier:Modifier=Modifier,action:(@Composable ()->Unit)?=null) {
    Column(modifier.fillMaxWidth().padding(30.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) { Icon(icon,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(24.dp));Text(title,style=MaterialTheme.typography.headlineMedium,textAlign=TextAlign.Center);Spacer(Modifier.height(12.dp));Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyLarge);if(action!=null) { Spacer(Modifier.height(24.dp));action() }
    }
}
@Composable fun SectionTitle(title:String,meta:String="") { Row(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) { Text(title,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));if(meta.isNotBlank()) Text(meta,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable fun BookRow(book:Book,onClick:()->Unit,onOptions:()->Unit={},trailing:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick=onClick,onLongClickLabel="Book options",onLongClick=onOptions).testTag("shelfBook-${book.id}").padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Cover(book,Modifier.width(58.dp).height(78.dp));Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Text(book.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);Text(book.author,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Spacer(Modifier.height(5.dp));Text(if(book.finished) "Finished" else if(book.inProgress) "${(book.progress*100).toInt()}% · ${duration(book.duration-book.position)} left" else duration(book.duration),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.bodySmall) }
        if(trailing!=null) trailing() else Icon(Icons.Rounded.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun MiniPlayer(book:Book,playback:Playback,onOpen:()->Unit,onToggle:()->Unit,onOptions:()->Unit) {
    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(18.dp),modifier=Modifier.padding(horizontal=16.dp,vertical=6.dp).fillMaxWidth().combinedClickable(onClick=onOpen,onLongClickLabel="Book options",onLongClick=onOptions)) {
        Column {
            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Cover(book,Modifier.size(48.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text(book.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text(if(playback.buffering) "Loading audio…" else "${clock(playback.position)} · ${playback.speed}×",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer) }
                IconAction(if(playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(playback.playing) "Pause" else "Play",onToggle)
            }
            LinearProgressIndicator(progress={ if(book.duration>0) (playback.position.toFloat()/book.duration).coerceIn(0f,1f) else 0f },modifier=Modifier.fillMaxWidth().height(2.dp),color=MaterialTheme.colorScheme.primary,trackColor=Color.Transparent,drawStopIndicator={})
        }
    }
}
