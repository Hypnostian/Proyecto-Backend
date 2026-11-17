package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.ProfessorSeeder
import co.edu.unab.unabgo.backend.models.Professor
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GET /api/v1/professors y /professors/{slug}: datos públicos de APOLO. */
class ProfessorTest {

    private fun insert(slug: String, name: String, title: String?, unit: String?) = transaction {
        Professor.new {
            this.slug = slug
            this.name = name
            this.title = title
            this.unit = unit
            unitUrl = unit?.let { "https://apolo.unab.edu.co/es/organisations/${it.lowercase().replace(' ', '-')}/" }
            apoloUrl = "https://apolo.unab.edu.co/es/persons/$slug/"
            cvlacUrl = null
        }
    }

    private fun seedThree() {
        insert("ana-maria-gomez", "Ana María Gómez", "Profesor", "Ingeniería de Sistemas")
        insert("carlos-ruiz", "Carlos Ruiz", "Investigador", "Medicina")
        insert("beatriz-lopez", "Beatriz López", "Profesor", "Ingeniería de Sistemas")
    }

    @Test
    fun listaPaginadaYOrdenadaPorNombre() = apiTest(seed = false) {
        seedThree()
        val (status, body) = client.getAuth("/api/v1/professors?per_page=2").json()
        assertEquals(200, status)
        assertEquals(listOf("Ana María Gómez", "Beatriz López"), body["data"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        val meta = body["meta"]!!.jsonObject
        assertEquals("3", meta["total"]!!.jsonPrimitive.content)
        assertEquals("2", meta["last_page"]!!.jsonPrimitive.content)
        val second = client.getAuth("/api/v1/professors?per_page=2&page=2").json().second["data"]!!.jsonArray
        assertEquals(1, second.size)
    }

    @Test
    fun filtraPorTextoYPorUnidad() = apiTest(seed = false) {
        seedThree()
        val byName = client.getAuth("/api/v1/professors?q=ruiz").json().second["data"]!!.jsonArray
        assertEquals("carlos-ruiz", byName.single().jsonObject["slug"]!!.jsonPrimitive.content)
        val byUnit = client.getAuth("/api/v1/professors?unit=sistemas").json().second["data"]!!.jsonArray
        assertEquals(2, byUnit.size)
        assertEquals(0, client.getAuth("/api/v1/professors?q=nadie").json().second["data"]!!.jsonArray.size)
    }

    @Test
    fun muestraUnProfesorPorSuSlug() = apiTest(seed = false) {
        seedThree()
        val (status, body) = client.getAuth("/api/v1/professors/carlos-ruiz").json()
        assertEquals(200, status)
        val data = body.data()
        assertEquals("Carlos Ruiz", data["name"]!!.jsonPrimitive.content)
        assertEquals("https://apolo.unab.edu.co/es/persons/carlos-ruiz/", data["apolo_url"]!!.jsonPrimitive.content)
    }

    @Test
    fun unSlugInexistenteEs404() = apiTest(seed = false) {
        val (status, body) = client.getAuth("/api/v1/professors/no-existe").json()
        assertEquals(404, status)
        assertEquals("NOT_FOUND", body["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun elSeederNoFallaSiFaltaElArchivoDeProfesores() {
        TestDb.fresh(seed = false).use {
            transaction { ProfessorSeeder().run() }
            assertTrue(transaction { Professor.count() } >= 0)
        }
        assertEquals("andres-fabian-solano-perez", ProfessorSeeder.slugify("Andrés Fabián Solano Pérez"))
    }
}
