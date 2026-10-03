package app.sonder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Forest=Color(0xFF285C4D)
val Cream=Color(0xFFF8F7F2)
val Gold=Color(0xFFDEA665)
// Every Material 3 role is set so dialogs, menus, switches, and snackbars stay in the Sonder palette instead of the baseline purple.
private val Light=lightColorScheme(primary=Forest,onPrimary=Color.White,primaryContainer=Color(0xFFDCE8DF),onPrimaryContainer=Color(0xFF113A2D),inversePrimary=Color(0xFFADCFB5),
    secondary=Color(0xFF8A5A33),onSecondary=Color.White,secondaryContainer=Color(0xFFEDE6D8),onSecondaryContainer=Color(0xFF3A2A17),tertiary=Color(0xFF9A6A2E),onTertiary=Color.White,tertiaryContainer=Color(0xFFF6E3C6),onTertiaryContainer=Color(0xFF3B2604),
    background=Cream,onBackground=Color(0xFF232B26),surface=Cream,onSurface=Color(0xFF232B26),surfaceVariant=Color(0xFFE6E7DE),onSurfaceVariant=Color(0xFF5D665B),surfaceTint=Forest,
    inverseSurface=Color(0xFF2A322D),inverseOnSurface=Color(0xFFEEF1E9),outline=Color(0xFF8F968A),outlineVariant=Color(0xFFD8DACF),scrim=Color.Black,
    surfaceBright=Cream,surfaceDim=Color(0xFFDAD9D2),surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFF2F1EB),surfaceContainer=Color(0xFFEDECE5),surfaceContainerHigh=Color(0xFFE8E7DF),surfaceContainerHighest=Color(0xFFE2E2D9))
private val Dark=darkColorScheme(primary=Color(0xFFADCFB5),onPrimary=Color(0xFF0F3526),primaryContainer=Color(0xFF2A4637),onPrimaryContainer=Color(0xFFD3E8D6),inversePrimary=Forest,
    secondary=Gold,onSecondary=Color(0xFF3F2A10),secondaryContainer=Color(0xFF39342A),onSecondaryContainer=Color(0xFFEDE1CB),tertiary=Color(0xFFE7C38E),onTertiary=Color(0xFF412C06),tertiaryContainer=Color(0xFF5B4219),onTertiaryContainer=Color(0xFFFBE0B6),
    background=Color(0xFF121A16),onBackground=Color(0xFFE6EBE3),surface=Color(0xFF121A16),onSurface=Color(0xFFE6EBE3),surfaceVariant=Color(0xFF2B3630),onSurfaceVariant=Color(0xFFB9C3B5),surfaceTint=Color(0xFFADCFB5),
    inverseSurface=Color(0xFFE6EBE3),inverseOnSurface=Color(0xFF263029),outline=Color(0xFF87918A),outlineVariant=Color(0xFF38443C),scrim=Color.Black,
    surfaceBright=Color(0xFF38423C),surfaceDim=Color(0xFF121A16),surfaceContainerLowest=Color(0xFF0D1411),surfaceContainerLow=Color(0xFF18211C),surfaceContainer=Color(0xFF1C2620),surfaceContainerHigh=Color(0xFF242E28),surfaceContainerHighest=Color(0xFF2E3832))
private val Shape=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp),extraLarge=RoundedCornerShape(28.dp))
private val Type=Typography(
    displaySmall=TextStyle(fontFamily=FontFamily.Serif,fontWeight=FontWeight.Normal,fontSize=36.sp,lineHeight=42.sp),
    headlineLarge=TextStyle(fontFamily=FontFamily.Serif,fontSize=30.sp,lineHeight=36.sp,letterSpacing=(-.3).sp),
    headlineMedium=TextStyle(fontFamily=FontFamily.Serif,fontSize=26.sp,lineHeight=32.sp,letterSpacing=(-.2).sp),
    headlineSmall=TextStyle(fontFamily=FontFamily.Serif,fontSize=22.sp,lineHeight=28.sp),
    titleLarge=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=20.sp,lineHeight=26.sp),
    titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp),
    titleSmall=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=24.sp),bodyMedium=TextStyle(fontSize=14.sp,lineHeight=21.sp),bodySmall=TextStyle(fontSize=12.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=20.sp),labelMedium=TextStyle(fontWeight=FontWeight.Medium,fontSize=13.sp,lineHeight=18.sp),labelSmall=TextStyle(fontSize=12.sp,lineHeight=16.sp)
)
@Composable fun SonderTheme(theme:String,content:@Composable ()->Unit) { MaterialTheme(colorScheme=if(theme=="Dark" || theme=="System" && isSystemInDarkTheme()) Dark else Light,typography=Type,shapes=Shape,content=content) }
fun clock(ms:Long):String { val seconds=(ms.coerceAtLeast(0)/1000);return if(seconds>=3600) "%d:%02d:%02d".format(seconds/3600,seconds/60%60,seconds%60) else "%d:%02d".format(seconds/60,seconds%60) }
fun duration(ms:Long):String { val minutes=ms.coerceAtLeast(0)/60000;return if(minutes==0L) "${ms.coerceAtLeast(0)/1000}s" else if(minutes>=60) "${minutes/60}h ${minutes%60}m" else "${minutes}m" }
