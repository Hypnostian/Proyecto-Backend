package co.edu.unab.unabgo.backend.services.integrations

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Modelo de lenguaje que redacta las respuestas de Banu cuando las preguntas frecuentes no alcanzan. */
interface LlmClient {
    /** Respuesta del modelo; lanza ExternalServiceException si el proveedor falla. */
    suspend fun complete(system: String, user: String): String
}

/** Cualquier proveedor compatible con OpenAI (Groq, Ollama, OpenAI...): POST {BANU_LLM_URL}/chat/completions. */
class OpenAiCompatibleLlmClient(
    private val client: HttpClient,
    baseUrl: String,
    private val apiKey: String,
    private val model: String
) : LlmClient {

    private val endpoint = baseUrl.trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" }

    override suspend fun complete(system: String, user: String): String {
        try {
            val response = client.post(endpoint) {
                // Los modelos con razonamiento (gpt-oss en Ollama Cloud) tardan más que el tiempo por defecto
                timeout { requestTimeoutMillis = REQUEST_TIMEOUT_MS }
                bearerAuth(apiKey)
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject {
                    put("model", model)
                    put("temperature", 0.2)
                    put("stream", false)
                    // Los modelos con razonamiento gastan parte de este límite pensando antes de responder
                    put("max_tokens", MAX_TOKENS)
                    put("messages", buildJsonArray {
                        add(buildJsonObject { put("role", "system"); put("content", system) })
                        add(buildJsonObject { put("role", "user"); put("content", user) })
                    })
                }.toString())
            }
            // 402 (sin créditos), 429 (límite del plan) o cualquier otro error: Banu responde con sus datos locales
            if (!response.status.isSuccess()) throw ExternalServiceException("El proveedor de IA respondió HTTP ${response.status.value}.")
            return Json.parseToJsonElement(response.bodyAsText()).jsonObject["choices"]!!.jsonArray[0]
                .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content.trim()
                .ifEmpty { throw ExternalServiceException("El proveedor de IA devolvió una respuesta vacía.") }
        } catch (e: ExternalServiceException) {
            throw e
        } catch (e: Exception) {
            throw ExternalServiceException("No se pudo consultar el proveedor de IA.", e)
        }
    }

    private companion object {
        const val MAX_TOKENS = 1500
        const val REQUEST_TIMEOUT_MS = 30_000L
    }
}
