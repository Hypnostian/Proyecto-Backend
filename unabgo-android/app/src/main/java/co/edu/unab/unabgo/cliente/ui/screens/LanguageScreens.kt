package co.edu.unab.unabgo.cliente.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unab.unabgo.cliente.i18n.APP_LANGUAGES
import co.edu.unab.unabgo.cliente.i18n.LanguageController
import co.edu.unab.unabgo.cliente.i18n.SOURCE_LANGUAGE
import co.edu.unab.unabgo.cliente.i18n.Text
import co.edu.unab.unabgo.cliente.i18n.TranslationStatus
import co.edu.unab.unabgo.cliente.i18n.languageOf
import co.edu.unab.unabgo.cliente.ui.components.*
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette
import kotlinx.coroutines.delay

/** Pantalla de idioma: el cambio es inmediato y queda guardado en el perfil de quien tiene la sesión. */
class LanguageViewModel(private val controller: LanguageController) : ViewModel() {
    val current = controller.current
    val status = controller.status
    val saveError = controller.saveError
    fun choose(code: String) = controller.select(code)
    fun retry() = controller.retry()
}

/** Mensaje del estado del traductor, en español (se traduce solo cuando el paquete ya está listo). */
fun translationStatusText(status: TranslationStatus, code: String): String {
    val name = languageOf(code).spanishName.lowercase()
    return when (status) {
        TranslationStatus.OFF -> "La app está en español, su idioma original."
        TranslationStatus.CHECKING -> "Preparando el traductor de $name."
        TranslationStatus.DOWNLOADING -> "Descargando el paquete de $name por wifi. La app se traduce sola al terminar."
        TranslationStatus.WAITING_WIFI ->
            "Conéctate a una red wifi para descargar el paquete de $name (unos 30 MB). La descarga empieza sola al conectarte."
        TranslationStatus.READY -> "La app está en $name. La traducción se hace en el teléfono y funciona sin conexión."
        TranslationStatus.ERROR -> "No se pudo preparar el traductor de $name. Mientras tanto la app sigue en español."
    }
}

@Composable
fun LanguageScreen(vm: LanguageViewModel, loggedIn: Boolean, onBack: () -> Unit) {
    val current by vm.current.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val p = LocalRolePalette.current
    AppScaffold(title = "Idioma", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (loggedIn) "Elige el idioma de la app. Queda guardado en tu perfil: al cerrar sesión y volver a entrar, la app abre en el idioma que dejaste."
                else "Elige el idioma de la app. Cuando inicies sesión se usará el idioma guardado en tu perfil.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            APP_LANGUAGES.forEach { lang ->
                val selected = lang.code == current
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)
                        .background(if (selected) p.container else MaterialTheme.colorScheme.surface)
                        .border(if (selected) 2.dp else 1.dp, if (selected) p.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                        .clickable { vm.choose(lang.code) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(42.dp).clip(CircleShape).background(if (selected) p.primary else MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        Text(
                            lang.code.uppercase(), fontWeight = FontWeight.ExtraBold,
                            color = if (selected) p.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, translate = false
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(lang.nativeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, translate = false)
                        if (lang.code != SOURCE_LANGUAGE) Text(lang.spanishName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Default.Check, contentDescription = "Elegido", tint = p.accentText)
                }
            }
            TranslationStatusCard(status, current, vm::retry)
            saveError?.let { ErrorBanner("El idioma cambió en este teléfono, pero no se pudo guardar en tu perfil: ${it.userMessage}") }
            Text(
                "La traducción la hace ML Kit de Google dentro del teléfono: los textos no salen a internet. Cada idioma se descarga una sola vez y solo por wifi.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TranslationStatusCard(status: TranslationStatus, code: String, onRetry: () -> Unit) {
    val p = LocalRolePalette.current
    val busy = status == TranslationStatus.CHECKING || status == TranslationStatus.DOWNLOADING
    AppCard(container = if (status == TranslationStatus.ERROR) MaterialTheme.colorScheme.errorContainer else p.container) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = p.onContainer)
            else Box(Modifier.size(12.dp).clip(CircleShape).background(statusColor(status)))
            Spacer(Modifier.width(12.dp))
            Text(translationStatusText(status, code), color = p.onContainer, modifier = Modifier.weight(1f))
        }
        if (status == TranslationStatus.DOWNLOADING) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)), color = p.primary)
        }
        if (status == TranslationStatus.ERROR || status == TranslationStatus.WAITING_WIFI) {
            AppTextButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
}

@Composable
private fun statusColor(status: TranslationStatus): Color = when (status) {
    TranslationStatus.READY, TranslationStatus.OFF -> LocalRolePalette.current.success
    TranslationStatus.WAITING_WIFI -> Color(0xFFF79800)
    TranslationStatus.ERROR -> MaterialTheme.colorScheme.error
    else -> LocalRolePalette.current.primary
}

/** Fila "Idioma de la app" del perfil. */
@Composable
fun LanguageRow(code: String?, onClick: () -> Unit) {
    val p = LocalRolePalette.current
    val lang = languageOf(code)
    AppCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.container), contentAlignment = Alignment.Center) {
                Text(lang.code.uppercase(), color = p.onContainer, fontWeight = FontWeight.ExtraBold, translate = false)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Idioma de la app", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lang.nativeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, translate = false)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** Botón de idioma del encabezado (inicio de sesión). */
@Composable
fun LanguagePill(code: String, tint: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.16f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(languageOf(code).code.uppercase(), color = tint, fontWeight = FontWeight.ExtraBold, translate = false)
        Spacer(Modifier.width(6.dp))
        Text(languageOf(code).nativeName, color = tint, style = MaterialTheme.typography.labelLarge, translate = false)
    }
}

/**
 * Aviso flotante mientras se descarga un idioma o se espera el wifi; al quedar listo muestra la confirmación unos
 * segundos y desaparece.
 */
@Composable
fun TranslationStatusBanner(status: TranslationStatus, code: String, modifier: Modifier = Modifier) {
    var showReady by remember { mutableStateOf(false) }
    var previous by remember { mutableStateOf(status) }
    LaunchedEffect(status) {
        if (status == TranslationStatus.READY && (previous == TranslationStatus.DOWNLOADING || previous == TranslationStatus.WAITING_WIFI)) {
            showReady = true
            delay(3500)
            showReady = false
        }
        previous = status
    }
    val visible = status == TranslationStatus.DOWNLOADING || status == TranslationStatus.WAITING_WIFI || showReady
    AnimatedVisibility(visible, modifier, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Surface(
            shape = RoundedCornerShape(50), color = Color(0xFF2F024C), contentColor = Color.White, shadowElevation = 6.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status == TranslationStatus.DOWNLOADING) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFFF79800))
                else Box(Modifier.size(10.dp).clip(CircleShape).background(if (status == TranslationStatus.READY) Color(0xFF95C11F) else Color(0xFFF79800)))
                Spacer(Modifier.width(10.dp))
                Text(translationStatusText(status, code), style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
        }
    }
}
