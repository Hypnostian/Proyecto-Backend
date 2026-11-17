@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package co.edu.unab.unabgo.cliente

import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.data.repo.*
import co.edu.unab.unabgo.cliente.data.session.InMemorySessionStore
import co.edu.unab.unabgo.cliente.i18n.*
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.screens.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

// ------------------------------------------------------------------------------------------------ datos de prueba

private fun semester(n: Int, credits: Double) = StudyPlanSemesterDto(n, "Semestre $n", credits, listOf(StudyPlanCourseDto("Materia $n", credits)))

/** Plan de 9 semestres de 16 créditos cada uno (144 en total). */
private val sistemasPlan = StudyPlanDto(
    "ingenieria-de-sistemas", "Ingeniería de Sistemas", "Profesional", totalCredits = 144.0,
    semesters = (1..9).map { semester(it, 16.0) }
)

private val sistemas = ProgramSummaryDto(188, "Ingeniería de Sistemas", "Ingeniería", "Profesional", "ingenieria-de-sistemas")

private fun student(language: String = "es", program: ProgramSummaryDto? = sistemas, semester: Int? = 6) =
    UserDto(16, "Juan Sebastian Lizcano Jaimes", "jlizcano471@unab.edu.co", "ESTUDIANTE", profile = ProfileDto(semester = semester, language = language, program = program))

/** Motor falso: traduce agregando el idioma entre corchetes; el wifi y el paquete se controlan desde la prueba. */
private class FakeEngine(var ready: Boolean = true, var wifi: Boolean = true) : TranslationEngine {
    val translated = mutableListOf<String>()
    val failing = mutableSetOf<String>()
    var downloads = 0
    var slowMs = 0L
    override suspend fun isReady(language: String) = ready
    override suspend fun download(language: String) { downloads++; ready = true }
    override suspend fun translate(language: String, text: String): String {
        if (slowMs > 0) kotlinx.coroutines.delay(slowMs)
        translated += text
        if (text in failing) error("no se pudo")
        return "[$language] $text"
    }
    override fun onWifi() = wifi
}

private class FakeAuthRepo(var me: ApiResult<UserDto>) : AuthRepository {
    val profileBodies = mutableListOf<ProfileBody>()
    var store: InMemorySessionStore? = null
    override suspend fun login(email: String, password: String) = me
    override suspend fun register(form: RegisterForm) = me
    override suspend fun logout() {}
    override suspend fun refreshMe() = me.also { r -> if (r is ApiResult.Success) store?.updateUser(r.data) }
    override suspend fun updateProfile(body: ProfileBody): ApiResult<UserDto> {
        profileBodies += body
        val current = (me as? ApiResult.Success)?.data ?: return me
        val updated = current.copy(profile = (current.profile ?: ProfileDto()).copy(language = body.language ?: current.profile?.language ?: "es"))
        me = ApiResult.Success(updated)
        store?.updateUser(updated)
        return me
    }
    override suspend fun health() = ApiResult.Success(HealthDto("ok"))
}

private class FakePlanRepo : StudyPlanRepository {
    val requested = mutableListOf<String>()
    override suspend fun list(page: Int, query: String?, level: String?): ApiResult<List<StudyPlanSummaryDto>> = ApiResult.Success(emptyList())
    override suspend fun get(slug: String): ApiResult<StudyPlanDto> {
        requested += slug
        return if (slug == sistemasPlan.slug) ApiResult.Success(sistemasPlan) else ApiResult.Failure(ApiError(404, "NOT_FOUND", "No existe."))
    }
}

private class FakeCareers : CatalogRepository {
    val calls = mutableListOf<Triple<Int, String?, String?>>()
    override suspend fun programs(page: Int, query: String?) = ApiResult.Success(emptyList<ProgramDto>())
    override suspend fun careers(page: Int, query: String?, level: String?): ApiResult<List<ProgramDto>> {
        calls += Triple(page, query, level)
        val items = listOf(ProgramDto(188, "ingenieria-de-sistemas", "Ingeniería de Sistemas", "Ingeniería", "Profesional", url = "https://u", studyPlanSlug = "ingenieria-de-sistemas"))
        return ApiResult.Success(items, PageMeta(1, 50, 1, 1))
    }
    override suspend fun program(slug: String) = ApiResult.Failure(ApiError(404, "NOT_FOUND", "No existe."))
    override suspend fun professors(page: Int, query: String?) = ApiResult.Success(emptyList<ProfessorDto>())
    override suspend fun professor(slug: String) = ApiResult.Failure(ApiError(404, "NOT_FOUND", "No existe."))
}

