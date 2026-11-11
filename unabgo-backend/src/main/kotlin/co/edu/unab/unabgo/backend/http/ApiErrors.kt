package co.edu.unab.unabgo.backend.http

// Errores de la API con su código HTTP y un cuerpo JSON uniforme:
// { "error": "mensaje", "code": "CODIGO", "fields": { "campo": "mensaje" } }

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

open class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val fields: Map<String, String>? = null
) : RuntimeException(message)

class ValidationException(fields: Map<String, String>) :
    ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", "Los datos enviados no son válidos.", fields)

class UnauthorizedException(message: String = "Debes iniciar sesión.") :
    ApiException(HttpStatusCode.Unauthorized, "UNAUTHENTICATED", message)

class ForbiddenException(message: String) : ApiException(HttpStatusCode.Forbidden, "FORBIDDEN", message)

class NotFoundException(resource: String) : ApiException(HttpStatusCode.NotFound, "NOT_FOUND", "$resource no existe.")

class ConflictException(message: String) : ApiException(HttpStatusCode.Conflict, "CONFLICT", message)

class NotImplementedYetException(message: String) : ApiException(HttpStatusCode.NotImplemented, "NOT_IMPLEMENTED", message)

@Serializable
data class ErrorBody(val error: String, val code: String, val fields: Map<String, String>? = null)

fun Application.installErrorHandling() {
    install(StatusPages) {
        exception<ApiException> { call, e ->
            call.respond(e.status, ErrorBody(e.message, e.code, e.fields))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorBody("La petición no es válida: revisa el JSON y los encabezados.", "BAD_REQUEST"))
        }
        exception<Throwable> { call, e ->
            call.application.log.error("Error no controlado", e)
            call.respond(HttpStatusCode.InternalServerError, ErrorBody("Ocurrió un error interno. Intenta de nuevo.", "SERVER_ERROR"))
        }
        status(HttpStatusCode.Unauthorized) { call, status ->
            call.respond(status, ErrorBody("Debes iniciar sesión con un token válido.", "UNAUTHENTICATED"))
        }
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorBody("La ruta solicitada no existe.", "NOT_FOUND"))
        }
    }
}
