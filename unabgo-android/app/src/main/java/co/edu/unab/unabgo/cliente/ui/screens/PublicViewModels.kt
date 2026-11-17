package co.edu.unab.unabgo.cliente.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.data.repo.*
import co.edu.unab.unabgo.cliente.ui.common.FormState
import co.edu.unab.unabgo.cliente.ui.common.PagedLoader
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.common.reload
import co.edu.unab.unabgo.cliente.ui.common.submit
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

val ANNOUNCEMENT_CATEGORIES = listOf("academico" to "Académico", "bienestar" to "Bienestar", "transporte" to "Transporte", "evento" to "Evento")

// ------------------------------------------------------------------------------------------------ inicio

/** Tarjeta "Mi próxima clase" (GET /schedule/next). Estado Ready con null significa que no hay clases. */
class NextClassViewModel(private val repo: SubjectRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<NextClassDto?>>(UiState.Loading)
    val state: StateFlow<UiState<NextClassDto?>> get() = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _state.reload { repo.nextClass() } }
    }
}

// ------------------------------------------------------------------------------------------------ avisos

class AnnouncementsViewModel(private val repo: AnnouncementRepository) : ViewModel() {
    private val _category = MutableStateFlow<String?>(null)
    val category: StateFlow<String?> get() = _category
    private val loader = PagedLoader(viewModelScope) { page -> repo.list(page, _category.value) }
    val list = loader.state
    private val _action = MutableStateFlow(FormState())
    val action: StateFlow<FormState> get() = _action

    init {
        loader.refresh()
    }

    fun refresh() = loader.refresh()
    fun loadMore() = loader.loadMore()

    fun filter(category: String?) {
        _category.value = category
        loader.refresh()
    }

    fun delete(id: Long) {
        viewModelScope.launch { _action.submit({ repo.delete(id) }) { loader.remove { it.id == id } } }
    }

    fun dismissAction() {
        _action.value = FormState()
    }
}

/** Crear o editar un aviso (solo gestor o administrador). */
class AnnouncementFormViewModel(private val repo: AnnouncementRepository) : ViewModel() {
    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> get() = _form

    fun reset() {
        _form.value = FormState()
    }

    fun save(id: Long?, title: String, body: String, category: String, publish: Boolean) {
        viewModelScope.launch {
            _form.submit({ repo.save(id, AnnouncementBody(title.trim(), body.trim(), category, publish)) })
        }
    }
}

// ------------------------------------------------------------------------------------------------ profesores y programas

private const val SEARCH_DEBOUNCE_MS = 400L

class ProfessorsViewModel(private val repo: CatalogRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> get() = _query
    private val loader = PagedLoader(viewModelScope) { page -> repo.professors(page, _query.value.trim().ifEmpty { null }) }
    val list = loader.state
    private var searchJob: Job? = null

    init {
        loader.refresh()
    }

    /** Busca cuando el usuario deja de escribir durante 400 ms. */
    fun onQueryChange(text: String) {
        _query.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loader.refresh()
        }
    }

    fun refresh() = loader.refresh()
    fun loadMore() = loader.loadMore()
}

class ProfessorDetailViewModel(private val repo: CatalogRepository, private val slug: String) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ProfessorDto>>(UiState.Loading)
    val state: StateFlow<UiState<ProfessorDto>> get() = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _state.reload { repo.professor(slug) } }
    }
}

class ProgramsViewModel(private val repo: CatalogRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> get() = _query
    private val loader = PagedLoader(viewModelScope) { page -> repo.programs(page, _query.value.trim().ifEmpty { null }) }
    val list = loader.state
    private var searchJob: Job? = null

    init {
        loader.refresh()
    }

    fun onQueryChange(text: String) {
        _query.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            loader.refresh()
        }
    }

    fun refresh() = loader.refresh()
    fun loadMore() = loader.loadMore()
}

class ProgramDetailViewModel(private val repo: CatalogRepository, private val slug: String) : ViewModel() {
    private val _state = MutableStateFlow<UiState<ProgramDto>>(UiState.Loading)
    val state: StateFlow<UiState<ProgramDto>> get() = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _state.reload { repo.program(slug) } }
    }
}

