package co.edu.unab.unabgo.backend.services.integrations

// Capa de servicios de integración con APIs externas. Los controladores dependen de estas interfaces,
// nunca de un cliente HTTP. Cada una tiene una implementación real (cliente Ktor) y las pruebas
// la reemplazan con un MockEngine. Se inyectan en Application.module().

import co.edu.unab.unabgo.backend.config.AppConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import org.slf4j.LoggerFactory
import java.io.File

/** Notificaciones push a los estudiantes (Firebase Cloud Messaging HTTP v1). */
fun interface PushNotifier {
    /** Envía la notificación al tema de la categoría; true si se envió. Nunca lanza: un fallo devuelve false. */
    suspend fun send(topic: String, title: String, body: String): Boolean
}

/** Implementación de desarrollo: registra el envío en el log. */
class LogPushNotifier : PushNotifier {
    private val log = LoggerFactory.getLogger("unabgo.push")
    override suspend fun send(topic: String, title: String, body: String): Boolean {
        log.info("Push al tema {}: {}", topic, title)
        return true
    }
}

/**
 * Todas las integraciones externas de la aplicación. Una integración en null significa "no configurada":
 * el endpoint que la necesita responde 503 con un mensaje claro.
 */
class Integrations(
    val push: PushNotifier = LogPushNotifier(),
    val gpsFeed: GpsFeedService? = null,
    val llm: LlmClient? = null,
    val unabWeb: UnabWebService? = null,
    val tokenVerifier: GoogleTokenVerifier? = null
) {
    companion object {
        private val log = LoggerFactory.getLogger("unabgo.integrations")

        /** Arma las integraciones a partir del .env; las que no tienen configuración quedan desactivadas. */
        fun from(config: AppConfig, engine: HttpClientEngine? = null): Integrations {
            val http: HttpClient by lazy { externalHttpClient(engine) }
            return Integrations(
                push = fcm(config) { http } ?: LogPushNotifier(),
                gpsFeed = config.gpsFeedUrl.takeIf { it.isNotBlank() }?.let { HttpGpsFeedService(http, it) },
                llm = if (config.banuLlmConfigured) OpenAiCompatibleLlmClient(http, config.banuLlmUrl, config.banuLlmKey, config.banuLlmModel) else null,
                unabWeb = config.unabWpUrl.takeIf { it.isNotBlank() }?.let { WordPressUnabWebService(http, it) },
                tokenVerifier = config.firebaseProjectId.takeIf { it.isNotBlank() }?.let { FirebaseIdTokenVerifier(http, it) }
            )
        }

        private fun fcm(config: AppConfig, http: () -> HttpClient): PushNotifier? {
            if (config.firebaseProjectId.isBlank() || config.firebaseCredentials.isBlank()) return null
            val file = File(config.firebaseCredentials)
            if (!file.isFile) {
                log.warn("FIREBASE_CREDENTIALS no apunta a un archivo: las notificaciones push solo se registran en el log.")
                return null
            }
            return runCatching { FcmPushNotifier(http(), config.firebaseProjectId, ServiceAccount.fromJson(file.readText())) }
                .onFailure { log.warn("No se pudo leer la cuenta de servicio de Firebase: {}", it.message) }
                .getOrNull()
        }
    }
}
