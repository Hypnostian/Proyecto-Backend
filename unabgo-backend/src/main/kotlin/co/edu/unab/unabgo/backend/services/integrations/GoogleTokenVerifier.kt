package co.edu.unab.unabgo.backend.services.integrations

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.security.PublicKey
import java.time.Clock

/** Identidad verificada de un ID token de Firebase Auth (inicio de sesión con Google). */
data class GoogleIdentity(val uid: String, val email: String, val name: String?, val emailVerified: Boolean)

/** El ID token no es válido: firma, emisor, audiencia o vencimiento incorrectos. */
class InvalidIdTokenException(message: String) : RuntimeException(message)

interface GoogleTokenVerifier {
    /** Verifica el token. Lanza InvalidIdTokenException si no es válido o ExternalServiceException si Google no responde. */
    suspend fun verify(idToken: String): GoogleIdentity
}

/**
 * Verifica ID tokens de Firebase Auth: firma RS256 con las claves públicas de Google, emisor
 * https://securetoken.google.com/{proyecto}, audiencia = id del proyecto y vencimiento.
 */
class FirebaseIdTokenVerifier(
    private val client: HttpClient,
    private val projectId: String,
    private val jwksUrl: String = DEFAULT_JWKS_URL,
    private val clock: Clock = Clock.systemUTC()
) : GoogleTokenVerifier {

    private val lock = Mutex()
    private var keys: Map<String, PublicKey> = emptyMap()
    private var keysFetchedAt = 0L

    override suspend fun verify(idToken: String): GoogleIdentity {
        val jwt = try {
            Jwt.parse(idToken)
        } catch (e: InvalidJwtException) {
            throw InvalidIdTokenException(e.message ?: "Token inválido.")
        }
        if (jwt.header.str("alg") != "RS256") throw InvalidIdTokenException("El token debe estar firmado con RS256.")
        val kid = jwt.header.str("kid") ?: throw InvalidIdTokenException("El token no indica la clave (kid).")
        val key = publicKey(kid) ?: throw InvalidIdTokenException("El token usa una clave desconocida.")
        if (!Jwt.verifyRs256(jwt, key)) throw InvalidIdTokenException("La firma del token no es válida.")

        val claims = jwt.payload
        val now = clock.millis() / 1000
        if (claims.str("iss") != "https://securetoken.google.com/$projectId") throw InvalidIdTokenException("El emisor del token no es válido.")
        if (claims.str("aud") != projectId) throw InvalidIdTokenException("El token no es para esta aplicación.")
        val exp = claims.str("exp")?.toLongOrNull() ?: throw InvalidIdTokenException("El token no tiene vencimiento.")
        if (exp + LEEWAY_SECONDS < now) throw InvalidIdTokenException("El token venció.")
        val iat = claims.str("iat")?.toLongOrNull()
        if (iat != null && iat - LEEWAY_SECONDS > now) throw InvalidIdTokenException("El token todavía no es válido.")
        val uid = claims.str("sub")?.takeIf { it.isNotBlank() } ?: throw InvalidIdTokenException("El token no identifica al usuario.")
        val email = claims.str("email")?.takeIf { it.isNotBlank() } ?: throw InvalidIdTokenException("El token no trae correo.")
        return GoogleIdentity(uid, email.lowercase(), claims.str("name"), claims.str("email_verified") == "true")
    }

    /** Claves públicas en caché una hora; si llega un kid nuevo se vuelven a pedir (rotación de claves de Google). */
    private suspend fun publicKey(kid: String): PublicKey? = lock.withLock {
        val now = clock.millis()
        val stale = now - keysFetchedAt > CACHE_MILLIS
        val unknownKid = kid !in keys && now - keysFetchedAt > MIN_REFETCH_MILLIS
        if (stale || unknownKid) loadKeys(now)
        keys[kid]
    }

    private suspend fun loadKeys(now: Long) {
        try {
            val response = client.get(jwksUrl)
            if (!response.status.isSuccess()) throw ExternalServiceException("Google respondió HTTP ${response.status.value} al pedir las claves públicas.")
            val list = Json.parseToJsonElement(response.bodyAsText()).jsonObject["keys"]!!.jsonArray
            keys = list.map { it.jsonObject }
                .filter { it.str("kty") == "RSA" && it.str("kid") != null }
                .associate { it.str("kid")!! to Jwt.publicKeyFromJwk(it.str("n")!!, it.str("e")!!) }
            keysFetchedAt = now
        } catch (e: ExternalServiceException) {
            throw e
        } catch (e: Exception) {
            throw ExternalServiceException("No se pudieron obtener las claves públicas de Google.", e)
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    companion object {
        const val DEFAULT_JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
        private const val LEEWAY_SECONDS = 60L
        private const val CACHE_MILLIS = 3_600_000L
        private const val MIN_REFETCH_MILLIS = 60_000L
    }
}
