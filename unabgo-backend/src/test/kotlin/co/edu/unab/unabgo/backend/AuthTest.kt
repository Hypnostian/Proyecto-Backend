package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.UserSeeder
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Registro, inicio y cierre de sesión con tokens (equivalente a Sanctum). */
class AuthTest {

    @Test
    fun registroEmiteTokenYCreaElPerfil() = apiTest(seed = true) {
        val (status, body) = client.postJson("/api/v1/auth/register",
            """{"name":"Ana Gómez","email":"ana.gomez@unab.edu.co","password":"clave2026","password_confirmation":"clave2026"}""").json()
        assertEquals(201, status)
        val data = body["data"]!!.jsonObject
        assertEquals("Bearer", data["token_type"]!!.jsonPrimitive.content)
        assertEquals("ESTUDIANTE", data["user"]!!.jsonObject["role"]!!.jsonPrimitive.content)
        val token = data["token"]!!.jsonPrimitive.content
        assertTrue(token.substringBefore('.').toLongOrNull() != null, "el token tiene la forma id.secreto")
        assertEquals(200, client.getAuth("/api/v1/me", token).status.value)
    }

    @Test
    fun registroValidaLosCampos() = apiTest(seed = true) {
        val (status, body) = client.postJson("/api/v1/auth/register",
            """{"name":"","email":"ana@gmail.com","password":"corta","password_confirmation":"otra"}""").json()
        assertEquals(400, status)
        assertEquals("VALIDATION_ERROR", body["code"]!!.jsonPrimitive.content)
        val fields = body["fields"]!!.jsonObject.keys
        assertTrue(fields.containsAll(listOf("name", "email", "password", "password_confirmation")), "campos: $fields")
    }

    @Test
    fun registroGuardaCarreraSemestreEIdiomaEnElPerfil() = apiTest(seed = true) {
        val programId = client.getAuth("/api/v1/programs/ingenieria-de-sistemas").json().second.data()["id"]!!.jsonPrimitive.content
        val (status, body) = client.postJson("/api/v1/auth/register",
            """{"name":"Juan Pérez","email":"juan.perez@unab.edu.co","password":"clave2026","password_confirmation":"clave2026",
               "program_id":$programId,"semester":6,"language":"en"}""").json()
        assertEquals(201, status)
        val token = body.data()["token"]!!.jsonPrimitive.content
        // El perfil queda listo para mostrar solo el plan de la carrera elegida
        val profile = client.getAuth("/api/v1/me", token).json().second.data()["profile"]!!.jsonObject
        assertEquals(6, profile["semester"]!!.jsonPrimitive.content.toInt())
        assertEquals("en", profile["language"]!!.jsonPrimitive.content)
        val program = profile["program"]!!.jsonObject
        assertEquals("Ingeniería de Sistemas", program["name"]!!.jsonPrimitive.content)
        assertEquals("Profesional", program["level"]!!.jsonPrimitive.content)
        assertEquals("ingenieria-de-sistemas", program["study_plan_slug"]!!.jsonPrimitive.content)
    }

    @Test
    fun registroValidaCarreraSemestreEIdioma() = apiTest(seed = true) {
        val (status, body) = client.postJson("/api/v1/auth/register",
            """{"name":"Ana","email":"ana.b@unab.edu.co","password":"clave2026","password_confirmation":"clave2026","semester":13,"language":"de"}""").json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.keys.containsAll(listOf("semester", "language")))
        val (status2, body2) = client.postJson("/api/v1/auth/register",
            """{"name":"Ana","email":"ana.b@unab.edu.co","password":"clave2026","password_confirmation":"clave2026","program_id":999999}""").json()
        assertEquals(400, status2)
        assertTrue(body2["fields"]!!.jsonObject.containsKey("program_id"))
        // Con la carrera inválida no queda ninguna cuenta a medias
        assertEquals(401, client.postJson("/api/v1/auth/login", """{"email":"ana.b@unab.edu.co","password":"clave2026"}""").status.value)
    }

    @Test
    fun correoRepetidoEsConflicto() = apiTest(seed = true) {
        val response = client.postJson("/api/v1/auth/register",
            """{"name":"Otro","email":"${UserSeeder.STUDENT_EMAIL}","password":"clave2026","password_confirmation":"clave2026"}""")
        assertEquals(409, response.status.value)
    }

    @Test
    fun loginIncorrectoYRutasProtegidas() = apiTest(seed = true) {
        assertEquals(401, client.postJson("/api/v1/auth/login", """{"email":"${UserSeeder.STUDENT_EMAIL}","password":"mala123"}""").status.value)
        assertEquals(401, client.getAuth("/api/v1/me").status.value)
        assertEquals(401, client.getAuth("/api/v1/me", "1.token-inventado").status.value)
    }

    @Test
    fun logoutRevocaElToken() = apiTest(seed = true) {
        val token = tokenFor(UserSeeder.STUDENT_EMAIL)
        assertEquals(200, client.getAuth("/api/v1/me", token).status.value)
        assertEquals(204, client.postJson("/api/v1/auth/logout", "{}", token).status.value)
        assertEquals(401, client.getAuth("/api/v1/me", token).status.value)
    }
}
