package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.UserSeeder
import co.edu.unab.unabgo.backend.services.integrations.Integrations
import co.edu.unab.unabgo.backend.services.integrations.WordPressUnabWebService
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals

/** POST /api/v1/admin/programs/sync con la API de WordPress de unab.edu.co simulada. */
class ProgramSyncTest {

    private val categories = """[{"id":164,"name":"Facultad de Ingeniería"},{"id":165,"name":"Facultad Nueva"}]"""
    private val tags = """[{"id":197,"slug":"pregrado"},{"id":198,"slug":"presencial"},{"id":199,"slug":"virtual"}]"""
    private val projects = """[
        {"id":41970,"slug":"ingenieria-de-sistemas","link":"https://unab.edu.co/programas/ingenieria-de-sistemas/",
         "title":{"rendered":"Ingeniería de Sistemas"},"excerpt":{"rendered":"<p>Presencial</p>"},"project_category":[164],"project_tag":[197,198]},
        {"id":999001,"slug":"programa-nuevo-de-prueba","link":"https://unab.edu.co/programas/programa-nuevo-de-prueba/",
         "title":{"rendered":"Nuevo Programa &amp; M&#225;s &#8211; Virtual"},"excerpt":{"rendered":"<p>Virtual</p>\n"},"project_category":[165],"project_tag":[197,199]}
    ]"""

    private fun wordPress(failing: Boolean = false) = MockHttp { request ->
        val path = request.url.encodedPath
        when {
            failing -> json("""{"code":"error"}""", HttpStatusCode.BadGateway)
            path.endsWith("/project_category") -> json(categories)
            path.endsWith("/project_tag") -> json(tags)
            path.endsWith("/project") -> json(projects)
            else -> json("[]", HttpStatusCode.NotFound)
        }
    }

    private fun integrations(mock: MockHttp) = Integrations(unabWeb = WordPressUnabWebService(mock.client, "https://unab.test/wp-json/wp/v2/"))

    @Test
    fun elAdministradorSincronizaYCreaLosProgramasNuevos() {
        val mock = wordPress()
        apiTest(integrations = integrations(mock)) {
            val admin = tokenFor(UserSeeder.ADMIN_EMAIL)
            val (status, body) = client.postJson("/api/v1/admin/programs/sync", "{}", admin).json()
            assertEquals(200, status, body.toString())
            val data = body.data()
            assertEquals("2", data["fetched"]!!.toString())
            assertEquals("1", data["created"]!!.toString())
            assertEquals(2, data["updated"]!!.toString().toInt() + data["unchanged"]!!.toString().toInt() + data["created"]!!.toString().toInt())

            val created = client.getAuth("/api/v1/programs/programa-nuevo-de-prueba").json().second.data()
            assertEquals("Nuevo Programa & Más – Virtual", created["name"]!!.toString().trim('"'))
            assertEquals("Facultad Nueva", created["faculty"]!!.toString().trim('"'))
            assertEquals("Profesional", created["level"]!!.toString().trim('"'))
            assertEquals("Virtual", created["modality"]!!.toString().trim('"'))

            // Una segunda sincronización ya no crea ni cambia nada
            val again = client.postJson("/api/v1/admin/programs/sync", "{}", admin).json().second.data()
            assertEquals("0", again["created"]!!.toString())
            assertEquals("0", again["updated"]!!.toString())
            assertEquals("2", again["unchanged"]!!.toString())

            // Se pidieron las categorías, las etiquetas y los programas publicados
            assertEquals(true, mock.requests.any { it.url.encodedPath.endsWith("/project") && it.url.parameters["status"] == "publish" })
        }
    }

    @Test
    fun soloElAdministradorPuedeSincronizar() {
        apiTest(integrations = integrations(wordPress())) {
            assertEquals(401, client.postJson("/api/v1/admin/programs/sync", "{}").status.value)
            assertEquals(403, client.postJson("/api/v1/admin/programs/sync", "{}", tokenFor(UserSeeder.BIENESTAR_EMAIL)).status.value)
            assertEquals(403, client.postJson("/api/v1/admin/programs/sync", "{}", tokenFor(UserSeeder.STUDENT_EMAIL)).status.value)
        }
    }

    @Test
    fun sinUrlDeWordPressResponde503() = apiTest {
        assertEquals(503, client.postJson("/api/v1/admin/programs/sync", "{}", tokenFor(UserSeeder.ADMIN_EMAIL)).status.value)
    }

    @Test
    fun siWordPressFallaResponde503() {
        apiTest(integrations = integrations(wordPress(failing = true))) {
            assertEquals(503, client.postJson("/api/v1/admin/programs/sync", "{}", tokenFor(UserSeeder.ADMIN_EMAIL)).status.value)
        }
    }

    @Test
    fun decodificaLasEntidadesHtmlDeWordPress() {
        assertEquals("Gestión del Talento Humano – Virtual & más", WordPressUnabWebService.decodeHtml("Gesti&#xF3;n del Talento Humano &#8211; Virtual &amp; m&#225;s"))
    }
}
