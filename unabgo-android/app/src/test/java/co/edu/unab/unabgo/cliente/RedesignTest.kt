@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package co.edu.unab.unabgo.cliente

import androidx.compose.ui.graphics.Color
import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.data.repo.*
import co.edu.unab.unabgo.cliente.data.session.Roles
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.screens.*
import co.edu.unab.unabgo.cliente.ui.theme.Brand
import co.edu.unab.unabgo.cliente.ui.theme.rolePalette
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.pow

private fun lum(c: Color): Double {
    fun ch(v: Float): Double = v.toDouble().let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
}

private fun contrast(a: Color, b: Color): Double {
    val x = lum(a)
    val y = lum(b)
    val hi = maxOf(x, y)
    val lo = minOf(x, y)
    return (hi + 0.05) / (lo + 0.05)
}

class RolePaletteTest {
    @Test
    fun cadaRolTieneSuColor() {
        assertEquals(Brand.Purple, rolePalette(Roles.STUDENT).primary)
        assertEquals(Brand.Orange, rolePalette(Roles.MANAGER).primary)
        assertEquals(Brand.CyanDeep, rolePalette(Roles.ADMIN).primary)
    }

    @Test
    fun elNaranjaLlevaTextoOscuroYLosDemasBlanco() {
        assertEquals(Brand.Ink, rolePalette(Roles.MANAGER).onPrimary)
        assertEquals(Color.White, rolePalette(Roles.STUDENT).onPrimary)
        assertEquals(Color.White, rolePalette(Roles.ADMIN).onPrimary)
    }

    @Test
    fun sinSesionOUnRolDesconocidoUsaLaPaletaNeutra() {
        assertTrue(rolePalette(null).neutral)
        assertTrue(rolePalette("OTRO").neutral)
        assertFalse(rolePalette(Roles.STUDENT).neutral)
    }

    @Test
    fun lasPaletasTienenEtiquetaPropia() {
        assertEquals("Estudiante", rolePalette(Roles.STUDENT).label)
        assertEquals("Gestor", rolePalette(Roles.MANAGER).label)
        assertEquals("Administrador", rolePalette(Roles.ADMIN).label)
    }

    @Test
    fun elTextoSobreElColorPrincipalCumpleContraste() {
        listOf(Roles.STUDENT, Roles.MANAGER, Roles.ADMIN, null).forEach { role ->
            listOf(false, true).forEach { dark ->
                val p = rolePalette(role, dark)
                assertTrue("$role dark=$dark primary", contrast(p.primary, p.onPrimary) >= 4.5)
                assertTrue("$role dark=$dark gradiente", contrast(p.gradientStart, p.onGradient) >= 4.5 && contrast(p.gradientEnd, p.onGradient) >= 4.5)
                assertTrue("$role dark=$dark contenedor", contrast(p.container, p.onContainer) >= 4.5)
            }
        }
    }

    @Test
    fun elTextoDeAcentoSobreBlancoCumpleContraste() {
        listOf(Roles.STUDENT, Roles.MANAGER, Roles.ADMIN).forEach { role ->
            assertTrue(role, contrast(rolePalette(role).accentText, Color.White) >= 4.5)
        }
    }

    @Test
    fun lasPestanasDependenDelRol() {
        assertEquals(listOf("Inicio", "Académico", "Universidad", "Perfil"), tabsFor(Roles.STUDENT).map { it.label })
        assertEquals("Gestión", tabsFor(Roles.MANAGER)[1].label)
        assertEquals("Administración", tabsFor(Roles.ADMIN)[1].label)
    }

    @Test
    fun saludoYCuentaRegresiva() {
        assertEquals("Buenos días", greetingFor(8))
        assertEquals("Buenas tardes", greetingFor(15))
        assertEquals("Buenas noches", greetingFor(22))
        assertEquals("Buenas noches", greetingFor(2))
        assertEquals(Triple(1L, 1L, 5L), countdownParts(24 * 60 + 65))
        assertEquals(Triple(0L, 0L, 0L), countdownParts(-3))
    }
}

class StudyPlanApiTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: StudyPlanRepository

    private fun client() = ApiClient({ server.url("/").toString().trimEnd('/') }, { null })

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repo = ApiStudyPlanRepository(client())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun leeLaListaConMetaYEnviaLosFiltros() = runBlocking {
        server.enqueue(json("""{"data":[
            {"slug":"ing-sistemas","program_name":"Ingeniería de Sistemas","level":"Profesional","structure":"semestres","total_credits":160,"semesters_count":10,"plan_url":"https://unab.edu.co/plan.pdf"},
            {"slug":"maestria-x","program_name":"Maestría X","level":null,"structure":"texto","total_credits":null,"semesters_count":0,"plan_url":null}],
            "meta":{"page":1,"per_page":20,"total":2,"last_page":1}}"""))
        val r = repo.list(1, "sistemas", "Profesional") as ApiResult.Success
        assertEquals(2, r.data.size)
        assertEquals(160.0, r.data[0].totalCredits!!, 0.0)
        assertEquals(10, r.data[0].semestersCount)
        assertNull(r.data[1].level)
        assertNull(r.data[1].totalCredits)
        assertEquals("texto", r.data[1].structure)
        assertEquals(2L, r.meta?.total)
        val path = server.takeRequest().path!!
        assertTrue(path.startsWith("/api/v1/study-plans?"))
        assertTrue(path.contains("q=sistemas") && path.contains("level=Profesional") && path.contains("page=1"))
    }

    @Test
    fun leeElDetalleEstructurado() = runBlocking {
        server.enqueue(json("""{"data":{"slug":"ing-sistemas","program_name":"Ingeniería de Sistemas","level":"Profesional","structure":"semestres",
            "total_credits":160.0,"plan_url":"https://x/plan","source_url":"https://x/src","program":{"id":3,"name":"Ingeniería de Sistemas","slug":"ing-sistemas"},
            "semesters":[{"number":1,"name":"Semestre 1","credits":16,"courses":[{"name":"Cálculo I","credits":4},{"name":"Programación","credits":3.5}]},
            {"number":2,"name":null,"credits":null,"courses":[]}],"text":null}}"""))
        val plan = (repo.get("ing-sistemas") as ApiResult.Success).data
        assertFalse(plan.isText)
        assertEquals(160.0, plan.totalCredits!!, 0.0)
        assertEquals(2, plan.semesters.size)
        assertEquals("Cálculo I", plan.semesters[0].courses[0].name)
        assertEquals(3.5, plan.semesters[0].courses[1].credits!!, 0.0)
        assertEquals("ing-sistemas", plan.program?.slug)
        assertEquals(16.0, semesterCredits(plan.semesters[0]), 0.0)
        assertEquals(0.0, semesterCredits(plan.semesters[1]), 0.0)
        assertEquals("/api/v1/study-plans/ing-sistemas", server.takeRequest().path)
    }

    @Test
    fun leeElDetalleEnTexto() = runBlocking {
        server.enqueue(json("""{"data":{"slug":"maestria-x","program_name":"Maestría X","level":null,"structure":"texto","total_credits":null,
            "plan_url":null,"source_url":"https://x/src","program":null,"semesters":[],"text":"Línea uno\nLínea dos"}}"""))
        val plan = (repo.get("maestria-x") as ApiResult.Success).data
        assertTrue(plan.isText)
        assertNull(plan.totalCredits)
        assertNull(plan.program)
        assertEquals("Línea uno\nLínea dos", plan.text)
        assertTrue(plan.semesters.isEmpty())
    }

    @Test
    fun un404SeConvierteEnError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setHeader("Content-Type", "application/json").setBody("""{"error":"El plan no existe.","code":"NOT_FOUND"}"""))
        val r = repo.get("nada") as ApiResult.Failure
        assertEquals(404, r.error.status)
        assertEquals("El plan no existe.", r.error.message)
    }

    @Test
    fun elProgramaTraeElSlugDelPlanOnull() = runBlocking {
        server.enqueue(json("""{"data":{"id":1,"slug":"a","name":"P","faculty":"F","level":"Profesional","url":"https://u","study_plan_slug":"plan-a"}}"""))
        server.enqueue(json("""{"data":{"id":1,"slug":"b","name":"P","faculty":"F","level":"Profesional","url":"https://u","study_plan_slug":null}}"""))
        val catalog = ApiCatalogRepository(client())
        assertEquals("plan-a", (catalog.program("a") as ApiResult.Success).data.studyPlanSlug)
        assertNull((catalog.program("b") as ApiResult.Success).data.studyPlanSlug)
    }

    @Test
    fun banuNoFallaConUnOrigenDesconocido() = runBlocking {
        server.enqueue(json("""{"data":{"respuesta":"Hola","fuentes":[],"origen":"plan"}}"""))
        server.enqueue(json("""{"data":{"respuesta":"Hola","fuentes":[],"origen":"otro"}}"""))
        val banu = ApiBanuRepository(client())
        assertEquals("plan", (banu.ask("x") as ApiResult.Success).data.origen)
        assertEquals("otro", (banu.ask("y") as ApiResult.Success).data.origen)
        assertEquals("Origen: Plan de estudios oficial", banuOriginLabel("plan"))
        assertTrue(banuOriginLabel("otro").startsWith("Origen"))
    }
}

