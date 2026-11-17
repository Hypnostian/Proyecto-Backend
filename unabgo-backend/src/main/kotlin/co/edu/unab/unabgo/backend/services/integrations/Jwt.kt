package co.edu.unab.unabgo.backend.services.integrations

// JWT con RS256 (RSASSA-PKCS1-v1_5 + SHA-256) usando solo la librería estándar de Java. Sirve para firmar la
// aserción OAuth2 de la cuenta de servicio de Firebase y para verificar los ID tokens de Firebase Auth.

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.math.BigInteger
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

class InvalidJwtException(message: String) : RuntimeException(message)

object Jwt {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    class Parsed(val header: JsonObject, val payload: JsonObject, val signingInput: ByteArray, val signature: ByteArray)

    /** Firma los claims con RS256 y devuelve el token "cabecera.carga.firma". */
    fun signRs256(claims: JsonObject, key: PrivateKey, kid: String? = null): String {
        val header = buildJsonObject {
            put("alg", kotlinx.serialization.json.JsonPrimitive("RS256"))
            put("typ", kotlinx.serialization.json.JsonPrimitive("JWT"))
            if (kid != null) put("kid", kotlinx.serialization.json.JsonPrimitive(kid))
        }
        val input = "${b64(header.toString())}.${b64(claims.toString())}"
        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(key)
            update(input.toByteArray(Charsets.US_ASCII))
            sign()
        }
        return "$input.${encoder.encodeToString(signature)}"
    }

    fun parse(token: String): Parsed {
        val parts = token.split('.')
        if (parts.size != 3 || parts.any { it.isEmpty() }) throw InvalidJwtException("El token no tiene el formato JWT.")
        return try {
            Parsed(
                header = Json.parseToJsonElement(String(decoder.decode(parts[0]))).jsonObject,
                payload = Json.parseToJsonElement(String(decoder.decode(parts[1]))).jsonObject,
                signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII),
                signature = decoder.decode(parts[2])
            )
        } catch (e: Exception) {
            throw InvalidJwtException("El token no se puede leer.")
        }
    }

    fun verifyRs256(parsed: Parsed, key: PublicKey): Boolean = runCatching {
        Signature.getInstance("SHA256withRSA").run {
            initVerify(key)
            update(parsed.signingInput)
            verify(parsed.signature)
        }
    }.getOrDefault(false)

    /** Clave privada PKCS#8 en formato PEM ("-----BEGIN PRIVATE KEY-----"), como la entrega Google Cloud. */
    fun privateKeyFromPem(pem: String): PrivateKey {
        val body = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "").replace(Regex("\\s"), "")
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)))
    }

    /** Clave pública a partir de un JWK (n y e en Base64 URL). */
    fun publicKeyFromJwk(n: String, e: String): PublicKey =
        KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(BigInteger(1, decoder.decode(n)), BigInteger(1, decoder.decode(e))))

    private fun b64(text: String) = encoder.encodeToString(text.toByteArray(Charsets.UTF_8))
}
