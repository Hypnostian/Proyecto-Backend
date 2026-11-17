package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.UserSeeder
import co.edu.unab.unabgo.backend.services.integrations.FcmPushNotifier
import co.edu.unab.unabgo.backend.services.integrations.Integrations
import co.edu.unab.unabgo.backend.services.integrations.Jwt
import co.edu.unab.unabgo.backend.services.integrations.ServiceAccount
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.formUrlEncode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.KeyPair
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Firebase Cloud Messaging HTTP v1: OAuth2 con JWT RS256 y envío al tema de la categoría. */
class PushNotificationTest {

    private val keys: KeyPair = newRsaKeyPair()
    private val account = ServiceAccount("push@unabgo-test.iam.gserviceaccount.com", pemPrivateKey(keys), "https://oauth.test/token")

    private fun text(request: HttpRequestData): String = when (val body: OutgoingContent = request.body) {
        is TextContent -> body.text
        is FormDataContent -> body.formData.formUrlEncode()
        else -> error("cuerpo inesperado: ${body::class}")
    }

    private fun google(sendStatus: HttpStatusCode = HttpStatusCode.OK) = MockHttp { request ->
        when {
            request.url.encodedPath == "/token" -> json("""{"access_token":"ya29.token-de-prueba","expires_in":3600,"token_type":"Bearer"}""")
            request.url.encodedPath == "/v1/projects/unabgo-test/messages:send" -> json("""{"name":"projects/unabgo-test/messages/1"}""", sendStatus)
            else -> json("{}", HttpStatusCode.NotFound)
        }
    }

    private fun notifier(mock: MockHttp) = FcmPushNotifier(mock.client, "unabgo-test", account, fcmBaseUrl = "https://fcm.test")

    @Test
    fun obtieneElTokenOAuthFirmandoUnJwtYEnviaAlTema() = runBlocking {
        val mock = google()
        assertTrue(notifier(mock).send("bienestar", "Jornada de salud", "Este jueves en el campus"))

        val oauth = mock.requests[0]
        val form = text(oauth)
        assertTrue(form.contains("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"))
        val assertion = form.substringAfter("assertion=").substringBefore('&')
        val jwt = Jwt.parse(assertion)
        assertTrue(Jwt.verifyRs256(jwt, keys.public), "el JWT está firmado con la clave privada de la cuenta de servicio")
        assertEquals("push@unabgo-test.iam.gserviceaccount.com", jwt.payload["iss"]!!.jsonPrimitive.content)
        assertEquals("https://www.googleapis.com/auth/firebase.messaging", jwt.payload["scope"]!!.jsonPrimitive.content)
        assertEquals("https://oauth.test/token", jwt.payload["aud"]!!.jsonPrimitive.content)

        val send = mock.requests[1]
        assertEquals("Bearer ya29.token-de-prueba", send.headers[HttpHeaders.Authorization])
        val message = Json.parseToJsonElement(text(send)).jsonObject["message"]!!.jsonObject
        assertEquals("bienestar", message["topic"]!!.jsonPrimitive.content)
        assertEquals("Jornada de salud", message["notification"]!!.jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun reutilizaElTokenMientrasNoVence() = runBlocking {
        val mock = google()
        val push = notifier(mock)
        assertTrue(push.send("transporte", "Ruta 1", "Cambio de horario"))
        assertTrue(push.send("transporte", "Ruta 2", "Cambio de horario"))
        assertEquals(1, mock.requests.count { it.url.encodedPath == "/token" })
        assertEquals(2, mock.requests.count { it.url.encodedPath.endsWith("messages:send") })
    }

    @Test
    fun siFcmRechazaElMensajeDevuelveFalseSinLanzar() = runBlocking {
        assertFalse(notifier(google(HttpStatusCode.Forbidden)).send("evento", "Título", "Cuerpo"))
        val noToken = MockHttp { json("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest) }
        assertFalse(notifier(noToken).send("evento", "Título", "Cuerpo"))
    }

    @Test
    fun alPublicarUnAvisoSeEnviaElPushAlTemaDeLaCategoria() {
        val mock = google()
        apiTest(integrations = Integrations(push = notifier(mock))) {
            val gestor = tokenFor(UserSeeder.TRANSPORTE_EMAIL)
            val created = client.postJson("/api/v1/announcements",
                """{"title":"Cambio de ruta","body":"La Ruta 1 sale 10 minutos antes","category":"transporte","publish":true}""", gestor).json()
            assertEquals(201, created.first)
            assertTrue(created.second.data()["push_sent_at"]!!.jsonPrimitive.content.isNotBlank())
            val message = Json.parseToJsonElement(text(mock.requests.last())).jsonObject["message"]!!.jsonObject
            assertEquals("transporte", message["topic"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun unFalloDeFcmNoImpidePublicarElAviso() {
        apiTest(integrations = Integrations(push = notifier(google(HttpStatusCode.InternalServerError)))) {
            val gestor = tokenFor(UserSeeder.BIENESTAR_EMAIL)
            val (status, body) = client.postJson("/api/v1/announcements",
                """{"title":"Aviso","body":"Contenido","category":"bienestar","publish":true}""", gestor).json()
            assertEquals(201, status)
            val data = body.data()
            assertTrue(data["published_at"]!!.jsonPrimitive.content.isNotBlank())
            assertTrue(data["push_sent_at"] is JsonNull, "sin push enviado el aviso queda pendiente de notificar")
        }
    }
}
