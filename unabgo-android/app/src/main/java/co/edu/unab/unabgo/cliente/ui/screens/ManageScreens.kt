package co.edu.unab.unabgo.cliente.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unab.unabgo.cliente.data.api.FaqDto
import co.edu.unab.unabgo.cliente.data.api.UserDto
import co.edu.unab.unabgo.cliente.data.session.Roles
import co.edu.unab.unabgo.cliente.ui.components.*
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette
import co.edu.unab.unabgo.cliente.ui.theme.rolePalette
import androidx.compose.ui.text.font.FontWeight
import co.edu.unab.unabgo.cliente.i18n.Text

// ------------------------------------------------------------------------------------------------ preguntas de Banu

@Composable
fun FaqsScreen(vm: FaqsViewModel, formVm: FaqFormViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val action by vm.action.collectAsStateWithLifecycle()
    val form by formVm.form.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<FaqDto?>(null) }
    var creating by remember { mutableStateOf(false) }
    var toDeactivate by remember { mutableStateOf<FaqDto?>(null) }
    LaunchedEffect(form.done) { if (form.done) { creating = false; editing = null; vm.refresh() } }

    AppScaffold(
        title = "Preguntas de Banu", onBack = onBack,
        actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") } },
        floatingActionButton = { FloatingActionButton(onClick = { formVm.reset(); creating = true }) { Icon(Icons.Default.Add, contentDescription = "Nueva pregunta") } }
    ) { padding ->
        StateContent(state, vm::refresh, padding) { faqs ->
            action.error?.let { ErrorBanner(it.userMessage) }
            if (faqs.isEmpty()) EmptyView("No hay preguntas frecuentes.")
            faqs.forEach { f ->
                AppCard(accent = if (f.active) LocalRolePalette.current.primary else MaterialTheme.colorScheme.outline) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(f.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(f.answer, maxLines = 3, style = MaterialTheme.typography.bodyMedium)
                        Text(if (f.active) "Activa" else "Inactiva", color = if (f.active) LocalRolePalette.current.success else MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Row {
                            AppTextButton(onClick = { formVm.reset(); editing = f }) { Text("Editar") }
                            if (f.active) AppTextButton(onClick = { vm.dismissAction(); toDeactivate = f }) { Text("Desactivar") }
                        }
                    }
                }
            }
        }
    }
    if (creating) FaqDialog(null, form, { creating = false }) { q, k, a, s, act -> formVm.save(null, q, k, a, s, act) }
    editing?.let { f -> FaqDialog(f, form, { editing = null }) { q, k, a, s, act -> formVm.save(f.id, q, k, a, s, act) } }
    toDeactivate?.let { f ->
        ConfirmDialog("Desactivar pregunta", "Banu dejará de usar \"${f.question}\".", "Desactivar", { vm.deactivate(f.id); toDeactivate = null }, { toDeactivate = null })
    }
}

@Composable
private fun FaqDialog(
    existing: FaqDto?,
    form: co.edu.unab.unabgo.cliente.ui.common.FormState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean) -> Unit
) {
    var question by remember { mutableStateOf(existing?.question.orEmpty()) }
    var keywords by remember { mutableStateOf(existing?.keywords.orEmpty()) }
    var answer by remember { mutableStateOf(existing?.answer.orEmpty()) }
    var source by remember { mutableStateOf(existing?.sourceUrl ?: "https://") }
    var active by remember { mutableStateOf(existing?.active ?: true) }
    AlertDialog(
        onDismissRequest = { if (!form.submitting) onDismiss() },
        title = { Text(if (existing == null) "Nueva pregunta" else "Editar pregunta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FormField("Pregunta", question, { question = it }, form, "question")
                FormField("Formas de preguntarla (palabras clave)", keywords, { keywords = it }, form, "keywords", singleLine = false)
                FormField("Respuesta", answer, { answer = it }, form, "answer", singleLine = false)
                FormField("Fuente oficial (https)", source, { source = it }, form, "source_url")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = active, onCheckedChange = { active = it }, enabled = !form.submitting)
                    Spacer(Modifier.width(8.dp)); Text("Activa")
                }
                form.generalError?.let { ErrorBanner(it) }
            }
        },
        confirmButton = { AppButton(onClick = { onSave(question, keywords, answer, source, active) }, enabled = !form.submitting) { Text(if (form.submitting) "Enviando" else "Guardar") } },
        dismissButton = { AppTextButton(onClick = onDismiss, enabled = !form.submitting) { Text("Cancelar") } }
    )
}

