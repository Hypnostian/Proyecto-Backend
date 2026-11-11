package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.UserSeeder
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Roles, Gates y Policies: cada perfil solo hace lo que le corresponde (403 si no). */
class AuthorizationTest {

    private val subjectJson = """{"name":"Arquitectura Backend","credits":3,"color":"#8600dd",
        "blocks":[{"day_of_week":6,"start_time":"08:00","end_time":"10:00","room":"A-201"}]}"""

    @Test
    fun estudianteCreaSusMateriasYOtroNoLasVe() = apiTest(seed = true) {
        val mine = tokenFor(UserSeeder.STUDENT_EMAIL)
        val (status, body) = client.postJson("/api/v1/subjects", subjectJson, mine).json()
        assertEquals(201, status, body.toString())
        val id = body["data"]!!.jsonObject["id"]!!.jsonPrimitive.long

        // Otro estudiante: se registra y no puede ver ni borrar la materia ajena
        val other = client.postJson("/api/v1/auth/register",
            """{"name":"Luis","email":"luis.p@unab.edu.co","password":"clave2026","password_confirmation":"clave2026"}""")
            .json().second["data"]!!.jsonObject["token"]!!.jsonPrimitive.content
        assertEquals(403, client.getAuth("/api/v1/subjects/$id", other).status.value)
        assertEquals(403, client.deleteAuth("/api/v1/subjects/$id", other).status.value)

        // Ni el administrador lee las notas de un estudiante (mínimo privilegio)
        val admin = tokenFor(UserSeeder.ADMIN_EMAIL)
        assertEquals(403, client.getAuth("/api/v1/subjects/$id", admin).status.value)

        assertEquals(204, client.deleteAuth("/api/v1/subjects/$id", mine).status.value)
        assertEquals(404, client.getAuth("/api/v1/subjects/$id", mine).status.value)
    }

    @Test
    fun choqueDeHorarioSeRechaza() = apiTest(seed = true) {
        val token = tokenFor(UserSeeder.STUDENT_EMAIL)
        assertEquals(201, client.postJson("/api/v1/subjects", subjectJson, token).status.value)
        val clash = """{"name":"Otra","credits":2,"color":"#123456",
            "blocks":[{"day_of_week":6,"start_time":"09:00","end_time":"11:00"}]}"""
        val (status, body) = client.postJson("/api/v1/subjects", clash, token).json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.containsKey("blocks.0"))
    }

    @Test
    fun resumenDeNotasSegunElReglamento() = apiTest(seed = true) {
        val token = tokenFor(UserSeeder.STUDENT_EMAIL)
        val id = client.postJson("/api/v1/subjects", subjectJson, token).json().second["data"]!!.jsonObject["id"]!!.jsonPrimitive.long
        val cat = client.postJson("/api/v1/subjects/$id/categories", """{"name":"Corte 1","weight":50}""", token)
            .json().second["data"]!!.jsonObject["id"]!!.jsonPrimitive.long
        assertEquals(201, client.postJson("/api/v1/categories/$cat/items", """{"name":"Parcial","score":4.2}""", token).status.value)
        assertEquals(201, client.postJson("/api/v1/categories/$cat/items", """{"name":"Quiz","score":4.2}""", token).status.value)
        // Los cortes no pueden pasar de 100 %
        assertEquals(400, client.postJson("/api/v1/subjects/$id/categories", """{"name":"Corte 2","weight":60}""", token).status.value)
        val summary = client.getAuth("/api/v1/subjects/$id/summary", token).json().second["data"]!!.jsonObject
        assertEquals(4.2, summary["average"]!!.jsonPrimitive.content.toDouble())
        assertEquals(1.8, summary["needed_to_pass"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun soloGestoresPublicanAvisosYSoloElAutorLosEdita() = apiTest(seed = true) {
        val announcement = """{"title":"Prueba","body":"Contenido","category":"bienestar","publish":true}"""
        val student = tokenFor(UserSeeder.STUDENT_EMAIL)
        assertEquals(403, client.postJson("/api/v1/announcements", announcement, student).status.value)

        val bienestar = tokenFor(UserSeeder.BIENESTAR_EMAIL)
        val (status, body) = client.postJson("/api/v1/announcements", announcement, bienestar).json()
        assertEquals(201, status)
        val data = body["data"]!!.jsonObject
        assertTrue(data["push_sent_at"]!!.jsonPrimitive.content.isNotBlank(), "al publicar se envía la notificación")
        val id = data["id"]!!.jsonPrimitive.long

        val transporte = tokenFor(UserSeeder.TRANSPORTE_EMAIL)
        assertEquals(403, client.putJson("/api/v1/announcements/$id", announcement, transporte).status.value)
        val admin = tokenFor(UserSeeder.ADMIN_EMAIL)
        assertEquals(200, client.putJson("/api/v1/announcements/$id", announcement, admin).status.value)
        assertEquals(204, client.deleteAuth("/api/v1/announcements/$id", bienestar).status.value)
    }

    @Test
    fun soloElAdministradorGestionaUsuarios() = apiTest(seed = true) {
        val gestor = tokenFor(UserSeeder.BIENESTAR_EMAIL)
        assertEquals(403, client.getAuth("/api/v1/admin/users", gestor).status.value)

        val admin = tokenFor(UserSeeder.ADMIN_EMAIL)
        val (status, body) = client.getAuth("/api/v1/admin/users?role=estudiante", admin).json()
        assertEquals(200, status)
        assertEquals(12, body["meta"]!!.jsonObject["total"]!!.jsonPrimitive.int)
        val studentId = body["data"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.long
        val changed = client.patchJson("/api/v1/admin/users/$studentId/role", """{"role":"GESTOR"}""", admin).json()
        assertEquals(200, changed.first)
        assertEquals("GESTOR", changed.second["data"]!!.jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals(400, client.patchJson("/api/v1/admin/users/$studentId/role", """{"role":"JEFE"}""", admin).status.value)
    }

    @Test
    fun rutasPublicas() = apiTest(seed = true) {
        val programs = client.getAuth("/api/v1/programs?q=sistemas").json()
        assertEquals(200, programs.first)
        assertTrue(programs.second["meta"]!!.jsonObject["total"]!!.jsonPrimitive.int > 0)
        assertEquals(4, client.getAuth("/api/v1/bus/routes").json().second["data"]!!.jsonArray.size)
        assertEquals(200, client.getAuth("/api/v1/announcements").status.value)
        assertEquals(404, client.getAuth("/api/v1/programs/no-existe").status.value)
        assertEquals(200, client.getAuth("/api/v1/health").status.value)
    }
}
