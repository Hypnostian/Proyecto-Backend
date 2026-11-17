@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package co.edu.unab.unabgo.cliente

import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.data.repo.*
import co.edu.unab.unabgo.cliente.data.session.InMemorySettingsStore
import co.edu.unab.unabgo.cliente.ui.common.UiState
import co.edu.unab.unabgo.cliente.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

private fun <T> ok(data: T, meta: PageMeta? = null): ApiResult<T> = ApiResult.Success(data, meta)
private fun fail(status: Int, message: String, fields: Map<String, String> = emptyMap()): ApiResult.Failure =
    ApiResult.Failure(ApiError(status, "X", message, fields))

private val user = UserDto(1, "Ana", "ana@unab.edu.co", "ESTUDIANTE")

@OptIn(ExperimentalCoroutinesApi::class)
abstract class VmTest {
    protected val dispatcher = StandardTestDispatcher()

    @Before
    fun setMain() = Dispatchers.setMain(dispatcher)

    @After
    fun resetMain() = Dispatchers.resetMain()
}

class AuthViewModelTest : VmTest() {
    private class FakeAuth(var result: ApiResult<UserDto>) : AuthRepository {
        var calls = 0
        var lastRegister: RegisterForm? = null
        override suspend fun login(email: String, password: String): ApiResult<UserDto> { calls++; kotlinx.coroutines.delay(50); return result }
        override suspend fun register(form: RegisterForm): ApiResult<UserDto> { calls++; lastRegister = form; return result }
        override suspend fun logout() {}
        override suspend fun refreshMe() = ok(user)
        override suspend fun updateProfile(body: ProfileBody) = ok(user)
        override suspend fun health() = ok(HealthDto("ok", "ok", "1"))
    }

    @Test
    fun loginExitosoTerminaSinError() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeAuth(ok(user)))
        vm.login("ana@unab.edu.co", "Unab2026*")
        advanceUntilIdle()
        assertTrue(vm.form.value.done)
        assertNull(vm.form.value.error)
    }

    @Test
    fun registroMuestraLosErroresPorCampo() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeAuth(fail(400, "Los datos enviados no son válidos.", mapOf("password_confirmation" to "La confirmación no coincide con la contraseña."))))
        vm.register("Ana", "ana@unab.edu.co", "clave2026", "otra", sistemas, 6)
        advanceUntilIdle()
        assertEquals("La confirmación no coincide con la contraseña.", vm.form.value.fields["password_confirmation"])
        assertNull(vm.form.value.generalError)
    }

    @Test
    fun registroSinCarreraNiSemestreNoSeEnvia() = runTest(dispatcher) {
        val fake = FakeAuth(ok(user))
        val vm = AuthViewModel(fake)
        vm.register("Ana", "ana@unab.edu.co", "clave2026", "clave2026", null, null)
        advanceUntilIdle()
        assertEquals(0, fake.calls)
        assertEquals("Elige tu carrera.", vm.form.value.fields["program_id"])
        assertEquals("Elige el semestre en que vas.", vm.form.value.fields["semester"])
    }

    @Test
    fun registroEnviaCarreraSemestreEIdiomaDeLaApp() = runTest(dispatcher) {
        val fake = FakeAuth(ok(user))
        val vm = AuthViewModel(fake) { "en" }
        vm.register("Juan", "jlizcano471@unab.edu.co", "clave2026", "clave2026", sistemas, 6)
        advanceUntilIdle()
        assertTrue(vm.form.value.done)
        val sent = fake.lastRegister!!
        assertEquals(188L, sent.programId)
        assertEquals(6, sent.semester)
        assertEquals("en", sent.language)
    }

    private val sistemas = CareerChoice(188, "Ingeniería de Sistemas", "Ingeniería", "Profesional", "ingenieria-de-sistemas")

    @Test
    fun credencialesMalasDanErrorGeneral() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeAuth(fail(401, "Correo o contraseña incorrectos.")))
        vm.login("a@unab.edu.co", "mala")
        advanceUntilIdle()
        assertEquals("Correo o contraseña incorrectos.", vm.form.value.generalError)
    }

    @Test
    fun noEnviaDosVecesMientrasEnvia() = runTest(dispatcher) {
        val fake = FakeAuth(ok(user))
        val vm = AuthViewModel(fake)
        vm.login("a@unab.edu.co", "x")
        vm.login("a@unab.edu.co", "x")
        advanceUntilIdle()
        assertEquals(1, fake.calls)
    }
}

