package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.http.ServiceUnavailableException
import co.edu.unab.unabgo.backend.services.BusLiveService
import co.edu.unab.unabgo.backend.services.BusState
import co.edu.unab.unabgo.backend.services.GpsWebhookStore
import co.edu.unab.unabgo.backend.services.integrations.ExternalServiceException
import co.edu.unab.unabgo.backend.services.integrations.GpsFeedService
import co.edu.unab.unabgo.backend.services.integrations.GpsReport
import co.edu.unab.unabgo.backend.services.integrations.HttpGpsFeedService
import co.edu.unab.unabgo.backend.services.integrations.Integrations
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val COLOMBIA = ZoneOffset.ofHours(-5)

/** Hora local de Colombia (sin zona), como la entrega la plataforma GPS. */
private fun localTime(secondsAgo: Long): String =
    LocalDateTime.now(COLOMBIA).minusSeconds(secondsAgo).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

private fun report(
    plate: String, lat: Double, lng: Double, secondsAgo: Long,
    ignition: Boolean = true, event: String = "Posicion", sentido: String = "Norte"
) = """{"id":"${plate}-$secondsAgo","placa":"$plate","lat":$lat,"lng":$lng,"sentido":"$sentido",""" +
    """"fhEvento":"${localTime(secondsAgo)}","estadoIgnicion":$ignition,"evento":"$event"}"""

private fun feedOf(vararg items: String) = "[" + items.joinToString(",") + "]"

private fun feedIntegrations(mock: MockHttp) = Integrations(gpsFeed = HttpGpsFeedService(mock.client, "https://gps.test/feed"))

/** GET /api/v1/bus/live con la plataforma GPS simulada (MockEngine). */
class BusLiveTest {

