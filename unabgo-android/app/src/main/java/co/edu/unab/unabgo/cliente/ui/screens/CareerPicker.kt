package co.edu.unab.unabgo.cliente.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import co.edu.unab.unabgo.cliente.data.api.ApiResult
import co.edu.unab.unabgo.cliente.data.api.ProgramDto
import co.edu.unab.unabgo.cliente.data.api.ProgramSummaryDto
import co.edu.unab.unabgo.cliente.data.api.StudyPlanDto
import co.edu.unab.unabgo.cliente.data.repo.CatalogRepository
import co.edu.unab.unabgo.cliente.data.repo.StudyPlanRepository
import co.edu.unab.unabgo.cliente.i18n.Text
import co.edu.unab.unabgo.cliente.ui.common.PagedLoader
import co.edu.unab.unabgo.cliente.ui.components.*
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ------------------------------------------------------------------------------------------------ modelo

/** Carrera elegida: viene de la lista de carreras o del perfil guardado. */
data class CareerChoice(val id: Long, val name: String, val faculty: String, val level: String?, val studyPlanSlug: String?)

fun ProgramDto.toChoice() = CareerChoice(id, name, faculty, level, studyPlanSlug)
fun ProgramSummaryDto.toChoice() = CareerChoice(id, name, faculty, level, studyPlanSlug)

/** Niveles de las carreras que tienen plan de estudios, en orden académico. */
val CAREER_LEVELS = listOf("Profesional", "Tecnología", "Técnico laboral", "Especialización", "Maestría", "Doctorado")

/** Nivel con el que abre la lista: la mayoría de quienes se registran estudian una carrera profesional. */
const val DEFAULT_CAREER_LEVEL = "Profesional"

const val MAX_SEMESTER = 12

/** Semestres que se pueden elegir: los del plan de la carrera, o de 1 a 12 si todavía no se conoce. */
fun semesterOptions(plan: StudyPlanDto?): Int {
    val n = plan?.takeIf { !it.isText }?.semesters?.size ?: 0
    return if (n in 1..MAX_SEMESTER) n else MAX_SEMESTER
}

/** "sexto", "décimo"... para decir "Vas en sexto semestre". */
fun semesterOrdinal(n: Int): String = when (n) {
    1 -> "primer"
    2 -> "segundo"
    3 -> "tercer"
    4 -> "cuarto"
    5 -> "quinto"
    6 -> "sexto"
    7 -> "séptimo"
    8 -> "octavo"
    9 -> "noveno"
    10 -> "décimo"
    11 -> "undécimo"
    12 -> "duodécimo"
    else -> "$n."
}

private const val CAREER_SEARCH_DEBOUNCE_MS = 350L

/**
 * Lista de carreras con plan de estudios (se carga apenas se abre el registro o el perfil), con búsqueda y filtro
 * por nivel. Al elegir una carrera trae su plan para mostrar cuántos semestres y créditos tiene.
 */
class CareerPickerViewModel(
    private val catalog: CatalogRepository,
    private val plans: StudyPlanRepository,
    initial: CareerChoice? = null
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> get() = _query
    private val _level = MutableStateFlow<String?>(DEFAULT_CAREER_LEVEL)
    val level: StateFlow<String?> get() = _level
    private val _selected = MutableStateFlow(initial)
    val selected: StateFlow<CareerChoice?> get() = _selected
    private val _plan = MutableStateFlow<StudyPlanDto?>(null)
    /** Plan de la carrera elegida (null mientras carga o si no tiene). */
    val plan: StateFlow<StudyPlanDto?> get() = _plan
    private val loader = PagedLoader(viewModelScope) { page -> catalog.careers(page, _query.value.trim().ifEmpty { null }, _level.value) }
    val list = loader.state
    private var searchJob: Job? = null
    private var planJob: Job? = null

    init {
        loader.refresh()
        initial?.let { loadPlan(it) }
    }

    fun onQueryChange(text: String) {
        _query.value = text
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(CAREER_SEARCH_DEBOUNCE_MS)
            loader.refresh()
        }
    }

    /** null es "Todas"; tocar el nivel que ya está elegido lo quita. */
    fun selectLevel(level: String?) {
        _level.value = if (level != null && level == _level.value) null else level
        searchJob?.cancel()
        loader.refresh()
    }

    fun select(choice: CareerChoice) {
        _selected.value = choice
        loadPlan(choice)
    }

    /** Cuando el perfil llega del servidor después de abrir la pantalla. */
    fun syncInitial(choice: CareerChoice?) {
        if (_selected.value == null && choice != null) select(choice)
    }

    private fun loadPlan(choice: CareerChoice) {
        planJob?.cancel()
        _plan.value = null
        val slug = choice.studyPlanSlug ?: return
        planJob = viewModelScope.launch {
            (plans.get(slug) as? ApiResult.Success)?.data?.let { if (_selected.value?.id == choice.id) _plan.value = it }
        }
    }

    fun loadMore() = loader.loadMore()
    fun refresh() = loader.refresh()
}

