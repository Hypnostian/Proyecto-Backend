package co.edu.unab.unabgo.cliente.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.edu.unab.unabgo.cliente.data.api.NextClassDto
import co.edu.unab.unabgo.cliente.data.repo.SummaryKind
import co.edu.unab.unabgo.cliente.data.session.Roles
import co.edu.unab.unabgo.cliente.data.session.Session
import co.edu.unab.unabgo.cliente.i18n.Text
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.components.*
import co.edu.unab.unabgo.cliente.ui.theme.LocalReduceMotion
import co.edu.unab.unabgo.cliente.ui.theme.LocalRolePalette
import java.time.LocalTime

private val DAYS = listOf("", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")

/** Secciones de la barra inferior. */
enum class HomeSection { HOME, WORK, UNIVERSITY, PROFILE }

data class TabSpec(val section: HomeSection, val label: String)

/** Pestañas de la barra inferior según el rol: Inicio, Académico, Gestión o Administración, Universidad y Perfil. */
fun tabsFor(role: String?): List<TabSpec> = listOf(
    TabSpec(HomeSection.HOME, "Inicio"),
    TabSpec(
        HomeSection.WORK,
        when (role) {
            Roles.MANAGER -> "Gestión"
            Roles.ADMIN -> "Administración"
            else -> "Académico"
        }
    ),
    TabSpec(HomeSection.UNIVERSITY, "Universidad"),
    TabSpec(HomeSection.PROFILE, "Perfil")
)

/** Destinos de la pantalla de inicio. */
class HomeActions(
    val subjects: () -> Unit, val profile: () -> Unit, val announcements: () -> Unit, val professors: () -> Unit,
    val programs: () -> Unit, val studyPlans: () -> Unit, val bus: () -> Unit, val banu: () -> Unit, val faqs: () -> Unit,
    val users: () -> Unit, val sync: () -> Unit, val settings: () -> Unit,
    val myPlan: () -> Unit = studyPlans, val language: () -> Unit = settings
)

private fun iconFor(section: HomeSection, role: String?): ImageVector = when (section) {
    HomeSection.HOME -> Icons.Default.Home
    HomeSection.WORK -> when (role) {
        Roles.MANAGER -> Icons.Default.Build
        Roles.ADMIN -> Icons.Default.Lock
        else -> Icons.Default.DateRange
    }
    HomeSection.UNIVERSITY -> Icons.Default.Place
    HomeSection.PROFILE -> Icons.Default.AccountCircle
}

@Composable
fun HomeScreen(session: Session, nextClassVm: NextClassViewModel?, summaryVm: HomeSummaryViewModel?, loggingOut: Boolean, onLogout: () -> Unit, go: HomeActions) {
    val role = session.user.role
    val tabs = remember(role) { tabsFor(role) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = { AppBottomBar(tabs, role, selected) { selected = it } }
    ) { padding ->
        AnimatedContent(
            targetState = selected,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
            transitionSpec = {
                if (reduce) EnterTransition.None togetherWith ExitTransition.None
                else {
                    val dir = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { dir * it / 10 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(260)) { -dir * it / 10 })
                }
            },
            label = "tabs"
        ) { index ->
            when (tabs[index].section) {
                HomeSection.HOME -> HomeTab(session, nextClassVm, summaryVm, go)
                HomeSection.WORK -> WorkTab(session, tabs[index].label, go)
                HomeSection.UNIVERSITY -> UniversityTab(go)
                HomeSection.PROFILE -> ProfileTab(session, loggingOut, go, onAskLogout = { confirm = true })
            }
        }
    }
    if (confirm) ConfirmDialog("Cerrar sesión", "¿Quieres salir de tu cuenta?", "Salir", { confirm = false; onLogout() }, { confirm = false })
}

