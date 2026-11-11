package co.edu.unab.unabgo.backend.auth

import co.edu.unab.unabgo.backend.database.dbQuery
import co.edu.unab.unabgo.backend.models.AccessToken
import co.edu.unab.unabgo.backend.models.User
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** Contraseñas con BCrypt (nunca se guardan en claro). */
object PasswordHasher {
    fun hash(password: String): String = BCrypt.hashpw(password, BCrypt.gensalt(10))
    fun verify(password: String, hash: String?): Boolean = hash != null && runCatching { BCrypt.checkpw(password, hash) }.getOrDefault(false)
}

/**
 * Tokens de acceso personales, como Laravel Sanctum: el cliente recibe "id.secreto" una sola vez y
 * en la tabla access_tokens solo se guarda la huella SHA-256 del secreto. Si alguien roba la base de
 * datos, no puede usar los tokens.
 */
class TokenService(private val ttl: Duration, private val clock: Clock = Clock.systemUTC()) {

    data class IssuedToken(val plainText: String, val expiresAt: Instant)

    private val random = SecureRandom()

    /** Crea un token para el usuario (se llama dentro de una transacción). */
    fun issue(user: User, deviceName: String): IssuedToken {
        val secret = ByteArray(40).also(random::nextBytes).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val now = Instant.now(clock)
        val token = AccessToken.new {
            this.user = user
            name = deviceName.take(60)
            tokenHash = sha256(secret)
            abilities = Ability.forRole(user.userRole).joinToString(",") { it.key }
            expiresAt = now.plus(ttl)
            createdAt = now
        }
        // "." separa el id del secreto (es válido en el encabezado Authorization: Bearer)
        return IssuedToken("${token.id.value}.$secret", token.expiresAt)
    }

    /** Valida el token del encabezado Authorization: Bearer; null si no es válido o venció. */
    suspend fun authenticate(plainText: String): AuthUser? {
        val id = plainText.substringBefore('.', "").toLongOrNull() ?: return null
        val secret = plainText.substringAfter('.', "").takeIf { it.isNotEmpty() } ?: return null
        return dbQuery {
            val token = AccessToken.findById(id) ?: return@dbQuery null
            val now = Instant.now(clock)
            val matches = MessageDigest.isEqual(token.tokenHash.toByteArray(), sha256(secret).toByteArray())
            if (!matches || token.expiresAt.isBefore(now) || !token.user.active) return@dbQuery null
            // Se actualiza el último uso como mucho una vez por minuto
            if (token.lastUsedAt?.isAfter(now.minusSeconds(60)) != true) token.lastUsedAt = now
            val user = token.user
            AuthUser(
                id = user.id.value,
                name = user.name,
                email = user.email,
                role = user.userRole,
                tokenId = token.id.value,
                abilities = token.abilities.split(',').mapNotNull(Ability::fromKey).toSet()
            )
        }
    }

    /** Revoca un token (cierre de sesión). */
    fun revoke(tokenId: Long) {
        AccessToken.findById(tokenId)?.delete()
    }

    companion object {
        fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