class StudyPlansViewModelTest : VmTest() {
    private class FakePlans(var fail: Boolean = false) : StudyPlanRepository {
        val calls = mutableListOf<Triple<Int, String?, String?>>()
        override suspend fun list(page: Int, query: String?, level: String?): ApiResult<List<StudyPlanSummaryDto>> {
            calls += Triple(page, query, level)
            if (fail) return ApiResult.Failure(ApiError(500, "X", "Falló el servidor."))
            val all = listOf(
                StudyPlanSummaryDto("a", "Ingeniería", "Profesional", "semestres", 160.0, 10),
                StudyPlanSummaryDto("b", "Maestría", "posgrado", "texto"),
                StudyPlanSummaryDto("c", "Derecho", "PROFESIONAL", "semestres", 150.0, 10),
                StudyPlanSummaryDto("d", "Libre", null, "texto")
            )
            val items = all.filter { level == null || it.level.equals(level, ignoreCase = true) }
            return ApiResult.Success(items, PageMeta(1, 20, items.size.toLong(), 1))
        }
        override suspend fun get(slug: String): ApiResult<StudyPlanDto> =
            if (slug == "a") ApiResult.Success(StudyPlanDto("a", "Ingeniería")) else ApiResult.Failure(ApiError(404, "NF", "No existe."))
    }

