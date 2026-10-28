package ninja.richter.soundfork.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF191919), onPrimary = Color.White,
    primaryContainer = Color(0xFFECECEB), onPrimaryContainer = Color(0xFF191919),
    secondary = Color(0xFF62615E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0EFED), onSecondaryContainer = Color(0xFF252523),
    tertiary = Color(0xFF8E7169), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0E7E3), onTertiaryContainer = Color(0xFF4B3933),
    background = Color(0xFFF7F7F6), onBackground = Color(0xFF202020),
    surface = Color(0xFFFAFAF9), onSurface = Color(0xFF202020),
    surfaceVariant = Color(0xFFF0F0EE), onSurfaceVariant = Color(0xFF73736E),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFECECEB),
    outline = Color(0xFFAAAAA5), outlineVariant = Color(0xFFE3E3E0),
    error = Color(0xFFAC342D), errorContainer = Color(0xFFFBEAE7),
    onErrorContainer = Color(0xFF79251F)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF0EFEC), onPrimary = Color(0xFF161616),
    primaryContainer = Color(0xFF30302F), onPrimaryContainer = Color(0xFFF0EFEC),
    secondary = Color(0xFFB9B7B1), onSecondary = Color(0xFF161616),
    secondaryContainer = Color(0xFF292928), onSecondaryContainer = Color(0xFFE8E6E1),
    tertiary = Color(0xFFD3B0A2), onTertiary = Color(0xFF2D211C),
    tertiaryContainer = Color(0xFF392F2A), onTertiaryContainer = Color(0xFFEAD7CD),
    background = Color(0xFF141414), onBackground = Color(0xFFF0EFEC),
    surface = Color(0xFF171717), onSurface = Color(0xFFF0EFEC),
    surfaceVariant = Color(0xFF292928), onSurfaceVariant = Color(0xFFAAA9A4),
    surfaceContainerLowest = Color(0xFF101010), surfaceContainerLow = Color(0xFF1E1E1D),
    surfaceContainer = Color(0xFF20201F), surfaceContainerHigh = Color(0xFF20201F),
    surfaceContainerHighest = Color(0xFF30302F),
    outline = Color(0xFF777771), outlineVariant = Color(0xFF383836),
    error = Color(0xFFFFB4AB), errorContainer = Color(0xFF522924),
    onErrorContainer = Color(0xFFFFDAD6)
)

// The original SoundTouch interface uses light, spaced lettering and quiet surfaces.
private val SoundTouchFont = FontFamily(android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL))
private val SoundForkTypography = Typography(
    headlineLarge = TextStyle(fontFamily = SoundTouchFont, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.6.sp),
    headlineMedium = TextStyle(fontFamily = SoundTouchFont, fontSize = 27.sp, lineHeight = 35.sp, letterSpacing = 0.5.sp),
    titleLarge = TextStyle(fontFamily = SoundTouchFont, fontWeight = FontWeight.Medium, fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = 1.4.sp),
    titleMedium = TextStyle(fontFamily = SoundTouchFont, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.6.sp),
    titleSmall = TextStyle(fontFamily = SoundTouchFont, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontFamily = SoundTouchFont, fontSize = 16.sp, lineHeight = 23.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontFamily = SoundTouchFont, fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = SoundTouchFont, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = SoundTouchFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = SoundTouchFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.sp),
    labelSmall = TextStyle(fontFamily = SoundTouchFont, fontSize = 10.sp, lineHeight = 15.sp, letterSpacing = 0.7.sp)
)

private val SoundForkShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(16.dp)
)

@Composable
fun SoundForkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SoundForkTypography,
        shapes = SoundForkShapes,
        content = content
    )
}
