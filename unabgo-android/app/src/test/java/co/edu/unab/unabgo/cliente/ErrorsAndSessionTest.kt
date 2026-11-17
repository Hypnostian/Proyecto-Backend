package co.edu.unab.unabgo.cliente

import co.edu.unab.unabgo.cliente.data.api.*
import co.edu.unab.unabgo.cliente.data.repo.ApiAuthRepository
import co.edu.unab.unabgo.cliente.data.session.InMemorySessionStore
import co.edu.unab.unabgo.cliente.data.session.Roles
import co.edu.unab.unabgo.cliente.data.session.Session
import co.edu.unab.unabgo.cliente.data.session.normalizeBaseUrl
import co.edu.unab.unabgo.cliente.ui.screens.formatMinutes
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ErrorParsingTest {
    @Test
    fun leeErrorCodigoYCampos() {
        val e = ApiClient.parseError(400, """{"error":"Los datos enviados no son válidos.","code":"VALIDATION_ERROR","fields":{"name":"El nombre es obligatorio."}}""")
        assertEquals("VALIDATION_ERROR", e.code)
        assertEquals("El nombre es obligatorio.", e.fields["name"])
    }

    @Test
    fun cuerpoSinJsonUsaTextoPorCodigo() {
        val e = ApiClient.parseError(404, "<html>")
        assertEquals("HTTP_404", e.code)
        assertEquals("No se encontró lo que buscas.", e.message)
    }

    @Test
    fun el403ExplicaQueFaltaUnRol() {
        val e = ApiClient.parseError(403, """{"error":"Solo el administrador gestiona usuarios.","code":"FORBIDDEN"}""")
        assertTrue(e.isForbidden)
        assertTrue(e.userMessage.startsWith("Tu rol no tiene permiso"))
        assertTrue(e.userMessage.contains("Solo el administrador gestiona usuarios."))
    }

    @Test
    fun el429TieneTextoAmable() {
        val e = ApiClient.parseError(429, """{"error":"x","code":"TOO_MANY_REQUESTS"}""")
        assertTrue(e.userMessage.contains("Espera un minuto"))
    }
}

class SessionAndSettingsTest {
    private lateinit var server: MockWebServer
    private lateinit var store: InMemorySessionStore
    private lateinit var repo: ApiAuthRepository

    private val user = UserDto(1, "Ana", "ana@unab.edu.co", Roles.STUDENT)

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        store = InMemorySessionStore()
        val api = ApiClient({ server.url("/").toString().trimEnd('/') }, { store.session.value?.token }, { store.clear() })
        repo = ApiAuthRepository(api, store)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun loginGuardaTokenYUsuario() = runBlocking {
        server.enqueue(json(200, """{"data":{"token":"1.abc","user":{"id":1,"name":"Ana","email":"ana@unab.edu.co","role":"GESTOR"}}}"""))
        repo.login("ana@unab.edu.co", "Unab2026*")
        val s = store.session.value!!
        assertEquals("1.abc", s.token)
        assertTrue(s.isManager)
        assertFalse(s.isAdmin)
    }

    @Test
    fun loginFallidoNoGuardaSesion() = runBlocking {
        server.enqueue(json(401, """{"error":"Correo o contraseña incorrectos.","code":"UNAUTHENTICATED"}"""))
        repo.login("ana@unab.edu.co", "mala")
        assertNull(store.session.value)
    }

    @Test
    fun un401ConSesionLaBorra() = runBlocking {
        store.save("1.vencido", user)
        server.enqueue(json(401, """{"error":"Debes iniciar sesión con un token válido.","code":"UNAUTHENTICATED"}"""))
        val result = repo.refreshMe()
        assertTrue(result is ApiResult.Failure)
        assertNull(store.session.value)
    }

    @Test
    fun logoutLlamaALaApiYBorraLaSesion() = runBlocking {
        store.save("1.ok", user)
        server.enqueue(MockResponse().setResponseCode(204))
        repo.logout()
        assertEquals("/api/v1/auth/logout", server.takeRequest().path)
        assertNull(store.session.value)
    }

    @Test
    fun logoutBorraLaSesionAunqueNoHayaRed() = runBlocking {
        store.save("1.ok", user)
        server.shutdown()
        repo.logout()
        assertNull(store.session.value)
    }

    @Test
    fun refreshMeActualizaElUsuarioGuardado() = runBlocking {
        store.save("1.ok", user)
        server.enqueue(json(200, """{"data":{"id":1,"name":"Ana María","email":"ana@unab.edu.co","role":"ESTUDIANTE"}}"""))
        repo.refreshMe()
        assertEquals("Ana María", store.session.value!!.user.name)
        assertEquals("1.ok", store.session.value!!.token)
    }

    @Test
    fun rolesDeLaSesion() {
        assertTrue(Session("t", user).isStudent)
        assertTrue(Session("t", user.copy(role = Roles.ADMIN)).isAdmin)
        assertTrue(Session("t", user.copy(role = Roles.ADMIN)).isManager)
    }

    @Test
    fun normalizaLaDireccionDelServidor() {
        assertEquals("http://10.0.2.2:8080", normalizeBaseUrl("10.0.2.2:8080/", true))
        assertEquals("http://192.168.1.5:8080", normalizeBaseUrl(" http://192.168.1.5:8080/api/v1/ ", true))
        assertNull(normalizeBaseUrl("", true))
        assertNull(normalizeBaseUrl("http://", true))
    }

    @Test
    fun enReleaseSoloSeAceptaHttps() {
        assertNull(normalizeBaseUrl("http://10.0.2.2:8080", false))
        assertEquals("https://api.ejemplo.edu.co", normalizeBaseUrl("https://api.ejemplo.edu.co/", false))
    }

    @Test
    fun formateaMinutos() {
        assertEquals("0 min", formatMinutes(0))
        assertEquals("45 min", formatMinutes(45))
        assertEquals("2 h 5 min", formatMinutes(125))
        assertEquals("1 d 1 h", formatMinutes(24 * 60 + 60))
    }
}