/**
 * Ámbito de "app" para las pruebas: corre en el reloj virtual de la prueba (advanceUntilIdle lo avanza, cosa que no
 * hace con backgroundScope) y no es hijo de runTest, así que el trabajador del traductor no impide terminar.
 */
private fun TestScope.appScope() = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

// ------------------------------------------------------------------------------------------------ traductor

class TranslatorTest {

    private fun TestScope.translator(engine: FakeEngine, cache: TranslationCache = InMemoryTranslationCache()) =
        AppTranslator(engine, cache, appScope(), io = StandardTestDispatcher(testScheduler), batchWindowMs = 100, saveDelayMs = 500)

    @Test
    fun soloSeTraducenTextosConPalabras() {
        assertTrue(shouldTranslate("Mi plan de estudios"))
        assertTrue(shouldTranslate("Vas en sexto semestre"))
        assertFalse(shouldTranslate("151"))
        assertFalse(shouldTranslate("10:30"))
        assertTrue(shouldTranslate("10:30 a 12:00"))
        assertFalse(shouldTranslate("jlizcano471@unab.edu.co"))
        assertFalse(shouldTranslate("https://unab.edu.co"))
        assertFalse(shouldTranslate("UNAB GO"))
        assertFalse(shouldTranslate("S6"))
        assertFalse(shouldTranslate("   "))
    }

    @Test
    fun losNombresPropiosNoSeTraducen() {
        val (prepared, terms) = protectTerms("Consulta las Rutas UNAB desde la app")
        assertEquals("Consulta las Rutas UNAB desde la APP", prepared)
        // Lo que devolvería el traductor, con mayúsculas cambiadas
        assertEquals("Check the UNAB Routes from the app", restoreTerms("Check the Unab Routes from the APP", terms))

        // "Banu" no pasa por el traductor: se traduce lo que está alrededor
        val sent = mutableListOf<String>()
        val out = kotlinx.coroutines.runBlocking {
            translateKeepingNames("Preguntar a Banu por el sexto semestre") { sent += it; "<$it>" }
        }
        assertEquals("<Preguntar a> Banu <por el sexto semestre>", out)
        assertEquals(listOf("Preguntar a", "por el sexto semestre"), sent)
        assertEquals("Banu", kotlinx.coroutines.runBlocking { translateKeepingNames("Banu") { "<$it>" } })
        assertEquals("<Bananos>", kotlinx.coroutines.runBlocking { translateKeepingNames("Bananos") { "<$it>" } }) // solo palabras completas
    }

    @Test
    fun conElPaqueteListoTraduceEnTandasYGuardaLoTraducido() = runTest {
        val engine = FakeEngine(ready = true)
        val cache = InMemoryTranslationCache()
        val t = translator(engine, cache)
        t.setLanguage("en")
        advanceUntilIdle()
        assertEquals(TranslationStatus.READY, t.status.value)
        t.request("Mi plan de estudios")
        t.request("Explorar planes de estudio")
        t.request("Mi plan de estudios") // repetido: una sola traducción
        t.request("151") // sin palabras: no se pide
        advanceTimeBy(1000)
        advanceUntilIdle()
        assertEquals("[en] Mi plan de estudios", t.table.value.lookup("Mi plan de estudios"))
        assertEquals("[en] Explorar planes de estudio", t.table.value.lookup("Explorar planes de estudio"))
        assertEquals(2, engine.translated.size)
        assertEquals("[en] Mi plan de estudios", cache.load("en")["Mi plan de estudios"])
    }

    @Test
    fun alVolverAlEspanolSeMuestranLosOriginales() = runTest {
        val t = translator(FakeEngine())
        t.setLanguage("fr")
        advanceUntilIdle()
        t.request("Inicio")
        advanceUntilIdle()
        assertEquals("[fr] Inicio", t.table.value.lookup("Inicio"))
        t.setLanguage("es")
        assertEquals(TranslationStatus.OFF, t.status.value)
        assertEquals("Inicio", t.table.value.lookup("Inicio"))
    }

