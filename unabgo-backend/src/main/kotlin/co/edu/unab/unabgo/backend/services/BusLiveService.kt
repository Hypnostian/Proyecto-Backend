package co.edu.unab.unabgo.backend.services

// Posición en vivo de los buses: combina la plataforma GPS de las Rutas UNAB (GPS_FEED_URL) o, si no hay
// plataforma configurada, las posiciones que los dispositivos a bordo envían por el webhook firmado.

import co.edu.unab.unabgo.backend.http.ServiceUnavailableException
import co.edu.unab.unabgo.backend.services.integrations.ExternalServiceException
import co.edu.unab.unabgo.backend.services.integrations.GpsFeedService
import co.edu.unab.unabgo.backend.services.integrations.GpsReport
import co.edu.unab.unabgo.backend.services.integrations.HttpGpsFeedService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.text.Normalizer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class BusState { MOVING, STOPPED, PARKED }

/** Posición de un bus lista para mostrar. [routeCode] es el code de bus_routes (RUTA1, RUTA2, RUTA02, RUTA3). */
data class BusPosition(
    val deviceId: String,
    val plate: String,
    val routeCode: String?,
    val lat: Double,
    val lng: Double,
    val state: BusState,
    val speedKmh: Double?,
    val headingDeg: Double?,
    val reportedAt: Instant,
    val ageSeconds: Long,
    val source: String
)

/** Posición enviada por el webhook de un dispositivo a bordo. */
data class DevicePosition(
    val deviceId: String,
    val lat: Double,
    val lng: Double,
    val speedKmh: Double?,
    val headingDeg: Double?,
    val ignitionOn: Boolean?,
    val reportedAt: Instant
)

/** Última posición por dispositivo, en memoria (se pierde al reiniciar, como una caché). */
class GpsWebhookStore {
    private val positions = ConcurrentHashMap<String, DevicePosition>()

    fun put(position: DevicePosition) {
        if (positions.size >= MAX_DEVICES && !positions.containsKey(position.deviceId)) return
        positions.merge(position.deviceId, position) { old, new -> if (new.reportedAt >= old.reportedAt) new else old }
    }

    fun all(): List<DevicePosition> = positions.values.toList()
    fun isEmpty() = positions.isEmpty()

    private companion object {
        const val MAX_DEVICES = 500
    }
}

