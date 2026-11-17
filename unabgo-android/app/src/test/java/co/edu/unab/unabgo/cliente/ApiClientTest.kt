package co.edu.unab.unabgo.cliente

import co.edu.unab.unabgo.cliente.data.api.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    private lateinit var server: MockWebServer
    private var token: String? = null
    private var unauthorizedCalls = 0
    private lateinit var api: ApiClient

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = ApiClient(
            baseUrl = { server.url("/").toString().trimEnd('/') },
            token = { token },
            onUnauthorized = { unauthorizedCalls++ }
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private val userJson = """{"id":1,"name":"Ana","email":"ana@unab.edu.co","role":"ESTUDIANTE","active":true,"created_at":"2026-01-01T00:00:00Z",
        "profile":{"student_code":null,"semester":4,"language":"es","reminder_minutes":30,"program":{"id":2,"name":"Ingeniería","faculty":"Ingeniería"}}}"""

    @Test
    fun loginExitosoDesenvuelveDataYLeeSnakeCase() = runBlocking {
        server.enqueue(json(200, """{"data":{"token":"1.abc","token_type":"Bearer","expires_at":"2026-12-01T00:00:00Z","user":$userJson}}"""))
        val result = api.send<TokenDto, LoginBody>("POST", "/api/v1/auth/login", LoginBody("ana@unab.edu.co", "Unab2026*"))
        val ok = result as ApiResult.Success
        assertEquals("1.abc", ok.data.token)
        assertEquals("ESTUDIANTE", ok.data.user.role)
        assertEquals(30, ok.data.user.profile?.reminderMinutes)
        assertEquals("Ingeniería", ok.data.user.profile?.program?.name)
        val sent = server.takeRequest()
        assertEquals("/api/v1/auth/login", sent.path)
        assertTrue(sent.body.readUtf8().contains("\"device_name\""))
    }

    @Test
    fun elCuerpoDelRegistroUsaSnakeCase() = runBlocking {
        server.enqueue(json(201, """{"data":{"token":"2.x","user":$userJson}}"""))
        api.send<TokenDto, RegisterBody>("POST", "/api/v1/auth/register", RegisterBody("Ana", "ana@unab.edu.co", "clave2026", "clave2026"))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"password_confirmation\":\"clave2026\""))
    }

    @Test
    fun enviaElTokenBearerCuandoHaySesion() = runBlocking {
        token = "7.secreto"
        server.enqueue(json(200, """{"data":$userJson}"""))
        api.get<UserDto>("/api/v1/me")
        assertEquals("Bearer 7.secreto", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun noEnviaAuthorizationSinSesion() = runBlocking {
        server.enqueue(json(200, """{"data":[]}"""))
        api.get<List<ProgramDto>>("/api/v1/programs")
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun error401ConTokenAvisaQueLaSesionVencio() = runBlocking {
        token = "7.vencido"
        server.enqueue(json(401, """{"error":"Debes iniciar sesión con un token válido.","code":"UNAUTHENTICATED"}"""))
        val result = api.get<UserDto>("/api/v1/me")
        val error = (result as ApiResult.Failure).error
        assertEquals(401, error.status)
        assertEquals("UNAUTHENTICATED", error.code)
        assertEquals(1, unauthorizedCalls)
    }

    @Test
    fun loginIncorrectoSin401DeSesionNoCierraNada() = runBlocking {
        server.enqueue(json(401, """{"error":"Correo o contraseña incorrectos.","code":"UNAUTHENTICATED"}"""))
        val result = api.send<TokenDto, LoginBody>("POST", "/api/v1/auth/login", LoginBody("a@unab.edu.co", "mala"))
        assertEquals("Correo o contraseña incorrectos.", (result as ApiResult.Failure).error.message)
        assertEquals(0, unauthorizedCalls)
    }

    @Test
    fun validacionDevuelveLosCamposPorNombre() = runBlocking {
        server.enqueue(json(400, """{"error":"Los datos enviados no son válidos.","code":"VALIDATION_ERROR","fields":{"email":"Usa tu correo institucional @unab.edu.co.","password_confirmation":"La confirmación no coincide con la contraseña."}}"""))
        val result = api.send<TokenDto, RegisterBody>("POST", "/api/v1/auth/register", RegisterBody("A", "a@gmail.com", "x", "y"))
        val error = (result as ApiResult.Failure).error
        assertTrue(error.isValidation)
        assertEquals("Usa tu correo institucional @unab.edu.co.", error.fields["email"])
        assertEquals(2, error.fields.size)
    }

    @Test
    fun paginacionLlegaEnMeta() = runBlocking {
        server.enqueue(json(200, """{"data":[{"id":1,"title":"A","body":"b","category":"academico","author":"Ana"}],"meta":{"page":2,"per_page":10,"total":31,"last_page":4}}"""))
        val result = api.get<List<AnnouncementDto>>("/api/v1/announcements", mapOf("page" to "2", "category" to null))
        val ok = result as ApiResult.Success
        assertEquals(1, ok.data.size)
        assertEquals(PageMeta(2, 10, 31, 4), ok.meta)
        assertEquals("/api/v1/announcements?page=2", server.takeRequest().path)
    }

    @Test
    fun respuesta204SeTrataComoExito() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(api.noContent("DELETE", "/api/v1/subjects/3") is ApiResult.Success)
        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun healthNoUsaElSobreData() = runBlocking {
        server.enqueue(json(200, """{"status":"ok","database":"ok","version":"0.3.0"}"""))
        val ok = api.health() as ApiResult.Success
        assertEquals("ok", ok.data.status)
        assertEquals("0.3.0", ok.data.version)
    }

    @Test
    fun dataNulaEsUnaProximaClaseVacia() = runBlocking {
        server.enqueue(json(200, """{"data":null}"""))
        val ok = api.get<NextClassDto?>("/api/v1/schedule/next") as ApiResult.Success
        assertNull(ok.data)
    }

    @Test
    fun proximaClaseSeLeeCompleta() = runBlocking {
        server.enqueue(json(200, """{"data":{"subject_id":3,"subject_name":"Cálculo","color":"#E9A427","teacher":null,"block_id":9,"date":"2026-11-17","day_of_week":2,"start_time":"07:00","end_time":"09:00","room":"B-201","in_progress":false,"starts_in_minutes":95}}"""))
        val c = (api.get<NextClassDto?>("/api/v1/schedule/next") as ApiResult.Success).data!!
        assertEquals("Cálculo", c.subjectName)
        assertEquals(95L, c.startsInMinutes)
        assertEquals("B-201", c.room)
    }

    @Test
    fun sinServidorDevuelveErrorDeRed() = runBlocking {
        server.shutdown()
        val error = (api.get<UserDto>("/api/v1/me") as ApiResult.Failure).error
        assertTrue(error.isNetwork)
    }

    @Test
    fun cuerpoQueNoEsElEsperadoDaErrorControlado() = runBlocking {
        server.enqueue(json(200, "<html>no es json</html>"))
        val error = (api.get<UserDto>("/api/v1/me") as ApiResult.Failure).error
        assertEquals("BAD_RESPONSE", error.code)
    }

    @Test
    fun urlInvalidaNoLanzaExcepcion() = runBlocking {
        val broken = ApiClient(baseUrl = { "no es una url" }, token = { null })
        val error = (broken.get<UserDto>("/api/v1/me") as ApiResult.Failure).error
        assertTrue(error.isNetwork)
    }
}