    @Test
    fun laBusquedaEsperaAQueTerminesDeEscribir() = runTest(dispatcher) {
        val fake = FakePlans()
        val vm = StudyPlansViewModel(fake)
        advanceUntilIdle()
        vm.onQueryChange("i")
        advanceTimeBy(200)
        vm.onQueryChange("ing")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, fake.calls.size)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(Triple(1, null, null), Triple(1, "ing", null)), fake.calls)
    }

    @Test
    fun losNivelesSalenDeLosDatosSinRepetirYSeConservanAlFiltrar() = runTest(dispatcher) {
        val vm = StudyPlansViewModel(FakePlans())
        assertEquals(STANDARD_LEVELS, vm.levels.value)
        advanceUntilIdle()
        assertEquals(STANDARD_LEVELS + "posgrado", vm.levels.value)
        vm.selectLevel("posgrado")
        advanceUntilIdle()
        assertEquals(1, vm.list.value.items.size)
        assertEquals(STANDARD_LEVELS + "posgrado", vm.levels.value)
        assertEquals("posgrado", vm.level.value)
    }

    @Test
    fun tocarElMismoNivelOtraVezLoQuita() = runTest(dispatcher) {
        val fake = FakePlans()
        val vm = StudyPlansViewModel(fake)
        advanceUntilIdle()
        vm.selectLevel("Profesional")
        advanceUntilIdle()
        assertEquals(2, vm.list.value.items.size)
        vm.selectLevel("PROFESIONAL")
        advanceUntilIdle()
        assertNull(vm.level.value)
        assertEquals(4, vm.list.value.items.size)
        assertEquals("Profesional", fake.calls[1].third)
    }

    @Test
    fun unErrorQuedaEnElEstadoDeLaLista() = runTest(dispatcher) {
        val vm = StudyPlansViewModel(FakePlans(fail = true))
        advanceUntilIdle()
        assertEquals("Falló el servidor.", vm.list.value.error?.message)
        assertTrue(vm.list.value.items.isEmpty())
    }

    @Test
    fun elDetalleCargaOFallaConElMensaje() = runTest(dispatcher) {
        val ok = StudyPlanDetailViewModel(FakePlans(), "a")
        val bad = StudyPlanDetailViewModel(FakePlans(), "zzz")
        advanceUntilIdle()
        assertTrue(ok.state.value is UiState.Ready)
        assertEquals("No existe.", (bad.state.value as UiState.Failed).error.message)
    }

    @Test
    fun formatosDeNivelYCreditos() {
        assertEquals("Profesional", levelLabel("Profesional"))
        assertEquals("Pregrado", levelLabel("pregrado"))
        assertNull(levelLabel(null))
        assertEquals("160", formatCredits(160.0))
        assertEquals("2.5", formatCredits(2.5))
        assertEquals("-", formatCredits(null))
        assertEquals(listOf("Profesional", "posgrado"), mergeLevels(listOf("Profesional"), listOf("PROFESIONAL", null, " ", "posgrado")))
        assertEquals(listOf("Profesional", "Maestría", "Doctorado", "Otro"), mergeLevels(emptyList(), listOf("Doctorado", "Otro", "Maestría", "Profesional")))
        assertEquals("¿Qué materias tiene el primer semestre de Derecho?", firstSemesterQuestion("Derecho"))
    }
}

class ProgramPlanLinkTest : VmTest() {
    private class Catalog(val slugOfPlan: String?) : CatalogRepository {
        override suspend fun programs(page: Int, query: String?): ApiResult<List<ProgramDto>> = ApiResult.Success(emptyList())
        override suspend fun program(slug: String): ApiResult<ProgramDto> =
            ApiResult.Success(ProgramDto(1, slug, "Programa", "Facultad", "Profesional", url = "https://u", studyPlanSlug = slugOfPlan))
        override suspend fun professors(page: Int, query: String?): ApiResult<List<ProfessorDto>> = ApiResult.Success(emptyList())
        override suspend fun professor(slug: String): ApiResult<ProfessorDto> = ApiResult.Failure(ApiError(404, "X", "x"))
    }

    @Test
    fun elDetalleDelProgramaConservaElSlugDelPlan() = runTest(dispatcher) {
        val con = ProgramDetailViewModel(Catalog("plan-p"), "p")
        val sin = ProgramDetailViewModel(Catalog(null), "p")
        advanceUntilIdle()
        assertEquals("plan-p", (con.state.value as UiState.Ready).data.studyPlanSlug)
        assertNull((sin.state.value as UiState.Ready).data.studyPlanSlug)
    }

    @Test
    fun banuHaceLaPreguntaInicialUnaSolaVez() = runTest(dispatcher) {
        var asked = 0
        val vm = BanuViewModel(object : BanuRepository {
            override suspend fun ask(question: String): ApiResult<BanuAnswerDto> { asked++; return ApiResult.Success(BanuAnswerDto("r")) }
        })
        vm.askInitial("¿Qué materias tiene el primer semestre de Derecho?")
        advanceUntilIdle()
        vm.askInitial("¿Qué materias tiene el primer semestre de Derecho?")
        advanceUntilIdle()
        assertEquals(1, asked)
        assertEquals(2, vm.messages.value.size)
    }
}
