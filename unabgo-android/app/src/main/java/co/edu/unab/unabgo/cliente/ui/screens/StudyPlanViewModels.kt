package co.edu.unab.unabgo.cliente.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.unab.unabgo.cliente.data.api.ApiError
import co.edu.unab.unabgo.cliente.data.api.ApiResult
import co.edu.unab.unabgo.cliente.data.api.StudyPlanDto
import co.edu.unab.unabgo.cliente.data.repo.AuthRepository
import co.edu.unab.unabgo.cliente.data.repo.StudyPlanRepository
import co.edu.unab.unabgo.cliente.data.session.SessionStore
import co.edu.unab.unabgo.cliente.ui.common.PagedLoader
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.common.reload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Locale

/** Etiqueta de un nivel tal como llega del servidor ("Profesional", "pregrado"...): primera letra en mayúscula. */
fun levelLabel(level: String?): String? {
    if (level.isNullOrBlank()) return null
    return level.trim().replaceFirstChar { it.uppercase() }
}

/** Niveles que publica el backend, en orden académico: así los filtros salen completos desde el inicio. */
val STANDARD_LEVELS = listOf("Profesional", "Tecnología", "Técnico", "Especialización", "Maestría", "Doctorado")

private fun levelRank(level: String): Int =
    STANDARD_LEVELS.indexOfFirst { it.equals(level, ignoreCase = true) }.let { if (it < 0) STANDARD_LEVELS.size else it }

/**
 * Suma los niveles nuevos a los conocidos sin repetir (se comparan sin distinguir mayúsculas). Los niveles
 * estándar van en orden académico y los demás después, por orden alfabético.
 */
fun mergeLevels(known: List<String>, found: List<String?>): List<String> {
    val result = known.toMutableList()
    found.forEach { l ->
        val v = l?.trim().orEmpty()
        if (v.isNotEmpty() && result.none { it.equals(v, ignoreCase = true) }) result += v
    }
    return result.sortedWith(compareBy<String>({ levelRank(it) }, { it.lowercase() }))
}

/** 160.0 se muestra como "160" y 2.5 como "2.5". */
fun formatCredits(value: Double?): String = when {
    value == null -> "-"
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> String.format(Locale.US, "%.1f", value)
}

/** Pregunta de acceso rápido a Banu desde el detalle de un plan. */
fun firstSemesterQuestion(programName: String) = semesterQuestion(programName, 1)

/** "¿Qué materias tiene el sexto semestre de Ingeniería de Sistemas?" */
fun semesterQuestion(programName: String, semester: Int) = "¿Qué materias tiene el ${semesterOrdinal(semester)} semestre de $programName?"

/** Avance del estudiante dentro del plan de su carrera. */
data class SemesterProgress(
    val current: Int,
    val total: Int,
    /** Créditos de los semestres anteriores al actual. */
    val creditsBefore: Double,
    val totalCredits: Double?,
    /** Semestres que siguen después del actual. */
    val remaining: Int
)

/** null si no hay semestre o el plan no tiene semestres; un semestre mayor que los del plan se toma como el último. */
fun semesterProgress(plan: StudyPlanDto, current: Int?): SemesterProgress? {
    if (current == null || current < 1 || plan.isText || plan.semesters.isEmpty()) return null
    val total = plan.semesters.size
    val c = current.coerceAtMost(total)
    val before = plan.semesters.filter { it.number < c }.sumOf { semesterCredits(it) }
    return SemesterProgress(c, total, before, plan.totalCredits, total - c)
}

private const val PLAN_SEARCH_DEBOUNCE_MS = 400L

/** Lista de planes de estudio con búsqueda (con espera de 400 ms) y filtro por nivel. */
class StudyPlansViewModel(private val repo: StudyPlanRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> get() = _query
    private val _level = MutableStateFlow<String?>(null)
    val level: StateFlow<String?> get() = _level
    private val _levels = MutableStateFlow(STANDARD_LEVELS)
    /** Niveles estándar más los que aparezcan en los datos cargados; no desaparecen al filtrar. */
    val levels: StateFlow<List<String>> get() = _levels
    private val loader = PagedLoader(viewModelScope) { page ->
        repo.list(page, _query.value.trim().ifEmpty { null }, _level.value).also { r ->
            if (r is co.edu.unab.unabgo.cliente.data.api.ApiResult.Success) _levels.value = mergeLevels(_levels.value, r.data.map { it.level })
        }
    }
    val list = loader.state
    private var searchJob: Job? = null

    init {
        loader.refresh()
    }

    fun onQueryChange(text: String) {
        _query.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(PLAN_SEARCH_DEBOUNCE_MS)
            loader.refresh()
        }
    }

    /** Cambia el nivel (null es "Todos") y recarga de inmediato; si se toca el mismo nivel, lo quita. */
    fun selectLevel(level: String?) {
        _level.value = if (level != null && level.equals(_level.value, ignoreCase = true)) null else level
        searchJob?.cancel()
        loader.refresh()
    }

    fun refresh() = loader.refresh()
    fun loadMore() = loader.loadMore()
}

class StudyPlanDetailViewModel(private val repo: StudyPlanRepository, private val slug: String) : ViewModel() {
    private val _state = MutableStateFlow<UiState<StudyPlanDto>>(UiState.Loading)
    val state: StateFlow<UiState<StudyPlanDto>> get() = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _state.reload { repo.get(slug) } }
    }
}

/** Lo que muestra Mi plan de estudios: la carrera del perfil, el semestre y el plan (null si no tiene). */
data class MyPlanData(val career: CareerChoice?, val semester: Int?, val plan: StudyPlanDto?)

/** Plan de la carrera elegida en el perfil. Refresca el perfil primero para tomar la carrera más reciente. */
class MyPlanViewModel(
    private val auth: AuthRepository,
    private val repo: StudyPlanRepository,
    private val store: SessionStore
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<MyPlanData>>(UiState.Loading)
    val state: StateFlow<UiState<MyPlanData>> get() = _state

    init {
        refresh()
        // Al cambiar la carrera o el semestre en el perfil, el plan se actualiza solo.
        viewModelScope.launch {
            store.session.map { it?.user?.profile?.let { p -> p.program?.id to p.semester } }.distinctUntilChanged().drop(1)
                .collect { _state.reload { load(fromServer = false) } }
        }
    }

    fun refresh() {
        viewModelScope.launch { _state.reload { load(fromServer = true) } }
    }

    private suspend fun load(fromServer: Boolean): ApiResult<MyPlanData> {
        // Sin conexión con el servidor se usa el perfil guardado en el teléfono.
        val user = if (!fromServer) store.session.value?.user ?: return ApiResult.Failure(ApiError.network("Sin sesión."))
        else when (val me = auth.refreshMe()) {
            is ApiResult.Success -> me.data
            is ApiResult.Failure -> store.session.value?.user ?: return me
        }
        val career = user.profile?.program?.toChoice()
        val semester = user.profile?.semester
        val slug = career?.studyPlanSlug ?: return ApiResult.Success(MyPlanData(career, semester, null))
        return when (val r = repo.get(slug)) {
            is ApiResult.Success -> ApiResult.Success(MyPlanData(career, semester, r.data))
            is ApiResult.Failure -> r
        }
    }
}
