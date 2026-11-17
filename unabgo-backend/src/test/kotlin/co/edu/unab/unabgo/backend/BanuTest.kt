package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.services.BanuService
import co.edu.unab.unabgo.backend.services.integrations.Integrations
import co.edu.unab.unabgo.backend.services.integrations.OpenAiCompatibleLlmClient
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** POST /api/v1/banu/ask: preguntas frecuentes, IA (simulada con MockEngine), validación y límite por IP. */
class BanuTest {

    private fun ask(question: String) = """{"pregunta":"$question"}"""

    private fun llm(mock: MockHttp) = Integrations(llm = OpenAiCompatibleLlmClient(mock.client, "https://llm.test/v1/", "llm-key", "modelo-x"))

    @Test
    fun respondeConLaPreguntaFrecuenteIgnorandoTildesYMayusculas() = apiTest {
        val (status, body) = client.postJson("/api/v1/banu/ask", ask("¿CUÁNTO se aprueba la MATERIA?")).json()
        assertEquals(200, status, body.toString())
        val data = body.data()
        assertEquals("faq", data["origen"]!!.jsonPrimitive.content)
        assertTrue(data["respuesta"]!!.jsonPrimitive.content.contains("3.0"))
        val source = data["fuentes"]!!.jsonArray.single().jsonObject
        assertTrue(source["url"]!!.jsonPrimitive.content.startsWith("https://"))
        assertTrue(source["titulo"]!!.jsonPrimitive.content.isNotBlank())
    }

    @Test
    fun usaLasFormasAlternativasDeLaPregunta() = apiTest {
        // "nota mínima aprobatoria" es una de las palabras clave de la misma pregunta
        val data = client.postJson("/api/v1/banu/ask", ask("nota minima aprobatoria")).json().second.data()
        assertEquals("faq", data["origen"]!!.jsonPrimitive.content)
        assertTrue(data["respuesta"]!!.jsonPrimitive.content.contains("3.0"))
    }

    @Test
    fun sinCoincidenciaLlamaALaIAConProgramasComoContexto() {
        val mock = MockHttp {
            json("""{"choices":[{"message":{"role":"assistant","content":"Ingeniería de Sistemas es un programa presencial de la Facultad de Ingeniería."}}]}""")
        }
        apiTest(integrations = llm(mock)) {
            val (status, body) = client.postJson("/api/v1/banu/ask", ask("informacion sobre ingenieria de sistemas")).json()
            assertEquals(200, status, body.toString())
            val data = body.data()
            assertEquals("ia", data["origen"]!!.jsonPrimitive.content)
            assertTrue(data["respuesta"]!!.jsonPrimitive.content.contains("presencial"))
            assertTrue(data["fuentes"]!!.jsonArray.any { it.jsonObject["url"]!!.jsonPrimitive.content.contains("/programas/ingenieria-de-sistemas") })

            val request: HttpRequestData = mock.requests.single()
            assertEquals("https://llm.test/v1/chat/completions", request.url.toString())
            assertEquals("Bearer llm-key", request.headers[HttpHeaders.Authorization])
            val sent = (request.body as TextContent).text
            assertTrue(sent.contains("modelo-x"))
            assertTrue(sent.contains("Ingeniería de Sistemas"), "el contexto incluye los programas parecidos")
        }
    }

    @Test
    fun siLaIAFallaSeResponde200ConElMensajeAlternativo() {
        val mock = MockHttp { json("""{"error":"sobrecargado"}""", HttpStatusCode.InternalServerError) }
        apiTest(integrations = llm(mock)) {
            val (status, body) = client.postJson("/api/v1/banu/ask", ask("informacion sobre ingenieria de sistemas")).json()
            assertEquals(200, status)
            assertEquals("faq", body.data()["origen"]!!.jsonPrimitive.content)
            assertEquals(1, mock.requests.size)
        }
    }

    @Test
    fun sinIaYSinCoincidenciaSugiereElSitioOficial() = apiTest {
        val data = client.postJson("/api/v1/banu/ask", ask("zzzz qqqq wwww")).json().second.data()
        assertEquals("faq", data["origen"]!!.jsonPrimitive.content)
        assertEquals("https://unab.edu.co", data["fuentes"]!!.jsonArray.single().jsonObject["url"]!!.jsonPrimitive.content)
    }

    @Test
    fun validaLaPregunta() = apiTest {
        listOf("""{"pregunta":"ab"}""", """{"pregunta":""}""", """{}""", """{"pregunta":"${"x".repeat(501)}"}""").forEach {
            val (status, body) = client.postJson("/api/v1/banu/ask", it).json()
            assertEquals(400, status, it)
            assertTrue(body["fields"]!!.jsonObject.containsKey("pregunta"))
        }
        assertEquals(400, client.postJson("/api/v1/banu/ask", "no es json").status.value)
    }

    @Test
    fun limitaLasPreguntasPorIp() = apiTest(config = TestDb.testConfig.copy(banuRateLimitPerMinute = 2)) {
        assertEquals(200, client.postJson("/api/v1/banu/ask", ask("nota minima aprobatoria")).status.value)
        assertEquals(200, client.postJson("/api/v1/banu/ask", ask("nota minima aprobatoria")).status.value)
        val (status, body) = client.postJson("/api/v1/banu/ask", ask("nota minima aprobatoria")).json()
        assertEquals(429, status)
        assertEquals("TOO_MANY_REQUESTS", body["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun elAnalisisDePalabrasIgnoraTildesPluralesYConectores() {
        assertEquals(setOf("cuanto", "aprueba", "materia"), BanuService.tokens("¿Con CUÁNTO se aprueba una materia?"))
        assertEquals(BanuService.tokens("materias"), BanuService.tokens("MATERIA"))
        val (score, common) = BanuService.match(setOf("nota", "minima"), setOf("nota", "minima", "aprobatoria"))
        assertEquals(2, common)
        assertTrue(score in 0.7..0.9)
    }
}
