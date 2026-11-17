package co.edu.unab.unabgo.cliente.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.ui.common.FormState
import co.edu.unab.unabgo.cliente.i18n.Text
import co.edu.unab.unabgo.cliente.ui.components.*
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette
import androidx.compose.ui.text.font.FontWeight

private val DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

private fun grade(v: Double?) = v?.let { "%.1f".format(java.util.Locale.US, it) } ?: "pendiente"

@Composable
fun SubjectsScreen(vm: SubjectsViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit, onCreate: () -> Unit, onEdit: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<SubjectDto?>(null) }
    AppScaffold(
        title = "Mis materias", onBack = onBack,
        floatingActionButton = { FloatingActionButton(onClick = onCreate) { Icon(Icons.Default.Add, contentDescription = "Nueva materia") } }
    ) { padding ->
        StateContent(state, vm::refresh, padding) { subjects ->
            action.error?.let { ErrorBanner(it.userMessage) }
            if (subjects.isEmpty()) EmptyView("Aún no tienes materias. Toca el botón + para crear la primera.")
            subjects.forEach { s ->
                AppCard(onClick = { onOpen(s.id) }, accent = parseColor(s.color), contentPadding = PaddingValues(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${s.credits} créditos" + (s.teacher?.let { ", $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEdit(s.id) }) { Icon(Icons.Default.Edit, contentDescription = "Editar") }
                        IconButton(onClick = { vm.dismissAction(); toDelete = s }) { Icon(Icons.Default.Delete, contentDescription = "Borrar") }
                    }
                }
            }
        }
    }
    toDelete?.let { s ->
        ConfirmDialog(
            "Borrar materia", "Se borrarán también su horario, cortes y notas de ${s.name}. No se puede deshacer.", "Borrar",
            { vm.delete(s.id); toDelete = null }, { toDelete = null }
        )
    }
}