// ------------------------------------------------------------------------------------------------ administrador

@Composable
fun AdminUsersScreen(vm: AdminViewModel, onBack: () -> Unit) {
    val list by vm.users.collectAsStateWithLifecycle()
    val role by vm.role.collectAsStateWithLifecycle()
    val action by vm.roleAction.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<UserDto?>(null) }
    LaunchedEffect(action.done) { if (action.done) { target = null; vm.dismissRoleAction() } }

    AppScaffold(
        title = "Usuarios", onBack = onBack,
        actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualizar") } }
    ) { padding ->
        PagedContent(
            list, vm::refresh, vm::loadMore, padding, "No hay usuarios con ese rol.",
            header = {
                item {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = role == null, onClick = { vm.filter(null) }, label = { Text("Todos") })
                        listOf(Roles.STUDENT, Roles.MANAGER, Roles.ADMIN).forEach { r ->
                            FilterChip(selected = role == r, onClick = { vm.filter(r) }, label = { Text(Roles.label(r)) })
                        }
                    }
                }
                item { Text("${list.total} usuarios", style = MaterialTheme.typography.bodySmall) }
            }
        ) { u ->
            val up = rolePalette(u.role, androidx.compose.foundation.isSystemInDarkTheme())
            AppCard(accent = up.primary, contentPadding = PaddingValues(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(u.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, translate = false)
                        Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                        AppChip(up.label, container = up.container, content = up.onContainer)
                    }
                    AppTextButton(onClick = { vm.dismissRoleAction(); target = u }) { Text("Cambiar rol") }
                }
            }
        }
    }
    target?.let { u ->
        AlertDialog(
            onDismissRequest = { if (!action.submitting) target = null },
            title = { Text("Rol de ${u.name}") },
            text = {
                Column {
                    Text("Rol actual: ${Roles.label(u.role)}")
                    action.error?.let { ErrorBanner(it.userMessage) }
                    listOf(Roles.STUDENT, Roles.MANAGER, Roles.ADMIN).filter { it != u.role }.forEach { r ->
                        AppOutlinedButton(onClick = { vm.changeRole(u.id, r) }, enabled = !action.submitting, modifier = Modifier.fillMaxWidth()) {
                            Text("Pasar a ${Roles.label(r)}")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { AppTextButton(onClick = { target = null }, enabled = !action.submitting) { Text("Cerrar") } }
        )
    }
}

@Composable
fun SyncScreen(vm: AdminViewModel, onBack: () -> Unit) {
    val sync by vm.sync.collectAsStateWithLifecycle()
    val result by vm.syncResult.collectAsStateWithLifecycle()
    AppScaffold(title = "Sincronizar programas", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Trae los programas del sitio de la UNAB y crea o actualiza los que cambiaron.")
            AppButton(onClick = vm::syncPrograms, enabled = !sync.submitting, modifier = Modifier.fillMaxWidth()) {
                if (sync.submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text("Ejecutar sincronización")
            }
            sync.error?.let {
                ErrorBanner(if (it.isUnavailable) "La sincronización no está configurada en el servidor (${it.message})." else it.userMessage)
            }
            result?.let {
                AppCard(container = LocalRolePalette.current.container) {
                    Text("Resultado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf("Leídos" to it.fetched, "Creados" to it.created, "Actualizados" to it.updated, "Sin cambios" to it.unchanged).forEach { (label, n) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedNumber(n.toFloat(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold), color = LocalRolePalette.current.onContainer)
                                Text(label, style = MaterialTheme.typography.labelSmall, color = LocalRolePalette.current.onContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}
