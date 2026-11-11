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
