package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.config.AppConfig
import co.edu.unab.unabgo.backend.database.AppDatabase
import co.edu.unab.unabgo.backend.database.DatabaseFactory
import co.edu.unab.unabgo.backend.database.factories.Factories
import co.edu.unab.unabgo.backend.database.seeders.DatabaseSeeder
import co.edu.unab.unabgo.backend.services.integrations.Integrations
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.TimeZone
import java.util.UUID

/** Base de datos H2 en memoria, nueva en cada prueba, con las migraciones ejecutadas (y los seeders). */
object TestDb {
    init {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    val testConfig = AppConfig(env = "testing")

    fun fresh(seed: Boolean): AppDatabase {
        val db = DatabaseFactory.connect(
            testConfig.copy(dbUrl = "jdbc:h2:mem:t${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
        )
        DatabaseFactory.fresh(db)
        if (seed) DatabaseSeeder().run()
        return db
    }
}

/**
 * Prueba de la API completa con base H2 en memoria. Las integraciones externas no se crean desde el .env:
 * cada prueba pasa las suyas (con MockEngine) y, si no pasa ninguna, todas quedan desactivadas.
 */
fun apiTest(
    seed: Boolean = true,
    config: AppConfig = TestDb.testConfig,
    integrations: Integrations = Integrations(),
    block: suspend ApplicationTestBuilder.() -> Unit
) = testApplication {
    val db = TestDb.fresh(seed)
    application { module(config, integrations) }
    try {
        block()
    } finally {
        db.close()
    }
}

fun HttpResponse.json(): Pair<Int, JsonObject> = status.value to ApiJson.parseToJsonElement(kotlinx.coroutines.runBlocking { bodyAsText() }).jsonObject

suspend fun HttpClient.postJson(path: String, body: String, token: String? = null) = post(path) {
    contentType(ContentType.Application.Json); setBody(body); token?.let { bearerAuth(it) }
}

suspend fun HttpClient.putJson(path: String, body: String, token: String? = null) = put(path) {
    contentType(ContentType.Application.Json); setBody(body); token?.let { bearerAuth(it) }
}

suspend fun HttpClient.patchJson(path: String, body: String, token: String? = null) = patch(path) {
    contentType(ContentType.Application.Json); setBody(body); token?.let { bearerAuth(it) }
}

suspend fun HttpClient.getAuth(path: String, token: String? = null) = get(path) { token?.let { bearerAuth(it) } }

suspend fun HttpClient.deleteAuth(path: String, token: String? = null) = delete(path) { token?.let { bearerAuth(it) } }

/** Inicia sesión con un usuario sembrado y devuelve su token. */
suspend fun ApplicationTestBuilder.tokenFor(email: String, password: String = Factories.DEFAULT_PASSWORD): String {
    val (status, body) = client.postJson("/api/v1/auth/login", """{"email":"$email","password":"$password","device_name":"pruebas"}""").json()
    check(status == 200) { "login falló: $status $body" }
    return body["data"]!!.jsonObject["token"]!!.jsonPrimitive.content
}

/** Firma el cuerpo como lo haría el dispositivo a bordo (HMAC-SHA256 en hexadecimal). */
suspend fun HttpClient.postSigned(path: String, body: String, signature: String?) = post(path) {
    contentType(ContentType.Application.Json); setBody(body); signature?.let { header("X-Signature", it) }
}

fun JsonObject.data(): JsonObject = this["data"]!!.jsonObject

/** Firma HMAC-SHA256 en hexadecimal, calculada aparte del código de producción. */
fun signBody(secret: String, body: String): String {
    val mac = javax.crypto.Mac.getInstance("HmacSHA256")
    mac.init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(body.toByteArray()).joinToString("") { "%02x".format(it) }
}
