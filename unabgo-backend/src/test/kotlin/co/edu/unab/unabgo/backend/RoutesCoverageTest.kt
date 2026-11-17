package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.UserSeeder
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Casos de éxito y de error de las rutas de http/routes/ApiRoutes.kt que no cubren las demás pruebas
 * (salud, perfil, favoritas, materias, rutas del bus, avisos, preguntas frecuentes y administración).
 */
class RoutesCoverageTest {

    private val subjectJson = """{"name":"Redes","credits":3,"color":"#336699",
        "blocks":[{"day_of_week":6,"start_time":"08:00","end_time":"10:00","room":"B-101"}]}"""

    // ------------------------------------------------------------------ salud y perfil

    @Test
    fun salud() = apiTest {
        val (status, body) = client.getAuth("/api/v1/health").json()
        assertEquals(200, status)
        assertEquals("ok", body["status"]!!.jsonPrimitive.content)
    }

    @Test
    fun rutaInexistenteDevuelveJsonUniforme() = apiTest {
        val (status, body) = client.getAuth("/api/v1/no-existe").json()
        assertEquals(404, status)
        assertEquals("NOT_FOUND", body["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun miPerfilSeConsultaYSeActualiza() = apiTest {
        val token = tokenFor(UserSeeder.STUDENT_EMAIL)
        val me = client.getAuth("/api/v1/me", token).json()
        assertEquals(200, me.first)
        assertEquals(UserSeeder.STUDENT_EMAIL, me.second.data()["email"]!!.jsonPrimitive.content)

        val programId = client.getAuth("/api/v1/programs/ingenieria-de-sistemas").json().second.data()["id"]!!.jsonPrimitive.long
        val (status, body) = client.putJson("/api/v1/me/profile",
            """{"program_id":$programId,"semester":5,"language":"en","reminder_minutes":60,"student_code":"U0001"}""", token).json()
        assertEquals(200, status, body.toString())
        val profile = body.data()["profile"]!!.jsonObject
        assertEquals("5", profile["semester"]!!.jsonPrimitive.content)
        assertEquals("en", profile["language"]!!.jsonPrimitive.content)
        assertEquals("Ingeniería de Sistemas", profile["program"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun miPerfilValidaYExigeToken() = apiTest {
        val token = tokenFor(UserSeeder.STUDENT_EMAIL)
        val (status, body) = client.putJson("/api/v1/me/profile", """{"semester":30,"language":"xx","reminder_minutes":7}""", token).json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.keys.containsAll(listOf("semester", "language", "reminder_minutes")))
        assertEquals(404, client.putJson("/api/v1/me/profile", """{"program_id":999999}""", token).status.value)
        assertEquals(401, client.putJson("/api/v1/me/profile", "{}").status.value)
    }

    // ------------------------------------------------------------------ rutas del bus y favoritas

    private suspend fun io.ktor.server.testing.ApplicationTestBuilder.routeIds(): List<Long> =
        client.getAuth("/api/v1/bus/routes").json().second["data"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.long }

    @Test
    fun rutasFavoritas() = apiTest {
        val token = tokenFor(UserSeeder.STUDENT_EMAIL)
        val listed = client.getAuth("/api/v1/me/favorite-routes", token).json()
        assertEquals(200, listed.first)
        val favorite = listed.second["data"]!!.jsonArray.first().jsonObject["id"]!!.jsonPrimitive.long

        assertEquals(204, client.deleteAuth("/api/v1/me/favorite-routes/$favorite", token).status.value)
        assertEquals(404, client.deleteAuth("/api/v1/me/favorite-routes/$favorite", token).status.value)
        val added = client.postJson("/api/v1/me/favorite-routes/$favorite", "{}", token).json()
        assertEquals(201, added.first)
        assertTrue(added.second["data"]!!.jsonArray.any { it.jsonObject["id"]!!.jsonPrimitive.long == favorite })

        assertEquals(404, client.postJson("/api/v1/me/favorite-routes/999999", "{}", token).status.value)
        assertEquals(401, client.getAuth("/api/v1/me/favorite-routes").status.value)
        assertEquals(401, client.postJson("/api/v1/me/favorite-routes/$favorite", "{}").status.value)
        assertEquals(401, client.deleteAuth("/api/v1/me/favorite-routes/$favorite").status.value)
    }

    @Test
    fun elGestorEditaUnaRutaDelBus() = apiTest {
        val gestor = tokenFor(UserSeeder.TRANSPORTE_EMAIL)
        val route = client.getAuth("/api/v1/bus/routes").json().second["data"]!!.jsonArray.first().jsonObject
        val id = route["id"]!!.jsonPrimitive.long
        val stops = route["stops"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.long }
        val reversed = stops.reversed().joinToString(",")

        val (status, body) = client.putJson("/api/v1/bus/routes/$id", """{"name":"Ruta Uno","color":"#112233","active":false,"stop_ids":[$reversed]}""", gestor).json()
        assertEquals(200, status, body.toString())
        assertEquals("Ruta Uno", body.data()["name"]!!.jsonPrimitive.content)
        assertEquals(stops.reversed(), body.data()["stops"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.long })
    }

    @Test
    fun editarRutasValidaYAutoriza() = apiTest {
        val ids = routeIds()
        val valid = """{"name":"Ruta","color":"#112233","stop_ids":[1,2]}"""
        assertEquals(403, client.putJson("/api/v1/bus/routes/${ids[0]}", valid, tokenFor(UserSeeder.STUDENT_EMAIL)).status.value)
        assertEquals(401, client.putJson("/api/v1/bus/routes/${ids[0]}", valid).status.value)
        val gestor = tokenFor(UserSeeder.TRANSPORTE_EMAIL)
        assertEquals(400, client.putJson("/api/v1/bus/routes/${ids[0]}", """{"name":"","color":"rojo","stop_ids":[1]}""", gestor).status.value)
        assertEquals(400, client.putJson("/api/v1/bus/routes/${ids[0]}", """{"name":"Ruta","color":"#112233","stop_ids":[1,999999]}""", gestor).status.value)
        assertEquals(404, client.putJson("/api/v1/bus/routes/999999", valid, gestor).status.value)
    }

    // ------------------------------------------------------------------ programas

    @Test
    fun programasFiltrosYDetalle() = apiTest {
        val detail = client.getAuth("/api/v1/programs/ingenieria-de-sistemas").json()
        assertEquals(200, detail.first)
        assertEquals("Ingeniería de Sistemas", detail.second.data()["name"]!!.jsonPrimitive.content)
        assertEquals(200, client.getAuth("/api/v1/programs?faculty=UNAB%20Tec&per_page=5").status.value)
        assertEquals(400, client.getAuth("/api/v1/programs?max_price=barato").status.value)
    }

    // ------------------------------------------------------------------ materias

    @Test
    fun crudDeMaterias() = apiTest {
        val token = tokenFor(UserSeeder.STUDENT_EMAIL)
        val list = client.getAuth("/api/v1/subjects", token).json()
        assertEquals(200, list.first)
        val before = list.second["data"]!!.jsonArray.size

        val created = client.postJson("/api/v1/subjects", subjectJson.replace("08:00", "19:00").replace("10:00", "21:00"), token).json()
        assertEquals(201, created.first, created.second.toString())
        val id = created.second.data()["id"]!!.jsonPrimitive.long
        assertEquals(before + 1, client.getAuth("/api/v1/subjects", token).json().second["data"]!!.jsonArray.size)

        val shown = client.getAuth("/api/v1/subjects/$id", token).json()
        assertEquals(200, shown.first)
        assertEquals("Redes", shown.second.data()["name"]!!.jsonPrimitive.content)

        val updated = client.putJson("/api/v1/subjects/$id", subjectJson.replace("Redes", "Redes II").replace("08:00", "19:00").replace("10:00", "21:00"), token).json()
        assertEquals(200, updated.first, updated.second.toString())
        assertEquals("Redes II", updated.second.data()["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun materiasValidanAutorizanYNoEncuentran() = apiTest {
        val student = tokenFor(UserSeeder.STUDENT_EMAIL)
        val (status, body) = client.postJson("/api/v1/subjects", """{"name":"","credits":50,"color":"azul"}""", student).json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.keys.containsAll(listOf("name", "credits", "color")))
        assertEquals(400, client.putJson("/api/v1/subjects/1", """{"name":""}""", student).status.value)

        assertEquals(401, client.getAuth("/api/v1/subjects").status.value)
        assertEquals(403, client.getAuth("/api/v1/subjects", tokenFor(UserSeeder.ADMIN_EMAIL)).status.value)
        assertEquals(403, client.postJson("/api/v1/subjects", subjectJson, tokenFor(UserSeeder.BIENESTAR_EMAIL)).status.value)

        assertEquals(404, client.getAuth("/api/v1/subjects/999999", student).status.value)
        assertEquals(404, client.putJson("/api/v1/subjects/999999", subjectJson, student).status.value)
        assertEquals(404, client.deleteAuth("/api/v1/subjects/999999", student).status.value)
        assertEquals(404, client.getAuth("/api/v1/subjects/999999/summary", student).status.value)
        assertEquals(404, client.postJson("/api/v1/subjects/999999/categories", """{"name":"Corte","weight":10}""", student).status.value)
        assertEquals(404, client.postJson("/api/v1/categories/999999/items", """{"name":"Quiz","score":3.0}""", student).status.value)
    }

    @Test
    fun cortesYNotasDeOtroEstudianteSonPrivados() = apiTest {
        val mine = tokenFor(UserSeeder.STUDENT_EMAIL)
        val subject = client.postJson("/api/v1/subjects", subjectJson, mine).json().second.data()["id"]!!.jsonPrimitive.long
        val category = client.postJson("/api/v1/subjects/$subject/categories", """{"name":"Corte 1","weight":40}""", mine).json().second.data()["id"]!!.jsonPrimitive.long
        val other = client.postJson("/api/v1/auth/register",
            """{"name":"Luis","email":"luis.q@unab.edu.co","password":"clave2026","password_confirmation":"clave2026"}""")
            .json().second.data()["token"]!!.jsonPrimitive.content
        assertEquals(403, client.postJson("/api/v1/subjects/$subject/categories", """{"name":"Corte 2","weight":10}""", other).status.value)
        assertEquals(403, client.postJson("/api/v1/categories/$category/items", """{"name":"Quiz","score":3.0}""", other).status.value)
        assertEquals(403, client.getAuth("/api/v1/subjects/$subject/summary", other).status.value)
        assertEquals(400, client.postJson("/api/v1/categories/$category/items", """{"name":"Quiz","score":9.5}""", mine).status.value)
    }

    // ------------------------------------------------------------------ avisos

    @Test
    fun avisosPublicosFiltranPorCategoria() = apiTest {
        val (status, body) = client.getAuth("/api/v1/announcements?category=transporte&per_page=5").json()
        assertEquals(200, status)
        assertTrue(body["data"]!!.jsonArray.all { it.jsonObject["category"]!!.jsonPrimitive.content == "transporte" })
        assertEquals(400, client.getAuth("/api/v1/announcements?category=inventada").status.value)
    }

    @Test
    fun avisosValidanYNoEncuentran() = apiTest {
        val gestor = tokenFor(UserSeeder.BIENESTAR_EMAIL)
        val (status, body) = client.postJson("/api/v1/announcements", """{"title":"","body":"","category":"otra"}""", gestor).json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.keys.containsAll(listOf("title", "body", "category")))
        val valid = """{"title":"Aviso","body":"Contenido","category":"evento"}"""
        assertEquals(401, client.postJson("/api/v1/announcements", valid).status.value)
        assertEquals(404, client.putJson("/api/v1/announcements/999999", valid, gestor).status.value)
        assertEquals(404, client.deleteAuth("/api/v1/announcements/999999", gestor).status.value)
        // Un borrador no sale en la lista pública hasta que se publica
        val draft = client.postJson("/api/v1/announcements", valid, gestor).json().second.data()
        assertEquals("null", draft["published_at"]!!.toString())
        assertEquals(200, client.putJson("/api/v1/announcements/${draft["id"]!!.jsonPrimitive.long}", valid.replace("}", ""","publish":true}"""), gestor).status.value)
    }

    // ------------------------------------------------------------------ preguntas frecuentes (gestor)

    private val faqJson = """{"question":"¿Dónde queda la biblioteca?","keywords":"biblioteca; sala de estudio","answer":"En el bloque principal.","source_url":"https://unab.edu.co/biblioteca/"}"""

    @Test
    fun crudDePreguntasFrecuentes() = apiTest {
        val gestor = tokenFor(UserSeeder.BIENESTAR_EMAIL)
        val list = client.getAuth("/api/v1/faqs", gestor).json()
        assertEquals(200, list.first)
        assertEquals(58, list.second["data"]!!.jsonArray.size)

        val created = client.postJson("/api/v1/faqs", faqJson, gestor).json()
        assertEquals(201, created.first, created.second.toString())
        val id = created.second.data()["id"]!!.jsonPrimitive.long

        val updated = client.putJson("/api/v1/faqs/$id", faqJson.replace("En el bloque principal.", "En el primer piso."), gestor).json()
        assertEquals(200, updated.first)
        assertEquals("En el primer piso.", updated.second.data()["answer"]!!.jsonPrimitive.content)

        assertEquals(204, client.deleteAuth("/api/v1/faqs/$id", gestor).status.value)
        // Banu ya no responde con una pregunta desactivada
        val answer = client.postJson("/api/v1/banu/ask", """{"pregunta":"donde queda la biblioteca"}""").json().second.data()
        assertTrue(!answer["respuesta"]!!.jsonPrimitive.content.contains("primer piso"))
    }

    @Test
    fun preguntasFrecuentesValidanYAutorizan() = apiTest {
        val gestor = tokenFor(UserSeeder.BIENESTAR_EMAIL)
        val (status, body) = client.postJson("/api/v1/faqs", """{"question":"","keywords":"","answer":"","source_url":"http://inseguro"}""", gestor).json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.keys.containsAll(listOf("question", "keywords", "answer", "source_url")))

        assertEquals(401, client.getAuth("/api/v1/faqs").status.value)
        val student = tokenFor(UserSeeder.STUDENT_EMAIL)
        assertEquals(403, client.getAuth("/api/v1/faqs", student).status.value)
        assertEquals(403, client.postJson("/api/v1/faqs", faqJson, student).status.value)
        assertEquals(403, client.putJson("/api/v1/faqs/1", faqJson, student).status.value)
        assertEquals(403, client.deleteAuth("/api/v1/faqs/1", student).status.value)
        assertEquals(404, client.putJson("/api/v1/faqs/999999", faqJson, gestor).status.value)
        assertEquals(404, client.deleteAuth("/api/v1/faqs/999999", gestor).status.value)
    }

    // ------------------------------------------------------------------ administración de usuarios

    @Test
    fun administracionDeUsuariosExigeTokenYRol() = apiTest {
        assertEquals(401, client.getAuth("/api/v1/admin/users").status.value)
        assertEquals(401, client.patchJson("/api/v1/admin/users/1/role", """{"role":"GESTOR"}""").status.value)
        assertEquals(403, client.getAuth("/api/v1/admin/users", tokenFor(UserSeeder.STUDENT_EMAIL)).status.value)
        assertEquals(400, client.getAuth("/api/v1/admin/users?role=jefe", tokenFor(UserSeeder.ADMIN_EMAIL)).status.value)
        assertEquals(403, client.patchJson("/api/v1/admin/users/1/role", """{"role":"GESTOR"}""", tokenFor(UserSeeder.BIENESTAR_EMAIL)).status.value)
    }

    @Test
    fun elAdministradorNoCambiaSuPropioRolNiUnUsuarioInexistente() = apiTest {
        val admin = tokenFor(UserSeeder.ADMIN_EMAIL)
        val ownId = client.getAuth("/api/v1/me", admin).json().second.data()["id"]!!.jsonPrimitive.long
        assertEquals(403, client.patchJson("/api/v1/admin/users/$ownId/role", """{"role":"ESTUDIANTE"}""", admin).status.value)
        assertEquals(404, client.patchJson("/api/v1/admin/users/999999/role", """{"role":"ESTUDIANTE"}""", admin).status.value)
    }
}