class SubjectsViewModelTest : VmTest() {
    private class FakeSubjects(var list: ApiResult<List<SubjectDto>>, var deleteResult: ApiResult<Unit> = ok(Unit)) : SubjectRepository {
        var created: SubjectBody? = null
        override suspend fun list() = list
        override suspend fun get(id: Long) = ok(SubjectDto(id, "Cálculo", 3))
        override suspend fun create(body: SubjectBody): ApiResult<SubjectDto> { created = body; return ok(SubjectDto(9, body.name, 3)) }
        override suspend fun update(id: Long, body: SubjectBody) = ok(SubjectDto(id, body.name, 3))
        override suspend fun delete(id: Long) = deleteResult
        override suspend fun summary(id: Long) = ok(GradeSummaryDto(id, average = 3.5))
        override suspend fun nextClass(): ApiResult<NextClassDto?> = ok(null)
        override suspend fun addCategory(subjectId: Long, body: CategoryBody) = ok(GradeCategoryDto(1, body.name, body.weight ?: 0.0))
        override suspend fun addItem(categoryId: Long, body: ItemBody) = ok(GradeItemDto(1, body.name, body.score))
    }

    private val two = listOf(SubjectDto(1, "Cálculo", 3), SubjectDto(2, "Física", 4))

    @Test
    fun cargaLaLista() = runTest(dispatcher) {
        val vm = SubjectsViewModel(FakeSubjects(ok(two)))
        assertTrue(vm.state.value is UiState.Loading)
        advanceUntilIdle()
        assertEquals(2, (vm.state.value as UiState.Ready).data.size)
    }

    @Test
    fun errorDeCargaSeMuestraConElTextoDelBackend() = runTest(dispatcher) {
        val vm = SubjectsViewModel(FakeSubjects(fail(403, "Solo los estudiantes gestionan materias.")))
        advanceUntilIdle()
        val failed = vm.state.value as UiState.Failed
        assertTrue(failed.error.userMessage.contains("Solo los estudiantes gestionan materias."))
    }

    @Test
    fun borrarQuitaLaMateriaDeLaLista() = runTest(dispatcher) {
        val vm = SubjectsViewModel(FakeSubjects(ok(two)))
        advanceUntilIdle()
        vm.delete(1)
        advanceUntilIdle()
        assertEquals(listOf(2L), (vm.state.value as UiState.Ready).data.map { it.id })
    }

    @Test
    fun siBorrarFallaLaListaQuedaIgualYHayError() = runTest(dispatcher) {
        val vm = SubjectsViewModel(FakeSubjects(ok(two), fail(404, "La materia no existe.")))
        advanceUntilIdle()
        vm.delete(1)
        advanceUntilIdle()
        assertEquals(2, (vm.state.value as UiState.Ready).data.size)
        assertEquals("La materia no existe.", vm.action.value.error?.message)
    }

    @Test
    fun refrescarConservaLosDatosSiFalla() = runTest(dispatcher) {
        val fake = FakeSubjects(ok(two))
        val vm = SubjectsViewModel(fake)
        advanceUntilIdle()
        fake.list = fail(0, "Sin conexión")
        vm.refresh()
        advanceUntilIdle()
        val ready = vm.state.value as UiState.Ready
        assertEquals(2, ready.data.size)
        assertEquals("Sin conexión", ready.error?.message)
    }

    @Test
    fun elDetalleTraeMateriaYResumen() = runTest(dispatcher) {
        val vm = SubjectDetailViewModel(FakeSubjects(ok(two)), 5)
        advanceUntilIdle()
        val detail = (vm.state.value as UiState.Ready).data
        assertEquals(3.5, detail.summary?.average)
    }

    @Test
    fun pesoNoNumericoSeRechazaSinLlamarAlServidor() = runTest(dispatcher) {
        val vm = SubjectDetailViewModel(FakeSubjects(ok(two)), 5)
        advanceUntilIdle()
        vm.addCategory("Corte 1", "abc")
        advanceUntilIdle()
        assertEquals("Escribe un número.", vm.categoryForm.value.fields["weight"])
    }