@Composable
private fun AppBottomBar(tabs: List<TabSpec>, role: String?, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalRolePalette.current
    Column {
        Box(Modifier.fillMaxWidth().height(3.dp).background(p.primary))
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            tabs.forEachIndexed { i, t ->
                val isSelected = i == selected
                val scale by animateFloatAsState(if (isSelected) 1.18f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "scale")
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(i) },
                    icon = { Icon(iconFor(t.section, role), contentDescription = null, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) },
                    label = { Text(t.label, maxLines = 1, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = p.onContainer, selectedTextColor = p.accentText, indicatorColor = p.container,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ inicio

@Composable
private fun HomeTab(session: Session, nextClassVm: NextClassViewModel?, summaryVm: HomeSummaryViewModel?, go: HomeActions) {
    val p = LocalRolePalette.current
    val counter = remember { EntranceCounter() }
    val greeting = remember { greetingFor(LocalTime.now().hour) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GradientHeader {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LogoWordmark(36.dp, p.onGradient)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = go.settings) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes de conexión", tint = p.onGradient)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(greeting, color = p.onGradient.copy(alpha = 0.9f), style = MaterialTheme.typography.titleMedium)
                Text(
                    session.user.name, color = p.onGradient, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold, maxLines = 2, translate = false
                )
                Spacer(Modifier.height(10.dp))
                AppChip(p.label, container = p.onGradient.copy(alpha = 0.18f), content = p.onGradient)
            }
        }
        CompositionLocalProvider(LocalEntranceCounter provides counter) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (nextClassVm != null) {
                    val next by nextClassVm.state.collectAsStateWithLifecycle()
                    NextClassCard(next, nextClassVm::refresh)
                }
                if (session.isStudent) MyCareerCard(session, go)
                if (summaryVm != null) SummarySection(summaryVm, go)
                SectionTitle("Accesos rápidos")
                quickTiles(session, go).chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { t -> QuickTile(t.icon, t.title, t.onClick, Modifier.weight(1f).fillMaxHeight()) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ resumen

private class StatSpec(val icon: ImageVector, val label: String, val live: Boolean = false)

private fun statSpec(kind: SummaryKind) = when (kind) {
    SummaryKind.ANNOUNCEMENTS -> StatSpec(Icons.Default.Notifications, "Avisos publicados")
    SummaryKind.FAQS -> StatSpec(Icons.Default.Face, "Preguntas de Banu activas")
    SummaryKind.ROUTES -> StatSpec(Icons.Default.Place, "Rutas activas")
    SummaryKind.LIVE_BUSES -> StatSpec(Icons.Default.Info, "Buses en vivo", live = true)
    SummaryKind.USERS -> StatSpec(Icons.Default.Person, "Usuarios registrados")
    SummaryKind.PROGRAMS -> StatSpec(Icons.Default.Info, "Programas académicos")
    SummaryKind.STUDY_PLANS -> StatSpec(Icons.Default.List, "Planes de estudio")
}

private fun statAction(kind: SummaryKind, go: HomeActions): () -> Unit = when (kind) {
    SummaryKind.ANNOUNCEMENTS -> go.announcements
    SummaryKind.FAQS -> go.faqs
    SummaryKind.ROUTES, SummaryKind.LIVE_BUSES -> go.bus
    SummaryKind.USERS -> go.users
    SummaryKind.PROGRAMS -> go.programs
    SummaryKind.STUDY_PLANS -> go.studyPlans
}

@Composable
private fun SummarySection(vm: HomeSummaryViewModel, go: HomeActions) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }
    val visible = vm.kinds.filter { stats[it] != StatState.Hidden }
    if (visible.isEmpty()) return
    SectionTitle("Resumen")
    visible.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { kind ->
                // Con un número impar de tarjetas, la última ocupa todo el ancho en vez de dejar un hueco
                StatCard(statSpec(kind), stats[kind] ?: StatState.Loading, statAction(kind, go), Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun StatCard(spec: StatSpec, state: StatState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalRolePalette.current
    AppCard(onClick = onClick, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(p.container), contentAlignment = Alignment.Center) {
                Icon(spec.icon, contentDescription = null, tint = p.onContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (spec.live && state is StatState.Value && state.total > 0) PulsingDot(p.success)
        }
        Spacer(Modifier.height(10.dp))
        when (state) {
            is StatState.Value -> AnimatedNumber(
                state.total.toFloat(), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            else -> Box(Modifier.padding(vertical = 6.dp)) { SkeletonBlock(height = 24.dp, fraction = 0.45f) }
        }
        Text(spec.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class Tile(val icon: ImageVector, val title: String, val onClick: () -> Unit)

private fun quickTiles(session: Session, go: HomeActions): List<Tile> = when {
    session.isAdmin -> listOf(
        Tile(Icons.Default.Person, "Usuarios y roles", go.users),
        Tile(Icons.Default.Refresh, "Sincronizar programas", go.sync),
        Tile(Icons.Default.Notifications, "Avisos", go.announcements),
        Tile(Icons.Default.Face, "Preguntas de Banu", go.faqs)
    )
    session.isManager -> listOf(
        Tile(Icons.Default.Notifications, "Avisos", go.announcements),
        Tile(Icons.Default.Face, "Preguntas de Banu", go.faqs),
        Tile(Icons.Default.List, "Explorar planes de estudio", go.studyPlans),
        Tile(Icons.Default.Place, "Rutas UNAB", go.bus)
    )
    else -> listOf(
        Tile(Icons.Default.DateRange, "Mis materias", go.subjects),
        Tile(Icons.Default.List, "Mi plan de estudios", go.myPlan),
        Tile(Icons.Default.Place, "Rutas UNAB", go.bus),
        Tile(Icons.Default.Face, "Preguntar a Banu", go.banu)
    )
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalRolePalette.current
    AppCard(onClick = onClick, modifier = modifier) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(p.container), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = p.onContainer)
        }
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CountBox(value: Long, label: String) {
    val p = LocalRolePalette.current
    Surface(color = p.primary, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.width(74.dp).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedNumber(value.toFloat(), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = p.onPrimary)
            Text(label, color = p.onPrimary.copy(alpha = 0.9f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PulsingDot(color: Color) {
    val scale = if (LocalReduceMotion.current) 1f else rememberInfiniteTransition(label = "pulse").animateFloat(
        0.7f, 1.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse"
    ).value
    Box(Modifier.size(12.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(color))
}

@Composable
private fun NextClassCard(state: UiState<NextClassDto?>, onRefresh: () -> Unit) {
    val p = LocalRolePalette.current
    AppCard(container = p.container, accent = p.primary) {
        Text("Mi próxima clase", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = p.onContainer)
        Spacer(Modifier.height(8.dp))
        when (state) {
            UiState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SkeletonBlock(height = 22.dp, fraction = 0.7f)
                SkeletonBlock(fraction = 0.9f)
                SkeletonBlock(height = 48.dp, fraction = 0.6f)
            }
            is UiState.Failed -> {
                Text(state.error.userMessage, color = MaterialTheme.colorScheme.error)
                AppTextButton(onClick = onRefresh) { Text("Reintentar") }
            }
            is UiState.Ready -> {
                val c = state.data
                if (c == null) Text("Todavía no tienes clases en tu horario. Crea una materia con sus bloques.", color = p.onContainer)
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(c.subjectName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = p.onContainer)
                    Text(
                        "${DAYS.getOrElse(c.dayOfWeek) { "" }} ${c.date}, de ${c.startTime.take(5)} a ${c.endTime.take(5)}" + (c.room?.let { ", en $it" } ?: ""),
                        color = p.onContainer
                    )
                    if (c.inProgress) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PulsingDot(p.success)
                            Text("En curso ahora", color = p.onContainer, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val (days, hours, minutes) = countdownParts(c.startsInMinutes)
                        Text("Empieza en", color = p.onContainer, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (days > 0) CountBox(days, if (days == 1L) "día" else "días")
                            if (days > 0 || hours > 0) CountBox(hours, if (hours == 1L) "hora" else "horas")
                            CountBox(minutes, "min")
                        }
                    }
                    c.teacher?.let { Text("Docente: $it", color = p.onContainer, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

/** "Ingeniería de Sistemas, sexto semestre", o null si no hay carrera en el perfil. */
fun careerLine(profile: co.edu.unab.unabgo.cliente.data.api.ProfileDto?): String? {
    val name = profile?.program?.name ?: return null
    return profile.semester?.let { "$name, ${semesterOrdinal(it)} semestre" } ?: name
}

/** Carrera y semestre del estudiante; abre Mi plan de estudios (o el perfil si aún no elige carrera). */
@Composable
private fun MyCareerCard(session: Session, go: HomeActions) {
    val p = LocalRolePalette.current
    val profile = session.user.profile
    val career = profile?.program
    val semester = profile?.semester
    AppCard(onClick = if (career != null) go.myPlan else go.profile, accent = BRAND_ORANGE) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(BRAND_ORANGE), contentAlignment = Alignment.Center) {
                Text(semester?.toString() ?: "?", color = BRAND_INK, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text("Mi carrera", style = MaterialTheme.typography.labelMedium, color = p.accentText, fontWeight = FontWeight.Bold)
                Text(career?.name ?: "Elige tu carrera", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                Text(
                    when {
                        career == null -> "Así verás solo el plan de estudios de tu carrera."
                        semester != null -> "Vas en ${semesterOrdinal(semester)} semestre. Toca para ver tu plan."
                        else -> "Toca para ver tu plan de estudios."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------------------------------------ menús por sección

private class MenuEntry(val icon: ImageVector, val title: String, val subtitle: String, val onClick: () -> Unit)

@Composable
private fun MenuPage(title: String, intro: String, entries: List<MenuEntry>) {
    val counter = remember { EntranceCounter() }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title)
        CompositionLocalProvider(LocalEntranceCounter provides counter) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(intro, color = MaterialTheme.colorScheme.onSurfaceVariant)
                entries.forEach { e -> MenuCard(e) }
            }
        }
    }
}

@Composable
private fun MenuCard(e: MenuEntry) {
    val p = LocalRolePalette.current
    AppCard(onClick = e.onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(46.dp).clip(MaterialTheme.shapes.medium).background(p.container), contentAlignment = Alignment.Center) {
                Icon(e.icon, contentDescription = null, tint = p.onContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(e.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WorkTab(session: Session, label: String, go: HomeActions) {
    val entries = remember(session.user.role) {
        buildList {
            if (session.isStudent) {
                add(MenuEntry(Icons.Default.DateRange, "Mis materias", "Horario, cortes, notas y lo que necesitas para aprobar", go.subjects))
                add(
                    MenuEntry(
                        Icons.Default.List, "Mi plan de estudios",
                        careerLine(session.user.profile) ?: "Elige tu carrera para ver su plan", go.myPlan
                    )
                )
                add(MenuEntry(Icons.Default.Search, "Explorar planes de estudio", "Planes de estudio de los demás programas de la UNAB", go.studyPlans))
                add(MenuEntry(Icons.Default.AccountCircle, "Mi perfil", "Carrera, semestre, idioma y recordatorio", go.profile))
            }
            if (session.isAdmin) {
                add(MenuEntry(Icons.Default.Person, "Usuarios", "Lista de usuarios y cambio de rol", go.users))
                add(MenuEntry(Icons.Default.Refresh, "Sincronizar programas", "Actualiza los programas desde el sitio de la UNAB", go.sync))
            }
            if (session.isManager) {
                add(MenuEntry(Icons.Default.Notifications, "Avisos", "Crea, edita y retira avisos", go.announcements))
                add(MenuEntry(Icons.Default.Face, "Preguntas de Banu", "Crea, edita y desactiva las preguntas frecuentes", go.faqs))
                add(MenuEntry(Icons.Default.Place, "Rutas UNAB", "Edita rutas y revisa los buses en vivo", go.bus))
            }
        }
    }
    val intro = when (session.user.role) {
        Roles.ADMIN -> "Herramientas de administración y de gestión."
        Roles.MANAGER -> "Publica avisos, mantén las respuestas de Banu y edita las rutas."
        else -> "Tu vida académica en un solo lugar."
    }
    MenuPage(label, intro, entries)
}

@Composable
private fun UniversityTab(go: HomeActions) {
    val entries = remember {
        listOf(
            MenuEntry(Icons.Default.Notifications, "Avisos", "Noticias de las dependencias, con filtro por categoría", go.announcements),
            MenuEntry(Icons.Default.Person, "Profesores", "Busca profesores y abre su perfil público", go.professors),
            MenuEntry(Icons.Default.Info, "Programas", "Oferta académica de la UNAB", go.programs),
            MenuEntry(Icons.Default.List, "Explorar planes de estudio", "Semestres, materias y créditos de todos los programas", go.studyPlans),
            MenuEntry(Icons.Default.Place, "Rutas UNAB", "Rutas y buses en vivo", go.bus),
            MenuEntry(Icons.Default.Face, "Banu", "Pregúntale al asistente de la universidad", go.banu)
        )
    }
    MenuPage("Universidad", "Información pública de la UNAB.", entries)
}

// ------------------------------------------------------------------------------------------------ perfil

@Composable
private fun ProfileTab(session: Session, loggingOut: Boolean, go: HomeActions, onAskLogout: () -> Unit) {
    val p = LocalRolePalette.current
    val user = session.user
    val counter = remember { EntranceCounter() }
    Column(Modifier.fillMaxSize()) {
        AppTopBar("Perfil")
        CompositionLocalProvider(LocalEntranceCounter provides counter) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(p.primary), contentAlignment = Alignment.Center) {
                            Text(
                                user.name.trim().take(1).uppercase(), color = p.onPrimary,
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, translate = false)
                            Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, translate = false)
                            AppChip(p.label)
                        }
                    }
                    if (session.isStudent) {
                        val profile = user.profile
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                        Text(profile?.program?.name ?: "Sin carrera elegida", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        profile?.semester?.let {
                            Text("${semesterOrdinal(it).replaceFirstChar { c -> c.uppercase() }} semestre", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                LanguageRow(user.profile?.language, go.language)
                if (session.isStudent) {
                    AppButton(onClick = go.profile, modifier = Modifier.fillMaxWidth()) { Text("Editar mi perfil") }
                }
                AppOutlinedButton(onClick = go.settings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Conexión con el servidor")
                }
                AppOutlinedButton(onClick = onAskLogout, enabled = !loggingOut, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (loggingOut) "Saliendo" else "Cerrar sesión")
                }
                Text(
                    "UNAB GO Cliente, versión 1.0", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}
