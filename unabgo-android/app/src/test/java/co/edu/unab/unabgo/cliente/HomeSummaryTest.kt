@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package co.edu.unab.unabgo.cliente

import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.data.repo.*
import co.edu.unab.unabgo.cliente.data.session.Roles
import co.edu.unab.unabgo.cliente.ui.screens.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SummaryRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: SummaryRepository

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        repo = ApiSummaryRepository(ApiClient(baseUrl = { server.url("/").toString().trimEnd('/') }, token = { "1.abc" }))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun enListasPaginadasLeeElTotalConUnaSolaFila() = runBlocking {
        server.enqueue(json(200, """{"data":[{"id":1}],"meta":{"page":1,"per_page":1,"total":228,"last_page":228}}"""))
        val r = repo.total(SummaryKind.PROGRAMS) as ApiResult.Success
        assertEquals(228L, r.data)
        val path = server.takeRequest().path.orEmpty()
        assertTrue(path, path.startsWith("/api/v1/programs") && path.contains("per_page=1"))
    }

    @Test
    fun cuentaSoloLasPreguntasActivasYLasRutasActivas() = runBlocking {
        server.enqueue(json(200, """{"data":[
            {"id":1,"question":"a","keywords":"","answer":"x","source_url":"","active":true},
            {"id":2,"question":"b","keywords":"","answer":"x","source_url":"","active":false}],"meta":null}"""))
        server.enqueue(json(200, """{"data":[
            {"id":1,"code":"R1","name":"Ruta 1","color":"#000","active":true,"stops":[]},
            {"id":2,"code":"R2","name":"Ruta 2","color":"#000","active":false,"stops":[]}],"meta":null}"""))
        assertEquals(1L, (repo.total(SummaryKind.FAQS) as ApiResult.Success).data)
        assertEquals(1L, (repo.total(SummaryKind.ROUTES) as ApiResult.Success).data)
    }

    @Test
    fun losBusesEnVivoSeCuentanYUn503EsFallo() = runBlocking {
        server.enqueue(json(200, """{"data":[],"meta":null}"""))
        server.enqueue(json(503, """{"error":"Sin plataforma GPS","code":"UNAVAILABLE"}"""))
        assertEquals(0L, (repo.total(SummaryKind.LIVE_BUSES) as ApiResult.Success).data)
        assertTrue(repo.total(SummaryKind.LIVE_BUSES) is ApiResult.Failure)
    }
}

class HomeSummaryViewModelTest : VmTest() {
    private class FakeSummary(val values: Map<SummaryKind, ApiResult<Long>>) : SummaryRepository {
        var calls = 0
        override suspend fun total(kind: SummaryKind): ApiResult<Long> { calls++; return values.getValue(kind) }
    }

    @Test
    fun cadaRolTieneSusTarjetas() {
        assertEquals(
            listOf(SummaryKind.ANNOUNCEMENTS, SummaryKind.FAQS, SummaryKind.ROUTES, SummaryKind.LIVE_BUSES),
            summaryKindsFor(Roles.MANAGER)
        )
        assertEquals(SummaryKind.USERS, summaryKindsFor(Roles.ADMIN).first())
        assertTrue(summaryKindsFor(Roles.STUDENT).isEmpty())
    }

    @Test
    fun empiezaCargandoYLuegoMuestraLosTotales() = runTest(dispatcher) {
        val kinds = summaryKindsFor(Roles.ADMIN)
        val vm = HomeSummaryViewModel(FakeSummary(kinds.associateWith { ApiResult.Success(7L) }), kinds)
        assertTrue(vm.stats.value.values.all { it == StatState.Loading })
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.stats.value.values.all { it == StatState.Value(7L) })
    }

    @Test
    fun unaLlamadaQueFallaOcultaSoloEsaTarjeta() = runTest(dispatcher) {
        val kinds = summaryKindsFor(Roles.MANAGER)
        val values = kinds.associateWith<SummaryKind, ApiResult<Long>> { ApiResult.Success(3L) } +
            (SummaryKind.LIVE_BUSES to ApiResult.Failure(ApiError(403, "FORBIDDEN", "No tienes permiso.")))
        val vm = HomeSummaryViewModel(FakeSummary(values), kinds)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(StatState.Hidden, vm.stats.value[SummaryKind.LIVE_BUSES])
        assertEquals(StatState.Value(3L), vm.stats.value[SummaryKind.FAQS])
    }
}
