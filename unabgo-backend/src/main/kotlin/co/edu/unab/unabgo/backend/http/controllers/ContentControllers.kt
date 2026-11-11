package co.edu.unab.unabgo.backend.http.controllers

// Avisos de las dependencias, preguntas frecuentes de Banu y administración de usuarios.

import co.edu.unab.unabgo.backend.auth.AnnouncementPolicy
import co.edu.unab.unabgo.backend.auth.FaqPolicy
import co.edu.unab.unabgo.backend.auth.UserPolicy
import co.edu.unab.unabgo.backend.auth.UserRole
import co.edu.unab.unabgo.backend.auth.authorize
import co.edu.unab.unabgo.backend.database.Announcements
import co.edu.unab.unabgo.backend.database.Faqs
import co.edu.unab.unabgo.backend.database.Users
import co.edu.unab.unabgo.backend.database.dbQuery
import co.edu.unab.unabgo.backend.http.NotFoundException
import co.edu.unab.unabgo.backend.http.NotImplementedYetException
import co.edu.unab.unabgo.backend.http.ValidationException
import co.edu.unab.unabgo.backend.http.authUser
import co.edu.unab.unabgo.backend.http.longParam
import co.edu.unab.unabgo.backend.http.pageRequest
import co.edu.unab.unabgo.backend.http.requests.AnnouncementRequest
import co.edu.unab.unabgo.backend.http.requests.FaqRequest
import co.edu.unab.unabgo.backend.http.requests.UpdateRoleRequest
import co.edu.unab.unabgo.backend.http.resources.DataResponse
import co.edu.unab.unabgo.backend.http.resources.toResource
import co.edu.unab.unabgo.backend.http.validated
import co.edu.unab.unabgo.backend.models.Announcement
import co.edu.unab.unabgo.backend.models.Faq
import co.edu.unab.unabgo.backend.models.Role
import co.edu.unab.unabgo.backend.models.User
import co.edu.unab.unabgo.backend.services.AnnouncementService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import org.jetbrains.exposed.sql.and
import java.time.Instant
import java.time.temporal.ChronoUnit

class AnnouncementController(private val service: AnnouncementService) {

    private fun find(call: ApplicationCall): Announcement =
        Announcement.findById(call.longParam("id", "El aviso")) ?: throw NotFoundException("El aviso")

    /** GET /api/v1/announcements?category=&page=  (solo publicados, el más reciente primero) */
    suspend fun index(call: ApplicationCall) {
        val page = call.pageRequest(10)
        val category = call.request.queryParameters["category"]?.takeIf { it.isNotBlank() }
        if (category != null && category !in Announcement.CATEGORIES) {
            throw ValidationException(mapOf("category" to "La categoría debe ser una de: ${Announcement.CATEGORIES.joinToString(", ")}."))
        }
        val body = dbQuery {
            var filter: Op<Boolean> = Announcements.publishedAt.isNotNull()
            category?.let { filter = filter and (Announcements.category eq it) }
            val query = Announcement.find(filter)
            val total = query.count()
            val items = query.orderBy(Announcements.publishedAt to SortOrder.DESC).limit(page.perPage).offset(page.offset).map { it.toResource() }
            DataResponse(items, page.meta(total))
        }
        call.respond(body)
    }

    /** POST /api/v1/announcements  ->  201; con "publish": true se publica y se envía la notificación push. */
    suspend fun store(call: ApplicationCall) {
        val user = call.authUser()
        authorize(AnnouncementPolicy.create(user), "Solo gestores y administradores publican avisos.")
        val request = call.validated<AnnouncementRequest>()
        val body = dbQuery {
            val announcement = Announcement.new {
                author = User[user.id]
                title = request.title!!.trim()
                body = request.body!!.trim()
                category = request.category!!
                createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            }
            service.publishIfNeeded(announcement, request.publish)
            announcement.toResource()
        }
        call.respond(HttpStatusCode.Created, DataResponse(body))
    }

    /** PUT /api/v1/announcements/{id} (autor o administrador) */
    suspend fun update(call: ApplicationCall) {
        val user = call.authUser()
        val request = call.validated<AnnouncementRequest>()
        val body = dbQuery {
            val announcement = find(call)
            authorize(AnnouncementPolicy.update(user, announcement), "Solo el autor o un administrador puede editar este aviso.")
            announcement.title = request.title!!.trim()
            announcement.body = request.body!!.trim()
            announcement.category = request.category!!
            service.publishIfNeeded(announcement, request.publish)
            announcement.toResource()
        }
        call.respond(DataResponse(body))
    }

