package co.edu.unab.unabgo.backend.services.integrations

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Un reporte de la plataforma GPS de las Rutas UNAB, ya convertido a tipos de Kotlin (la hora a UTC). */
data class GpsReport(
    val id: String?,
    val plate: String,
    val lat: Double,
    val lng: Double,
    val headingDeg: Double?,
    val at: Instant,
    val ignitionOn: Boolean,
    val event: String
)

/** Plataforma GPS de las Rutas UNAB (la URL está en GPS_FEED_URL). */
interface GpsFeedService {
    /** Reportes recientes de los buses. Lanza ExternalServiceException si el proveedor falla. */
    suspend fun fetchReports(): List<GpsReport>
}

class HttpGpsFeedService(private val client: HttpClient, private val url: String) : GpsFeedService {

    override suspend fun fetchReports(): List<GpsReport> {
        val text = try {
            val response = client.get(url) { header("Accept", "application/json") }
            if (!response.status.isSuccess()) throw ExternalServiceException("La plataforma GPS respondió HTTP ${response.status.value}.")
            response.bodyAsText()
        } catch (e: ExternalServiceException) {
            throw e
        } catch (e: Exception) {
            throw ExternalServiceException("No se pudo consultar la plataforma GPS.", e)
        }
        val array = try {
            Json.parseToJsonElement(text).jsonArray
        } catch (e: Exception) {
            throw ExternalServiceException("La plataforma GPS devolvió un formato inesperado.", e)
        }
        return parse(array)
    }

    companion object {
        /** La hora del GPS viene en hora local de Colombia (UTC-5) y sin zona. */
        val COLOMBIA: ZoneOffset = ZoneOffset.ofHours(-5)

        /** Lee cada elemento con tolerancia: los reportes dañados se descartan sin tumbar el resto. */
        fun parse(array: JsonArray): List<GpsReport> = array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val plate = o.text("placa")?.let(::normalizePlate)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val lat = o.text("lat")?.toDoubleLoose() ?: return@mapNotNull null
            val lng = o.text("lng")?.toDoubleLoose() ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return@mapNotNull null
            val at = o.text("fhEvento")?.let(::parseLocalTime) ?: return@mapNotNull null
            GpsReport(
                id = o.text("id"),
                plate = plate,
                lat = lat,
                lng = lng,
                headingDeg = parseHeading(o.text("sentido")),
                at = at,
                ignitionOn = parseBoolean(o.text("estadoIgnicion")),
                event = o.text("evento").orEmpty()
            )
        }

        /** "ruta 02" -> "RUTA02" */
        fun normalizePlate(raw: String) = raw.uppercase().filter { it.isLetterOrDigit() }

        fun parseLocalTime(raw: String): Instant? = runCatching {
            LocalDateTime.parse(raw.trim().replace(' ', 'T').substringBefore('Z')).toInstant(COLOMBIA)
        }.getOrNull()

        fun parseBoolean(raw: String?): Boolean = when (raw?.trim()?.lowercase()) {
            "true", "1", "on", "si", "sí", "encendido", "yes" -> true
            else -> false
        }

        /** El "sentido" puede ser un ángulo en grados o un punto cardinal ("Norte", "NE", "Suroeste"). */
        fun parseHeading(raw: String?): Double? {
            val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            text.toDoubleLoose()?.let { return ((it % 360) + 360) % 360 }
            return when (stripAccents(text).uppercase().replace(" ", "")) {
                "N", "NORTE" -> 0.0
                "NE", "NORESTE" -> 45.0
                "E", "ESTE", "ORIENTE" -> 90.0
                "SE", "SURESTE" -> 135.0
                "S", "SUR" -> 180.0
                "SO", "SW", "SUROESTE" -> 225.0
                "O", "W", "OESTE", "OCCIDENTE" -> 270.0
                "NO", "NW", "NOROESTE" -> 315.0
                else -> null
            }
        }

        fun stripAccents(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")

        private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
        private fun String.toDoubleLoose(): Double? = trim().replace(',', '.').toDoubleOrNull()
    }
}
