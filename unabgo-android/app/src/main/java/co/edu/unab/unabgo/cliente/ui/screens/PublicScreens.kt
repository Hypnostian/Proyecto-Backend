package co.edu.unab.unabgo.cliente.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.components.*
import co.edu.unab.unabgo.cliente.ui.theme.Brand
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import co.edu.unab.unabgo.cliente.i18n.Text

private val BANU_SUGGESTIONS = listOf("¿Cómo pago la matrícula?", "¿Qué servicios ofrece Bienestar Universitario?")

/** Color de la barra lateral de cada categoría de aviso, con los colores institucionales. */
private fun categoryColor(category: String): Color = when (category) {
    "academico" -> Brand.Purple
    "bienestar" -> Brand.Green
    "transporte" -> Brand.Cyan
    else -> Brand.Orange
}

// ------------------------------------------------------------------------------------------------ avisos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(
    vm: AnnouncementsViewModel,
    formVm: AnnouncementFormViewModel,
    canManage: Boolean,
    onBack: () -> Unit
) {
    val list by vm.list.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val form by formVm.form.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AnnouncementDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<AnnouncementDto?>(null) }

    LaunchedEffect(form.done) {
        if (form.done) { creating = false; editing = null; vm.refresh() }
    }

    AppScaffold(
        title = "Avisos", onBack = onBack,
        actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") } },
        floatingActionButton = {
            if (canManage) FloatingActionButton(onClick = { formVm.reset(); creating = true }) { Icon(Icons.Default.Add, contentDescription = "Nuevo aviso") }
        }
    ) { padding ->
        PagedContent(
            list, vm::refresh, vm::loadMore, padding, "No hay avisos publicados en esta categoría.",
            header = {
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = category == null, onClick = { vm.filter(null) }, label = { Text("Todos") })
                        ANNOUNCEMENT_CATEGORIES.forEach { (k, label) ->
                            FilterChip(selected = category == k, onClick = { vm.filter(k) }, label = { Text(label) })
                        }
                    }
                }
                action.error?.let { e -> item { ErrorBanner(e.userMessage) } }
            }
        ) { a ->
            AppCard(accent = categoryColor(a.category)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${ANNOUNCEMENT_CATEGORIES.firstOrNull { it.first == a.category }?.second ?: a.category} - ${a.author} - ${a.publishedAt?.take(10).orEmpty()}",
                        style = MaterialTheme.typography.bodySmall, color = LocalRolePalette.current.accentText, fontWeight = FontWeight.SemiBold)
                    Text(a.body)
                    if (canManage) Row {
                        AppTextButton(onClick = { formVm.reset(); editing = a }) { Icon(Icons.Default.Edit, null); Spacer(Modifier.width(4.dp)); Text("Editar") }
                        AppTextButton(onClick = { vm.dismissAction(); toDelete = a }) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(4.dp)); Text("Retirar") }
                    }
                }
            }
        }
    }
    if (creating) AnnouncementDialog(null, form, { creating = false }) { t, b, c, p -> formVm.save(null, t, b, c, p) }
    editing?.let { a -> AnnouncementDialog(a, form, { editing = null }) { t, b, c, p -> formVm.save(a.id, t, b, c, p) } }
    toDelete?.let { a ->
        ConfirmDialog("Retirar aviso", "Se borrará el aviso \"${a.title}\". No se puede deshacer.", "Retirar", { vm.delete(a.id); toDelete = null }, { toDelete = null })
    }
}

@Composable
private fun AnnouncementDialog(
    existing: AnnouncementDto?,
    form: co.edu.unab.unabgo.cliente.ui.common.FormState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var body by remember { mutableStateOf(existing?.body.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: ANNOUNCEMENT_CATEGORIES.first().first) }
    var publish by remember { mutableStateOf(existing?.publishedAt != null || existing == null) }
    AlertDialog(
        onDismissRequest = { if (!form.submitting) onDismiss() },
        title = { Text(if (existing == null) "Nuevo aviso" else "Editar aviso") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormField("Título", title, { title = it }, form, "title")
                FormField("Contenido", body, { body = it }, form, "body", singleLine = false)
                Text("Categoría")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ANNOUNCEMENT_CATEGORIES.take(2).forEach { (k, l) -> FilterChip(category == k, { category = k }, { Text(l) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ANNOUNCEMENT_CATEGORIES.drop(2).forEach { (k, l) -> FilterChip(category == k, { category = k }, { Text(l) }) }
                }
                form.fields["category"]?.let { ErrorBanner(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = publish, onCheckedChange = { publish = it }, enabled = !form.submitting)
                    Spacer(Modifier.width(8.dp))
                    Text("Publicar ahora (envía la notificación)")
                }
                form.generalError?.let { ErrorBanner(it) }
            }
        },
        confirmButton = { AppButton(onClick = { onSave(title, body, category, publish) }, enabled = !form.submitting) { Text(if (form.submitting) "Enviando" else "Guardar") } },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !form.submitting) { Text("Cancelar") } }
    )
}

