package co.edu.unab.unabgo.backend.http.routes

// Rutas de la API (equivalente a routes/api.php). Las públicas no piden token; las demás están dentro
// de authenticate(AUTH_API), y cada controlador aplica el Gate o la Policy que corresponde.

import co.edu.unab.unabgo.backend.http.NotImplementedYetException
import co.edu.unab.unabgo.backend.http.controllers.AdminUserController
import co.edu.unab.unabgo.backend.http.controllers.AnnouncementController
import co.edu.unab.unabgo.backend.http.controllers.AuthController
import co.edu.unab.unabgo.backend.http.controllers.BusController
import co.edu.unab.unabgo.backend.http.controllers.FaqController
import co.edu.unab.unabgo.backend.http.controllers.HealthController
import co.edu.unab.unabgo.backend.http.controllers.ProgramController
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
    val programs: ProgramController,
    val bus: BusController,
    val announcements: AnnouncementController,
    val faqs: FaqController,
    val admin: AdminUserController
)

fun Route.apiRoutes(c: Controllers) = route("/api/v1") {

    // ------------------------------------------------------------ públicas
    get("/health") { c.health.show(call) }
    route("/auth") {
        post("/register") { c.auth.register(call) }
        post("/login") { c.auth.login(call) }
        post("/google") { throw NotImplementedYetException("El inicio de sesión con Google institucional llega en la entrega 3.") }
    }
    get("/programs") { c.programs.index(call) }
    get("/programs/{slug}") { c.programs.show(call) }
    get("/bus/routes") { c.bus.routes(call) }
    get("/bus/live") { c.bus.live(call) }
    get("/announcements") { c.announcements.index(call) }
    post("/banu/ask") { throw NotImplementedYetException("Banu (preguntas frecuentes e IA) llega en la entrega 3.") }
    post("/webhooks/gps") { throw NotImplementedYetException("El webhook del GPS (firma HMAC) llega en la entrega 3.") }

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
