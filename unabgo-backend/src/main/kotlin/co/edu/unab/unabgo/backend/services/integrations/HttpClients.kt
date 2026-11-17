package co.edu.unab.unabgo.backend.services.integrations

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout

/** Falla de un servicio externo (red, timeout, respuesta con error o con un formato inesperado). */
class ExternalServiceException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Cliente HTTP compartido por las integraciones. Las pruebas le pasan un MockEngine, así ninguna prueba
 * sale a internet. Los controladores nunca lo usan: solo las clases de services/integrations.
 */
fun externalHttpClient(engine: HttpClientEngine? = null): HttpClient {
    val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 5_000
            requestTimeoutMillis = 15_000
        }
    }
    return if (engine == null) HttpClient(CIO, configure) else HttpClient(engine, configure)
}