    @Test
    fun elFormularioDeMateriaArmaElCuerpo() = runTest(dispatcher) {
        val fake = FakeSubjects(ok(two))
        val vm = SubjectFormViewModel(fake, null)
        vm.update(SubjectDraft(name = " Cálculo ", credits = "3", teacher = "", blocks = listOf(BlockDraft(2, "07:00", "09:00", "B-201"))))
        vm.save()
        advanceUntilIdle()
        val body = fake.created!!
        assertEquals("Cálculo", body.name)
        assertEquals(3, body.credits)
        assertNull(body.teacher)
        assertEquals(BlockBody(2, "07:00", "09:00", "B-201"), body.blocks.single())
        assertTrue(vm.form.value.done)
    }

    @Test
    fun decimalesConComaSeEntienden() {
        assertEquals(3.5, parseDecimal("3,5")!!, 0.0)
        assertNull(parseDecimal("tres"))
    }
}

class PagingAndSearchTest : VmTest() {
    private class FakeCatalog : CatalogRepository {
        val queries = mutableListOf<String?>()
        override suspend fun programs(page: Int, query: String?): ApiResult<List<ProgramDto>> = ok(emptyList())
        override suspend fun program(slug: String) = fail(404, "x")
        override suspend fun professors(page: Int, query: String?): ApiResult<List<ProfessorDto>> {
            queries += query
            val items = (1..2).map { ProfessorDto((page * 10 + it).toLong(), "p$page$it", "Profe $page$it", null, null, null, "https://apolo") }
            return ok(items, PageMeta(page, 2, 6, 3))
        }
        override suspend fun professor(slug: String) = fail(404, "x")
    }

    @Test
    fun cargarMasAgregaLaSiguientePagina() = runTest(dispatcher) {
        val vm = ProfessorsViewModel(FakeCatalog())
        advanceUntilIdle()
        assertEquals(2, vm.list.value.items.size)
        assertTrue(vm.list.value.canLoadMore)
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(4, vm.list.value.items.size)
        assertEquals(2, vm.list.value.page)
        vm.loadMore()
        advanceUntilIdle()
        assertEquals(6, vm.list.value.items.size)
        assertFalse(vm.list.value.canLoadMore)
    }

    @Test
    fun laBusquedaEsperaAQueTerminesDeEscribir() = runTest(dispatcher) {
        val fake = FakeCatalog()
        val vm = ProfessorsViewModel(fake)
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), fake.queries)
        vm.onQueryChange("m")
        advanceTimeBy(200)
        vm.onQueryChange("mar")
        advanceTimeBy(300)
        runCurrent()
        assertEquals(1, fake.queries.size)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf<String?>(null, "mar"), fake.queries)
    }

    @Test
    fun refrescarVuelveALaPaginaUno() = runTest(dispatcher) {
        val vm = ProfessorsViewModel(FakeCatalog())
        advanceUntilIdle()
        vm.loadMore()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, vm.list.value.page)
        assertEquals(2, vm.list.value.items.size)
    }
}

class BanuViewModelTest : VmTest() {
    private class FakeBanu(var result: ApiResult<BanuAnswerDto>) : BanuRepository {
        override suspend fun ask(question: String) = result
    }

    @Test
    fun respuestaConFuenteYOrigen() = runTest(dispatcher) {
        val answer = BanuAnswerDto("Las matrículas abren en enero.", listOf(BanuSourceDto("Matrículas", "https://unab.edu.co")), "faq")
        val vm = BanuViewModel(FakeBanu(ok(answer)))
        vm.ask("¿Cuándo abren matrículas?")
        advanceUntilIdle()
        val last = vm.messages.value.last() as ChatMessage.FromBanu
        assertEquals("faq", last.answer.origen)
        assertEquals(2, vm.messages.value.size)
        assertFalse(vm.sending.value)
    }

    @Test
    fun el429SeExplicaConAmabilidad() = runTest(dispatcher) {
        val vm = BanuViewModel(FakeBanu(fail(429, "x")))
        vm.ask("hola banu")
        advanceUntilIdle()
        assertTrue((vm.messages.value.last() as ChatMessage.Problem).text.contains("Espera un minuto"))
    }