    @Test
    fun sinWifiEsperaYAlConectarseDescargaYTraduceLoPendiente() = runTest {
        val engine = FakeEngine(ready = false, wifi = false)
        val t = translator(engine)
        t.setLanguage("ko")
        advanceUntilIdle()
        assertEquals(TranslationStatus.WAITING_WIFI, t.status.value)
        assertEquals(0, engine.downloads)
        t.request("Perfil") // se pide mientras se espera el wifi
        advanceUntilIdle()
        assertNull(t.table.value.lookup("Perfil"))

        engine.wifi = true
        t.retry()
        advanceUntilIdle()
        assertEquals(1, engine.downloads)
        assertEquals(TranslationStatus.READY, t.status.value)
        assertEquals("[ko] Perfil", t.table.value.lookup("Perfil"))
    }

    @Test
    fun loGuardadoSeMuestraAntesDeTenerElPaquete() = runTest {
        val cache = InMemoryTranslationCache().apply { save("pt", mapOf("Inicio" to "Início")) }
        val t = translator(FakeEngine(ready = false, wifi = false), cache)
        t.setLanguage("pt")
        advanceUntilIdle()
        assertEquals(TranslationStatus.WAITING_WIFI, t.status.value)
        assertEquals("Início", t.table.value.lookup("Inicio"))
    }

    @Test
    fun unaTraduccionLentaNoSeCortaNiQuedaComoFallida() = runTest {
        val engine = FakeEngine().apply { slowMs = 300 } // más que la ventana de 100 ms
        val t = translator(engine)
        t.setLanguage("en")
        advanceUntilIdle()
        listOf("Idioma", "Mi plan de estudios", "La traducción la hace ML Kit de Google dentro del teléfono").forEach { t.request(it) }
        advanceUntilIdle()
        assertEquals("[en] Idioma", t.table.value.lookup("Idioma"))
        assertEquals("[en] Mi plan de estudios", t.table.value.lookup("Mi plan de estudios"))
        assertEquals("[en] La traducción la hace ML Kit de Google dentro del teléfono", t.table.value.lookup("La traducción la hace ML Kit de Google dentro del teléfono"))
    }

    @Test
    fun unTextoQueFallaNoSeVuelveAPedir() = runTest {
        val engine = FakeEngine().apply { failing += "Texto raro" }
        val t = translator(engine)
        t.setLanguage("en")
        advanceUntilIdle()
        t.request("Texto raro")
        advanceUntilIdle()
        t.request("Texto raro")
        advanceUntilIdle()
        assertEquals(1, engine.translated.count { it == "Texto raro" })
        assertNull(t.table.value.lookup("Texto raro"))
    }
}

// ------------------------------------------------------------------------------------------------ idioma por usuario

class LanguageControllerTest {

    private class Setup(scope: TestScope, sessionUser: UserDto?, engine: FakeEngine = FakeEngine()) {
        val session = InMemorySessionStore(sessionUser?.let { co.edu.unab.unabgo.cliente.data.session.Session("t", it) })
        val auth = FakeAuthRepo(sessionUser?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiError(401, "UNAUTHENTICATED", "Sin sesión.")))
            .also { it.store = session }
        val store = InMemoryLanguageStore()
        private val app = scope.appScope()
        val translator = AppTranslator(engine, InMemoryTranslationCache(), app, io = StandardTestDispatcher(scope.testScheduler))
        val controller = LanguageController(translator, store, session, auth, app)
    }

    @Test
    fun alEntrarSeUsaElIdiomaGuardadoEnElPerfil() = runTest {
        val s = Setup(this, student(language = "fr"))
        s.controller.start()
        advanceUntilIdle()
        assertEquals("fr", s.store.language.value)
        assertEquals("fr", s.translator.language)
    }

    @Test
    fun elegirIdiomaLoGuardaEnElPerfilYSobreviveAlCerrarSesion() = runTest {
        val s = Setup(this, student(language = "es"))
        s.controller.start()
        advanceUntilIdle()
        val result = s.controller.choose("en")
        advanceUntilIdle()
        assertTrue(result is ApiResult.Success)
        assertEquals("en", s.auth.profileBodies.single().language)
        assertEquals("en", s.session.session.value!!.user.profile!!.language)

        // Cierra sesión: el teléfono sigue en inglés
        s.session.clear()
        advanceUntilIdle()
        assertEquals("en", s.translator.language)

        // Al volver a entrar, la misma persona encuentra su idioma
        s.session.save("t2", student(language = "en"))
        advanceUntilIdle()
        assertEquals("en", s.translator.language)

        // Otra persona con español en su perfil ve la app en español
        s.session.save("t3", student(language = "es").copy(id = 99, email = "otra@unab.edu.co"))
        advanceUntilIdle()
        assertEquals("es", s.translator.language)
        assertEquals(TranslationStatus.OFF, s.translator.status.value)
    }

    @Test
    fun sinSesionSoloCambiaEnElTelefono() = runTest {
        val s = Setup(this, null)
        s.controller.start()
        val result = s.controller.choose("pt")
        advanceUntilIdle()
        assertNull(result)
        assertTrue(s.auth.profileBodies.isEmpty())
        assertEquals("pt", s.store.language.value)
    }
}

