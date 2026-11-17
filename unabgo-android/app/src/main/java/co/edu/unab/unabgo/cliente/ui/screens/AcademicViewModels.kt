package co.edu.unab.unabgo.cliente.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.data.repo.AuthRepository
import co.edu.unab.unabgo.cliente.data.repo.CatalogRepository
import co.edu.unab.unabgo.cliente.data.repo.SubjectRepository
import co.edu.unab.unabgo.cliente.data.session.SessionStore
import co.edu.unab.unabgo.cliente.ui.common.FormState
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.common.reload
import co.edu.unab.unabgo.cliente.ui.common.submit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Número escrito por el usuario (acepta coma decimal). Null si está vacío o no es un número. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

// ------------------------------------------------------------------------------------------------ materias

class SubjectsViewModel(private val repo: SubjectRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<SubjectDto>>>(UiState.Loading)
    val state: StateFlow<UiState<List<SubjectDto>>> get() = _state
    private val _action = MutableStateFlow(FormState())
    val action: StateFlow<FormState> get() = _action

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _state.reload { repo.list() } }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            _action.submit({ repo.delete(id) }) {
                val current = _state.value
                if (current is UiState.Ready) _state.value = current.copy(data = current.data.filterNot { it.id == id })
            }
        }
    }

    fun dismissAction() {
        _action.value = FormState()
    }
}

data class SubjectDetail(val subject: SubjectDto, val summary: GradeSummaryDto?, val summaryError: ApiError? = null)

class SubjectDetailViewModel(private val repo: SubjectRepository, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow<UiState<SubjectDetail>>(UiState.Loading)
    val state: StateFlow<UiState<SubjectDetail>> get() = _state
    private val _categoryForm = MutableStateFlow(FormState())
    val categoryForm: StateFlow<FormState> get() = _categoryForm
    private val _itemForm = MutableStateFlow(FormState())
    val itemForm: StateFlow<FormState> get() = _itemForm

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _state.reload { load() } }
    }

    private suspend fun load(): ApiResult<SubjectDetail> {
        val subject = repo.get(id)
        if (subject is ApiResult.Failure) return subject
        val summary = repo.summary(id)
        return ApiResult.Success(
            SubjectDetail(
                (subject as ApiResult.Success).data,
                (summary as? ApiResult.Success)?.data,
                (summary as? ApiResult.Failure)?.error
            )
        )
    }

    fun addCategory(name: String, weight: String) {
        viewModelScope.launch {
            val value = parseDecimal(weight)
            if (weight.isNotBlank() && value == null) {
                _categoryForm.value = FormState(error = ApiError(400, "VALIDATION_ERROR", "Revisa los datos.", mapOf("weight" to "Escribe un número.")))
                return@launch
            }
            _categoryForm.submit({ repo.addCategory(id, CategoryBody(name.trim(), value)) }) { refreshQuietly() }
        }
    }

    fun addItem(categoryId: Long, name: String, score: String, weight: String, date: String) {
        viewModelScope.launch {
            val s = parseDecimal(score)
            val w = parseDecimal(weight)
            val bad = buildMap {
                if (score.isNotBlank() && s == null) put("score", "Escribe un número, por ejemplo 3.5.")
                if (weight.isNotBlank() && w == null) put("weight", "Escribe un número.")
            }
            if (bad.isNotEmpty()) {
                _itemForm.value = FormState(error = ApiError(400, "VALIDATION_ERROR", "Revisa los datos.", bad))
                return@launch
            }
            _itemForm.submit({ repo.addItem(categoryId, ItemBody(name.trim(), s, w, date.trim().ifEmpty { null })) }) { refreshQuietly() }
        }
    }

    private suspend fun refreshQuietly() {
        (load() as? ApiResult.Success)?.let { _state.value = UiState.Ready(it.data) }
    }

    fun resetForms() {
        _categoryForm.value = FormState()
        _itemForm.value = FormState()
    }
}

/** Un bloque de horario mientras se edita (los textos se validan en el servidor). */
data class BlockDraft(val day: Int = 1, val start: String = "07:00", val end: String = "09:00", val room: String = "")

data class SubjectDraft(
    val name: String = "",
    val credits: String = "3",
    val teacher: String = "",
    val color: String = COLORS.first(),
    val blocks: List<BlockDraft> = emptyList()
) {
    companion object {
        val COLORS = listOf("#E9A427", "#2E7D32", "#1565C0", "#C62828", "#6A1B9A", "#00838F")
    }
}

fun SubjectDraft.toBody() = SubjectBody(
    name = name.trim(),
    credits = credits.trim().toIntOrNull(),
    teacher = teacher.trim().ifEmpty { null },
    color = color,
    blocks = blocks.map { BlockBody(it.day, it.start.trim(), it.end.trim(), it.room.trim().ifEmpty { null }) }
)

fun SubjectDto.toDraft() = SubjectDraft(
    name = name,
    credits = credits.toString(),
    teacher = teacher.orEmpty(),
    color = color,
    blocks = blocks.map { BlockDraft(it.dayOfWeek, it.startTime.take(5), it.endTime.take(5), it.room.orEmpty()) }
)

/** Crea (id nulo) o edita una materia con sus bloques de horario. */
class SubjectFormViewModel(private val repo: SubjectRepository, private val id: Long?) : ViewModel() {
    private val _loading = MutableStateFlow<UiState<Unit>>(if (id == null) UiState.Ready(Unit) else UiState.Loading)
    val loading: StateFlow<UiState<Unit>> get() = _loading
    private val _draft = MutableStateFlow(SubjectDraft())
    val draft: StateFlow<SubjectDraft> get() = _draft
    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> get() = _form

    init {
        if (id != null) load()
    }

    fun load() {
        val subjectId = id ?: return
        viewModelScope.launch {
            _loading.value = UiState.Loading
            _loading.value = when (val r = repo.get(subjectId)) {
                is ApiResult.Success -> {
                    _draft.value = r.data.toDraft()
                    UiState.Ready(Unit)
                }
                is ApiResult.Failure -> UiState.Failed(r.error)
            }
        }
    }

    fun update(draft: SubjectDraft) {
        _draft.value = draft
    }

    fun save() {
        viewModelScope.launch {
            val body = _draft.value.toBody()
            _form.submit({ if (id == null) repo.create(body) else repo.update(id, body) })
        }
    }
}

// ------------------------------------------------------------------------------------------------ perfil

val REMINDERS = listOf(0 to "Sin recordatorio", 30 to "30 minutos antes", 60 to "1 hora antes")

/** Perfil académico: carrera, semestre, código y recordatorio. El idioma tiene su propia pantalla. */
class ProfileViewModel(
    private val auth: AuthRepository,
    store: SessionStore
) : ViewModel() {
    val session = store.session
    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> get() = _form

    init {
        viewModelScope.launch { auth.refreshMe() }
    }

    fun save(programId: Long?, studentCode: String, semester: Int?, reminder: Int) {
        viewModelScope.launch {
            val body = ProfileBody(programId, studentCode.trim().ifEmpty { null }, semester, reminderMinutes = reminder)
            _form.submit({ auth.updateProfile(body) })
        }
    }

    fun dismiss() {
        _form.value = FormState()
    }
}