    @Test
    fun el503ExplicaQueLaIaNoEstaDisponible() = runTest(dispatcher) {
        val vm = BanuViewModel(FakeBanu(fail(503, "x")))
        vm.ask("hola banu")
        advanceUntilIdle()
        assertTrue((vm.messages.value.last() as ChatMessage.Problem).text.contains("inteligencia artificial"))
    }

    @Test
    fun ignoraPreguntasVacias() = runTest(dispatcher) {
        val vm = BanuViewModel(FakeBanu(fail(500, "x")))
        vm.ask("   ")
        advanceUntilIdle()
        assertTrue(vm.messages.value.isEmpty())
    }
}

class AdminAndSettingsTest : VmTest() {
    private class FakeAdmin(var role: ApiResult<UserDto>) : AdminRepository {
        override suspend fun users(page: Int, role: String?): ApiResult<List<UserDto>> = ok(listOf(user), PageMeta(1, 15, 1, 1))
        override suspend fun changeRole(userId: Long, role: String) = this.role
        override suspend fun syncPrograms(): ApiResult<SyncResultDto> = fail(503, "La sincronización no está configurada (falta UNAB_WP_URL).")
    }

    @Test
    fun cambiarMiPropioRolMuestraElForbidden() = runTest(dispatcher) {
        val vm = AdminViewModel(FakeAdmin(fail(403, "Solo el administrador cambia roles, y no puede cambiar el suyo.")))
        advanceUntilIdle()
        vm.changeRole(1, "GESTOR")
        advanceUntilIdle()
        val e = vm.roleAction.value.error!!
        assertTrue(e.isForbidden)
        assertTrue(e.userMessage.contains("no puede cambiar el suyo"))
    }

    @Test
    fun cambioDeRolExitosoRecargaLaLista() = runTest(dispatcher) {
        val vm = AdminViewModel(FakeAdmin(ok(user.copy(role = "GESTOR"))))
        advanceUntilIdle()
        vm.changeRole(2, "GESTOR")
        advanceUntilIdle()
        assertTrue(vm.roleAction.value.done)
        assertEquals(1, vm.users.value.items.size)
    }

    @Test
    fun laSincronizacionSinConfigurarMuestraElError503() = runTest(dispatcher) {
        val vm = AdminViewModel(FakeAdmin(ok(user)))
        vm.syncPrograms()
        advanceUntilIdle()
        assertTrue(vm.sync.value.error!!.isUnavailable)
        assertNull(vm.syncResult.value)
    }

    private class FakeAuthHealth(val result: ApiResult<HealthDto>) : AuthRepository {
        override suspend fun login(email: String, password: String) = ok(user)
        override suspend fun register(form: RegisterForm) = ok(user)
        override suspend fun logout() {}
        override suspend fun refreshMe() = ok(user)
        override suspend fun updateProfile(body: ProfileBody) = ok(user)
        override suspend fun health() = result
    }

    @Test
    fun probarConexionGuardaLaUrlYMuestraElEstado() = runTest(dispatcher) {
        val settings = InMemorySettingsStore("http://10.0.2.2:8080")
        val vm = SettingsViewModel(settings, FakeAuthHealth(ok(HealthDto("ok", "ok", "0.3.0"))), allowCleartext = true)
        vm.test("192.168.1.9:8080")
        advanceUntilIdle()
        assertEquals("http://192.168.1.9:8080", settings.baseUrl.value)
        assertEquals(true, vm.state.value.testOk)
    }

    @Test
    fun probarConexionSinServidorMuestraElError() = runTest(dispatcher) {
        val settings = InMemorySettingsStore("http://10.0.2.2:8080")
        val vm = SettingsViewModel(settings, FakeAuthHealth(fail(0, "No se pudo conectar con el servidor.")), allowCleartext = true)
        vm.test("http://10.0.2.2:8080")
        advanceUntilIdle()
        assertEquals(false, vm.state.value.testOk)
    }

    @Test
    fun enReleaseRechazaHttp() = runTest(dispatcher) {
        val settings = InMemorySettingsStore("https://x.edu.co")
        val vm = SettingsViewModel(settings, FakeAuthHealth(ok(HealthDto("ok"))), allowCleartext = false)
        vm.save("http://10.0.2.2:8080")
        advanceUntilIdle()
        assertNotNull(vm.state.value.urlError)
        assertEquals("https://x.edu.co", settings.baseUrl.value)
    }
}
