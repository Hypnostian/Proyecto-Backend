package co.edu.unab.unabgo.cliente.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import co.edu.unab.unabgo.cliente.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.edu.unab.unabgo.cliente.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import java.util.Locale

/** Cuenta el orden de aparición de las tarjetas dentro de una pantalla para escalonar su entrada. */
class EntranceCounter {
    var next = 0
}

val LocalEntranceCounter = compositionLocalOf<EntranceCounter?> { null }

/** Número de orden de una tarjeta: el explícito, o el siguiente del contador de la pantalla. */
@Composable
fun rememberEntranceIndex(explicit: Int? = null): Int {
    val counter = LocalEntranceCounter.current
    return remember { explicit ?: counter?.let { it.next++ } ?: 0 }
}

/** Entrada escalonada: aparece con fundido y un pequeño desplazamiento hacia arriba, con retardo según [index]. */
@Composable
fun Modifier.entrance(index: Int): Modifier {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce || index >= 14) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) {
            delay(index.coerceAtMost(10) * 55L)
            progress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 36.dp.toPx()
    }
}

/** Brillo que recorre un bloque gris mientras se carga (queda quieto si el sistema quitó las animaciones). */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val light = MaterialTheme.colorScheme.surface
    if (LocalReduceMotion.current) return this.background(base)
    val x = rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = -0.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "x"
    )
    return this.drawBehind {
        drawRect(
            Brush.horizontalGradient(
                listOf(base, light, base),
                startX = size.width * (x.value - 0.4f), endX = size.width * (x.value + 0.4f)
            )
        )
    }
}

/** Número que sube desde cero hasta [value] al aparecer y se anima cuando cambia. */
@Composable
fun AnimatedNumber(value: Float, modifier: Modifier = Modifier, decimals: Int = 0, style: TextStyle = MaterialTheme.typography.headlineMedium, color: Color = Color.Unspecified) {
    val reduce = LocalReduceMotion.current
    val shown = remember { Animatable(if (reduce) value else 0f) }
    LaunchedEffect(value) {
        if (reduce) shown.snapTo(value) else shown.animateTo(value, tween(900, easing = FastOutSlowInEasing))
    }
    Text(String.format(Locale.US, "%.${decimals}f", shown.value), modifier, style = style, color = color)
}

/** Anillo de progreso (0 a 1) que se dibuja de forma animada, con contenido en el centro. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    stroke: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    center: @Composable () -> Unit = {}
) {
    val reduce = LocalReduceMotion.current
    val sweep = remember { Animatable(if (reduce) progress else 0f) }
    LaunchedEffect(progress) {
        if (reduce) sweep.snapTo(progress) else sweep.animateTo(progress.coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing))
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val inset = s / 2f
            val arcSize = Size(this.size.width - s, this.size.height - s)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s))
            drawArc(color, -90f, 360f * sweep.value.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
        center()
    }
}
