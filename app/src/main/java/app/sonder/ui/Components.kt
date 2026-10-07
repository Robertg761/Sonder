package app.sonder.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.sonder.data.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

@Composable fun Cover(book:Book,modifier:Modifier=Modifier,large:Boolean=false) {
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,book.cover) { value=withContext(Dispatchers.IO) { if(book.cover.isBlank()) null else runCatching { BitmapFactory.decodeFile(book.cover)?.asImageBitmap() }.getOrNull() } }
    val palette=listOf(Color(0xFF1E2A3A) to Color(0xFFF6C27A),Color(0xFF5A2E2A) to Color(0xFFF2D2A9),Color(0xFF24414A) to Color(0xFFCFE3DD),Color(0xFF3F3352) to Color(0xFFEBC9B8),Color(0xFF4A4030) to Color(0xFFE9DAB2))
    val (base,accent)=palette[(book.title.hashCode().toLong().absoluteValue%palette.size).toInt()]
    Box(modifier.clip(RoundedCornerShape(if(large) 14.dp else 8.dp)).background(base).clearAndSetSemantics { contentDescription="Cover of ${book.title}" }) {
        if(bitmap!=null) Image(bitmap!!,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.linearGradient(listOf(base,base.copy(red=(base.red+.12f).coerceAtMost(1f)))))
                drawCircle(accent.copy(alpha=.24f),size.width*.52f,Offset(size.width*.78f,size.height*.72f))
                drawCircle(base,size.width*.4f,Offset(size.width*.92f,size.height*.78f))
                drawCircle(accent.copy(alpha=.4f),size.width*.14f,Offset(size.width*.23f,size.height*.7f))
                drawLine(accent.copy(alpha=.35f),Offset(size.width*.08f,0f),Offset(size.width*.08f,size.height),size.width*.018f)
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Shrink the title until its longest word fits on one line, so words never split mid-word.
                val longest=book.title.split(' ').maxOf { it.length }.coerceAtLeast(1)
                val titleSize=minOf(if(large) 29f else 20f,(maxWidth.value-if(large) 50f else 28f)/(longest*.6f)).coerceAtLeast(12f)
                // Thumbnails are too small for the full typographic cover, so they show a monogram instead of clipped text.
                if(!large && maxWidth<96.dp) Text(book.title.trim().take(1).uppercase(),color=Color(0xFFFFF7E7),fontFamily=FontFamily.Serif,fontSize=(maxWidth.value*.42f).sp,modifier=Modifier.align(Alignment.Center))
                else Column(Modifier.fillMaxSize().padding(if(large) 25.dp else 14.dp),verticalArrangement=Arrangement.SpaceBetween) {
                    Column { Text(book.author.uppercase(),color=accent,fontSize=if(large) 12.sp else 10.sp,letterSpacing=1.4.sp,maxLines=2,overflow=TextOverflow.Ellipsis);Spacer(Modifier.height(if(large) 18.dp else 10.dp));Text(book.title,color=Color(0xFFFFF7E7),fontFamily=FontFamily.Serif,fontSize=titleSize.sp,lineHeight=(titleSize*1.15f).sp,maxLines=if(large) 5 else 4,overflow=TextOverflow.Ellipsis) }
                    Text("SONDER",color=accent,fontSize=if(large) 10.sp else 8.sp,letterSpacing=if(large) 4.sp else 3.sp)
                }
            }
        }
    }
}
// The app icon's mark: two amber covers holding a page-edge waveform. On a tile it matches the launcher icon exactly.
private val MarkWave=listOf(50f to 58f,46f to 62f,52f to 56f,42f to 66f,47f to 61f,40f to 68f,49f to 59f,45f to 63f,51f to 57f)
@Composable fun SonderMark(modifier:Modifier=Modifier,tile:Boolean=true) {
    val covers=if(tile) Amber else MaterialTheme.colorScheme.primary
    val pages=if(tile) Paper else MaterialTheme.colorScheme.onSurface
    Canvas(modifier.then(if(tile) Modifier.clip(RoundedCornerShape(23)).background(Ink) else Modifier).semantics { contentDescription="Sonder" }) {
        // Tile: the launcher's visible 72-unit window of the 108-unit icon grid. Glyph: just the mark's 42-unit bounds.
        val (origin,span)=if(tile) 18f to 72f else 33f to 42f
        val k=size.minDimension/span
        fun x(v:Float)=(v-origin)*k
        listOf(33f,70.5f).forEach { top -> drawRoundRect(covers,Offset(x(33f),x(top)),androidx.compose.ui.geometry.Size(42f*k,4.5f*k),CornerRadius(2f*k)) }
        MarkWave.forEachIndexed { i,(top,bottom) -> val cx=x(38f+i*4f);drawLine(pages,Offset(cx,x(top)),Offset(cx,x(bottom)),2.6f*k,StrokeCap.Round) }
    }
}
// Small static waveform in the mark's style, used to flag the current chapter.
@Composable fun WaveBars(modifier:Modifier=Modifier,color:Color=MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(18.dp)) {
        val heights=listOf(.45f,.85f,.6f,1f,.5f);val gap=size.width/heights.size
        heights.forEachIndexed { i,h -> val cx=gap*(i+.5f);drawLine(color,Offset(cx,size.height*(1-h)/2),Offset(cx,size.height*(1+h)/2),gap*.55f,StrokeCap.Round) }
    }
}
@Composable fun IconAction(icon:ImageVector,label:String,onClick:()->Unit,modifier:Modifier=Modifier,tint:Color=MaterialTheme.colorScheme.onSurface) { IconButton(onClick,modifier) { Icon(icon,label,tint=tint) } }
@Composable fun EmptyState(icon:ImageVector?,title:String,body:String,modifier:Modifier=Modifier,action:(@Composable ()->Unit)?=null) {
    Column(modifier.fillMaxWidth().padding(30.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        if(icon!=null) Box(Modifier.size(72.dp).clip(MaterialTheme.shapes.extraLarge).background(MaterialTheme.colorScheme.primaryContainer),contentAlignment=Alignment.Center) { Icon(icon,null,Modifier.size(32.dp),tint=MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(20.dp));Text(title,style=MaterialTheme.typography.headlineSmall,textAlign=TextAlign.Center);Spacer(Modifier.height(8.dp));Text(body,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=TextAlign.Center,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.widthIn(max=320.dp));if(action!=null) { Spacer(Modifier.height(24.dp));action() }
    }
}
@Composable fun SectionTitle(title:String,meta:String="") { Row(Modifier.fillMaxWidth().padding(top=16.dp,bottom=10.dp),verticalAlignment=Alignment.CenterVertically) { Text(title,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));if(meta.isNotBlank()) Text(meta,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant) } }
// App-wide actions (Settings) that every tab's title row ends with; provided by SonderRoot.
val LocalHeaderActions=staticCompositionLocalOf<(@Composable RowScope.()->Unit)?> { null }
@Composable fun PageHeader(title:String,subtitle:String="",action:(@Composable ()->Unit)?=null) {
    Column(Modifier.fillMaxWidth().padding(bottom=4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) { Text(title,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.weight(1f));if(action!=null) action();LocalHeaderActions.current?.invoke(this) }
        if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
    }
}
@Composable fun PageAction(icon:ImageVector,label:String,onClick:()->Unit) { FilledTonalIconButton(onClick,colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.onPrimaryContainer)) { Icon(icon,label) } }
@Composable fun StatCard(value:String,label:String,modifier:Modifier=Modifier,highlight:Boolean=false) {
    Surface(modifier,shape=MaterialTheme.shapes.large,color=if(highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal=18.dp,vertical=16.dp)) { Text(value,style=MaterialTheme.typography.headlineMedium,maxLines=1);Spacer(Modifier.height(2.dp));Text(label,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
@Composable fun SearchField(query:String,onQuery:(String)->Unit,placeholder:String,clearLabel:String,modifier:Modifier=Modifier,onSearch:(()->Unit)?=null) {
    OutlinedTextField(query,onQuery,placeholder={ Text(placeholder,maxLines=1,overflow=TextOverflow.Ellipsis) },leadingIcon={ Icon(Icons.Rounded.Search,null) },trailingIcon={ if(query.isNotEmpty()) IconAction(Icons.Rounded.Close,clearLabel,{ onQuery("") },tint=MaterialTheme.colorScheme.onSurfaceVariant) },singleLine=true,shape=MaterialTheme.shapes.extraLarge,modifier=modifier.fillMaxWidth(),
        keyboardOptions=if(onSearch!=null) KeyboardOptions(imeAction=ImeAction.Search) else KeyboardOptions.Default,keyboardActions=KeyboardActions(onSearch={ onSearch?.invoke() }),
        colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainer,focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerLow,unfocusedBorderColor=Color.Transparent,unfocusedLeadingIconColor=MaterialTheme.colorScheme.onSurfaceVariant))
}
@Composable fun ChoiceChip(label:String,selected:Boolean,onClick:()->Unit) {
    FilterChip(selected=selected,onClick=onClick,label={ Text(label) },shape=MaterialTheme.shapes.extraLarge,
        colors=FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary,labelColor=MaterialTheme.colorScheme.onSurfaceVariant),
        border=FilterChipDefaults.filterChipBorder(enabled=true,selected=selected,borderColor=MaterialTheme.colorScheme.outlineVariant))
}
// Lets a horizontally scrolling row run to the screen edge inside padded content instead of clipping at the padding.
fun Modifier.bleed(horizontal:Dp)=layout { measurable,constraints ->
    val extra=horizontal.roundToPx()*2
    val placeable=measurable.measure(constraints.copy(minWidth=constraints.minWidth+extra,maxWidth=constraints.maxWidth+extra))
    layout(constraints.maxWidth,placeable.height) { placeable.place(-horizontal.roundToPx(),0) }
}
@Composable fun ProgressBar(progress:Float,modifier:Modifier=Modifier,height:Dp=4.dp) { LinearProgressIndicator(progress={ progress.coerceIn(0f,1f) },modifier=modifier.fillMaxWidth().height(height).clip(CircleShape),color=MaterialTheme.colorScheme.primary,trackColor=MaterialTheme.colorScheme.onSurface.copy(alpha=.1f),strokeCap=StrokeCap.Round,gapSize=0.dp,drawStopIndicator={}) }
@Composable fun ActionTile(icon:ImageVector,label:String,onClick:()->Unit,modifier:Modifier=Modifier,description:String?=null,active:Boolean=false) {
    Surface(onClick=onClick,modifier=modifier,shape=MaterialTheme.shapes.medium,color=if(active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical=12.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally) { Icon(icon,description,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(22.dp));Spacer(Modifier.height(6.dp));Text(label,style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis) }
    }
}
@Composable fun BookRow(book:Book,onClick:()->Unit,onOptions:()->Unit={},trailing:(@Composable ()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick=onClick,onLongClickLabel="Book options",onLongClick=onOptions).testTag("shelfBook-${book.id}").padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Cover(book,Modifier.width(56.dp).height(76.dp));Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) { Text(book.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);Text(book.author,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis);Spacer(Modifier.height(5.dp));Text(if(book.finished) "Finished" else if(book.inProgress) "${(book.progress*100).toInt()}% · ${duration(book.duration-book.position)} left" else duration(book.duration),color=MaterialTheme.colorScheme.primary,style=MaterialTheme.typography.labelMedium);if(book.inProgress) ProgressBar(book.progress,Modifier.padding(top=6.dp).widthIn(max=160.dp),3.dp) }
        if(trailing!=null) trailing() else Icon(Icons.Rounded.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun MiniPlayer(book:Book,playback:Playback,onOpen:()->Unit,onToggle:()->Unit,onOptions:()->Unit) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=MaterialTheme.shapes.large,shadowElevation=6.dp,modifier=Modifier.padding(horizontal=12.dp,vertical=6.dp).fillMaxWidth().clip(MaterialTheme.shapes.large).combinedClickable(onClick=onOpen,onLongClickLabel="Book options",onLongClick=onOptions)) {
        Column {
            Row(Modifier.padding(start=10.dp,end=10.dp,top=10.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                Cover(book,Modifier.width(40.dp).height(52.dp));Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text(book.title,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text(if(playback.buffering) "Loading audio…" else "${book.author} · ${clock(playback.position)}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis) }
                Spacer(Modifier.width(8.dp));FilledIconButton(onToggle,Modifier.size(44.dp)) { Icon(if(playback.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,if(playback.playing) "Pause" else "Play") }
            }
            ProgressBar(if(book.duration>0) playback.position.toFloat()/book.duration else 0f,Modifier.padding(horizontal=14.dp).padding(bottom=8.dp),2.dp)
        }
    }
}
