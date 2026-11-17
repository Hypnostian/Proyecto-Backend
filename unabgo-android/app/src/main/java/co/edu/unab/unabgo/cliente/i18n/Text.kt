package co.edu.unab.unabgo.cliente.i18n

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

/** Tabla de traducciones vigente y la forma de pedir las que falten. Sin traductor, todo queda en español. */
class UiTranslations(val table: TranslationTable, val request: (String) -> Unit) {
    companion object {
        val Spanish = UiTranslations(TranslationTable(SOURCE_LANGUAGE, emptyMap())) {}
    }
}

val LocalUiTranslations = compositionLocalOf { UiTranslations.Spanish }

/** Texto en el idioma de la app: la traducción si ya está, o el español mientras llega. */
@Composable
fun tr(text: String): String {
    val t = LocalUiTranslations.current
    if (t.table.language == SOURCE_LANGUAGE || !shouldTranslate(text)) return text
    val hit = t.table.lookup(text)
    if (hit == null) SideEffect { t.request(text) }
    return hit ?: text
}

/**
 * Reemplaza al Text de Material 3 en toda la app (se importa de forma explícita en cada pantalla) para que
 * cualquier texto se muestre traducido. Con [translate] en false se muestra tal cual (nombres, correos).
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    translate: Boolean = true
) {
    androidx.compose.material3.Text(
        text = if (translate) tr(text) else text, modifier = modifier, color = color, fontSize = fontSize,
        fontStyle = fontStyle, fontWeight = fontWeight, fontFamily = fontFamily, letterSpacing = letterSpacing,
        textDecoration = textDecoration, textAlign = textAlign, lineHeight = lineHeight, overflow = overflow,
        softWrap = softWrap, maxLines = maxLines, minLines = minLines, onTextLayout = onTextLayout, style = style
    )
}
