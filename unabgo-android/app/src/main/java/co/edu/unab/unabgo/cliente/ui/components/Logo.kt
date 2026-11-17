package co.edu.unab.unabgo.cliente.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import co.edu.unab.unabgo.cliente.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.edu.unab.unabgo.cliente.ui.theme.Brand

/**
 * Logo de "UNAB GO Cliente": un monograma de cuatro cuadrantes (morado, naranja, azul y verde) con un círculo
 * central y la letra U. Se dibuja con Compose, sin imágenes.
 */
@Composable
fun Logo(size: Dp = 72.dp, centerColor: Color = Color.White, letterColor: Color = Brand.Ink) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = this.size.width
            val gap = w * 0.04f
            val tile = (w - gap) / 2f
            val big = CornerRadius(w * 0.30f)
            val small = CornerRadius(w * 0.08f)
            fun tile(color: Color, x: Float, y: Float, topLeft: Boolean, topRight: Boolean, bottomRight: Boolean, bottomLeft: Boolean) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            x, y, x + tile, y + tile,
                            if (topLeft) big else small, if (topRight) big else small,
                            if (bottomRight) big else small, if (bottomLeft) big else small
                        )
                    )
                }
                drawPath(path, color)
            }
            tile(Brand.Purple, 0f, 0f, true, false, false, false)
            tile(Brand.Orange, tile + gap, 0f, false, true, false, false)
            tile(Brand.Cyan, 0f, tile + gap, false, false, false, true)
            tile(Brand.Green, tile + gap, tile + gap, false, false, true, false)
            drawCircle(centerColor, radius = w * 0.25f, center = Offset(w / 2f, w / 2f))
        }
        Text("U", color = letterColor, fontWeight = FontWeight.ExtraBold, fontSize = (size.value * 0.30f).sp)
    }
}

/** Logo con el nombre de la app al lado. */
@Composable
fun LogoWordmark(logoSize: Dp = 44.dp, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Logo(logoSize)
        Column {
            Text("UNAB GO", color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, translate = false)
            Text("Cliente", color = textColor.copy(alpha = 0.8f), fontSize = 13.sp, translate = false)
        }
    }
}

/** Franja de cuatro segmentos con los colores institucionales. */
@Composable
fun BrandStrip(modifier: Modifier = Modifier, thickness: Dp = 4.dp) {
    Row(modifier.fillMaxWidth().height(thickness)) {
        Brand.Four.forEach { c -> Box(Modifier.weight(1f).height(thickness).background(c)) }
    }
}
