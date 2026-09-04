package cn.edu.bistu.kebiao.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Paper = Color(0xFFF6F0E6)
val Ink = Color(0xFF182A26)
val Forest = Color(0xFF244D42)
val Coral = Color(0xFF9E4F39)
val Line = Color(0xFFD8CDBE)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCECE6),
    onPrimaryContainer = Color(0xFF0E342B),
    secondary = Coral,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7DDD4),
    onSecondaryContainer = Color(0xFF45251D),
    background = Paper,
    onBackground = Ink,
    surface = Color(0xFFFFFBF4),
    onSurface = Ink,
    surfaceVariant = Color(0xFFEDE4D7),
    onSurfaceVariant = Color(0xFF52605C),
    outline = Color(0xFF77847F),
    outlineVariant = Line,
    error = Color(0xFFB63D32),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410001),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BCDBD),
    onPrimary = Color(0xFF08382F),
    primaryContainer = Color(0xFF21443A),
    onPrimaryContainer = Color(0xFFD5F0E5),
    secondary = Color(0xFFFFB5A0),
    onSecondary = Color(0xFF5B1A0B),
    secondaryContainer = Color(0xFF633326),
    onSecondaryContainer = Color(0xFFFFDAD0),
    background = Color(0xFF111916),
    onBackground = Color(0xFFF0E9DD),
    surface = Color(0xFF18231F),
    onSurface = Color(0xFFF0E9DD),
    surfaceVariant = Color(0xFF25342F),
    onSurfaceVariant = Color(0xFFC4CCC6),
    outline = Color(0xFF8A9A94),
    outlineVariant = Color(0xFF3B4A45),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val KebiaoTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
)

@Composable
fun KebiaoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = KebiaoTypography,
        content = content,
    )
}
