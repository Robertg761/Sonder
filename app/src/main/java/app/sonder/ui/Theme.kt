package app.sonder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Forest=Color(0xFF285C4D)
val Cream=Color(0xFFF8F7F2)
val Gold=Color(0xFFDEA665)
private val Light=lightColorScheme(primary=Forest,onPrimary=Color.White,primaryContainer=Color(0xFFE3EBE4),onPrimaryContainer=Color(0xFF183E30),secondary=Color(0xFF936239),secondaryContainer=Color(0xFFF4E4CF),background=Cream,onBackground=Color(0xFF252E29),surface=Cream,onSurface=Color(0xFF252E29),surfaceVariant=Color(0xFFEDEDE4),onSurfaceVariant=Color(0xFF636B60),outline=Color(0xFFA0A698),outlineVariant=Color(0xFFDCDFD3))
private val Dark=darkColorScheme(primary=Color(0xFFADCFB5),onPrimary=Color(0xFF183E30),primaryContainer=Color(0xFF2C493A),onPrimaryContainer=Color(0xFFDCEADB),secondary=Gold,secondaryContainer=Color(0xFF55432F),background=Color(0xFF141D19),surface=Color(0xFF141D19),onSurface=Color(0xFFE8ECE2),onBackground=Color(0xFFE8ECE2),surfaceVariant=Color(0xFF253129),onSurfaceVariant=Color(0xFFB7C0B1),outlineVariant=Color(0xFF39473C))
private val Type=Typography(
    displaySmall=TextStyle(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Normal,fontSize=36.sp,lineHeight=42.sp),
    headlineLarge=TextStyle(fontFamily=FontFamily.Serif,fontSize=32.sp,lineHeight=38.sp),
    headlineMedium=TextStyle(fontFamily=FontFamily.Serif,fontSize=28.sp,lineHeight=34.sp),
    headlineSmall=TextStyle(fontFamily=FontFamily.Serif,fontSize=24.sp,lineHeight=30.sp),
    titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=20.sp,lineHeight=26.sp),
    titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp),
    titleSmall=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=20.sp),labelMedium=TextStyle(fontWeight=FontWeight.Medium,fontSize=13.sp,lineHeight=18.sp),labelSmall=TextStyle(fontSize=12.sp,lineHeight=16.sp)
)
@Composable fun SonderTheme(theme:String,content:@Composable ()->Unit) { MaterialTheme(colorScheme=if(theme=="Dark" || theme=="System" && isSystemInDarkTheme()) Dark else Light,typography=Type,content=content) }
fun clock(ms:Long):String { val seconds=(ms.coerceAtLeast(0)/1000);return if(seconds>=3600) "%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60) else "%d:%02d".format(seconds/60,seconds%60) }
fun duration(ms:Long):String { val minutes=ms.coerceAtLeast(0)/60000;return if(minutes==0L) "${ms.coerceAtLeast(0)/1000}s" else if(minutes>=60) "${minutes/60}h ${minutes%60}m" else "${minutes}m" }