    /** DELETE /api/v1/announcements/{id}  ->  204 (autor o administrador) */
    suspend fun destroy(call: ApplicationCall) {
        val user = call.authUser()
        dbQuery {
            val announcement = find(call)
            authorize(AnnouncementPolicy.delete(user, announcement), "Solo el autor o un administrador puede retirar este aviso.")
            announcement.delete()
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

class FaqController {

    private fun find(call: ApplicationCall): Faq = Faq.findById(call.longParam("id", "La pregunta")) ?: throw NotFoundException("La pregunta")

    /** GET /api/v1/faqs (gestor o administrador) */
    suspend fun index(call: ApplicationCall) {
        val user = call.authUser()
        authorize(FaqPolicy.manage(user), "Solo gestores y administradores administran las preguntas de Banu.")
        val body = dbQuery { Faq.all().orderBy(Faqs.question to SortOrder.ASC).map { it.toResource() } }
        call.respond(DataResponse(body))
    }

    /** POST /api/v1/faqs  ->  201 */
    suspend fun store(call: ApplicationCall) {
        val user = call.authUser()
        authorize(FaqPolicy.manage(user), "Solo gestores y administradores administran las preguntas de Banu.")
        val request = call.validated<FaqRequest>()
        val body = dbQuery {
            Faq.new {
                createdBy = User[user.id]
                question = request.question!!.trim()
                keywords = request.keywords!!.trim()
                answer = request.answer!!.trim()
                sourceUrl = request.sourceUrl!!.trim()
                active = request.active
                updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            }.toResource()
        }
        call.respond(HttpStatusCode.Created, DataResponse(body))
    }

    /** PUT /api/v1/faqs/{id} */
    suspend fun update(call: ApplicationCall) {
        val user = call.authUser()
        authorize(FaqPolicy.manage(user), "Solo gestores y administradores administran las preguntas de Banu.")
        val request = call.validated<FaqRequest>()
        val body = dbQuery {
            val faq = find(call)
            faq.question = request.question!!.trim()
            faq.keywords = request.keywords!!.trim()
            faq.answer = request.answer!!.trim()
            faq.sourceUrl = request.sourceUrl!!.trim()
            faq.active = request.active
            faq.updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            faq.toResource()
        }
        call.respond(DataResponse(body))
    }

    /** DELETE /api/v1/faqs/{id}  ->  204 (se desactiva; Banu deja de usarla) */
    suspend fun destroy(call: ApplicationCall) {
        val user = call.authUser()
        authorize(FaqPolicy.manage(user), "Solo gestores y administradores administran las preguntas de Banu.")
        dbQuery {
            val faq = find(call)
            faq.active = false
            faq.updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        }
        call.respond(HttpStatusCode.NoContent)
    }
}

class AdminUserController {

    /** GET /api/v1/admin/users?role=&page= */
    suspend fun index(call: ApplicationCall) {
        val user = call.authUser()
        authorize(UserPolicy.viewAny(user), "Solo el administrador gestiona usuarios.")
        val page = call.pageRequest()
        val roleName = call.request.queryParameters["role"]?.uppercase()?.takeIf { it.isNotBlank() }
        if (roleName != null && UserRole.entries.none { it.name == roleName }) {
            throw ValidationException(mapOf("role" to "El rol debe ser ESTUDIANTE, GESTOR o ADMINISTRADOR."))
        }
        val body = dbQuery {
            val query = if (roleName == null) User.all() else User.find { Users.role eq Role.of(UserRole.valueOf(roleName)).id }
            val total = query.count()
            val items = query.orderBy(Users.id to SortOrder.ASC).limit(page.perPage).offset(page.offset).map { it.toResource(withProfile = false) }
            DataResponse(items, page.meta(total))
        }
        call.respond(body)
    }

    /** PATCH /api/v1/admin/users/{id}/role */
    suspend fun updateRole(call: ApplicationCall) {
        val user = call.authUser()
        val request = call.validated<UpdateRoleRequest>()
        val body = dbQuery {
            val target = User.findById(call.longParam("id", "El usuario")) ?: throw NotFoundException("El usuario")
            authorize(UserPolicy.changeRole(user, target), "Solo el administrador cambia roles, y no puede cambiar el suyo.")
            target.role = Role.of(UserRole.valueOf(request.role!!))
            target.updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            target.toResource(withProfile = false)
        }
        call.respond(DataResponse(body))
    }

    /** POST /api/v1/admin/programs/sync (entrega 3: sincroniza desde la API de WordPress de unab.edu.co) */
    suspend fun syncPrograms(call: ApplicationCall) {
        val user = call.authUser()
        authorize(UserPolicy.viewAny(user), "Solo el administrador sincroniza los programas.")
        throw NotImplementedYetException("La sincronización con unab.edu.co llega en la entrega 3.")
    }
}