// ------------------------------------------------------------------------------------------------ carrera y plan propio

class CareerAndMyPlanTest {

    @Test
    fun avanceDelSextoSemestre() {
        val p = semesterProgress(sistemasPlan, 6)!!
        assertEquals(6, p.current)
        assertEquals(9, p.total)
        assertEquals(3, p.remaining)
        assertEquals(80.0, p.creditsBefore, 0.001) // semestres 1 a 5
        assertEquals(144.0, p.totalCredits!!, 0.001)
        assertEquals(9, semesterProgress(sistemasPlan, 12)!!.current) // más allá del plan: el último
        assertNull(semesterProgress(sistemasPlan, null))
        assertNull(semesterProgress(StudyPlanDto("t", "Texto", structure = "texto", text = "Plan"), 3))
    }

    @Test
    fun textosDelSemestre() {
        assertEquals("sexto", semesterOrdinal(6))
        assertEquals("primer", semesterOrdinal(1))
        assertEquals("¿Qué materias tiene el sexto semestre de Ingeniería de Sistemas?", semesterQuestion("Ingeniería de Sistemas", 6))
        assertEquals("Ingeniería de Sistemas, sexto semestre", careerLine(student().profile))
        assertNull(careerLine(student(program = null).profile))
        assertEquals(9, semesterOptions(sistemasPlan))
        assertEquals(MAX_SEMESTER, semesterOptions(null))
    }
}

class CareerPickerViewModelTest : VmTest() {

    @Test
    fun cargaLasCarrerasAlAbrirYAlElegirTraeSuPlan() = runTest(dispatcher) {
        val careers = FakeCareers()
        val plans = FakePlanRepo()
        val vm = CareerPickerViewModel(careers, plans)
        advanceUntilIdle()
        assertEquals(Triple(1, null, DEFAULT_CAREER_LEVEL), careers.calls.first())
        assertEquals(1, vm.list.value.items.size)

        vm.select(vm.list.value.items.first().toChoice())
        advanceUntilIdle()
        assertEquals(188L, vm.selected.value!!.id)
        assertEquals(9, vm.plan.value!!.semesters.size)

        vm.selectLevel(null)
        advanceUntilIdle()
        assertEquals(Triple(1, null, null), careers.calls.last())
    }
}

class MyPlanViewModelTest : VmTest() {

    @Test
    fun muestraSoloElPlanDeLaCarreraDelPerfil() = runTest(dispatcher) {
        val session = InMemorySessionStore(co.edu.unab.unabgo.cliente.data.session.Session("t", student()))
        val auth = FakeAuthRepo(ApiResult.Success(student())).also { it.store = session }
        val plans = FakePlanRepo()
        val vm = MyPlanViewModel(auth, plans, session)
        advanceUntilIdle()
        val data = (vm.state.value as UiState.Ready).data
        assertEquals("ingenieria-de-sistemas", data.plan!!.slug)
        assertEquals(6, data.semester)
        assertEquals(listOf("ingenieria-de-sistemas"), plans.requested)
    }

    @Test
    fun sinCarreraNoPideNingunPlan() = runTest(dispatcher) {
        val user = student(program = null, semester = null)
        val session = InMemorySessionStore(co.edu.unab.unabgo.cliente.data.session.Session("t", user))
        val plans = FakePlanRepo()
        val vm = MyPlanViewModel(FakeAuthRepo(ApiResult.Success(user)), plans, session)
        advanceUntilIdle()
        val data = (vm.state.value as UiState.Ready).data
        assertNull(data.career)
        assertTrue(plans.requested.isEmpty())
    }

    @Test
    fun sinConexionUsaElPerfilGuardado() = runTest(dispatcher) {
        val session = InMemorySessionStore(co.edu.unab.unabgo.cliente.data.session.Session("t", student()))
        val vm = MyPlanViewModel(FakeAuthRepo(ApiResult.Failure(ApiError.network("Sin conexión."))), FakePlanRepo(), session)
        advanceUntilIdle()
        assertEquals(6, (vm.state.value as UiState.Ready).data.semester)
    }
}