    @Test
    fun calculaEstadoRumboYDescartaReportesViejos() {
        val mock = MockHttp {
            json(feedOf(
                // RUTA1: dos reportes a ~44 m en 30 s (~5 km/h) -> en movimiento
                report("RUTA1", 7.1000, -73.1000, 40), report("RUTA1", 7.1004, -73.1000, 10),
                // RUTA2: mismo punto en dos reportes -> detenido
                report("RUTA2", 7.1100, -73.1100, 60), report("RUTA2", 7.1100, -73.1100, 20),
                // RUTA3: ignición apagada hace 10 minutos -> estacionado (se conserva hasta 30 minutos)
                report("RUTA3", 7.1200, -73.1200, 600, ignition = false),
                // RUTA02: detenido y con el último reporte de hace 7 minutos -> se descarta (máximo 5)
                report("RUTA02", 7.1300, -73.1300, 420),
                // Placa que no es una ruta -> se ignora
                report("ABC123", 7.1400, -73.1400, 5)
            ))
        }
        apiTest(integrations = feedIntegrations(mock)) {
            val (status, body) = client.getAuth("/api/v1/bus/live").json()
            assertEquals(200, status, body.toString())
            val buses = body["data"]!!.jsonArray.map { it.jsonObject }.associateBy { it["plate"]!!.jsonPrimitive.content }
            assertEquals(setOf("RUTA1", "RUTA2", "RUTA3"), buses.keys)

            val moving = buses.getValue("RUTA1")
            assertEquals("MOVING", moving["state"]!!.jsonPrimitive.content)
            assertEquals("RUTA1", moving["route"]!!.jsonObject["code"]!!.jsonPrimitive.content)
            assertEquals("N", moving["heading"]!!.jsonPrimitive.content)
            assertTrue(moving["speed_kmh"]!!.jsonPrimitive.content.toDouble() in 3.0..9.0)
            assertTrue(moving["age_seconds"]!!.jsonPrimitive.content.toLong() in 8..30)

            assertEquals("STOPPED", buses.getValue("RUTA2")["state"]!!.jsonPrimitive.content)
            assertEquals("PARKED", buses.getValue("RUTA3")["state"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun laRespuestaSeGuardaCincoSegundos() {
        val mock = MockHttp { json(feedOf(report("RUTA1", 7.1, -73.1, 5))) }
        apiTest(integrations = feedIntegrations(mock)) {
            assertEquals(200, client.getAuth("/api/v1/bus/live").status.value)
            assertEquals(200, client.getAuth("/api/v1/bus/live").status.value)
            assertEquals(1, mock.requests.size, "la segunda petición sale de la caché de 5 s")
        }
    }

    @Test
    fun sinGpsConfiguradoResponde503() = apiTest {
        val (status, body) = client.getAuth("/api/v1/bus/live").json()
        assertEquals(503, status)
        assertEquals("SERVICE_UNAVAILABLE", body["code"]!!.jsonPrimitive.content)
        assertTrue(body["error"]!!.jsonPrimitive.content.contains("GPS_FEED_URL"))
    }

    @Test
    fun siElProveedorFallaResponde503() {
        val mock = MockHttp { json("""{"error":"caído"}""", HttpStatusCode.InternalServerError) }
        apiTest(integrations = feedIntegrations(mock)) {
            assertEquals(503, client.getAuth("/api/v1/bus/live").status.value)
        }
    }

    @Test
    fun siElProveedorDevuelveBasuraResponde503() {
        val mock = MockHttp { json("esto no es json") }
        apiTest(integrations = feedIntegrations(mock)) {
            assertEquals(503, client.getAuth("/api/v1/bus/live").status.value)
        }
    }

    // ------------------------------------------------------------------ webhook del dispositivo a bordo

    private val secret = "secreto-de-pruebas"
    private val webhookConfig = TestDb.testConfig.copy(gpsWebhookSecret = secret)
    private val position = """{"device_id":"RUTA1","lat":7.1167,"lng":-73.1052,"speed_kmh":22.5,"heading":90,"ignition":true}"""

    @Test
    fun webhookConFirmaValidaGuardaLaPosicionYSeVeEnBusLive() = apiTest(config = webhookConfig) {
        val response = client.postSigned("/api/v1/webhooks/gps", position, signBody(secret, position))
        assertEquals(202, response.status.value)

        val (status, body) = client.getAuth("/api/v1/bus/live").json()
        assertEquals(200, status, body.toString())
        val bus = body["data"]!!.jsonArray.single().jsonObject
        assertEquals("webhook", bus["source"]!!.jsonPrimitive.content)
        assertEquals("MOVING", bus["state"]!!.jsonPrimitive.content)
        assertEquals("E", bus["heading"]!!.jsonPrimitive.content)
        assertEquals("RUTA1", bus["route"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun webhookConFirmaMalaOSinFirmaEs401() = apiTest(config = webhookConfig) {
        assertEquals(401, client.postSigned("/api/v1/webhooks/gps", position, signBody("otro-secreto", position)).status.value)
        assertEquals(401, client.postSigned("/api/v1/webhooks/gps", position, null).status.value)
        // Si el cuerpo cambia después de firmar, la firma deja de valer
        assertEquals(401, client.postSigned("/api/v1/webhooks/gps", position.replace("7.1167", "9.9999"), signBody(secret, position)).status.value)
        // Y no quedó nada guardado
        assertEquals(503, client.getAuth("/api/v1/bus/live").status.value)
    }

    @Test
    fun webhookValidaElCuerpo() = apiTest(config = webhookConfig) {
        val invalid = """{"device_id":"RUTA1","lat":123.0,"lng":-73.1}"""
        val (status, body) = client.postSigned("/api/v1/webhooks/gps", invalid, signBody(secret, invalid)).json()
        assertEquals(400, status)
        assertTrue(body["fields"]!!.jsonObject.containsKey("lat"))
        val notJson = "no es json"
        assertEquals(400, client.postSigned("/api/v1/webhooks/gps", notJson, signBody(secret, notJson)).status.value)
    }

    @Test
    fun webhookSinSecretoConfiguradoEs503() = apiTest {
        assertEquals(503, client.postSigned("/api/v1/webhooks/gps", position, signBody(secret, position)).status.value)
    }

    @Test
    fun webhookDeUnDispositivoYaEstacionadoSeMarcaComoPARKED() = apiTest(config = webhookConfig) {
        val parked = """{"device_id":"BUS-99","lat":7.1,"lng":-73.1,"ignition":false}"""
        assertEquals(202, client.postSigned("/api/v1/webhooks/gps", parked, signBody(secret, parked)).status.value)
        val bus = client.getAuth("/api/v1/bus/live").json().second["data"]!!.jsonArray.single().jsonObject
        assertEquals("PARKED", bus["state"]!!.jsonPrimitive.content)
        assertTrue(bus["route"] is JsonNull || bus["route"] == null)
    }
}

/** Reglas del servicio (estado, antigüedad y respaldo ante fallos) con un reloj controlado. */
class BusLiveServiceTest {

    private class FakeClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    private class FakeFeed(var reports: List<GpsReport> = emptyList(), var failing: Boolean = false) : GpsFeedService {
        var calls = 0
        override suspend fun fetchReports(): List<GpsReport> {
            calls++
            if (failing) throw ExternalServiceException("caído")
            return reports
        }
    }

    private val start = Instant.parse("2026-10-27T16:00:00Z")

    private fun gps(plate: String, secondsAfterStart: Long, lat: Double = 7.1, event: String = "Posicion", ignition: Boolean = true) =
        GpsReport(null, plate, lat, -73.1, null, start.plusSeconds(secondsAfterStart), ignition, event)

    @Test
    fun unSoloReporteConEventoDeMovimientoSeConsideraEnMovimiento() = runBlocking {
        val clock = FakeClock(start.plusSeconds(10))
        val feed = FakeFeed(listOf(gps("RUTA1", 0, event = "Movimiento"), gps("RUTA2", 0, event = "Exceso de Speed"), gps("RUTA3", 0, event = "Posicion")))
        val buses = BusLiveService(feed, GpsWebhookStore(), clock).live().associateBy { it.plate }
        assertEquals(BusState.MOVING, buses.getValue("RUTA1").state)
        assertEquals(BusState.MOVING, buses.getValue("RUTA2").state)
        assertEquals(BusState.STOPPED, buses.getValue("RUTA3").state)
    }

    @Test
    fun elEventoEstacionadoGanaAlMovimiento() = runBlocking {
        val clock = FakeClock(start.plusSeconds(10))
        val feed = FakeFeed(listOf(gps("RUTA1", 0, event = "Estacionado")))
        assertEquals(BusState.PARKED, BusLiveService(feed, GpsWebhookStore(), clock).live().single().state)
    }

    @Test
    fun dosReportesConsecutivosLentosNoSonMovimiento() = runBlocking {
        val clock = FakeClock(start.plusSeconds(70))
        // 0.0001 grados (~11 m) en 60 s = 0.7 km/h aunque el evento diga "Movimiento"
        val feed = FakeFeed(listOf(gps("RUTA1", 0, 7.1000, "Movimiento"), gps("RUTA1", 60, 7.1001, "Movimiento")))
        assertEquals(BusState.STOPPED, BusLiveService(feed, GpsWebhookStore(), clock).live().single().state)
    }

    @Test
    fun elSegundoReporteSeCombinaConElDeLaConsultaAnterior() = runBlocking {
        val clock = FakeClock(start.plusSeconds(10))
        val feed = FakeFeed(listOf(gps("RUTA1", 0, 7.1000)))
        val service = BusLiveService(feed, GpsWebhookStore(), clock)
        assertEquals(BusState.STOPPED, service.live().single().state)
        // Pasan 30 s (más que la caché) y llega un reporte 55 m más al norte: ~6.6 km/h
        clock.now = start.plusSeconds(40)
        feed.reports = listOf(gps("RUTA1", 30, 7.1005))
        val bus = service.live().single()
        assertEquals(BusState.MOVING, bus.state)
        assertNotNull(bus.headingDeg, "sin sentido del GPS el rumbo se calcula entre los dos reportes")
        assertTrue(bus.headingDeg!! < 1.0 || bus.headingDeg!! > 359.0)
    }

    @Test
    fun siElProveedorFallaSeUsaElUltimoDatoBuenoHastaUnMinuto(): Unit = runBlocking {
        val clock = FakeClock(start.plusSeconds(10))
        val feed = FakeFeed(listOf(gps("RUTA1", 0)))
        val service = BusLiveService(feed, GpsWebhookStore(), clock)
        assertEquals(1, service.live().size)

        feed.failing = true
        clock.now = start.plusSeconds(40) // 30 s después del último dato bueno
        assertEquals(1, service.live().size, "todavía se sirve el último dato bueno")

        clock.now = start.plusSeconds(100) // más de un minuto sin datos nuevos
        assertFailsWith<ServiceUnavailableException> { service.live() }
    }

    @Test
    fun laCacheEvitaConsultarAlProveedorEnCincoSegundos() = runBlocking {
        val clock = FakeClock(start.plusSeconds(1))
        val feed = FakeFeed(listOf(gps("RUTA1", 0)))
        val service = BusLiveService(feed, GpsWebhookStore(), clock)
        service.live()
        clock.now = start.plusSeconds(4)
        service.live()
        assertEquals(1, feed.calls)
        clock.now = start.plusSeconds(7)
        service.live()
        assertEquals(2, feed.calls)
    }

    @Test
    fun elParseoDelFeedEsTolerante() {
        val array = kotlinx.serialization.json.Json.parseToJsonElement(
            """[{"placa":"ruta 02","lat":"7,11","lng":"-73.1","sentido":"Suroeste","fhEvento":"2026-10-27 11:06:32","estadoIgnicion":"1","evento":null},
                {"placa":"RUTA1","lat":"x","lng":-73.1,"fhEvento":"2026-10-27T11:06:32"},
                {"lat":7.1,"lng":-73.1,"fhEvento":"2026-10-27T11:06:32"}]"""
        ).jsonArray
        val parsed = HttpGpsFeedService.parse(array)
        assertEquals(1, parsed.size, "los reportes dañados se descartan")
        val r = parsed.single()
        assertEquals("RUTA02", r.plate)
        assertEquals(7.11, r.lat)
        assertEquals(225.0, r.headingDeg)
        assertTrue(r.ignitionOn)
        // 11:06:32 en Colombia (UTC-5) son las 16:06:32 UTC
        assertEquals(Instant.parse("2026-10-27T16:06:32Z"), r.at)
        assertNull(HttpGpsFeedService.parseHeading("???"))
    }
}