class BusLiveService(
    private val feed: GpsFeedService?,
    private val webhooks: GpsWebhookStore,
    private val clock: Clock = Clock.systemUTC(),
    private val cacheTtl: Duration = Duration.ofSeconds(5),
    private val staleFallback: Duration = Duration.ofMinutes(1)
) {
    private val log = LoggerFactory.getLogger("unabgo.bus")
    private val lock = Mutex()

    /** Últimos (máximo dos) reportes de cada placa, del más viejo al más reciente. */
    @Volatile private var history: Map<String, List<GpsReport>> = emptyMap()
    private var lastAttemptAt: Instant? = null
    private var lastGoodAt: Instant? = null

    suspend fun live(): List<BusPosition> {
        if (feed == null) {
            if (webhooks.isEmpty()) {
                throw ServiceUnavailableException("La plataforma GPS no está configurada (GPS_FEED_URL) y ningún dispositivo ha enviado su posición.")
            }
            return fromWebhooks(Instant.now(clock))
        }
        lock.withLock {
            val now = Instant.now(clock)
            val fresh = lastAttemptAt?.let { Duration.between(it, now) < cacheTtl } == true
            if (!fresh) refresh(feed, now)
        }
        return fromFeed(Instant.now(clock))
    }

    /** Pide los reportes al proveedor; si falla se sigue usando el último dato bueno durante un minuto. */
    private suspend fun refresh(feed: GpsFeedService, now: Instant) {
        lastAttemptAt = now
        try {
            val reports = feed.fetchReports()
            history = (history.keys + reports.map { it.plate }).associateWith { plate ->
                (history[plate].orEmpty() + reports.filter { it.plate == plate }).distinctBy { it.at }.sortedBy { it.at }.takeLast(2)
            }.filterValues { it.isNotEmpty() }
            lastGoodAt = now
        } catch (e: ExternalServiceException) {
            log.warn("Falló la plataforma GPS: {}", e.message)
            val good = lastGoodAt
            if (good == null || Duration.between(good, now) > staleFallback) {
                throw ServiceUnavailableException("La plataforma GPS de las Rutas UNAB no responde. Intenta de nuevo en un momento.")
            }
        }
    }

    private fun fromFeed(now: Instant): List<BusPosition> = history.values.mapNotNull { reports ->
        val last = reports.last()
        val routeCode = routeCodeOf(last.plate) ?: return@mapNotNull null
        val previous = reports.getOrNull(reports.size - 2)
        val speed = previous?.let { speedKmh(it.lat, it.lng, it.at, last.lat, last.lng, last.at) }
        val parked = !last.ignitionOn || contains(last.event, "estacionado")
        val moving = if (speed != null) speed >= MOVING_KMH else contains(last.event, "movimiento") || contains(last.event, "speed")
        val state = when {
            parked -> BusState.PARKED
            moving -> BusState.MOVING
            else -> BusState.STOPPED
        }
        val heading = last.headingDeg ?: previous?.takeIf { speed != null && speed >= MOVING_KMH }?.let { bearing(it.lat, it.lng, last.lat, last.lng) }
        build(last.id ?: last.plate, last.plate, routeCode, last.lat, last.lng, state, speed, heading, last.at, now, "gps")
    }.sortedBy { it.plate }

    private fun fromWebhooks(now: Instant): List<BusPosition> = webhooks.all().mapNotNull { d ->
        val speed = d.speedKmh
        val state = when {
            d.ignitionOn == false -> BusState.PARKED
            speed != null && speed >= MOVING_KMH -> BusState.MOVING
            else -> BusState.STOPPED
        }
        build(d.deviceId, d.deviceId, routeCodeOf(d.deviceId), d.lat, d.lng, state, speed, d.headingDeg, d.reportedAt, now, "webhook")
    }.sortedBy { it.deviceId }

    /** Descarta los reportes viejos: más de 5 minutos (30 si el bus está estacionado). */
    private fun build(
        deviceId: String, plate: String, routeCode: String?, lat: Double, lng: Double, state: BusState,
        speed: Double?, heading: Double?, at: Instant, now: Instant, source: String
    ): BusPosition? {
        val age = Duration.between(at, now).seconds.coerceAtLeast(0)
        val limit = if (state == BusState.PARKED) PARKED_MAX_AGE else MOVING_MAX_AGE
        if (age > limit.seconds) return null
        return BusPosition(deviceId, plate, routeCode, lat, lng, state, speed?.let { Math.round(it * 10) / 10.0 }, heading, at, age, source)
    }

    companion object {
        const val MOVING_KMH = 3.0
        val MOVING_MAX_AGE: Duration = Duration.ofMinutes(5)
        val PARKED_MAX_AGE: Duration = Duration.ofMinutes(30)

        /** Placas del GPS que corresponden a rutas (bus_routes.code). */
        private val ROUTE_PLATES = setOf("RUTA1", "RUTA2", "RUTA02", "RUTA3")

        fun routeCodeOf(plateOrDevice: String): String? =
            HttpGpsFeedService.normalizePlate(plateOrDevice).takeIf { it in ROUTE_PLATES }

        private fun contains(text: String, word: String) = strip(text).contains(word)
        private fun strip(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "").lowercase()

        /** Velocidad media entre dos reportes, en km/h; null si no hay tiempo entre ellos. */
        fun speedKmh(lat1: Double, lng1: Double, t1: Instant, lat2: Double, lng2: Double, t2: Instant): Double? {
            val seconds = Duration.between(t1, t2).seconds
            if (seconds <= 0) return null
            return distanceMeters(lat1, lng1, lat2, lng2) / seconds * 3.6
        }

        fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
            return 2 * r * atan2(sqrt(a), sqrt(1 - a))
        }

        /** Rumbo inicial en grados (0 = norte) de un punto al otro. */
        fun bearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val p1 = Math.toRadians(lat1)
            val p2 = Math.toRadians(lat2)
            val dl = Math.toRadians(lng2 - lng1)
            val y = sin(dl) * cos(p2)
            val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
            return (Math.toDegrees(atan2(y, x)) + 360) % 360
        }

        /** Punto cardinal de 8 vientos para el rumbo en grados. */
        fun compass(deg: Double): String = listOf("N", "NE", "E", "SE", "S", "SO", "O", "NO")[((deg + 22.5) / 45).toInt() % 8]
    }
}
