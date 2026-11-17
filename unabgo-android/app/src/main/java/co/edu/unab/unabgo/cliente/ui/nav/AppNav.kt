package co.edu.unab.unabgo.cliente.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.navArgument
import co.edu.unab.unabgo.cliente.AppContainer
import co.edu.unab.unabgo.cliente.ui.screens.*
import co.edu.unab.unabgo.cliente.ui.theme.LocalReduceMotion

@Composable
private inline fun <reified VM : ViewModel> viewModelOf(key: String? = null, crossinline create: () -> VM): VM =
    viewModel(key = key, factory = viewModelFactory { initializer { create() } })

object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val SETTINGS = "settings"
    const val HOME = "home"
    const val SUBJECTS = "subjects"
    const val SUBJECT = "subject/{id}"
    const val SUBJECT_FORM = "subject_form?id={id}"
    const val PROFILE = "profile"
    const val ANNOUNCEMENTS = "announcements"
    const val PROFESSORS = "professors"
    const val PROFESSOR = "professor/{slug}"
    const val PROGRAMS = "programs"
    const val PROGRAM = "program/{slug}"
    const val BUS = "bus"
    const val STUDY_PLANS = "study_plans"
    const val MY_PLAN = "my_plan"
    const val LANGUAGE = "language"
    const val STUDY_PLAN = "study_plan/{slug}"
    const val BANU = "banu?q={q}"
    const val FAQS = "faqs"
    const val USERS = "admin_users"
    const val SYNC = "admin_sync"
}

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val session by container.session.session.collectAsStateWithLifecycle()
    val start = remember { if (container.session.session.value == null) Routes.LOGIN else Routes.HOME }

    // La sesión manda: sin sesión (cierre o 401) se vuelve al login; con sesión se sale del login.
    LaunchedEffect(session) {
        val current = nav.currentDestination?.route
        if (session == null && current !in setOf(Routes.LOGIN, Routes.REGISTER, Routes.SETTINGS, Routes.LANGUAGE)) {
            nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
        } else if (session != null && (current == Routes.LOGIN || current == Routes.REGISTER)) {
            nav.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
        }
    }

    val reduce = LocalReduceMotion.current
    val enter: () -> EnterTransition = { if (reduce) EnterTransition.None else fadeIn(tween(260)) + slideInHorizontally(tween(300)) { it / 8 } }
    val exit: () -> ExitTransition = { if (reduce) ExitTransition.None else fadeOut(tween(160)) + slideOutHorizontally(tween(300)) { -it / 12 } }
    val popEnter: () -> EnterTransition = { if (reduce) EnterTransition.None else fadeIn(tween(260)) + slideInHorizontally(tween(300)) { -it / 12 } }
    val popExit: () -> ExitTransition = { if (reduce) ExitTransition.None else fadeOut(tween(160)) + slideOutHorizontally(tween(300)) { it / 8 } }

    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val status by container.translator.status.collectAsStateWithLifecycle()
    val language by container.languages.current.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        NavHost(
            nav, startDestination = start,
            enterTransition = { enter() }, exitTransition = { exit() }, popEnterTransition = { popEnter() }, popExitTransition = { popExit() }
        ) {
            composable(Routes.LOGIN) {
                val model = viewModelOf { AuthViewModel(container.auth) { container.languages.current.value } }
                LoginScreen(
                    model, onRegister = { nav.navigate(Routes.REGISTER) }, onSettings = { nav.navigate(Routes.SETTINGS) },
                    language = language, onLanguage = { nav.navigate(Routes.LANGUAGE) }
                )
            }
            composable(Routes.REGISTER) {
                val model = viewModelOf { AuthViewModel(container.auth) { container.languages.current.value } }
                // La lista de carreras se carga apenas se abre el registro.
                val careers = viewModelOf { CareerPickerViewModel(container.catalog, container.studyPlans) }
                RegisterScreen(model, careers, onBack = { nav.popBackStack() })
            }
            composable(Routes.LANGUAGE) {
                LanguageScreen(viewModelOf { LanguageViewModel(container.languages) }, loggedIn = session != null, onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                val model = viewModelOf { SettingsViewModel(container.settings, container.auth, container.allowCleartext) }
                SettingsScreen(model, onBack = { nav.popBackStack() })
            }
            composable(Routes.HOME) {
                val s = session ?: return@composable
                val sessionVm = viewModelOf { SessionViewModel(container.auth, container.session) }
                val nextVm = if (s.isStudent) viewModelOf { NextClassViewModel(container.subjects) } else null
                val summaryVm = if (s.isAdmin || s.isManager) viewModelOf { HomeSummaryViewModel(container.summary, summaryKindsFor(s.user.role)) } else null
                val loggingOut by sessionVm.loggingOut.collectAsStateWithLifecycle()
                HomeScreen(
                    s, nextVm, summaryVm, loggingOut, sessionVm::logout,
                    HomeActions(
                        subjects = { nav.navigate(Routes.SUBJECTS) }, profile = { nav.navigate(Routes.PROFILE) },
                        announcements = { nav.navigate(Routes.ANNOUNCEMENTS) }, professors = { nav.navigate(Routes.PROFESSORS) },
                        programs = { nav.navigate(Routes.PROGRAMS) }, studyPlans = { nav.navigate(Routes.STUDY_PLANS) },
                        bus = { nav.navigate(Routes.BUS) }, banu = { nav.navigate("banu") },
                        faqs = { nav.navigate(Routes.FAQS) }, users = { nav.navigate(Routes.USERS) }, sync = { nav.navigate(Routes.SYNC) },
                        settings = { nav.navigate(Routes.SETTINGS) },
                        myPlan = { nav.navigate(Routes.MY_PLAN) }, language = { nav.navigate(Routes.LANGUAGE) }
                    )
                )
            }
            academicGraph(nav, container) { session }
            publicGraph(nav, container) { session }
            manageGraph(nav, container)
        }
        // Aviso de descarga del idioma en cualquier pantalla, menos en la de idioma (que ya muestra el estado).
        if (route != Routes.LANGUAGE) {
            TranslationStatusBanner(status, language, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp))
        }
    }
}

