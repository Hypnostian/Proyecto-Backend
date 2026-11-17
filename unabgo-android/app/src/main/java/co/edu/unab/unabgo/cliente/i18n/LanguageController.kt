package co.edu.unab.unabgo.cliente.i18n

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import co.edu.unab.unabgo.cliente.data.api.ApiError
import co.edu.unab.unabgo.cliente.data.api.ApiResult
import co.edu.unab.unabgo.cliente.data.api.ProfileBody
import co.edu.unab.unabgo.cliente.data.repo.AuthRepository
import co.edu.unab.unabgo.cliente.data.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Último idioma usado en este teléfono (se aplica al abrir la app, incluso antes de iniciar sesión). */
interface LanguageStore {
    val language: StateFlow<String>
    suspend fun set(code: String)
}

open class InMemoryLanguageStore(initial: String = SOURCE_LANGUAGE) : LanguageStore {
    protected val state = MutableStateFlow(initial)
    override val language: StateFlow<String> get() = state
    override suspend fun set(code: String) { state.value = code }
}

private val KEY_LANGUAGE = stringPreferencesKey("language")

class DataStoreLanguageStore(private val dataStore: DataStore<Preferences>) : InMemoryLanguageStore() {
    suspend fun load() {
        state.value = languageOf(dataStore.data.first()[KEY_LANGUAGE]).code
    }

    override suspend fun set(code: String) {
        super.set(code)
        dataStore.edit { it[KEY_LANGUAGE] = code }
    }
}

/**
 * Idioma de cada persona. Se guarda en su perfil del servidor (student_profiles.language), así que al cerrar
 * sesión y volver a entrar, en este o en otro teléfono, la app abre en el idioma en que la dejó.
 */
class LanguageController(
    private val translator: AppTranslator,
    private val store: LanguageStore,
    private val session: SessionStore,
    private val auth: AuthRepository,
    private val scope: CoroutineScope
) {
    val current: StateFlow<String> get() = store.language
    val status: StateFlow<TranslationStatus> get() = translator.status

    fun start() {
        translator.setLanguage(store.language.value)
        scope.launch {
            // Al iniciar sesión manda el idioma guardado en el perfil de esa persona.
            session.session.map { it?.user?.profile?.language }.distinctUntilChanged().collect { saved ->
                if (saved != null && saved != store.language.value) apply(saved)
            }
        }
    }

    private suspend fun apply(code: String) {
        val target = languageOf(code).code
        store.set(target)
        translator.setLanguage(target)
    }

    /**
     * El usuario elige un idioma: la app cambia de inmediato y, si hay sesión, queda guardado en su perfil.
     * Devuelve null sin sesión, o el resultado de guardarlo en el servidor.
     */
    suspend fun choose(code: String): ApiResult<Unit>? {
        apply(code)
        if (session.session.value == null) return null
        return when (val r = auth.updateProfile(ProfileBody(language = languageOf(code).code))) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> r
        }
    }

    private val _saveError = MutableStateFlow<ApiError?>(null)
    /** Error al guardar el idioma en el perfil (el cambio igual queda en este teléfono). */
    val saveError: StateFlow<ApiError?> get() = _saveError

    /** Igual que [choose], pero sigue aunque se cierre la pantalla desde donde se eligió. */
    fun select(code: String) {
        scope.launch {
            _saveError.value = null
            val r = choose(code)
            if (r is ApiResult.Failure) _saveError.value = r.error
        }
    }

    /** Vuelve a intentar la descarga del paquete del idioma. */
    fun retry() = translator.retry()
}
