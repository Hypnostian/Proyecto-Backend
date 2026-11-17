package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.UserSeeder
import co.edu.unab.unabgo.backend.services.integrations.FirebaseIdTokenVerifier
import co.edu.unab.unabgo.backend.services.integrations.Integrations
import co.edu.unab.unabgo.backend.services.integrations.Jwt
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.KeyPair
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** POST /api/v1/auth/google: ID tokens de Firebase firmados con una clave RSA generada en la prueba. */
class GoogleAuthTest {

    private val project = "unabgo-test"
    private val keys: KeyPair = newRsaKeyPair()
    private val kid = "clave-1"

    private fun jwksServer(failing: Boolean = false) = MockHttp {
        if (failing) json("{}", HttpStatusCode.InternalServerError) else json(jwks(kid, keys))
    }

    private fun integrations(mock: MockHttp) =
        Integrations(tokenVerifier = FirebaseIdTokenVerifier(mock.client, project, "https://jwks.test/keys"))

    private fun idToken(
        email: String = "ana.torres@unab.edu.co",
        uid: String = "uid-ana",
        aud: String = project,
        iss: String = "https://securetoken.google.com/$project",
        expiresInSeconds: Long = 3600,
        verified: Boolean = true,
        signWith: KeyPair = keys,
        kid: String = this.kid
    ): String {
        val now = Instant.now().epochSecond
        return Jwt.signRs256(buildJsonObject {
            put("iss", iss)
            put("aud", aud)
            put("sub", uid)
            put("iat", now - 10)
            put("exp", now + expiresInSeconds)
            put("email", email)
            put("email_verified", verified)
            put("name", "Ana Torres")
        }, signWith.private, kid)
    }

    private fun body(token: String) = """{"id_token":"$token","device_name":"pruebas"}"""

    @Test
    fun creaElEstudianteLaPrimeraVezYReusaLaCuentaDespues() {
        val mock = jwksServer()
        apiTest(integrations = integrations(mock)) {
            val (status, response) = client.postJson("/api/v1/auth/google", body(idToken())).json()
            assertEquals(200, status, response.toString())
            val data = response.data()
            val user = data["user"]!!.jsonObject
            assertEquals("ESTUDIANTE", user["role"]!!.jsonPrimitive.content)
            assertEquals("ana.torres@unab.edu.co", user["email"]!!.jsonPrimitive.content)
            assertEquals("Ana Torres", user["name"]!!.jsonPrimitive.content)
            val token = data["token"]!!.jsonPrimitive.content
            assertEquals(200, client.getAuth("/api/v1/me", token).status.value)

            val second = client.postJson("/api/v1/auth/google", body(idToken())).json().second.data()["user"]!!.jsonObject
            assertEquals(user["id"], second["id"], "la misma persona no crea otra cuenta")
            assertEquals(1, mock.requests.size, "las claves públicas de Google quedan en caché")
        }
    }

    @Test
    fun enlazaConUnaCuentaExistenteDelMismoCorreo() = apiTest(integrations = integrations(jwksServer())) {
        val response = client.postJson("/api/v1/auth/google", body(idToken(email = UserSeeder.STUDENT_EMAIL, uid = "uid-demo"))).json().second
        assertEquals(UserSeeder.STUDENT_EMAIL, response.data()["user"]!!.jsonObject["email"]!!.jsonPrimitive.content)
        assertEquals(15, client.getAuth("/api/v1/admin/users", tokenFor(UserSeeder.ADMIN_EMAIL)).json().second["meta"]!!.jsonObject["total"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun soloAceptaCorreosInstitucionales() = apiTest(integrations = integrations(jwksServer())) {
        val (status, body) = client.postJson("/api/v1/auth/google", body(idToken(email = "ana@gmail.com"))).json()
        assertEquals(403, status)
        assertEquals("FORBIDDEN", body["code"]!!.jsonPrimitive.content)
        assertEquals(403, client.postJson("/api/v1/auth/google", body(idToken(verified = false))).status.value)
    }

    @Test
    fun rechazaTokensInvalidos() = apiTest(integrations = integrations(jwksServer())) {
        val invalid = mapOf(
            "firmado con otra clave" to idToken(signWith = newRsaKeyPair()),
            "vencido" to idToken(expiresInSeconds = -3600),
            "audiencia equivocada" to idToken(aud = "otro-proyecto"),
            "emisor equivocado" to idToken(iss = "https://accounts.example.com"),
            "kid desconocido" to idToken(kid = "no-existe"),
            "no es un JWT" to "abc.def.ghi"
        )
        invalid.forEach { (why, token) ->
            val (status, response) = client.postJson("/api/v1/auth/google", body(token)).json()
            assertEquals(401, status, why)
            assertEquals("UNAUTHENTICATED", response["code"]!!.jsonPrimitive.content, why)
        }
    }

    @Test
    fun validaElCuerpo() = apiTest(integrations = integrations(jwksServer())) {
        val (status, body) = client.postJson("/api/v1/auth/google", "{}").json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.containsKey("id_token"))
    }

    @Test
    fun sinFirebaseConfiguradoResponde503() = apiTest {
        assertEquals(503, client.postJson("/api/v1/auth/google", body(idToken())).status.value)
    }

    @Test
    fun siGoogleNoEntregaLasClavesResponde503() = apiTest(integrations = integrations(jwksServer(failing = true))) {
        assertEquals(503, client.postJson("/api/v1/auth/google", body(idToken())).status.value)
    }
}
