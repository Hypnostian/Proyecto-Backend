package co.edu.unab.unabgo.cliente.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unab.unabgo.cliente.data.api.FaqBody
import co.edu.unab.unabgo.cliente.data.api.FaqDto
import co.edu.unab.unabgo.cliente.data.api.SyncResultDto
import co.edu.unab.unabgo.cliente.data.repo.AdminRepository
import co.edu.unab.unabgo.cliente.data.repo.FaqRepository
import co.edu.unab.unabgo.cliente.ui.common.FormState
import co.edu.unab.unabgo.cliente.ui.common.PagedLoader
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.common.reload
import co.edu.unab.unabgo.cliente.ui.common.submit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ------------------------------------------------------------------------------------------------ preguntas de Banu (gestor)

class FaqsViewModel(private val repo: FaqRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<FaqDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<FaqDto>>> get() = _state
    private val _action = MutableStateFlow(FormState())
    val action: StateFlow<FormState> get() = _action

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _state.reload { repo.list() } }
    }

    /** DELETE del backend: la pregunta queda inactiva y Banu deja de usarla. */
    fun deactivate(id: Long) {
        viewModelScope.launch {
            _action.submit({ repo.deactivate(id) }) {
                val current = _state.value
                if (current is UiState.Ready) {
                    _state.value = current.copy(data = current.data.map { if (it.id == id) it.copy(active = false) else it })
                }
            }
        }
    }

    fun dismissAction() {
        _action.value = FormState()
    }
}

class FaqFormViewModel(private val repo: FaqRepository) : ViewModel() {
    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> get() = _form

    fun reset() {
        _form.value = FormState()
    }

    fun save(id: Long?, question: String, keywords: String, answer: String, sourceUrl: String, active: Boolean) {
        viewModelScope.launch {
            _form.submit({ repo.save(id, FaqBody(question.trim(), keywords.trim(), answer.trim(), sourceUrl.trim(), active)) })
        }
    }
}

// ------------------------------------------------------------------------------------------------ administrador

class AdminViewModel(private val repo: AdminRepository) : ViewModel() {
    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> get() = _role
    private val loader = PagedLoader(viewModelScope) { page -> repo.users(page, _role.value) }
    val users = loader.state
    private val _roleAction = MutableStateFlow(FormState())
    val roleAction: StateFlow<FormState> get() = _roleAction
    private val _sync = MutableStateFlow(FormState())
    val sync: StateFlow<FormState> get() = _sync
    private val _syncResult = MutableStateFlow<SyncResultDto?>(null)
    val syncResult: StateFlow<SyncResultDto?> get() = _syncResult

    init {
        loader.refresh()
    }

    fun refresh() = loader.refresh()
    fun loadMore() = loader.loadMore()

    fun filter(role: String?) {
        _role.value = role
        loader.refresh()
    }

    /** El backend responde 403 si el administrador intenta cambiar su propio rol. */
    fun changeRole(userId: Long, role: String) {
        viewModelScope.launch { _roleAction.submit({ repo.changeRole(userId, role) }) { loader.refresh() } }
    }

    fun dismissRoleAction() {
        _roleAction.value = FormState()
    }

    fun syncPrograms() {
        viewModelScope.launch {
            _syncResult.value = null
            _sync.submit({ repo.syncPrograms() }) { _syncResult.value = it }
        }
    }
}
