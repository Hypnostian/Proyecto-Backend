package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.services.integrations.externalHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList

/** Cliente HTTP falso para probar las integraciones: guarda las peticiones y responde lo que diga el manejador. */
class MockHttp(private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val requests = CopyOnWriteArrayList<HttpRequestData>()
    val client: HttpClient = externalHttpClient(MockEngine { request ->
        requests += request
        handler(request)
    })
}

fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

/** Par de claves RSA para firmar tokens en las pruebas (las claves reales de Google nunca se usan). */
fun newRsaKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

/** Documento JWKS con la clave pública, como lo publica Google. */
fun jwks(kid: String, pair: KeyPair): String {
    val pub = pair.public as RSAPublicKey
    val enc = Base64.getUrlEncoder().withoutPadding()
    fun big(n: java.math.BigInteger) = enc.encodeToString(n.toByteArray().let { if (it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it })
    return """{"keys":[{"kty":"RSA","alg":"RS256","use":"sig","kid":"$kid","n":"${big(pub.modulus)}","e":"${big(pub.publicExponent)}"}]}"""
}

fun pemPrivateKey(pair: KeyPair): String =
    "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.private.encoded) + "\n-----END PRIVATE KEY-----\n"