// ------------------------------------------------------------------------------------------------ interfaz

/** Campo "Carrera": muestra la elegida y abre la lista completa para escoger otra. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareerField(vm: CareerPickerViewModel, enabled: Boolean = true, error: String? = null) {
    val selected by vm.selected.collectAsStateWithLifecycle()
    val plan by vm.plan.collectAsStateWithLifecycle()
    val p = LocalRolePalette.current
    var open by remember { mutableStateOf(false) }
    val borderColor = if (error != null) MaterialTheme.colorScheme.error else if (selected != null) p.primary else MaterialTheme.colorScheme.outline
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).border(BorderStroke(if (selected != null) 2.dp else 1.dp, borderColor), MaterialTheme.shapes.medium)
                .clickable(enabled = enabled) { open = true }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(p.container), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.List, contentDescription = null, tint = p.onContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Carrera", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected?.name ?: "Elige tu carrera", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                selected?.let { Text(it.faculty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Ver carreras")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        AnimatedVisibility(selected != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            CareerPlanPreview(selected, plan)
        }
    }
    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            CareerSheet(vm, selected) { vm.select(it); open = false }
        }
    }
}

@Composable
private fun CareerPlanPreview(selected: CareerChoice?, plan: StudyPlanDto?) {
    val p = LocalRolePalette.current
    val text = when {
        selected == null -> ""
        selected.studyPlanSlug == null -> "Esta carrera todavía no tiene plan de estudios publicado."
        plan == null -> "Cargando el plan de estudios de la carrera."
        plan.isText -> "El plan de estudios de esta carrera está publicado como documento."
        else -> "Plan de estudios de ${plan.semesters.size} semestres y ${formatCredits(plan.totalCredits)} créditos. Lo verás en Mi plan de estudios."
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(p.container).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Check, contentDescription = null, tint = p.onContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = p.onContainer, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CareerSheet(vm: CareerPickerViewModel, selected: CareerChoice?, onPick: (CareerChoice) -> Unit) {
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val p = LocalRolePalette.current
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Elige tu carrera", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(
            "Solo aparecen los programas con plan de estudios publicado en unab.edu.co.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SearchField(query, vm::onQueryChange, "Buscar carrera")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = level == null, onClick = { vm.selectLevel(null) }, label = { Text("Todos los niveles") })
            CAREER_LEVELS.forEach { k -> FilterChip(selected = level == k, onClick = { vm.selectLevel(k) }, label = { Text(k) }) }
        }
        Text(if (list.total == 1L) "1 programa" else "${list.total} programas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            when {
                list.loading -> item { SkeletonList() }
                list.items.isEmpty() && list.error != null -> item { ErrorView(list.error!!, vm::refresh) }
                list.items.isEmpty() -> item { EmptyView("No hay programas con esa búsqueda.") }
                else -> {
                    items(list.items.size) { i ->
                        val c = list.items[i].toChoice()
                        val isSelected = c.id == selected?.id
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .background(if (isSelected) p.container else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onPick(c) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(c.faculty, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            c.level?.let { AppChip(it, Modifier.padding(start = 8.dp)) }
                            if (isSelected) Icon(Icons.Default.Check, contentDescription = "Elegida", tint = p.accentText, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    if (list.loadingMore) item { SkeletonCard() }
                    if (list.canLoadMore) item {
                        LaunchedEffect(list.page) { vm.loadMore() }
                        AppOutlinedButton(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) { Text("Cargar más") }
                    }
                }
            }
        }
    }
}

/** Semestre actual: círculos del 1 al último semestre del plan; el elegido queda resaltado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SemesterPicker(value: Int?, onChange: (Int) -> Unit, max: Int = MAX_SEMESTER, enabled: Boolean = true, error: String? = null) {
    val p = LocalRolePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (value != null) "Semestre actual: ${semesterOrdinal(value)} semestre" else "¿En qué semestre vas?",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..max).forEach { n ->
                val isSelected = n == value
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (isSelected) p.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = enabled) { onChange(n) },
                    contentAlignment = Alignment.Center
                ) {
                    Text("$n", color = if (isSelected) p.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}
