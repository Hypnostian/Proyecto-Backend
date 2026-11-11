package co.edu.unab.unabgo.backend.http

import co.edu.unab.unabgo.backend.auth.AuthUser
import co.edu.unab.unabgo.backend.http.resources.PageMeta
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.principal

/** Usuario del token (las rutas protegidas ya pasaron por la autenticación). */
fun ApplicationCall.authUser(): AuthUser = principal<AuthUser>() ?: throw UnauthorizedException()

/** Parámetro numérico de la ruta; si no es un número, el recurso no existe (404). */
fun ApplicationCall.longParam(name: String, resource: String): Long =
    parameters[name]?.toLongOrNull() ?: throw NotFoundException(resource)

/** Paginación ?page=1&per_page=15 (máximo 50 por página). */
data class PageRequest(val page: Int, val perPage: Int) {
    val offset: Long get() = (page - 1).toLong() * perPage
    fun meta(total: Long) = PageMeta(page, perPage, total, ((total + perPage - 1) / perPage).toInt().coerceAtLeast(1))
}

fun ApplicationCall.pageRequest(defaultPerPage: Int = 15): PageRequest = PageRequest(
    page = request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1,
    perPage = request.queryParameters["per_page"]?.toIntOrNull()?.coerceIn(1, 50) ?: defaultPerPage
)
