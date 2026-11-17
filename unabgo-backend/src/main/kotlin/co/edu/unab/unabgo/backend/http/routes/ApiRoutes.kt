package co.edu.unab.unabgo.backend.http.routes

// Rutas de la API (equivalente a routes/api.php). Las públicas no piden token; las demás están dentro
// de authenticate(AUTH_API), y cada controlador aplica el Gate o la Policy que corresponde.

import co.edu.unab.unabgo.backend.http.controllers.AdminUserController
import co.edu.unab.unabgo.backend.http.controllers.AnnouncementController
import co.edu.unab.unabgo.backend.http.controllers.AuthController
import co.edu.unab.unabgo.backend.http.controllers.BusController
import co.edu.unab.unabgo.backend.http.controllers.BanuController
import co.edu.unab.unabgo.backend.http.controllers.FaqController
import co.edu.unab.unabgo.backend.http.controllers.GpsWebhookController
import co.edu.unab.unabgo.backend.http.controllers.HealthController
import co.edu.unab.unabgo.backend.http.controllers.ProfessorController
import co.edu.unab.unabgo.backend.http.controllers.ProgramController
import co.edu.unab.unabgo.backend.http.controllers.ScheduleController
import co.edu.unab.unabgo.backend.http.controllers.StudyPlanController
import co.edu.unab.unabgo.backend.http.controllers.SubjectController
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

const val AUTH_API = "api"

class Controllers(
    val health: HealthController,
    val auth: AuthController,
    val subjects: SubjectController,
    val schedule: ScheduleController,
    val programs: ProgramController,
    val bus: BusController,
    val announcements: AnnouncementController,
    val faqs: FaqController,
    val admin: AdminUserController,
    val banu: BanuController,
    val webhooks: GpsWebhookController,
    val professors: ProfessorController,
    val studyPlans: StudyPlanController
)

fun Route.apiRoutes(c: Controllers) = route("/api/v1") {

    // ------------------------------------------------------------ públicas
    get("/health") { c.health.show(call) }
    route("/auth") {
        post("/register") { c.auth.register(call) }
        post("/login") { c.auth.login(call) }
        post("/google") { c.auth.google(call) }
    }
    get("/programs") { c.programs.index(call) }
    get("/programs/{slug}") { c.programs.show(call) }
    get("/bus/routes") { c.bus.routes(call) }
    get("/bus/live") { c.bus.live(call) }
    get("/announcements") { c.announcements.index(call) }
    get("/professors") { c.professors.index(call) }
    get("/professors/{slug}") { c.professors.show(call) }
    get("/study-plans") { c.studyPlans.index(call) }
    get("/study-plans/{slug}") { c.studyPlans.show(call) }
    post("/banu/ask") { c.banu.ask(call) }
    post("/webhooks/gps") { c.webhooks.receive(call) }

    // ------------------------------------------------------------ con token (Bearer)
    authenticate(AUTH_API) {
        post("/auth/logout") { c.auth.logout(call) }

        route("/me") {
            get { c.auth.me(call) }
            put("/profile") { c.auth.updateProfile(call) }
            get("/favorite-routes") { c.bus.favorites(call) }
            post("/favorite-routes/{routeId}") { c.bus.addFavorite(call) }
            delete("/favorite-routes/{routeId}") { c.bus.removeFavorite(call) }
        }

        // Estudiante (SubjectPolicy: solo sus materias)
        route("/subjects") {
            get { c.subjects.index(call) }
            post { c.subjects.store(call) }
            get("/{id}") { c.subjects.show(call) }
            put("/{id}") { c.subjects.update(call) }
            delete("/{id}") { c.subjects.destroy(call) }
            post("/{id}/categories") { c.subjects.storeCategory(call) }
            get("/{id}/summary") { c.subjects.summary(call) }
        }
        get("/schedule/next") { c.schedule.next(call) }
        post("/categories/{id}/items") { c.subjects.storeItem(call) }

        // Gestor o administrador (AnnouncementPolicy, FaqPolicy, BusRoutePolicy)
        post("/announcements") { c.announcements.store(call) }
        put("/announcements/{id}") { c.announcements.update(call) }
        delete("/announcements/{id}") { c.announcements.destroy(call) }
        route("/faqs") {
            get { c.faqs.index(call) }
            post { c.faqs.store(call) }
            put("/{id}") { c.faqs.update(call) }
            delete("/{id}") { c.faqs.destroy(call) }
        }
        put("/bus/routes/{id}") { c.bus.update(call) }

        // Administrador (UserPolicy)
        route("/admin") {
            get("/users") { c.admin.index(call) }
            patch("/users/{id}/role") { c.admin.updateRole(call) }
            post("/programs/sync") { c.admin.syncPrograms(call) }
        }
    }
}