// ------------------------------------------------------------------------------------------------ buses

data class BusUi(
    val routes: List<BusRouteDto> = emptyList(),
    val favoriteIds: Set<Long> = emptySet(),
    val live: List<BusLiveDto> = emptyList(),
    /** El GPS en vivo puede responder 503; no impide ver las rutas. */
    val liveError: ApiError? = null
)

class BusViewModel(private val repo: BusRepository, private val canFavorite: Boolean) : ViewModel() {
    private val _state = MutableStateFlow<UiState<BusUi>>(UiState.Loading)
    val state: StateFlow<UiState<BusUi>> get() = _state
    private val _action = MutableStateFlow(FormState())
    val action: StateFlow<FormState> get() = _action

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.reload {
                when (val routes = repo.routes()) {
                    is ApiResult.Failure -> routes
                    is ApiResult.Success -> {
                        val live = repo.live()
                        val favorites = if (canFavorite) (repo.favorites() as? ApiResult.Success)?.data.orEmpty() else emptyList()
                        ApiResult.Success(
                            BusUi(
                                routes = routes.data,
                                favoriteIds = favorites.map { it.id }.toSet(),
                                live = (live as? ApiResult.Success)?.data.orEmpty(),
                                liveError = (live as? ApiResult.Failure)?.error
                            )
                        )
                    }
                }
            }
        }
    }

    fun toggleFavorite(routeId: Long) {
        val current = (_state.value as? UiState.Ready)?.data ?: return
        viewModelScope.launch {
            val isFavorite = routeId in current.favoriteIds
            _action.submit({
                if (isFavorite) repo.removeFavorite(routeId) else repo.addFavorite(routeId).map { }
            }) {
                val now = (_state.value as? UiState.Ready) ?: return@submit
                val ids = if (isFavorite) now.data.favoriteIds - routeId else now.data.favoriteIds + routeId
                _state.value = now.copy(data = now.data.copy(favoriteIds = ids))
            }
        }
    }

    fun saveRoute(id: Long, name: String, color: String, active: Boolean, stopIds: List<Long>) {
        viewModelScope.launch {
            _routeForm.submit({ repo.updateRoute(id, BusRouteBody(name.trim(), color.trim(), active, stopIds)) }) { refresh() }
        }
    }

    private val _routeForm = MutableStateFlow(FormState())
    val routeForm: StateFlow<FormState> get() = _routeForm

    fun resetRouteForm() {
        _routeForm.value = FormState()
    }

    fun dismissAction() {
        _action.value = FormState()
    }
}

// ------------------------------------------------------------------------------------------------ Banu

sealed interface ChatMessage {
    data class FromUser(val text: String) : ChatMessage
    data class FromBanu(val answer: BanuAnswerDto) : ChatMessage
    data class Problem(val text: String) : ChatMessage
}

class BanuViewModel(private val repo: BanuRepository) : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> get() = _messages
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> get() = _sending

    fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _sending.value) return
        _messages.value += ChatMessage.FromUser(text)
        _sending.value = true
        viewModelScope.launch {
            _messages.value += when (val r = repo.ask(text)) {
                is ApiResult.Success -> ChatMessage.FromBanu(r.data)
                is ApiResult.Failure -> ChatMessage.Problem(friendly(r.error))
            }
            _sending.value = false
        }
    }

    /** Pregunta que llega desde otra pantalla (por ejemplo un plan de estudios); no se repite si ya hay conversación. */
    fun askInitial(question: String) {
        if (_messages.value.isEmpty()) ask(question)
    }

    private fun friendly(e: ApiError) = when {
        e.isTooManyRequests -> "Preguntaste muchas veces seguidas. Espera un minuto y vuelve a intentar."
        e.isUnavailable -> "Banu no pudo responder ahora porque la inteligencia artificial no está disponible. Prueba con una pregunta frecuente, por ejemplo de matrículas o de horarios."
        e.isValidation -> e.fields.values.first()
        else -> e.userMessage
    }
}
