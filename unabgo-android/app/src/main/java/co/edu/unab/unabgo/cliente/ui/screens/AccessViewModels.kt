package co.edu.unab.unabgo.cliente.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unab.unabgo.cliente.data.api.ApiError
import co.edu.unab.unabgo.cliente.data.api.ApiResult
import co.edu.unab.unabgo.cliente.data.repo.AuthRepository
import co.edu.unab.unabgo.cliente.data.repo.RegisterForm
import co.edu.unab.unabgo.cliente.data.session.Roles
import co.edu.unab.unabgo.cliente.data.session.SessionStore
import co.edu.unab.unabgo.cliente.data.session.SettingsStore
import co.edu.unab.unabgo.cliente.data.session.normalizeBaseUrl
import co.edu.unab.unabgo.cliente.ui.common.FormState
import co.edu.unab.unabgo.cliente.ui.common.submit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Cuenta de prueba que el backend crea con el seeder (ver README del backend). */
data class DemoAccount(val label: String, val email: String, val role: String)

object DemoAccounts {
    const val PASSWORD = "Unab2026*"
    val all = listOf(
        DemoAccount("Estudiante", "demo.estudiante@unab.edu.co", Roles.STUDENT),
        DemoAccount("Gestor (Bienestar)", "demo.bienestar@unab.edu.co", Roles.MANAGER),
        DemoAccount("Gestor (Transporte)", "demo.transporte@unab.edu.co", Roles.MANAGER),
        DemoAccount("Administrador", "demo.admin@unab.edu.co", Roles.ADMIN)
    )
}

/** Sesión actual y cierre de sesión (llama a la API y borra el token del teléfono). */
class SessionViewModel(private val auth: AuthRepository, private val store: SessionStore) : ViewModel() {
    val session = store.session
    private val _loggingOut = MutableStateFlow(false)
    val loggingOut: StateFlow<Boolean> get() = _loggingOut

    fun logout() {
        if (_loggingOut.value) return
        viewModelScope.launch {
            _loggingOut.value = true
            auth.logout()
            _loggingOut.value = false
        }
    }
}

/**
 * Inicio de sesión y registro. El éxito se refleja en la sesión guardada; aquí quedan los errores por campo.
 * [language] es el idioma en que está la app: con él se crea el perfil de quien se registra.
 */
class AuthViewModel(private val auth: AuthRepository, private val language: () -> String = { "es" }) : ViewModel() {
    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> get() = _form

    fun login(email: String, password: String) {
        viewModelScope.launch { _form.submit({ auth.login(email, password) }) }
    }

    /** Registro con la carrera y el semestre: sin carrera no se envía, porque de ella sale el plan de estudios propio. */
    fun register(name: String, email: String, password: String, confirmation: String, career: CareerChoice?, semester: Int?) {
        if (career == null || semester == null) {
            _form.value = FormState(
                error = ApiError(
                    400, "VALIDATION_ERROR", "Revisa los datos.",
                    buildMap {
                        if (career == null) put("program_id", "Elige tu carrera.")
                        if (semester == null) put("semester", "Elige el semestre en que vas.")
                    }
                )
            )
            return
        }
        viewModelScope.launch {
            _form.submit({ auth.register(RegisterForm(name, email, password, confirmation, career.id, semester, language())) })
        }
    }

    fun clear() {
        _form.value = FormState()
    }
}

data class SettingsUiState(
    val saved: Boolean = false,
    val urlError: String? = null,
    val testing: Boolean = false,
    val testMessage: String? = null,
    val testOk: Boolean? = null
)

/** Dirección del backend y prueba de conexión (GET /health). */
class SettingsViewModel(
    private val settings: SettingsStore,
    private val auth: AuthRepository,
    private val allowCleartext: Boolean
) : ViewModel() {
    val baseUrl = settings.baseUrl
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> get() = _state

    fun save(input: String) {
        val normalized = normalizeBaseUrl(input, allowCleartext)
        if (normalized == null) {
            _state.value = SettingsUiState(
                urlError = if (allowCleartext) "Escribe una dirección válida, por ejemplo http://10.0.2.2:8080."
                else "Escribe una dirección https válida; esta versión no permite HTTP sin cifrar."
            )
            return
        }
        viewModelScope.launch {
            settings.setBaseUrl(normalized)
            _state.value = SettingsUiState(saved = true)
        }
    }

    /** Guarda la dirección escrita y llama a /health para mostrar si el backend responde. */
    fun test(input: String) {
        val normalized = normalizeBaseUrl(input, allowCleartext)
        if (normalized == null) {
            save(input)
            return
        }
        viewModelScope.launch {
            settings.setBaseUrl(normalized)
            _state.value = SettingsUiState(testing = true)
            _state.value = when (val r = auth.health()) {
                is ApiResult.Success -> SettingsUiState(
                    testOk = r.data.status == "ok",
                    testMessage = "Conexión correcta. Estado ${r.data.status}, base de datos ${r.data.database}, versión ${r.data.version}."
                )
                is ApiResult.Failure -> SettingsUiState(testOk = false, testMessage = r.error.describe())
            }
        }
    }

    private fun ApiError.describe() = if (isNetwork) message else "El servidor respondió: $message"
}
