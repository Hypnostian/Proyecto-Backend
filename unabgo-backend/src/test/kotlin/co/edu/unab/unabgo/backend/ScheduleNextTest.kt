package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.UserSeeder
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GET /api/v1/schedule/next: próxima clase del estudiante a partir de los bloques de sus materias. */
class ScheduleNextTest {

    private val next = "/api/v1/schedule/next"

    /** Estudiante nuevo, sin materias ni horario. */
    private suspend fun ApplicationTestBuilder.newStudent(email: String): String =
        client.postJson("/api/v1/auth/register",
            """{"name":"Estudiante Nuevo","email":"$email","password":"clave2026","password_confirmation":"clave2026"}""")
            .json().second["data"]!!.jsonObject["token"]!!.jsonPrimitive.content

    /** Lunes 08:00 a 10:00 (Arquitectura) y lunes 14:00 a 16:00 mas miercoles 08:00 a 10:00 (Bases de datos). */
    private suspend fun ApplicationTestBuilder.createSchedule(token: String) {
        val a = """{"name":"Arquitectura","credits":3,"color":"#8600dd","teacher":"Docente A",
            "blocks":[{"day_of_week":1,"start_time":"08:00","end_time":"10:00","room":"A-201"}]}"""
        val b = """{"name":"Bases de datos","credits":3,"color":"#E9A427",
            "blocks":[{"day_of_week":1,"start_time":"14:00","end_time":"16:00","room":"B-101"},
                      {"day_of_week":3,"start_time":"08:00","end_time":"10:00"}]}"""
        assertEquals(201, client.postJson("/api/v1/subjects", a, token).status.value)
        assertEquals(201, client.postJson("/api/v1/subjects", b, token).status.value)
    }

    private suspend fun ApplicationTestBuilder.nextAt(token: String, at: String): JsonObject {
        val (status, body) = client.getAuth("$next?at=$at", token).json()
        assertEquals(200, status, body.toString())
        return body
    }

    @Test
    fun sinTokenResponde401() = apiTest(seed = true) {
        assertEquals(401, client.getAuth(next).status.value)
        assertEquals(401, client.getAuth(next, "1.token-falso").status.value)
    }

    @Test
    fun soloLosEstudiantesTienenHorario() = apiTest(seed = true) {
        assertEquals(403, client.getAuth(next, tokenFor(UserSeeder.ADMIN_EMAIL)).status.value)
        assertEquals(403, client.getAuth(next, tokenFor(UserSeeder.BIENESTAR_EMAIL)).status.value)
    }

    @Test
    fun sinClasesResponde200ConDataNula() = apiTest(seed = true) {
        val token = newStudent("sin.horario@unab.edu.co")
        val (status, body) = client.getAuth(next, token).json()
        assertEquals(200, status)
        assertEquals(JsonNull, body["data"])
    }

    @Test
    fun devuelveLaProximaClaseDelMismoDia() = apiTest(seed = true) {
        val token = newStudent("horario.uno@unab.edu.co")
        createSchedule(token)

        val data = nextAt(token, "2026-11-16T07:30").data()
        assertEquals("Arquitectura", data["subject_name"]!!.jsonPrimitive.content)
        assertEquals("2026-11-16", data["date"]!!.jsonPrimitive.content)
        assertEquals(1, data["day_of_week"]!!.jsonPrimitive.int)
        assertEquals("08:00", data["start_time"]!!.jsonPrimitive.content)
        assertEquals("A-201", data["room"]!!.jsonPrimitive.content)
        assertEquals("Docente A", data["teacher"]!!.jsonPrimitive.content)
        assertEquals(30, data["starts_in_minutes"]!!.jsonPrimitive.long)
        assertEquals(false, data["in_progress"]!!.jsonPrimitive.boolean)

        // A las 10:00 la primera clase terminó: sigue la de las 14:00 de otra materia
        val later = nextAt(token, "2026-11-16T10:00").data()
        assertEquals("Bases de datos", later["subject_name"]!!.jsonPrimitive.content)
        assertEquals("14:00", later["start_time"]!!.jsonPrimitive.content)
        assertEquals(240, later["starts_in_minutes"]!!.jsonPrimitive.long)
    }

    @Test
    fun unaClaseEnCursoEsLaProxima() = apiTest(seed = true) {
        val token = newStudent("horario.dos@unab.edu.co")
        createSchedule(token)
        val data = nextAt(token, "2026-11-16T09:00").data()
        assertEquals("Arquitectura", data["subject_name"]!!.jsonPrimitive.content)
        assertEquals(true, data["in_progress"]!!.jsonPrimitive.boolean)
        assertEquals(0, data["starts_in_minutes"]!!.jsonPrimitive.long)
    }

    @Test
    fun pasaAlSiguienteDiaYALaSemanaSiguiente() = apiTest(seed = true) {
        val token = newStudent("horario.tres@unab.edu.co")
        createSchedule(token)

        // Lunes 16:00: ya no quedan clases ese dia, la siguiente es el miércoles 18
        val wed = nextAt(token, "2026-11-16T16:00").data()
        assertEquals("2026-11-18", wed["date"]!!.jsonPrimitive.content)
        assertEquals(3, wed["day_of_week"]!!.jsonPrimitive.int)

        // Miércoles 10:00: terminaron todas las de la semana, vuelve el lunes 23 a las 08:00
        val week = nextAt(token, "2026-11-18T10:00").data()
        assertEquals("2026-11-23", week["date"]!!.jsonPrimitive.content)
        assertEquals("Arquitectura", week["subject_name"]!!.jsonPrimitive.content)

        // Domingo: la próxima es el lunes
        val sunday = nextAt(token, "2026-11-15T20:00").data()
        assertEquals("2026-11-16", sunday["date"]!!.jsonPrimitive.content)
    }

    @Test
    fun cadaEstudianteSoloVeSuPropioHorario() = apiTest(seed = true) {
        val mine = newStudent("horario.cuatro@unab.edu.co")
        createSchedule(mine)
        val other = newStudent("horario.cinco@unab.edu.co")
        assertEquals(JsonNull, nextAt(other, "2026-11-16T07:30")["data"])
    }

    @Test
    fun usaLaHoraActualSiNoSePideUnaFecha() = apiTest(seed = true) {
        val token = newStudent("horario.seis@unab.edu.co")
        createSchedule(token)
        val data = client.getAuth(next, token).json().second.data()
        assertTrue(data["starts_in_minutes"]!!.jsonPrimitive.long >= 0)
        assertTrue(data["date"]!!.jsonPrimitive.content.startsWith("20"))
    }

    @Test
    fun fechaInvalidaResponde400() = apiTest(seed = true) {
        val token = newStudent("horario.siete@unab.edu.co")
        val (status, body) = client.getAuth("$next?at=mañana", token).json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.containsKey("at"))
    }
}