private fun androidx.navigation.NavGraphBuilder.academicGraph(
    nav: NavHostController,
    c: AppContainer,
    session: () -> co.edu.unab.unabgo.cliente.data.session.Session?
) {
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    composable(Routes.SUBJECTS) {
        SubjectsScreen(
            viewModelOf { SubjectsViewModel(c.subjects) }, { nav.popBackStack() },
            onOpen = { nav.navigate("subject/$it") }, onCreate = { nav.navigate("subject_form") }, onEdit = { nav.navigate("subject_form?id=$it") }
        )
    }
    composable(Routes.SUBJECT, arguments = idArg) { e ->
        val id = e.arguments!!.getLong("id")
        SubjectDetailScreen(viewModelOf(key = "subject$id") { SubjectDetailViewModel(c.subjects, id) }, { nav.popBackStack() }, onEdit = { nav.navigate("subject_form?id=$it") })
    }
    composable(Routes.SUBJECT_FORM, arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { e ->
        val id = e.arguments!!.getLong("id").takeIf { it >= 0 }
        SubjectFormScreen(
            viewModelOf(key = "form$id") { SubjectFormViewModel(c.subjects, id) }, isEdit = id != null,
            onBack = { nav.popBackStack() }, onSaved = { nav.popBackStack(Routes.SUBJECTS, false) }
        )
    }
    composable(Routes.PROFILE) {
        val careers = viewModelOf { CareerPickerViewModel(c.catalog, c.studyPlans, session()?.user?.profile?.program?.toChoice()) }
        ProfileScreen(viewModelOf { ProfileViewModel(c.auth, c.session) }, careers, { nav.popBackStack() }, onLanguage = { nav.navigate(Routes.LANGUAGE) })
    }
    composable(Routes.MY_PLAN) {
        MyPlanScreen(
            viewModelOf { MyPlanViewModel(c.auth, c.studyPlans, c.session) }, { nav.popBackStack() },
            onChooseCareer = { nav.navigate(Routes.PROFILE) }, onExplore = { nav.navigate(Routes.STUDY_PLANS) },
            onOpenProgram = { nav.navigate("program/$it") },
            onAskBanu = { nav.navigate("banu?q=${android.net.Uri.encode(it)}") }
        )
    }
}

private fun androidx.navigation.NavGraphBuilder.publicGraph(nav: NavHostController, c: AppContainer, session: () -> co.edu.unab.unabgo.cliente.data.session.Session?) {
    composable(Routes.ANNOUNCEMENTS) {
        AnnouncementsScreen(
            viewModelOf { AnnouncementsViewModel(c.announcements) }, viewModelOf { AnnouncementFormViewModel(c.announcements) },
            canManage = session()?.isManager == true, onBack = { nav.popBackStack() }
        )
    }
    composable(Routes.PROFESSORS) {
        ProfessorsScreen(viewModelOf { ProfessorsViewModel(c.catalog) }, { nav.popBackStack() }, onOpen = { nav.navigate("professor/$it") })
    }
    composable(Routes.PROFESSOR, arguments = listOf(navArgument("slug") { type = NavType.StringType })) { e ->
        val slug = e.arguments!!.getString("slug").orEmpty()
        ProfessorDetailScreen(viewModelOf(key = "prof$slug") { ProfessorDetailViewModel(c.catalog, slug) }, { nav.popBackStack() })
    }
    composable(Routes.PROGRAMS) {
        ProgramsScreen(viewModelOf { ProgramsViewModel(c.catalog) }, { nav.popBackStack() }, onOpen = { nav.navigate("program/$it") })
    }
    composable(Routes.PROGRAM, arguments = listOf(navArgument("slug") { type = NavType.StringType })) { e ->
        val slug = e.arguments!!.getString("slug").orEmpty()
        ProgramDetailScreen(
            viewModelOf(key = "prog$slug") { ProgramDetailViewModel(c.catalog, slug) }, { nav.popBackStack() },
            onOpenStudyPlan = { nav.navigate("study_plan/$it") }
        )
    }
    composable(Routes.STUDY_PLANS) {
        val s = session()
        StudyPlansScreen(
            viewModelOf { StudyPlansViewModel(c.studyPlans) }, { nav.popBackStack() }, onOpen = { nav.navigate("study_plan/$it") },
            myCareer = if (s?.isStudent == true) s.user.profile?.program?.toChoice() else null,
            onOpenMine = { nav.navigate(Routes.MY_PLAN) }
        )
    }
    composable(Routes.STUDY_PLAN, arguments = listOf(navArgument("slug") { type = NavType.StringType })) { e ->
        val slug = e.arguments!!.getString("slug").orEmpty()
        StudyPlanDetailScreen(
            viewModelOf(key = "plan$slug") { StudyPlanDetailViewModel(c.studyPlans, slug) }, { nav.popBackStack() },
            onOpenProgram = { nav.navigate("program/$it") },
            onAskBanu = { nav.navigate("banu?q=${android.net.Uri.encode(it)}") },
            currentSemesterFor = { slug ->
                val profile = session()?.takeIf { it.isStudent }?.user?.profile
                if (profile?.program?.studyPlanSlug == slug) profile?.semester else null
            }
        )
    }
    composable(Routes.BUS) {
        val s = session()
        val student = s?.isStudent == true
        BusScreen(viewModelOf { BusViewModel(c.bus, student) }, canFavorite = student, canEdit = s?.isManager == true, onBack = { nav.popBackStack() })
    }
    composable(Routes.BANU, arguments = listOf(navArgument("q") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
        BanuScreen(viewModelOf { BanuViewModel(c.banu) }, { nav.popBackStack() }, initialQuestion = e.arguments?.getString("q"))
    }
}

private fun androidx.navigation.NavGraphBuilder.manageGraph(nav: NavHostController, c: AppContainer) {
    composable(Routes.FAQS) {
        FaqsScreen(viewModelOf { FaqsViewModel(c.faqs) }, viewModelOf { FaqFormViewModel(c.faqs) }, { nav.popBackStack() })
    }
    composable(Routes.USERS) {
        AdminUsersScreen(viewModelOf { AdminViewModel(c.admin) }, { nav.popBackStack() })
    }
    composable(Routes.SYNC) {
        SyncScreen(viewModelOf { AdminViewModel(c.admin) }, { nav.popBackStack() })
    }
}
