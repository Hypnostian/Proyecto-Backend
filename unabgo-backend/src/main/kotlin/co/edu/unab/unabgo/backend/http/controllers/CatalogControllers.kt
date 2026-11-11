package co.edu.unab.unabgo.backend.http.controllers

// Recursos públicos y del bus: salud del servicio, programas académicos, rutas y favoritas.

import co.edu.unab.unabgo.backend.auth.Ability
import co.edu.unab.unabgo.backend.auth.BusRoutePolicy
import co.edu.unab.unabgo.backend.auth.Gate
import co.edu.unab.unabgo.backend.auth.authorize
import co.edu.unab.unabgo.backend.database.BusRoutes
import co.edu.unab.unabgo.backend.database.Programs
import co.edu.unab.unabgo.backend.database.RouteStops
import co.edu.unab.unabgo.backend.database.dbQuery
import co.edu.unab.unabgo.backend.http.NotFoundException
import co.edu.unab.unabgo.backend.http.NotImplementedYetException
import co.edu.unab.unabgo.backend.http.ValidationException
import co.edu.unab.unabgo.backend.http.authUser
import co.edu.unab.unabgo.backend.http.longParam
import co.edu.unab.unabgo.backend.http.pageRequest
import co.edu.unab.unabgo.backend.http.requests.BusRouteRequest
import co.edu.unab.unabgo.backend.http.resources.DataResponse
import co.edu.unab.unabgo.backend.http.resources.toResource
import co.edu.unab.unabgo.backend.http.validated
import co.edu.unab.unabgo.backend.models.BusRoute
import co.edu.unab.unabgo.backend.models.BusStop
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.models.User
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll

class HealthController {
    @Serializable
    data class Health(val status: String, val database: String, val version: String)

    /** GET /api/v1/health */
    suspend fun show(call: ApplicationCall) {
        val db = runCatching { dbQuery { Programs.selectAll().limit(1).count() } }.isSuccess
        call.respond(
            if (db) HttpStatusCode.OK else HttpStatusCode.InternalServerError,
            Health(if (db) "ok" else "error", if (db) "ok" else "sin conexión", "0.2.0")
        )
    }
}

class ProgramController {
    /** GET /api/v1/programs?faculty=&level=&max_price=&q=&page=&per_page= */
    suspend fun index(call: ApplicationCall) {
        val q = call.request.queryParameters
        val page = call.pageRequest()
        val maxPrice = q["max_price"]?.let { it.toLongOrNull() ?: throw ValidationException(mapOf("max_price" to "max_price debe ser un número.")) }
        val body = dbQuery {
            var filter: Op<Boolean> = Op.TRUE
            q["faculty"]?.takeIf { it.isNotBlank() }?.let { filter = filter and (Programs.faculty eq it) }
            q["level"]?.takeIf { it.isNotBlank() }?.let { filter = filter and (Programs.level eq it) }
            q["q"]?.takeIf { it.isNotBlank() }?.let { filter = filter and (Programs.name.lowerCase() like "%${it.lowercase()}%") }
            maxPrice?.let { filter = filter and (Programs.priceCop lessEq it) }
            val query = Program.find(filter)
            val total = query.count()
            val items = query.orderBy(Programs.name to SortOrder.ASC).limit(page.perPage).offset(page.offset).map { it.toResource() }
            DataResponse(items, page.meta(total))
        }
        call.respond(body)
    }

    /** GET /api/v1/programs/{slug} */
    suspend fun show(call: ApplicationCall) {
        val slug = call.parameters["slug"].orEmpty()
        val body = dbQuery { (Program.find { Programs.slug eq slug }.singleOrNull() ?: throw NotFoundException("El programa")).toResource() }
        call.respond(DataResponse(body))
    }
}

class BusController {
    /** GET /api/v1/bus/routes: rutas con sus paradas en orden. */
    suspend fun routes(call: ApplicationCall) {
        val body = dbQuery { BusRoute.all().orderBy(BusRoutes.code to SortOrder.ASC).map { it.toResource() } }
        call.respond(DataResponse(body))
    }

    /** GET /api/v1/bus/live: posiciones en vivo desde la plataforma GPS (entrega 3). */
    suspend fun live(call: ApplicationCall) {
        throw NotImplementedYetException("La integración con la plataforma GPS de las Rutas UNAB llega en la entrega 3.")
    }

    /** PUT /api/v1/bus/routes/{id}: nombre, color, estado y orden de las paradas (gestor o administrador). */
    suspend fun update(call: ApplicationCall) {
        val user = call.authUser()
        authorize(BusRoutePolicy.update(user), "Solo gestores y administradores editan las rutas.")
        val request = call.validated<BusRouteRequest>()
        val body = dbQuery {
            val route = BusRoute.findById(call.longParam("id", "La ruta")) ?: throw NotFoundException("La ruta")
            val stops = request.stopIds.map { BusStop.findById(it) ?: throw ValidationException(mapOf("stop_ids" to "La parada $it no existe.")) }
            route.name = request.name!!.trim()
            route.color = request.color!!.uppercase()
            route.active = request.active
            RouteStops.deleteWhere { RouteStops.route eq route.id }
            stops.forEachIndexed { i, stop ->
                RouteStops.insert {
                    it[RouteStops.route] = route.id
                    it[RouteStops.stop] = stop.id
                    it[sortOrder] = i.toShort()
                }
            }
            route.toResource()
        }
        call.respond(DataResponse(body))
    }

    /** POST /api/v1/me/favorite-routes/{routeId}  ->  201 */
    suspend fun addFavorite(call: ApplicationCall) {
        val user = call.authUser()
        Gate.authorize(user, Ability.MANAGE_FAVORITES)
        val body = dbQuery {
            val route = BusRoute.findById(call.longParam("routeId", "La ruta")) ?: throw NotFoundException("La ruta")
            val entity = User[user.id]
            entity.favoriteRoutes = SizedCollection((entity.favoriteRoutes.toList() + route).distinctBy { it.id })
            entity.favoriteRoutes.map { it.toResource() }
        }
        call.respond(HttpStatusCode.Created, DataResponse(body))
    }

    /** DELETE /api/v1/me/favorite-routes/{routeId}  ->  204 */
    suspend fun removeFavorite(call: ApplicationCall) {
        val user = call.authUser()
        Gate.authorize(user, Ability.MANAGE_FAVORITES)
        dbQuery {
            val routeId = call.longParam("routeId", "La ruta")
            val entity = User[user.id]
            if (entity.favoriteRoutes.none { it.id.value == routeId }) throw NotFoundException("La ruta favorita")
            entity.favoriteRoutes = SizedCollection(entity.favoriteRoutes.filter { it.id.value != routeId })
        }
        call.respond(HttpStatusCode.NoContent)
    }

    /** GET /api/v1/me/favorite-routes */
    suspend fun favorites(call: ApplicationCall) {
        val user = call.authUser()
        val body = dbQuery { User[user.id].favoriteRoutes.map { it.toResource() } }
        call.respond(DataResponse(body))
    }
}
