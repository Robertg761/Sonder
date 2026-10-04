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

// Brand colors from the app icon: ink covers the page, amber binds the book, paper is the waveform.
val Ink=Color(0xFF101820)
val Amber=Color(0xFFF6A94B)
val Paper=Color(0xFFF2E8D5)
// Every Material 3 role is set so dialogs, menus, switches, and snackbars stay in the Sonder palette instead of the baseline purple.
private val Light=lightColorScheme(primary=Color(0xFF8F5410),onPrimary=Color.White,primaryContainer=Color(0xFFFCE1BA),onPrimaryContainer=Color(0xFF2F1B00),inversePrimary=Amber,
    secondary=Color(0xFF3A4A60),onSecondary=Color.White,secondaryContainer=Color(0xFFDCE3EC),onSecondaryContainer=Color(0xFF142233),tertiary=Color(0xFF2F5F86),onTertiary=Color.White,tertiaryContainer=Color(0xFFD2E6F8),onTertiaryContainer=Color(0xFF0B2236),
    background=Color(0xFFF6F1E7),onBackground=Color(0xFF141B24),surface=Color(0xFFF6F1E7),onSurface=Color(0xFF141B24),surfaceVariant=Color(0xFFE7E1D5),onSurfaceVariant=Color(0xFF5B6370),surfaceTint=Color(0xFF8F5410),
    inverseSurface=Color(0xFF1B2430),inverseOnSurface=Color(0xFFEFE9DD),outline=Color(0xFF8B909A),outlineVariant=Color(0xFFD9D3C6),scrim=Color.Black,
    surfaceBright=Color(0xFFF6F1E7),surfaceDim=Color(0xFFDDD7CA),surfaceContainerLowest=Color.White,surfaceContainerLow=Color(0xFFF1ECE1),surfaceContainer=Color(0xFFECE6DA),surfaceContainerHigh=Color(0xFFE6E0D3),surfaceContainerHighest=Color(0xFFE0DACC))
private val Dark=darkColorScheme(primary=Amber,onPrimary=Color(0xFF2A1800),primaryContainer=Color(0xFF3D2C14),onPrimaryContainer=Color(0xFFFFDDB0),inversePrimary=Color(0xFF8F5410),
    secondary=Color(0xFFE6DAC4),onSecondary=Color(0xFF2A2418),secondaryContainer=Color(0xFF2A3443),onSecondaryContainer=Color(0xFFE3E8EF),tertiary=Color(0xFF9CC3E6),onTertiary=Color(0xFF0B2236),tertiaryContainer=Color(0xFF1E3A55),onTertiaryContainer=Color(0xFFD2E6F8),
    background=Ink,onBackground=Color(0xFFECE5D8),surface=Ink,onSurface=Color(0xFFECE5D8),surfaceVariant=Color(0xFF253041),onSurfaceVariant=Color(0xFFA8B0BB),surfaceTint=Amber,
    inverseSurface=Color(0xFFECE5D8),inverseOnSurface=Color(0xFF1B2430),outline=Color(0xFF7D8794),outlineVariant=Color(0xFF2C3747),scrim=Color.Black,
    surfaceBright=Color(0xFF2B3647),surfaceDim=Ink,surfaceContainerLowest=Color(0xFF0B1119),surfaceContainerLow=Color(0xFF151E29),surfaceContainer=Color(0xFF19232F),surfaceContainerHigh=Color(0xFF202B38),surfaceContainerHighest=Color(0xFF283442))
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
