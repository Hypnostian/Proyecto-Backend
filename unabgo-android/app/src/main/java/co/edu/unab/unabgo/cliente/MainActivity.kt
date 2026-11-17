package co.edu.unab.unabgo.cliente

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unab.unabgo.cliente.i18n.LocalUiTranslations
import co.edu.unab.unabgo.cliente.i18n.UiTranslations
import co.edu.unab.unabgo.cliente.ui.nav.AppNav
import co.edu.unab.unabgo.cliente.ui.theme.UnabGoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as UnabGoApp).container
        setContent {
            // El color de toda la app sale del rol de la sesión; sin sesión se usa el tema institucional neutro.
            val session by container.session.session.collectAsStateWithLifecycle()
            // Toda la interfaz lee las traducciones del idioma elegido desde aquí.
            val table by container.translator.table.collectAsStateWithLifecycle()
            val translations = remember(table) { UiTranslations(table) { container.translator.request(it) } }
            CompositionLocalProvider(LocalUiTranslations provides translations) {
                UnabGoTheme(role = session?.user?.role) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppNav(container)
                    }
                }
            }
        }
    }
}
