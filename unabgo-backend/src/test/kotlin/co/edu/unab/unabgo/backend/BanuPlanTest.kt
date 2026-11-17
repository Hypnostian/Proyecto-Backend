package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.StudyPlanSeeder
import co.edu.unab.unabgo.backend.services.BanuService
import co.edu.unab.unabgo.backend.services.integrations.Integrations
import co.edu.unab.unabgo.backend.services.integrations.OpenAiCompatibleLlmClient
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Banu con planes de estudio: respuesta directa con los datos, con IA como contexto y con el proveedor caído. */
class BanuPlanTest {

    private val plans = StudyPlanTest()

    private fun ask(question: String) = """{"pregunta":"$question"}"""

    private fun llm(mock: MockHttp) = Integrations(llm = OpenAiCompatibleLlmClient(mock.client, "https://ollama.test/v1", "llm-key", "gpt-oss:20b"))

    private fun load() = transaction { StudyPlanSeeder().load(listOf(plans.sistemas(), plans.texto())) }

    private suspend fun ApplicationTestBuilder.banu(question: String): JsonObject =
        client.postJson("/api/v1/banu/ask", ask(question)).json().second.data()

    private fun JsonObject.answer() = this["respuesta"]!!.jsonPrimitive.content
    private fun JsonObject.origin() = this["origen"]!!.jsonPrimitive.content
    private fun JsonObject.urls() = this["fuentes"]!!.jsonArray.map { it.jsonObject["url"]!!.jsonPrimitive.content }

    @Test
    fun sinIaRespondeLosCreditosConLosDatosDelPlan() = apiTest(seed = false) {
        load()
        val data = banu("¿Cuántos créditos tiene Ingeniería de Sistemas?")
        assertEquals("plan", data.origin())
        assertTrue(data.answer().contains("80 créditos"), data.answer())
        assertTrue(data.answer().contains("5 semestres"), data.answer())
        assertEquals(listOf("https://admisiones.unab.edu.co/plan/sistemas"), data.urls())
    }

    @Test
    fun sinIaRespondeLasMateriasDelSemestrePedido() = apiTest(seed = false) {
        load()
        val data = banu("¿Qué materias tiene el quinto semestre de Ingeniería de Sistemas?")
        assertEquals("plan", data.origin())
        assertTrue(data.answer().contains("quinto semestre"), data.answer())
        assertTrue(data.answer().contains("Materia 5A (10 créditos)") && data.answer().contains("Materia 5B (6 créditos)"), data.answer())
        assertTrue(!data.answer().contains("Materia 4A"), data.answer())
        assertEquals(listOf("https://admisiones.unab.edu.co/plan/sistemas"), data.urls())
    }

    @Test
    fun entiendeElSemestreEscritoConNumeros() = apiTest(seed = false) {
        load()
        assertTrue(banu("materias de ingenieria de sistemas semestre 2").answer().contains("Materia 2A"))
        assertTrue(banu("materias del 3er semestre de ingenieria de sistemas").answer().contains("Materia 3B"))
    }

    @Test
    fun siElSemestreNoExisteLoDice() = apiTest(seed = false) {
        load()
        val data = banu("materias del noveno semestre de ingenieria de sistemas")
        assertEquals("plan", data.origin())
        assertTrue(data.answer().contains("5 semestres"), data.answer())
    }

    @Test
    fun sinSemestreDaUnResumen() = apiTest(seed = false) {
        load()
        val data = banu("pensum de ingenieria de sistemas")
        assertEquals("plan", data.origin())
        assertTrue(data.answer().contains("80 créditos") && data.answer().contains("2 materias"), data.answer())
    }

    @Test
    fun unPlanSoloTextoRemiteAlDocumento() = apiTest(seed = false) {
        load()
        val data = banu("materias de comunicacion social")
        assertEquals("plan", data.origin())
        assertTrue(data.answer().contains("https://drive.test/comunicacion"), data.answer())
    }

    @Test
    fun conIaElPlanVaEnElContextoYSuEnlaceEnLasFuentes() {
        val mock = MockHttp { json("""{"choices":[{"message":{"role":"assistant","content":"El quinto semestre tiene Materia 5A y Materia 5B."}}]}""") }
        apiTest(seed = false, integrations = llm(mock)) {
            load()
            val data = banu("¿Qué materias tiene el quinto semestre de Ingeniería de Sistemas?")
            assertEquals("ia", data.origin())
            assertTrue(data.answer().contains("Materia 5A"))
            assertEquals(listOf("https://admisiones.unab.edu.co/plan/sistemas"), data.urls())
            val sent = (mock.requests.single().body as TextContent).text
            assertTrue(sent.contains("Materia 5A") && sent.contains("Materia 5B"), "el prompt incluye las materias del semestre")
            assertTrue(!sent.contains("Materia 1A"), "solo el semestre pedido")
            assertTrue(sent.contains("gpt-oss:20b") && sent.contains("\"max_tokens\""))
        }
    }

    @Test
    fun siElProveedorNoTieneCreditosOLimitaSeResponde200ConElPlan() {
        listOf(HttpStatusCode.PaymentRequired, HttpStatusCode.TooManyRequests).forEach { failure ->
            val mock = MockHttp { json("""{"error":"sin creditos"}""", failure) }
            apiTest(seed = false, integrations = llm(mock)) {
                load()
                val (status, body) = client.postJson("/api/v1/banu/ask", ask("¿Cuántos créditos tiene Ingeniería de Sistemas?")).json()
                assertEquals(200, status, "$failure $body")
                assertEquals("plan", body.data().origin())
                assertTrue(body.data().answer().contains("80 créditos"))
                assertEquals(1, mock.requests.size)
            }
        }
    }

    @Test
    fun siElProveedorFallaSinPlanResponde200ConElMensajeAlternativo() {
        val mock = MockHttp { json("""{"error":"sin creditos"}""", HttpStatusCode.PaymentRequired) }
        apiTest(integrations = llm(mock)) {
            val (status, body) = client.postJson("/api/v1/banu/ask", ask("informacion sobre ingenieria de sistemas")).json()
            assertEquals(200, status)
            assertEquals("faq", body.data().origin())
        }
    }

    @Test
    fun conLosPlanesRealesSembradosLaPreguntaDeCreditosSeResuelveConElPlan() = apiTest {
        val data = banu("¿Cuántos créditos tiene Ingeniería de Sistemas?")
        assertEquals("plan", data.origin())
        assertTrue(data.answer().contains("créditos"), data.answer())
        assertTrue(data.urls().single().startsWith("https://"))
    }

    @Test
    fun laPreguntaSinPalabrasDePlanNoUsaElPlan() = apiTest(seed = false) {
        load()
        val data = banu("hola ingenieria de sistemas")
        assertEquals("faq", data.origin())
    }

    @Test
    fun detectaLaIntencionYElSemestre() {
        assertTrue(BanuService.asksAboutPlan("¿Cuál es el PENSUM?"))
        assertTrue(BanuService.asksAboutPlan("cuántos créditos"))
        assertTrue(!BanuService.asksAboutPlan("dónde queda la biblioteca"))
        assertEquals(5, BanuService.askedSemester("materias del quinto semestre"))
        assertEquals(10, BanuService.askedSemester("décimo semestre"))
        assertEquals(7, BanuService.askedSemester("semestre 7"))
        assertEquals(4, BanuService.askedSemester("materias de 4to semestre"))
        assertNull(BanuService.askedSemester("materias de ingenieria"))
        assertNull(BanuService.askedSemester("semestre 40"))
    }
}
