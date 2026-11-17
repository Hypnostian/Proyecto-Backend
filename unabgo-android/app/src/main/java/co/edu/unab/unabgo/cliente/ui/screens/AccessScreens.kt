package co.edu.unab.unabgo.cliente.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unab.unabgo.cliente.i18n.Text
import co.edu.unab.unabgo.cliente.ui.components.*
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette
import co.edu.unab.unabgo.cliente.ui.theme.rolePalette

@Composable
fun LoginScreen(vm: AuthViewModel, onRegister: () -> Unit, onSettings: () -> Unit, language: String = "es", onLanguage: () -> Unit = {}) {
    val form by vm.form.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val counter = remember { EntranceCounter() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        CompositionLocalProvider(LocalEntranceCounter provides counter) {
            Column(Modifier.padding(bottom = padding.calculateBottomPadding()).fillMaxSize().verticalScroll(rememberScrollState())) {
                val p = LocalRolePalette.current
                GradientHeader {
                    Box(Modifier.fillMaxWidth()) {
                        LanguagePill(language, p.onGradient, onLanguage, Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 10.dp))
                        IconButton(onClick = onSettings, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes de conexión", tint = p.onGradient)
                        }
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 28.dp, bottom = 36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Logo(88.dp)
                            Text("UNAB GO Cliente", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = p.onGradient, translate = false)
                            Text(
                                "Horario, notas, buses y planes de estudio en un solo lugar",
                                color = p.onGradient.copy(alpha = 0.9f), textAlign = TextAlign.Center
                            )
                            BrandStrip(Modifier.width(140.dp).clip(CircleShape), 6.dp)
                        }
                    }
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            FormField("Correo institucional", email, { email = it }, form, "email")
                            FormField("Contraseña", password, { password = it }, form, "password", password = true)
                            SubmitButton("Entrar", form, { vm.login(email, password) })
                            AppTextButton(onClick = { vm.clear(); onRegister() }, enabled = !form.submitting, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text("Crear una cuenta")
                            }
                        }
                    }
                    SectionTitle("Cuentas de prueba")
                    Text(
                        "Toca una para llenar el formulario. El color muestra el rol con el que entrarás.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DemoAccounts.all.forEach { acc ->
                        val rp = rolePalette(acc.role, dark)
                        AppCard(
                            onClick = { if (!form.submitting) { email = acc.email; password = DemoAccounts.PASSWORD } },
                            accent = rp.primary, contentPadding = PaddingValues(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(acc.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(acc.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                                }
                                AppChip(rp.label, container = rp.container, content = rp.onContainer)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(vm: AuthViewModel, careers: CareerPickerViewModel, onBack: () -> Unit) {
    val form by vm.form.collectAsStateWithLifecycle()
    val career by careers.selected.collectAsStateWithLifecycle()
    val plan by careers.plan.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var semester by remember { mutableStateOf<Int?>(null) }
    val maxSemester = semesterOptions(plan)
    // Si la nueva carrera tiene menos semestres que el elegido, se ajusta al último del plan.
    LaunchedEffect(maxSemester) { semester?.let { if (it > maxSemester) semester = maxSemester } }
    AppScaffold(title = "Crear cuenta", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tus datos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Usa tu correo @unab.edu.co. La contraseña lleva al menos 8 caracteres, con letras y números.", style = MaterialTheme.typography.bodyMedium)
                    FormField("Nombre completo", name, { name = it }, form, "name")
                    FormField("Correo institucional", email, { email = it }, form, "email")
                    FormField("Contraseña", password, { password = it }, form, "password", password = true)
                    FormField("Confirmar contraseña", confirmation, { confirmation = it }, form, "password_confirmation", password = true)
                }
            }
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Tu carrera", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "En Mi plan de estudios verás solo el plan de la carrera que elijas; los de los demás programas quedan en Explorar planes de estudio.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    CareerField(careers, enabled = !form.submitting, error = form.fields["program_id"])
                    if (career != null) SemesterPicker(semester, { semester = it }, maxSemester, !form.submitting, form.fields["semester"])
                }
            }
            SubmitButton("Registrarme", form, { vm.register(name, email, password, confirmation, career, semester) })
        }
    }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val saved by vm.baseUrl.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var url by remember(saved) { mutableStateOf(saved) }
    AppScaffold(title = "Conexión", onBack = onBack) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Dirección del backend. En el emulador usa http://10.0.2.2:8080; en un teléfono físico, la IP del computador en la red local, por ejemplo http://192.168.1.20:8080.")
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, label = { Text("URL base") }, singleLine = true,
                        isError = state.urlError != null, supportingText = state.urlError?.let { { Text(it) } },
                        shape = MaterialTheme.shapes.medium, colors = appFieldColors(), modifier = Modifier.fillMaxWidth()
                    )
                    AppButton(onClick = { vm.save(url) }, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
                    AppOutlinedButton(onClick = { vm.test(url) }, enabled = !state.testing, modifier = Modifier.fillMaxWidth()) {
                        if (state.testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Probar conexión")
                    }
                    if (state.saved) SuccessText("Dirección guardada.")
                    state.testMessage?.let {
                        if (state.testOk == true) SuccessText(it) else Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

fun formatMinutes(total: Long): String {
    val days = total / (60 * 24)
    val hours = total % (60 * 24) / 60
    val minutes = total % 60
    return buildList {
        if (days > 0) add("$days d")
        if (hours > 0) add("$hours h")
        if (minutes > 0 || isEmpty()) add("$minutes min")
    }.joinToString(" ")
}

/** Partes de la cuenta regresiva: días, horas y minutos que faltan. */
fun countdownParts(totalMinutes: Long): Triple<Long, Long, Long> {
    val t = totalMinutes.coerceAtLeast(0)
    return Triple(t / (60 * 24), t % (60 * 24) / 60, t % 60)
}

/** Saludo según la hora del día (0 a 23). */
fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Buenos días"
    in 12..18 -> "Buenas tardes"
    else -> "Buenas noches"
}
