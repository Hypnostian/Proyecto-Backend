package co.edu.unab.unabgo.backend.services.integrations

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import java.time.Clock

/** Credenciales de la cuenta de servicio de Firebase (el JSON que se descarga de la consola; nunca va en el código). */
class ServiceAccount(val clientEmail: String, val privateKeyPem: String, val tokenUri: String) {
    companion object {
        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"

        fun fromJson(text: String): ServiceAccount {
            val json = Json.parseToJsonElement(text).jsonObject
            fun field(name: String) = json[name]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException("Falta $name en el JSON de la cuenta de servicio.")
            return ServiceAccount(field("client_email"), field("private_key"), json["token_uri"]?.jsonPrimitive?.content ?: DEFAULT_TOKEN_URI)
        }
    }
}

/**
 * Firebase Cloud Messaging HTTP v1. Obtiene un token OAuth2 firmando un JWT RS256 con la cuenta de servicio
 * y envía el mensaje al tema de la categoría del aviso (los celulares se suscriben a ese tema).
 */
class FcmPushNotifier(
    private val client: HttpClient,
    private val projectId: String,
    private val account: ServiceAccount,
    private val clock: Clock = Clock.systemUTC(),
    private val fcmBaseUrl: String = "https://fcm.googleapis.com"
) : PushNotifier {

    private val log = LoggerFactory.getLogger("unabgo.fcm")
    private val privateKey by lazy { Jwt.privateKeyFromPem(account.privateKeyPem) }
    private val lock = Mutex()
    private var accessToken: String? = null
    private var accessTokenExpiresAt = 0L

    override suspend fun send(topic: String, title: String, body: String): Boolean = try {
        val response = client.post("$fcmBaseUrl/v1/projects/$projectId/messages:send") {
            bearerAuth(accessToken())
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("message", buildJsonObject {
                    put("topic", topic)
                    put("notification", buildJsonObject {
                        put("title", title.take(100))
                        put("body", body.take(400))
                    })
                })
            }.toString())
        }
        if (!response.status.isSuccess()) log.warn("FCM rechazó el mensaje del tema {}: HTTP {}", topic, response.status.value)
        response.status.isSuccess()
    } catch (e: Exception) {
        log.warn("No se pudo enviar el push al tema {}: {}", topic, e.message)
        false
    }

    /** Token OAuth2 con alcance firebase.messaging; se reutiliza hasta un minuto antes de que venza. */
    private suspend fun accessToken(): String = lock.withLock {
        val now = clock.millis() / 1000
        accessToken?.takeIf { now < accessTokenExpiresAt - 60 }?.let { return it }
        val assertion = Jwt.signRs256(buildJsonObject {
            put("iss", account.clientEmail)
            put("scope", SCOPE)
            put("aud", account.tokenUri)
            put("iat", now)
            put("exp", now + 3600)
        }, privateKey)
        val response = client.submitForm(account.tokenUri, parameters {
            append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            append("assertion", assertion)
        })
        if (!response.status.isSuccess()) throw ExternalServiceException("Google OAuth2 respondió HTTP ${response.status.value}.")
        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val token = json["access_token"]?.jsonPrimitive?.content ?: throw ExternalServiceException("Google OAuth2 no entregó access_token.")
        accessToken = token
        accessTokenExpiresAt = now + (json["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600)
        token
    }

    private companion object {
        const val SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    }
}
