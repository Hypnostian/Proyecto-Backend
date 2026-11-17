package co.edu.unab.unabgo.cliente.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Verdadero cuando el usuario quitó las animaciones del sistema; las animaciones decorativas se detienen. */
val LocalReduceMotion = compositionLocalOf { false }

val Orange get() = Brand.Orange

private fun schemeFor(p: RolePalette, dark: Boolean): ColorScheme =
    if (dark) darkColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, primaryContainer = p.container, onPrimaryContainer = p.onContainer,
        secondary = p.primary, onSecondary = p.onPrimary, secondaryContainer = p.container, onSecondaryContainer = p.onContainer,
        tertiary = p.success, onTertiary = Brand.Ink,
        background = Color(0xFF14101A), onBackground = Color(0xFFEDE6F2),
        surface = Color(0xFF1C1724), onSurface = Color(0xFFEDE6F2),
        surfaceVariant = Color(0xFF2A2333), onSurfaceVariant = Color(0xFFC9BFD3),
        surfaceContainerLowest = Color(0xFF14101A), surfaceContainerLow = Color(0xFF1C1724),
        surfaceContainer = Color(0xFF221C2B), surfaceContainerHigh = Color(0xFF2A2333), surfaceContainerHighest = Color(0xFF332B3D),
        outline = Color(0xFF8E8499), outlineVariant = Color(0xFF3F3649),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
    ) else lightColorScheme(
        primary = p.primary, onPrimary = p.onPrimary, primaryContainer = p.container, onPrimaryContainer = p.onContainer,
        secondary = p.primary, onSecondary = p.onPrimary, secondaryContainer = p.container, onSecondaryContainer = p.onContainer,
        tertiary = p.success, onTertiary = Brand.White,
        background = Brand.Neutral, onBackground = Brand.Ink,
        surface = Brand.White, onSurface = Brand.Ink,
        surfaceVariant = Color(0xFFEEE9F3), onSurfaceVariant = Brand.Gray,
        surfaceContainerLowest = Brand.White, surfaceContainerLow = Color(0xFFFBFAFD),
        surfaceContainer = Color(0xFFF3F0F7), surfaceContainerHigh = Color(0xFFECE8F2), surfaceContainerHighest = Color(0xFFE6E1EC),
        outline = Color(0xFF8A8394), outlineVariant = Color(0xFFDDD6E5),
        error = Color(0xFFB3261E), onError = Brand.White
    )

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Tema de la app: los colores salen de la paleta del rol de la sesión (null antes de iniciar sesión). */
@Composable
fun UnabGoTheme(role: String? = null, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = remember(role, dark) { rolePalette(role, dark) }
    val context = LocalContext.current
    val reduce = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalRolePalette provides palette, LocalReduceMotion provides reduce) {
        MaterialTheme(colorScheme = schemeFor(palette, dark), shapes = AppShapes, content = content)
    }
}
