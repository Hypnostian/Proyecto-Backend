package co.edu.unab.unabgo.backend.http.controllers

// Endpoints de la entrega 3: Banu (preguntas e IA), webhook del GPS y profesores (datos públicos de APOLO).
// Aquí no hay clientes HTTP: los controladores solo llaman a los servicios.

import co.edu.unab.unabgo.backend.ApiJson
import co.edu.unab.unabgo.backend.config.AppConfig
import co.edu.unab.unabgo.backend.database.Professors
import co.edu.unab.unabgo.backend.database.dbQuery
import co.edu.unab.unabgo.backend.http.NotFoundException
import co.edu.unab.unabgo.backend.http.ServiceUnavailableException
import co.edu.unab.unabgo.backend.http.TooManyRequestsException
import co.edu.unab.unabgo.backend.http.UnauthorizedException
import co.edu.unab.unabgo.backend.http.ValidationException
import co.edu.unab.unabgo.backend.http.pageRequest
import co.edu.unab.unabgo.backend.http.requests.BanuAskRequest
import co.edu.unab.unabgo.backend.http.requests.GpsWebhookRequest
import co.edu.unab.unabgo.backend.http.resources.DataResponse
import co.edu.unab.unabgo.backend.http.resources.MessageResponse
import co.edu.unab.unabgo.backend.http.resources.toResource
import co.edu.unab.unabgo.backend.http.validate
import co.edu.unab.unabgo.backend.http.validated
import co.edu.unab.unabgo.backend.models.Professor
import co.edu.unab.unabgo.backend.services.BanuService
import co.edu.unab.unabgo.backend.services.DevicePosition
import co.edu.unab.unabgo.backend.services.GpsWebhookStore
import co.edu.unab.unabgo.backend.services.RateLimiter
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import kotlinx.serialization.SerializationException
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** POST /api/v1/banu/ask: pública, con un límite de peticiones por IP (429). */
class BanuController(private val banu: BanuService, private val limiter: RateLimiter) {

    suspend fun ask(call: ApplicationCall) {
        if (!limiter.allow(clientIp(call))) {
            throw TooManyRequestsException("Hiciste demasiadas preguntas seguidas. Espera un minuto e intenta de nuevo.")
        }
        val request = call.validated<BanuAskRequest>()
        call.respond(DataResponse(banu.ask(request.pregunta!!.trim())))
    }

    /** Detrás de un proxy (Render, Railway...) la IP real llega en X-Forwarded-For. */
    private fun clientIp(call: ApplicationCall): String =
        call.request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() } ?: call.request.origin.remoteAddress
}

/** POST /api/v1/webhooks/gps: posición de un dispositivo a bordo, firmada con HMAC-SHA256 del cuerpo. */
class GpsWebhookController(private val config: AppConfig, private val store: GpsWebhookStore, private val clock: Clock = Clock.systemUTC()) {

    suspend fun receive(call: ApplicationCall) {
        val secret = config.gpsWebhookSecret.takeIf { it.isNotBlank() }
            ?: throw ServiceUnavailableException("El webhook del GPS no está configurado (falta GPS_WEBHOOK_SECRET).")
        val raw = call.receiveText()
        val signature = call.request.headers["X-Signature"]
        if (signature == null || !validSignature(secret, raw, signature)) throw UnauthorizedException("La firma del webhook no es válida.")

        val request = try {
            ApiJson.decodeFromString<GpsWebhookRequest>(raw)
        } catch (e: SerializationException) {
            throw ValidationException(mapOf("body" to "El cuerpo debe ser un JSON con device_id, lat y lng."))
        } catch (e: IllegalArgumentException) {
            throw ValidationException(mapOf("body" to "El cuerpo debe ser un JSON con device_id, lat y lng."))
        }
        request.validate()
        store.put(
            DevicePosition(
                deviceId = request.deviceId!!.trim(),
                lat = request.lat!!,
                lng = request.lng!!,
                speedKmh = request.speedKmh,
                headingDeg = request.heading,
                ignitionOn = request.ignition,
                reportedAt = request.timestamp?.let { OffsetDateTime.parse(it).toInstant() } ?: Instant.now(clock)
            )
        )
        call.respond(HttpStatusCode.Accepted, DataResponse(MessageResponse("Posición recibida.")))
    }

    companion object {
        /** Hex del HMAC-SHA256 del cuerpo sin modificar (se acepta el prefijo "sha256="). Comparación en tiempo constante. */
        fun sign(secret: String, body: String): String =
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                doFinal(body.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            }

        fun validSignature(secret: String, body: String, header: String): Boolean {
            val given = header.trim().removePrefix("sha256=").lowercase()
            return MessageDigest.isEqual(sign(secret, body).toByteArray(), given.toByteArray())
        }
    }
}

/** Profesores de la UNAB con sus datos públicos de APOLO. */
class ProfessorController {

    /** GET /api/v1/professors?q=&unit=&page=&per_page= */
    suspend fun index(call: ApplicationCall) {
        val q = call.request.queryParameters
        val page = call.pageRequest()
        val body = dbQuery {
            var filter: Op<Boolean> = Op.TRUE
            q["q"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                val term = "%${it.lowercase().replace("%", "").replace("_", "")}%"
                filter = filter and ((Professors.name.lowerCase() like term) or (Professors.title.lowerCase() like term))
            }
            q["unit"]?.trim()?.takeIf { it.isNotEmpty() }?.let {
                filter = filter and (Professors.unit.lowerCase() like "%${it.lowercase().replace("%", "").replace("_", "")}%")
            }
            val query = Professor.find(filter)
            val total = query.count()
            val items = query.orderBy(Professors.name to SortOrder.ASC).limit(page.perPage).offset(page.offset).map { it.toResource() }
            DataResponse(items, page.meta(total))
        }
        call.respond(body)
    }

    /** GET /api/v1/professors/{slug} */
    suspend fun show(call: ApplicationCall) {
        val slug = call.parameters["slug"].orEmpty()
        val body = dbQuery { (Professor.find { Professors.slug eq slug }.singleOrNull() ?: throw NotFoundException("El profesor")).toResource() }
        call.respond(DataResponse(body))
    }
}
