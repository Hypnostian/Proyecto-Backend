package co.edu.unab.unabgo.backend.http.controllers

import co.edu.unab.unabgo.backend.auth.Ability
import co.edu.unab.unabgo.backend.auth.Gate
import co.edu.unab.unabgo.backend.auth.TokenService
import co.edu.unab.unabgo.backend.database.dbQuery
import co.edu.unab.unabgo.backend.http.NotFoundException
import co.edu.unab.unabgo.backend.http.authUser
import co.edu.unab.unabgo.backend.http.requests.LoginRequest
import co.edu.unab.unabgo.backend.http.requests.RegisterRequest
import co.edu.unab.unabgo.backend.http.requests.UpdateProfileRequest
import co.edu.unab.unabgo.backend.http.resources.DataResponse
import co.edu.unab.unabgo.backend.http.resources.TokenResource
import co.edu.unab.unabgo.backend.http.resources.toResource
import co.edu.unab.unabgo.backend.http.validated
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.models.StudentProfile
import co.edu.unab.unabgo.backend.models.User
import co.edu.unab.unabgo.backend.services.AuthService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Registro, inicio y cierre de sesión con tokens, y perfil del usuario autenticado. */
class AuthController(private val tokens: TokenService) {

    private val auth = AuthService(tokens)

    /** POST /api/v1/auth/register  ->  201 Created con el token. */
    suspend fun register(call: ApplicationCall) {
        val request = call.validated<RegisterRequest>()
        val body = dbQuery {
            val (user, token) = auth.register(request)
            TokenResource(token.plainText, expiresAt = token.expiresAt.toString(), user = user.toResource())
        }
        call.respond(HttpStatusCode.Created, DataResponse(body))
    }

    /** POST /api/v1/auth/login  ->  200 OK con el token, o 401 si las credenciales no son válidas. */
    suspend fun login(call: ApplicationCall) {
        val request = call.validated<LoginRequest>()
        val body = dbQuery {
            val (user, token) = auth.login(request)
            TokenResource(token.plainText, expiresAt = token.expiresAt.toString(), user = user.toResource())
        }
        call.respond(DataResponse(body))
    }

    /** POST /api/v1/auth/logout  ->  204 No Content; el token deja de servir. */
    suspend fun logout(call: ApplicationCall) {
        val user = call.authUser()
        dbQuery { tokens.revoke(user.tokenId) }
        call.respond(HttpStatusCode.NoContent)
    }

    /** GET /api/v1/me */
    suspend fun me(call: ApplicationCall) {
        val user = call.authUser()
        val body = dbQuery { (User.findById(user.id) ?: throw NotFoundException("El usuario")).toResource() }
        call.respond(DataResponse(body))
    }

    /** PUT /api/v1/me/profile (programa, semestre, idioma y recordatorio). */
    suspend fun updateProfile(call: ApplicationCall) {
        val user = call.authUser()
        Gate.authorize(user, Ability.MANAGE_OWN_PROFILE)
        val request = call.validated<UpdateProfileRequest>()
        val body = dbQuery {
            val entity = User.findById(user.id) ?: throw NotFoundException("El usuario")
            val profile = entity.profile ?: StudentProfile.new { this.user = entity }
            request.programId?.let { profile.program = Program.findById(it) ?: throw NotFoundException("El programa") }
            request.studentCode?.let { profile.studentCode = it.trim() }
            request.semester?.let { profile.semester = it.toShort() }
            request.language?.let { profile.language = it }
            request.reminderMinutes?.let { profile.reminderMinutes = it.toShort() }
            entity.toResource()
        }
        call.respond(DataResponse(body))
    }
}