// ------------------------------------------------------------------------------------------------ profesores

@Composable
fun ProfessorsScreen(vm: ProfessorsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    AppScaffold(title = "Profesores", onBack = onBack) { padding ->
        PagedContent(
            list, vm::refresh, vm::loadMore, padding, "No se encontraron profesores con esa búsqueda.",
            header = {
                item { SearchField(query, vm::onQueryChange, "Buscar por nombre o cargo") }
                item { Text("${list.total} profesores", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        ) { p ->
            AppCard(onClick = { onOpen(p.slug) }, accent = LocalRolePalette.current.primary) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, translate = false)
                p.title?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                p.unit?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalRolePalette.current.accentText, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

@Composable
fun ProfessorDetailScreen(vm: ProfessorDetailViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    AppScaffold(title = "Profesor", onBack = onBack) { padding ->
        StateContent(state, vm::refresh, padding) { p ->
            AppCard {
                Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, translate = false)
                p.title?.let { Text(it) }
                p.unit?.let { Text("Unidad: $it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            AppButton(onClick = { uri.openUri(p.apoloUrl) }, modifier = Modifier.fillMaxWidth()) { Text("Ver perfil público en APOLO") }
            p.cvlacUrl?.let { AppOutlinedButton(onClick = { uri.openUri(it) }, modifier = Modifier.fillMaxWidth()) { Text("Ver CvLAC") } }
            p.unitUrl?.let { AppOutlinedButton(onClick = { uri.openUri(it) }, modifier = Modifier.fillMaxWidth()) { Text("Ver la unidad académica") } }
        }
    }
}

// ------------------------------------------------------------------------------------------------ programas

@Composable
fun ProgramsScreen(vm: ProgramsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    AppScaffold(title = "Programas", onBack = onBack) { padding ->
        PagedContent(
            list, vm::refresh, vm::loadMore, padding, "No se encontraron programas con esa búsqueda.",
            header = {
                item { SearchField(query, vm::onQueryChange, "Buscar programa") }
                item { Text("${list.total} programas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        ) { p ->
            AppCard(onClick = { onOpen(p.slug) }, accent = LocalRolePalette.current.primary) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${p.level} - ${p.faculty}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ProgramDetailScreen(vm: ProgramDetailViewModel, onBack: () -> Unit, onOpenStudyPlan: (String) -> Unit = {}) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    AppScaffold(title = "Programa", onBack = onBack) { padding ->
        StateContent(state, vm::refresh, padding) { p ->
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Text("Facultad: ${p.faculty}")
                    Text("Nivel: ${p.level}")
                    p.modality?.let { Text("Modalidad: $it") }
                    p.priceCop?.let { Text("Valor del semestre: $ ${"%,d".format(java.util.Locale.US, it)} COP") }
                    p.snies?.let { Text("SNIES: $it") }
                    p.verifiedAt?.let { Text("Verificado: ${it.take(10)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
            p.studyPlanSlug?.let { slug ->
                AppButton(onClick = { onOpenStudyPlan(slug) }, modifier = Modifier.fillMaxWidth()) { Text("Ver plan de estudios") }
            }
            AppOutlinedButton(onClick = { uri.openUri(p.url) }, modifier = Modifier.fillMaxWidth()) { Text("Ver en el sitio de la UNAB") }
        }
    }
}

// ------------------------------------------------------------------------------------------------ buses

@Composable
fun BusScreen(vm: BusViewModel, canFavorite: Boolean, canEdit: Boolean, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val routeForm by vm.routeForm.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<BusRouteDto?>(null) }
    LaunchedEffect(routeForm.done) { if (routeForm.done) { editing = null; vm.resetRouteForm() } }

    AppScaffold(
        title = "Rutas UNAB", onBack = onBack,
        actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") } }
    ) { padding ->
        StateContent(state, vm::refresh, padding) { ui ->
            action.error?.let { ErrorBanner(it.userMessage) }
            SectionTitle("Buses en vivo")
            when {
                ui.liveError != null -> Text(
                    if (ui.liveError.isUnavailable) "El GPS en vivo no está disponible por ahora. Las rutas sí se muestran abajo."
                    else ui.liveError.userMessage, color = MaterialTheme.colorScheme.error
                )
                ui.live.isEmpty() -> Text("No hay buses reportando en este momento.")
                else -> ui.live.forEach { b ->
                    AppCard(accent = b.route?.color?.let { parseColor(it) } ?: LocalRolePalette.current.primary) {
                        Column {
                            Text("${b.plate}: ${b.route?.name ?: "sin ruta"}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("${stateLabel(b.state)}${b.speedKmh?.let { ", ${it.toInt()} km/h" } ?: ""}${b.heading?.let { ", rumbo $it" } ?: ""}")
                            Text("Posición ${"%.5f".format(java.util.Locale.US, b.lat)}, ${"%.5f".format(java.util.Locale.US, b.lng)}. Reporte hace ${b.ageSeconds} s.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            SectionTitle("Rutas")
            if (ui.routes.isEmpty()) EmptyView("No hay rutas registradas.")
            ui.routes.forEach { r ->
                AppCard(accent = parseColor(r.color)) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${r.code}: ${r.name}", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (canFavorite) {
                                val fav = r.id in ui.favoriteIds
                                IconButton(onClick = { vm.toggleFavorite(r.id) }) {
                                    Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = if (fav) "Quitar de favoritas" else "Agregar a favoritas")
                                }
                            }
                            if (canEdit) IconButton(onClick = { vm.resetRouteForm(); editing = r }) { Icon(Icons.Default.Edit, contentDescription = "Editar ruta") }
                        }
                        if (!r.active) Text("Ruta inactiva", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text(r.stops.joinToString(" > ") { it.name }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    editing?.let { r ->
        var name by remember { mutableStateOf(r.name) }
        var color by remember { mutableStateOf(r.color) }
        var active by remember { mutableStateOf(r.active) }
        AlertDialog(
            onDismissRequest = { if (!routeForm.submitting) editing = null },
            title = { Text("Editar ruta ${r.code}") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FormField("Nombre", name, { name = it }, routeForm, "name")
                    FormField("Color #RRGGBB", color, { color = it }, routeForm, "color")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = active, onCheckedChange = { active = it }, enabled = !routeForm.submitting)
                        Spacer(Modifier.width(8.dp)); Text("Ruta activa")
                    }
                    Text("Se conservan las ${r.stops.size} paradas y su orden.", style = MaterialTheme.typography.bodySmall)
                    routeForm.fields["stop_ids"]?.let { ErrorBanner(it) }
                    routeForm.generalError?.let { ErrorBanner(it) }
                }
            },
            confirmButton = {
                AppButton(onClick = { vm.saveRoute(r.id, name, color, active, r.stops.map { it.id }) }, enabled = !routeForm.submitting) {
                    Text(if (routeForm.submitting) "Enviando" else "Guardar")
                }
            },
            dismissButton = { AppTextButton(onClick = { editing = null }, enabled = !routeForm.submitting) { Text("Cancelar") } }
        )
    }
}

private fun stateLabel(state: String) = when (state) {
    "MOVING" -> "En movimiento"
    "STOPPED" -> "Detenido"
    "PARKED" -> "Estacionado"
    else -> state
}

// ------------------------------------------------------------------------------------------------ Banu

@Composable
fun BanuScreen(vm: BanuViewModel, onBack: () -> Unit, initialQuestion: String? = null) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val sending by vm.sending.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val uri = LocalUriHandler.current
    LaunchedEffect(initialQuestion) { if (!initialQuestion.isNullOrBlank()) vm.askInitial(initialQuestion) }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    AppScaffold(title = "Banu", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (messages.isEmpty()) item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EmptyView("Hola, soy Banu. Pregúntame por matrículas, horarios, bienestar o cualquier trámite de la UNAB.")
                        BANU_SUGGESTIONS.forEach { q ->
                            AppOutlinedButton(onClick = { vm.ask(q) }, enabled = !sending, modifier = Modifier.fillMaxWidth()) { Text(q) }
                        }
                    }
                }
                items(messages.size) { i ->
                    when (val m = messages[i]) {
                        is ChatMessage.FromUser -> Box(Modifier.fillMaxWidth().padding(start = 48.dp), contentAlignment = Alignment.CenterEnd) {
                            Surface(color = LocalRolePalette.current.primary, shape = MaterialTheme.shapes.large) {
                                Text(m.text, Modifier.padding(12.dp), color = LocalRolePalette.current.onPrimary)
                            }
                        }
                        is ChatMessage.FromBanu -> AppCard(Modifier.padding(end = 24.dp), accent = LocalRolePalette.current.primary) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(m.answer.respuesta)
                                Text(
                                    banuOriginLabel(m.answer.origen),
                                    style = MaterialTheme.typography.bodySmall, color = LocalRolePalette.current.accentText
                                )
                                m.answer.fuentes.forEach { f ->
                                    AppTextButton(onClick = { uri.openUri(f.url) }) { Text("Fuente: ${f.titulo}") }
                                }
                            }
                        }
                        is ChatMessage.Problem -> Text(m.text, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (sending) item { SkeletonCard(Modifier.padding(end = 48.dp)) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text("Tu pregunta") }, modifier = Modifier.weight(1f), enabled = !sending, maxLines = 3,
                    shape = MaterialTheme.shapes.medium, colors = appFieldColors()
                )
                IconButton(onClick = { vm.ask(text); text = "" }, enabled = !sending && text.isNotBlank()) { Icon(Icons.Default.Send, contentDescription = "Enviar") }
            }
        }
    }
}

/** Texto del origen de la respuesta de Banu; un valor desconocido no rompe la pantalla. */
fun banuOriginLabel(origin: String): String = when (origin) {
    "ia" -> "Origen: respuesta generada con inteligencia artificial"
    "plan" -> "Origen: Plan de estudios oficial"
    else -> "Origen: preguntas frecuentes oficiales"
}
