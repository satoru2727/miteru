package io.github.satoru2727.miteru.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val KonomiPinkLight = Color(0xFFFFB1D0)
private val KonomiViolet = Color(0xFFB9A6FF)
private val KonomiTeal = Color(0xFF6FD6C4)

private val DarkColors = darkColorScheme(
    primary = KonomiPinkLight,
    onPrimary = Color(0xFF3D0026),
    primaryContainer = Color(0xFF5C1240),
    onPrimaryContainer = Color(0xFFFFD9E6),
    secondary = KonomiViolet,
    onSecondary = Color(0xFF211447),
    secondaryContainer = Color(0xFF352A5C),
    onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = KonomiTeal,
    onTertiary = Color(0xFF00382F),
    background = Color(0xFF0E0C14),
    onBackground = Color(0xFFE9E3F0),
    surface = Color(0xFF0E0C14),
    onSurface = Color(0xFFE9E3F0),
    surfaceVariant = Color(0xFF242030),
    onSurfaceVariant = Color(0xFFCAC2D6),
    surfaceContainerLowest = Color(0xFF09070E),
    surfaceContainerLow = Color(0xFF151220),
    surfaceContainer = Color(0xFF1A1626),
    surfaceContainerHigh = Color(0xFF221D30),
    surfaceContainerHighest = Color(0xFF2B253B),
    outline = Color(0xFF958DA3),
    outlineVariant = Color(0xFF48414F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3005E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD9E6),
    onPrimaryContainer = Color(0xFF3D0026),
    secondary = Color(0xFF5F4C9E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEFF),
    onSecondaryContainer = Color(0xFF1B1040),
    tertiary = Color(0xFF00695C),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFDF7FC),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFDF7FC),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFEBE0EA),
    onSurfaceVariant = Color(0xFF4C454F),
    outline = Color(0xFF7E757F),
    outlineVariant = Color(0xFFD0C4CE),
)

@Composable
fun KonomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = KonomiTypography,
        content = content,
    )
}
