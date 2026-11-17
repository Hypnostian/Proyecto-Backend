package co.edu.unab.unabgo.cliente.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import co.edu.unab.unabgo.cliente.ui.theme.Brand
import co.edu.unab.unabgo.cliente.ui.theme.LocalReduceMotion
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette

/**
 * Encabezado con degradado del rol y formas decorativas que se desplazan muy despacio.
 * Antes de iniciar sesión las formas llevan los cuatro colores institucionales.
 */
@Composable
fun GradientHeader(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalRolePalette.current
    val drift: State<Float> = if (LocalReduceMotion.current) remember { mutableFloatStateOf(0.5f) } else
        rememberInfiniteTransition(label = "drift").animateFloat(
            0f, 1f, infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift"
        )
    Box(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.linearGradient(p.gradient))
    ) {
        Canvas(Modifier.matchParentSize()) {
            val d = drift.value
            val w = size.width
            val h = size.height
            val soft = p.onGradient.copy(alpha = 0.10f)
            drawCircle(soft, radius = w * 0.34f, center = Offset(w * (0.98f - 0.05f * d), h * (0.05f + 0.12f * d)))
            drawCircle(soft, radius = w * 0.20f, center = Offset(w * (0.05f + 0.06f * d), h * (1.0f - 0.10f * d)))
            if (p.neutral) {
                val dots = listOf(Brand.Orange, Brand.Cyan, Brand.Green)
                dots.forEachIndexed { i, c ->
                    drawCircle(c.copy(alpha = 0.55f), radius = w * (0.035f + 0.01f * i), center = Offset(w * (0.62f + 0.11f * i), h * (0.22f + 0.10f * d * (i + 1) / 3f)))
                }
            } else {
                drawCircle(soft, radius = w * 0.09f, center = Offset(w * (0.70f + 0.04f * d), h * (0.78f - 0.1f * d)))
            }
        }
        Column(Modifier.statusBarsPadding().fillMaxWidth(), content = content)
    }
}