@Composable
fun SubjectDetailScreen(vm: SubjectDetailViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val categoryForm by vm.categoryForm.collectAsStateWithLifecycle()
    val itemForm by vm.itemForm.collectAsStateWithLifecycle()
    var addCategory by remember { mutableStateOf(false) }
    var addItemTo by remember { mutableStateOf<GradeCategoryDto?>(null) }

    LaunchedEffect(categoryForm.done) { if (categoryForm.done) { addCategory = false; vm.resetForms() } }
    LaunchedEffect(itemForm.done) { if (itemForm.done) { addItemTo = null; vm.resetForms() } }

    AppScaffold(
        title = (state as? co.edu.unab.unabgo.cliente.ui.common.UiState.Ready)?.data?.subject?.name ?: "Materia",
        onBack = onBack,
        actions = {
            val id = (state as? co.edu.unab.unabgo.cliente.ui.common.UiState.Ready)?.data?.subject?.id
            if (id != null) IconButton(onClick = { onEdit(id) }) { Icon(Icons.Default.Edit, contentDescription = "Editar materia") }
        }
    ) { padding ->
        StateContent(state, vm::refresh, padding) { detail ->
            val s = detail.subject
            AppCard(accent = parseColor(s.color)) {
                Text(s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("${s.credits} créditos" + (s.teacher?.let { ", docente $it" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.blocks.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    s.blocks.forEach { b ->
                        Text("${DAY_NAMES.getOrElse(b.dayOfWeek - 1) { "" }} ${b.startTime.take(5)} a ${b.endTime.take(5)}" + (b.room?.let { ", en $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            SectionTitle("Resumen de notas")
            SummaryCard(detail.summary, detail.summaryError)
            SectionTitle("Cortes y notas")
            val categories = s.categories.orEmpty()
            if (categories.isEmpty()) Text("Todavía no hay cortes. Agrega el primero con su porcentaje.")
            categories.forEach { c ->
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${c.name} (${c.weight.toInt()} %)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (c.items.isEmpty()) Text("Sin notas todavía.", style = MaterialTheme.typography.bodySmall)
                        c.items.forEach { i ->
                            Text("${i.name}: ${grade(i.score)}" + (i.weight?.let { " (peso ${it.toInt()} %)" } ?: ""))
                        }
                        AppTextButton(onClick = { vm.resetForms(); addItemTo = c }) { Text("Agregar nota") }
                    }
                }
            }
            AppOutlinedButton(onClick = { vm.resetForms(); addCategory = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar corte") }
        }
    }

    if (addCategory) CategoryDialog(categoryForm, { addCategory = false }) { n, w -> vm.addCategory(n, w) }
    addItemTo?.let { c -> ItemDialog(c.name, itemForm, { addItemTo = null }) { n, sc, w, d -> vm.addItem(c.id, n, sc, w, d) } }
}

@Composable
private fun SummaryCard(summary: GradeSummaryDto?, error: ApiError?) {
    val p = LocalRolePalette.current
    AppCard(container = p.container) {
        if (summary == null) {
            Text(error?.userMessage ?: "No se pudo calcular el resumen.", color = MaterialTheme.colorScheme.error)
            return@AppCard
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(((summary.average ?: 0.0) / 5.0).toFloat(), size = 104.dp, color = p.primary, track = MaterialTheme.colorScheme.surface) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (summary.average != null) AnimatedNumber(summary.average.toFloat(), decimals = 1, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = p.onContainer)
                    else Text("--", style = MaterialTheme.typography.headlineSmall, color = p.onContainer)
                    Text("de 5.0", style = MaterialTheme.typography.labelSmall, color = p.onContainer)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Nota actual (promedio): ${grade(summary.average)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = p.onContainer)
                Text("Acumulado: ${grade(summary.accumulated)}", color = p.onContainer)
                Text("${summary.evaluatedPercent.toInt()} % evaluado", color = p.onContainer, style = MaterialTheme.typography.bodySmall)
                val animated = remember { Animatable(0f) }
                LaunchedEffect(summary.evaluatedPercent) { animated.animateTo((summary.evaluatedPercent / 100.0).toFloat().coerceIn(0f, 1f), tween(900)) }
                LinearProgressIndicator(progress = { animated.value }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = p.primary, trackColor = MaterialTheme.colorScheme.surface)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            summary.passedAlready -> SuccessText("Ya aprobaste esta materia.")
            summary.cannotPass -> Text("Con las notas que quedan ya no alcanzas la nota mínima.", color = MaterialTheme.colorScheme.error)
            summary.neededToPass != null -> Text("Para aprobar necesitas ${grade(summary.neededToPass)} en lo que falta.", color = p.onContainer, fontWeight = FontWeight.SemiBold)
        }
        summary.habilitationNeeded?.let { Text("Para la habilitación necesitas ${grade(it)}.", color = p.onContainer) }
        summary.finalGrade?.let { Text("Nota final: ${grade(it)}", color = p.onContainer, fontWeight = FontWeight.Bold) }
        summary.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = p.onContainer) }
    }
}

@Composable
private fun CategoryDialog(form: FormState, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!form.submitting) onDismiss() },
        title = { Text("Nuevo corte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormField("Nombre (Corte 1)", name, { name = it }, form, "name")
                FormField("Porcentaje del corte", weight, { weight = it }, form, "weight", number = true)
                form.generalError?.let { ErrorBanner(it) }
            }
        },
        confirmButton = { AppButton(onClick = { onSave(name, weight) }, enabled = !form.submitting) { Text(if (form.submitting) "Enviando" else "Guardar") } },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !form.submitting) { Text("Cancelar") } }
    )
}

@Composable
private fun ItemDialog(category: String, form: FormState, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var score by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!form.submitting) onDismiss() },
        title = { Text("Nueva nota en $category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormField("Nombre (Parcial)", name, { name = it }, form, "name")
                FormField("Nota de 0 a 5 (vacía si está pendiente)", score, { score = it }, form, "score", number = true)
                FormField("Peso dentro del corte, % (opcional)", weight, { weight = it }, form, "weight", number = true)
                FormField("Fecha AAAA-MM-DD (opcional)", date, { date = it }, form, "graded_at")
                form.generalError?.let { ErrorBanner(it) }
            }
        },
        confirmButton = { AppButton(onClick = { onSave(name, score, weight, date) }, enabled = !form.submitting) { Text(if (form.submitting) "Enviando" else "Guardar") } },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !form.submitting) { Text("Cancelar") } }
    )
}

