package cl.jcleaner.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ambar = Color(0xFFF59F00)
val Rojo = Color(0xFFE5484D)

private val Claro = lightColorScheme(
    primary = Color(0xFF0B8F6A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F2E3),
    onPrimaryContainer = Color(0xFF00382A),
    secondary = Color(0xFF3B6EF5),
    secondaryContainer = Color(0xFFDCE4FF),
    onSecondaryContainer = Color(0xFF0C2466),
    background = Color(0xFFF5F7F6),
    onBackground = Color(0xFF151A18),
    surface = Color(0xFFF5F7F6),
    onSurface = Color(0xFF151A18),
    surfaceVariant = Color(0xFFE3EAE7),
    onSurfaceVariant = Color(0xFF56615C),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    error = Color(0xFFD13C41),
)

private val Oscuro = darkColorScheme(
    primary = Color(0xFF4FDDB0),
    onPrimary = Color(0xFF00382A),
    primaryContainer = Color(0xFF0F4D3C),
    onPrimaryContainer = Color(0xFFC9F2E3),
    secondary = Color(0xFF8AA8FF),
    secondaryContainer = Color(0xFF1E3170),
    onSecondaryContainer = Color(0xFFDCE4FF),
    background = Color(0xFF0F1412),
    onBackground = Color(0xFFE6ECE9),
    surface = Color(0xFF0F1412),
    onSurface = Color(0xFFE6ECE9),
    surfaceVariant = Color(0xFF26302C),
    onSurfaceVariant = Color(0xFFA9B5B0),
    surfaceContainer = Color(0xFF18201D),
    surfaceContainerHigh = Color(0xFF1C2421),
    surfaceContainerLow = Color(0xFF151C19),
    error = Color(0xFFFF7B7F),
)

@Composable
fun JCleanerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Oscuro else Claro, content = content)
}
