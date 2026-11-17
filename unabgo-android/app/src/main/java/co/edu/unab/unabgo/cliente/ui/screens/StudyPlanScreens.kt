package co.edu.unab.unabgo.cliente.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unab.unabgo.cliente.data.api.StudyPlanDto
import co.edu.unab.unabgo.cliente.data.api.StudyPlanSemesterDto
import co.edu.unab.unabgo.cliente.i18n.Text
import co.edu.unab.unabgo.cliente.ui.components.*
import co.edu.unab.unabgo.cliente.ui.theme.LocalReduceMotion
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette

// ------------------------------------------------------------------------------------------------ lista

@Composable
fun StudyPlansScreen(
    vm: StudyPlansViewModel,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    myCareer: CareerChoice? = null,
    onOpenMine: () -> Unit = {}
) {
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val level by vm.level.collectAsStateWithLifecycle()
    val levels by vm.levels.collectAsStateWithLifecycle()
    val p = LocalRolePalette.current
    AppScaffold(title = "Explorar planes de estudio", onBack = onBack) { padding ->
        PagedContent(
            list, vm::refresh, vm::loadMore, padding, "No se encontraron planes de estudio con esa búsqueda.",
            header = {
                if (myCareer?.studyPlanSlug != null) item { MyCareerShortcut(myCareer, onOpenMine) }
                item {
                    Text(
                        "Planes de estudio de todos los programas de la UNAB. Toca uno para ver sus semestres, materias y créditos.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { SearchField(query, vm::onQueryChange, "Buscar por programa") }
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = level == null, onClick = { vm.selectLevel(null) }, label = { Text("Todos los niveles") })
                        levels.forEach { k ->
                            FilterChip(selected = level.equals(k, ignoreCase = true), onClick = { vm.selectLevel(k) }, label = { Text(levelLabel(k).orEmpty()) })
                        }
                    }
                }
                item { Text(if (list.total == 1L) "1 plan" else "${list.total} planes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        ) { plan ->
            AppCard(onClick = { onOpen(plan.slug) }, accent = p.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(plan.programName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (plan.slug == myCareer?.studyPlanSlug) AppChip("Tu carrera", container = BRAND_ORANGE, content = BRAND_INK)
                            levelLabel(plan.level)?.let { AppChip(it) }
                            Text(
                                if (plan.structure == "texto" || plan.semestersCount == 0) "Plan en texto"
                                else "${plan.semestersCount} ${if (plan.semestersCount == 1) "semestre" else "semestres"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (plan.totalCredits != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp)) {
                            Text(formatCredits(plan.totalCredits), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = p.accentText)
                            Text("créditos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Acceso a la carrera propia desde Explorar planes. */
@Composable
private fun MyCareerShortcut(career: CareerChoice, onOpen: () -> Unit) {
    val p = LocalRolePalette.current
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brush.linearGradient(p.gradient)).clickable(onClick = onOpen)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Tu carrera", color = p.onGradient.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                Text(career.name, color = p.onGradient, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            Surface(shape = RoundedCornerShape(50), color = BRAND_ORANGE) {
                Text("Ver mi plan", color = BRAND_INK, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
    }
}

/** Naranja institucional y el texto oscuro que va sobre él (manual de marca UNAB). */
val BRAND_ORANGE = Color(0xFFF79800)
val BRAND_INK = Color(0xFF2F024C)

// ------------------------------------------------------------------------------------------------ mi plan

/** Plan de la carrera del estudiante, con su semestre actual resaltado. */
@Composable
fun MyPlanScreen(
    vm: MyPlanViewModel,
    onBack: () -> Unit,
    onChooseCareer: () -> Unit,
    onExplore: () -> Unit,
    onOpenProgram: (String) -> Unit,
    onAskBanu: (String) -> Unit
) {
    val state by vm.state.collectAsStateWithLifecycle()
    AppScaffold(title = "Mi plan de estudios", onBack = onBack) { padding ->
        StateContent(state, vm::refresh, padding) { data ->
            when {
                data.career == null -> NoCareerCard(
                    "Todavía no eliges tu carrera",
                    "Elige tu carrera y tu semestre en tu perfil para ver aquí su plan de estudios.",
                    "Elegir mi carrera", onChooseCareer, onExplore
                )
                data.plan == null -> NoCareerCard(
                    data.career.name,
                    "Esta carrera todavía no tiene plan de estudios publicado en unab.edu.co. Puedes cambiarla en tu perfil.",
                    "Cambiar de carrera", onChooseCareer, onExplore
                )
                else -> {
                    PlanBody(data.plan, data.semester, onOpenProgram, onAskBanu)
                    AppOutlinedButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) { Text("Explorar los planes de otros programas") }
                }
            }
        }
    }
}

@Composable
private fun NoCareerCard(title: String, text: String, action: String, onAction: () -> Unit, onExplore: () -> Unit) {
    AppCard {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        AppButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) { Text(action) }
        AppTextButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) { Text("Explorar planes de estudio") }
    }
}

// ------------------------------------------------------------------------------------------------ detalle

@Composable
fun StudyPlanDetailScreen(
    vm: StudyPlanDetailViewModel,
    onBack: () -> Unit,
    onOpenProgram: (String) -> Unit,
    onAskBanu: (String) -> Unit,
    /** Semestre del estudiante si este plan es el de su carrera; null en los demás. */
    currentSemesterFor: (String) -> Int? = { null }
) {
    val state by vm.state.collectAsStateWithLifecycle()
    AppScaffold(title = "Plan de estudios", onBack = onBack) { padding ->
        StateContent(state, vm::refresh, padding) { plan ->
            PlanBody(plan, currentSemesterFor(plan.slug), onOpenProgram, onAskBanu)
        }
    }
}

/** Resumen, gráfica de créditos, semestres y enlaces de un plan. Con [current] se resalta el semestre del estudiante. */
@Composable
private fun PlanBody(plan: StudyPlanDto, current: Int?, onOpenProgram: (String) -> Unit, onAskBanu: (String) -> Unit) {
    val uri = LocalUriHandler.current
    val progress = semesterProgress(plan, current)
    PlanOverview(plan, progress)
    if (plan.isText) {
        SectionTitle("Plan de estudios")
        AppCard {
            SelectionContainer {
                Text(plan.text.orEmpty(), style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp))
            }
        }
    } else {
        val credits = plan.semesters.map { it.number to semesterCredits(it) }
        if (credits.isNotEmpty()) {
            SectionTitle("Créditos por semestre")
            AppCard { CreditsChart(credits, progress?.current) }
        }
        SectionTitle("Semestres")
        if (plan.semesters.isEmpty()) EmptyView("Este plan todavía no tiene semestres cargados.")
        SemesterList(plan.semesters, progress?.current)
    }
    plan.planUrl?.ifBlank { null }?.let { url ->
        AppButton(onClick = { uri.openUri(url) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir el plan oficial") }
    } ?: plan.sourceUrl?.ifBlank { null }?.let { url ->
        AppButton(onClick = { uri.openUri(url) }, modifier = Modifier.fillMaxWidth()) { Text("Abrir la fuente oficial") }
    }
    val askSemester = progress?.current ?: 1
    AppOutlinedButton(onClick = { onAskBanu(semesterQuestion(plan.programName, askSemester)) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (progress != null) "Preguntar a Banu por mi semestre" else "Preguntar a Banu por el primer semestre")
    }
    plan.program?.let { prog ->
        AppOutlinedButton(onClick = { onOpenProgram(prog.slug) }, modifier = Modifier.fillMaxWidth()) { Text("Ver el programa") }
    }
}

/** Créditos de un semestre: el que informa el servidor o, si falta, la suma de sus materias. */
fun semesterCredits(s: StudyPlanSemesterDto): Double = s.credits ?: s.courses.sumOf { it.credits ?: 0.0 }

@Composable
private fun PlanOverview(plan: StudyPlanDto, progress: SemesterProgress? = null) {
    val p = LocalRolePalette.current
    Box(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(Brush.linearGradient(p.gradient))
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(plan.programName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = p.onGradient)
            levelLabel(plan.level)?.let { AppChip(it, container = p.onGradient.copy(alpha = 0.18f), content = p.onGradient) }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                plan.totalCredits?.let {
                    Column {
                        AnimatedNumber(it.toFloat(), decimals = if (it % 1.0 == 0.0) 0 else 1, style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold), color = p.onGradient)
                        Text("créditos en total", color = p.onGradient, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!plan.isText) Column {
                    AnimatedNumber(plan.semesters.size.toFloat(), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.ExtraBold), color = p.onGradient)
                    Text(if (plan.semesters.size == 1) "semestre" else "semestres", color = p.onGradient, style = MaterialTheme.typography.labelMedium)
                }
            }
            progress?.let { ProgressStrip(it, p.onGradient) }
        }
    }
}

/** "Vas en sexto semestre": barra de avance por semestres y créditos de los semestres anteriores. */
@Composable
private fun ProgressStrip(progress: SemesterProgress, onColor: Color) {
    val reduce = LocalReduceMotion.current
    val target = progress.current.toFloat() / progress.total
    val animated = remember { Animatable(if (reduce) target else 0f) }
    LaunchedEffect(target) { if (reduce) animated.snapTo(target) else animated.animateTo(target, tween(900, easing = FastOutSlowInEasing)) }
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(onColor.copy(alpha = 0.14f)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vas en ${semesterOrdinal(progress.current)} semestre", color = onColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Surface(shape = RoundedCornerShape(50), color = BRAND_ORANGE) {
                Text(
                    "${progress.current} / ${progress.total}", color = BRAND_INK, fontWeight = FontWeight.Bold, translate = false,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(onColor.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxWidth(animated.value).fillMaxHeight().clip(RoundedCornerShape(50)).background(BRAND_ORANGE))
        }
        Text(
            when (progress.remaining) {
                0 -> "Es el último semestre del plan."
                1 -> "Después de este te queda 1 semestre."
                else -> "Después de este te quedan ${progress.remaining} semestres."
            },
            color = onColor.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall
        )
        if (progress.current > 1 && progress.totalCredits != null) {
            Text(
                "Los semestres 1 a ${progress.current - 1} suman ${formatCredits(progress.creditsBefore)} de ${formatCredits(progress.totalCredits)} créditos.",
                color = onColor.copy(alpha = 0.92f), style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Gráfica de barras dibujada con Compose: una barra por semestre, con su cantidad de créditos. */
@Composable
private fun CreditsChart(values: List<Pair<Int, Double>>, current: Int? = null) {
    val p = LocalRolePalette.current
    val reduce = LocalReduceMotion.current
    val max = values.maxOf { it.second }.coerceAtLeast(1.0)
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(values) { if (!reduce) progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    val chartHeight = 120.dp
    // Hasta 12 semestres, cada columna toma una parte igual del ancho para que se vean todos (hay planes de
    // 9 o 10 semestres); con más, la gráfica se desplaza con columnas de ancho fijo.
    val fits = values.size <= 12
    Row(
        if (fits) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(if (fits) 4.dp else 8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEach { (number, credits) ->
            val isCurrent = number == current
            val barColor = if (isCurrent) BRAND_ORANGE else p.primary
            Column(
                if (fits) Modifier.weight(1f) else Modifier.width(36.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom
            ) {
                Text(formatCredits(credits), style = MaterialTheme.typography.labelSmall, fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Box(Modifier.height(chartHeight).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(
                        Modifier.fillMaxWidth(0.72f).height((chartHeight * (credits / max).toFloat() * progress.value).coerceAtLeast(if (credits > 0) 3.dp else 0.dp))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(Brush.verticalGradient(listOf(barColor, barColor.copy(alpha = 0.6f))))
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "S$number", style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = if (isCurrent) p.accentText else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else null
                )
            }
        }
    }
}

@Composable
private fun SemesterList(semesters: List<StudyPlanSemesterDto>, current: Int? = null) {
    // Abre el semestre actual del estudiante, o el primero en los demás planes.
    var expanded by remember(semesters, current) {
        mutableStateOf(setOf(current?.takeIf { c -> semesters.any { it.number == c } } ?: semesters.firstOrNull()?.number ?: 0))
    }
    if (semesters.size > 1) {
        AppTextButton(onClick = { expanded = if (expanded.size == semesters.size) emptySet() else semesters.map { it.number }.toSet() }) {
            Text(if (expanded.size == semesters.size) "Contraer todo" else "Expandir todo")
        }
    }
    semesters.forEach { s ->
        val phase = when {
            current == null -> SemesterPhase.NONE
            s.number < current -> SemesterPhase.BEFORE
            s.number == current -> SemesterPhase.CURRENT
            else -> SemesterPhase.AFTER
        }
        SemesterSection(s, s.number in expanded, phase) { expanded = if (s.number in expanded) expanded - s.number else expanded + s.number }
    }
}

/** Ubicación del semestre respecto al semestre actual del estudiante (NONE en planes ajenos). */
private enum class SemesterPhase { NONE, BEFORE, CURRENT, AFTER }

@Composable
private fun SemesterSection(s: StudyPlanSemesterDto, expanded: Boolean, phase: SemesterPhase, onToggle: () -> Unit) {
    val p = LocalRolePalette.current
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "arrow")
    val circle = when (phase) {
        SemesterPhase.CURRENT -> BRAND_ORANGE
        SemesterPhase.BEFORE -> p.container
        else -> p.primary
    }
    val onCircle = when (phase) {
        SemesterPhase.CURRENT -> BRAND_INK
        SemesterPhase.BEFORE -> p.onContainer
        else -> p.onPrimary
    }
    AppCard(onClick = onToggle, contentPadding = PaddingValues(0.dp), accent = if (phase == SemesterPhase.CURRENT) BRAND_ORANGE else null) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(circle), contentAlignment = Alignment.Center) {
                Text("${s.number}", color = onCircle, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                if (phase == SemesterPhase.CURRENT) {
                    Text("Tu semestre actual", style = MaterialTheme.typography.labelMedium, color = p.accentText, fontWeight = FontWeight.Bold)
                }
                Text(s.name?.takeIf { it.isNotBlank() } ?: "Semestre ${s.number}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${s.courses.size} ${if (s.courses.size == 1) "materia" else "materias"}, ${formatCredits(semesterCredits(s))} créditos",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = if (expanded) "Contraer" else "Expandir", modifier = Modifier.rotate(rotation))
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(260)) + fadeIn(), exit = shrinkVertically(tween(220)) + fadeOut()) {
            Column(Modifier.fillMaxWidth()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (s.courses.isEmpty()) Text("Sin materias cargadas.", Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                s.courses.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        if (c.credits != null) AppChip("${formatCredits(c.credits)} cr", Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