@Composable
fun SubjectFormScreen(vm: SubjectFormViewModel, isEdit: Boolean, onBack: () -> Unit, onSaved: () -> Unit) {
    val loading by vm.loading.collectAsStateWithLifecycle()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    LaunchedEffect(form.done) { if (form.done) onSaved() }
    AppScaffold(title = if (isEdit) "Editar materia" else "Nueva materia", onBack = onBack) { padding ->
        when (val l = loading) {
            co.edu.unab.unabgo.cliente.ui.common.UiState.Loading -> LoadingView(Modifier.padding(padding))
            is co.edu.unab.unabgo.cliente.ui.common.UiState.Failed -> ErrorView(l.error, { vm.load() }, Modifier.padding(padding))
            is co.edu.unab.unabgo.cliente.ui.common.UiState.Ready -> Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormField("Nombre de la materia", draft.name, { vm.update(draft.copy(name = it)) }, form, "name")
                FormField("Créditos (0 a 10)", draft.credits, { vm.update(draft.copy(credits = it)) }, form, "credits", number = true)
                FormField("Docente (opcional)", draft.teacher, { vm.update(draft.copy(teacher = it)) }, form, "teacher")
                Text("Color")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SubjectDraft.COLORS.forEach { c ->
                        Box(
                            Modifier.size(if (c == draft.color) 40.dp else 30.dp).clip(CircleShape).background(parseColor(c))
                                .clickable(enabled = !form.submitting) { vm.update(draft.copy(color = c)) }
                        )
                    }
                }
                form.fields["color"]?.let { ErrorBanner(it) }
                SectionTitle("Horario semanal")
                form.fields["blocks"]?.let { ErrorBanner(it) }
                draft.blocks.forEachIndexed { index, b ->
                    BlockEditor(index, b, form, onChange = { nb -> vm.update(draft.copy(blocks = draft.blocks.toMutableList().also { it[index] = nb })) },
                        onRemove = { vm.update(draft.copy(blocks = draft.blocks.filterIndexed { i, _ -> i != index })) })
                }
                AppOutlinedButton(onClick = { vm.update(draft.copy(blocks = draft.blocks + BlockDraft())) }, enabled = draft.blocks.size < 7 && !form.submitting) {
                    Text("Agregar bloque")
                }
                SubmitButton("Guardar", form, vm::save)
            }
        }
    }
}

@Composable
private fun BlockEditor(index: Int, b: BlockDraft, form: FormState, onChange: (BlockDraft) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val p = "blocks.$index"
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    AppOutlinedButton(onClick = { menu = true }, enabled = !form.submitting) { Text(DAY_NAMES[(b.day - 1).coerceIn(0, 6)]) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DAY_NAMES.forEachIndexed { i, n -> DropdownMenuItem(text = { Text(n) }, onClick = { onChange(b.copy(day = i + 1)); menu = false }) }
                    }
                }
                Spacer(Modifier.weight(1f))
                AppTextButton(onClick = onRemove, enabled = !form.submitting) { Text("Quitar") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField("Inicio HH:mm", b.start, { onChange(b.copy(start = it)) }, form, "$p.start_time", Modifier.weight(1f))
                FormField("Fin HH:mm", b.end, { onChange(b.copy(end = it)) }, form, "$p.end_time", Modifier.weight(1f))
            }
            FormField("Salón (opcional)", b.room, { onChange(b.copy(room = it)) }, form, "$p.room")
            form.fields["$p.day_of_week"]?.let { ErrorBanner(it) }
        }
    }
}

@Composable
fun ProfileScreen(vm: ProfileViewModel, careers: CareerPickerViewModel, onBack: () -> Unit, onLanguage: () -> Unit) {
    val session by vm.session.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val career by careers.selected.collectAsStateWithLifecycle()
    val plan by careers.plan.collectAsStateWithLifecycle()
    val user = session?.user
    val profile = user?.profile
    LaunchedEffect(profile?.program) { careers.syncInitial(profile?.program?.toChoice()) }
    var code by remember(profile) { mutableStateOf(profile?.studentCode.orEmpty()) }
    var semester by remember(profile) { mutableStateOf(profile?.semester) }
    var reminder by remember(profile) { mutableStateOf(profile?.reminderMinutes ?: 0) }
    val maxSemester = semesterOptions(plan)

    AppScaffold(title = "Mi perfil", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(user?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, translate = false)
            Text(user?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
            SectionTitle("Carrera y semestre")
            CareerField(careers, enabled = !form.submitting, error = form.fields["program_id"])
            SemesterPicker(semester, { semester = it }, maxOf(maxSemester, semester ?: 0), !form.submitting, form.fields["semester"])
            FormField("Código estudiantil", code, { code = it }, form, "student_code")
            SectionTitle("Idioma")
            LanguageRow(profile?.language, onLanguage)
            SectionTitle("Recordatorio de clase")
            REMINDERS.forEach { (k, label) ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !form.submitting) { reminder = k }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = reminder == k, onClick = { reminder = k }, enabled = !form.submitting)
                    Text(label)
                }
            }
            if (form.done) SuccessText("Perfil guardado.")
            SubmitButton("Guardar perfil", form, { vm.save(career?.id, code, semester, reminder) })
        }
    }
}
