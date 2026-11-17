package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.auth.TokenService
import co.edu.unab.unabgo.backend.config.AppConfig
import co.edu.unab.unabgo.backend.database.DatabaseFactory
import co.edu.unab.unabgo.backend.http.controllers.AdminUserController
import co.edu.unab.unabgo.backend.http.controllers.AnnouncementController
import co.edu.unab.unabgo.backend.http.controllers.AuthController
import co.edu.unab.unabgo.backend.http.controllers.BanuController
import co.edu.unab.unabgo.backend.http.controllers.BusController
import co.edu.unab.unabgo.backend.http.controllers.FaqController
import co.edu.unab.unabgo.backend.http.controllers.GpsWebhookController
import co.edu.unab.unabgo.backend.http.controllers.HealthController
import co.edu.unab.unabgo.backend.http.controllers.ProfessorController
import co.edu.unab.unabgo.backend.http.controllers.StudyPlanController
import co.edu.unab.unabgo.backend.http.controllers.ProgramController
import co.edu.unab.unabgo.backend.http.controllers.ScheduleController
import co.edu.unab.unabgo.backend.http.controllers.SubjectController
import co.edu.unab.unabgo.backend.http.installErrorHandling
import co.edu.unab.unabgo.backend.http.routes.AUTH_API
import co.edu.unab.unabgo.backend.http.routes.Controllers
import co.edu.unab.unabgo.backend.http.routes.apiRoutes
import co.edu.unab.unabgo.backend.services.AnnouncementService
import co.edu.unab.unabgo.backend.services.BanuService
import co.edu.unab.unabgo.backend.services.BusLiveService
import co.edu.unab.unabgo.backend.services.GpsWebhookStore
import co.edu.unab.unabgo.backend.services.RateLimiter
import co.edu.unab.unabgo.backend.services.integrations.Integrations
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.routing.routing
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.TimeZone

/** JSON de la API en snake_case (password_confirmation, created_at…), como en Laravel. */
@OptIn(ExperimentalSerializationApi::class)
val ApiJson = Json {
    namingStrategy = JsonNamingStrategy.SnakeCase
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun main() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val config = AppConfig.load()
    val log = LoggerFactory.getLogger("unabgo")
    val db = DatabaseFactory.connect(config)
    val executed = DatabaseFactory.migrate(db)
    log.info("UNAB GO! backend: {} (migraciones pendientes ejecutadas: {})", config, executed)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

/**
 * Arma la aplicación: plugins, autenticación por token y rutas. Las integraciones externas se inyectan:
 * en producción salen del .env y las pruebas pasan versiones con MockEngine.
 */
fun Application.module(config: AppConfig, integrations: Integrations = Integrations.from(config)) {
    val tokens = TokenService(Duration.ofDays(config.tokenTtlDays))

    install(DefaultHeaders) { header("X-Content-Type-Options", "nosniff") }
    install(CallLogging)
    install(ContentNegotiation) { json(ApiJson) }
    installErrorHandling()
    install(Authentication) {
        // Equivalente a auth:sanctum: Authorization: Bearer {id}.{secreto}
        bearer(AUTH_API) {
            realm = "unabgo"
            authenticate { credential -> tokens.authenticate(credential.token) }
        }
    }

    val webhookStore = GpsWebhookStore()
    val controllers = Controllers(
        health = HealthController(),
        auth = AuthController(tokens, integrations.tokenVerifier),
        subjects = SubjectController(),
        schedule = ScheduleController(),
        programs = ProgramController(),
        bus = BusController(BusLiveService(integrations.gpsFeed, webhookStore)),
        announcements = AnnouncementController(AnnouncementService(integrations.push)),
        faqs = FaqController(),
        admin = AdminUserController(integrations.unabWeb),
        banu = BanuController(BanuService(integrations.llm), RateLimiter(config.banuRateLimitPerMinute)),
        webhooks = GpsWebhookController(config, webhookStore),
        professors = ProfessorController(),
        studyPlans = StudyPlanController()
    )
    routing { apiRoutes(controllers) }
}
