package co.edu.unab.unabgo.backend.models

// Modelos del bus: rutas y paradas (N:M con route_stops) y seguidores de cada ruta (N:M con favorite_routes).

import co.edu.unab.unabgo.backend.database.BusRoutes
import co.edu.unab.unabgo.backend.database.BusStops
import co.edu.unab.unabgo.backend.database.FavoriteRoutes
import co.edu.unab.unabgo.backend.database.RouteStops
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class BusRoute(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<BusRoute>(BusRoutes)

    var code by BusRoutes.code
    var name by BusRoutes.name
    var color by BusRoutes.color
    var gpsDeviceId by BusRoutes.gpsDeviceId
    var active by BusRoutes.active

    /** N:M — paradas de la ruta (el orden está en route_stops.sort_order). */
    var stops by BusStop via RouteStops

    /** N:M — usuarios que la marcaron como favorita. */
    val followers by User via FavoriteRoutes
}

class BusStop(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<BusStop>(BusStops)

    var name by BusStops.name
    var lat by BusStops.lat
    var lon by BusStops.lon
    var isCampus by BusStops.isCampus

    /** N:M — rutas que pasan por la parada. */
    val routes by BusRoute via RouteStops
}
