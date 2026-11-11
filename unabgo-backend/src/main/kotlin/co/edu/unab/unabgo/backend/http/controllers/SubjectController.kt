package co.edu.unab.unabgo.backend.http.controllers

import co.edu.unab.unabgo.backend.auth.SubjectPolicy
import co.edu.unab.unabgo.backend.auth.authorize
import co.edu.unab.unabgo.backend.database.Subjects
import co.edu.unab.unabgo.backend.database.dbQuery
import co.edu.unab.unabgo.backend.http.NotFoundException
import co.edu.unab.unabgo.backend.http.ValidationException
import co.edu.unab.unabgo.backend.http.authUser
import co.edu.unab.unabgo.backend.http.longParam
import co.edu.unab.unabgo.backend.http.requests.GradeCategoryRequest
import co.edu.unab.unabgo.backend.http.requests.GradeItemRequest
import co.edu.unab.unabgo.backend.http.requests.SubjectRequest
import co.edu.unab.unabgo.backend.http.resources.DataResponse
import co.edu.unab.unabgo.backend.http.resources.toResource
import co.edu.unab.unabgo.backend.http.validated
import co.edu.unab.unabgo.backend.http.Validator
import co.edu.unab.unabgo.backend.models.GradeCategory
import co.edu.unab.unabgo.backend.models.GradeItem
import co.edu.unab.unabgo.backend.models.Subject
import co.edu.unab.unabgo.backend.models.User
import co.edu.unab.unabgo.backend.services.GradeService
import co.edu.unab.unabgo.backend.services.ScheduleService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Materias, horario, cortes y notas del estudiante (solo las propias, por SubjectPolicy). */
class SubjectController {

    private fun findSubject(call: ApplicationCall): Subject =
        Subject.findById(call.longParam("id", "La materia")) ?: throw NotFoundException("La materia")

    /** GET /api/v1/subjects */
    suspend fun index(call: ApplicationCall) {
        val user = call.authUser()
        authorize(SubjectPolicy.viewAny(user), "Solo los estudiantes gestionan materias.")
        val body = dbQuery {
            Subject.find { Subjects.user eq user.id }.sortedBy { it.name }.map { it.toResource() }
        }
        call.respond(DataResponse(body))
    }

    /** POST /api/v1/subjects  ->  201 Created; rechaza choques de horario. */
    suspend fun store(call: ApplicationCall) {
        val user = call.authUser()
        authorize(SubjectPolicy.create(user), "Solo los estudiantes gestionan materias.")
        val request = call.validated<SubjectRequest>()
        val body = dbQuery {
            val owner = User[user.id]
            ScheduleService.assertNoConflicts(owner, request.blocks)
            val subject = Subject.new {
                this.user = owner
                name = request.name!!.trim()
                credits = request.credits!!.toShort()
                teacher = request.teacher?.trim()?.ifEmpty { null }
                color = request.color!!.uppercase()
                createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
            }
            ScheduleService.replaceBlocks(subject, request.blocks)
            subject.toResource(withGrades = true)
        }
        call.respond(HttpStatusCode.Created, DataResponse(body))
    }

    /** GET /api/v1/subjects/{id} */
    suspend fun show(call: ApplicationCall) {
        val user = call.authUser()
        val body = dbQuery {
            val subject = findSubject(call)
            authorize(SubjectPolicy.view(user, subject))
            subject.toResource(withGrades = true)
        }
        call.respond(DataResponse(body))
    }

    /** PUT /api/v1/subjects/{id} */
    suspend fun update(call: ApplicationCall) {
        val user = call.authUser()
        val request = call.validated<SubjectRequest>()
        val body = dbQuery {
            val subject = findSubject(call)
            authorize(SubjectPolicy.update(user, subject))
            ScheduleService.assertNoConflicts(subject.user, request.blocks, ignoreSubjectId = subject.id.value)
            subject.name = request.name!!.trim()
            subject.credits = request.credits!!.toShort()
            subject.teacher = request.teacher?.trim()?.ifEmpty { null }
            subject.color = request.color!!.uppercase()
            ScheduleService.replaceBlocks(subject, request.blocks)
            subject.toResource(withGrades = true)
        }
        call.respond(DataResponse(body))
    }

    /** DELETE /api/v1/subjects/{id}  ->  204 (borra en cascada horario, cortes y notas). */
    suspend fun destroy(call: ApplicationCall) {
        val user = call.authUser()
        dbQuery {
            val subject = findSubject(call)
            authorize(SubjectPolicy.delete(user, subject))
            subject.delete()
        }
        call.respond(HttpStatusCode.NoContent)
    }

    /** POST /api/v1/subjects/{id}/categories  ->  201; la suma de los pesos no puede pasar de 100 %. */
    suspend fun storeCategory(call: ApplicationCall) {
        val user = call.authUser()
        val request = call.validated<GradeCategoryRequest>()
        val body = dbQuery {
            val subject = findSubject(call)
            authorize(SubjectPolicy.update(user, subject))
            val used = subject.categories.sumOf { it.weight.toDouble() }
            if (used + request.weight!! > 100.0001) {
                throw ValidationException(mapOf("weight" to "Los cortes ya suman $used %; este corte puede pesar máximo ${100 - used} %."))
            }
            GradeCategory.new {
                this.subject = subject
                name = request.name!!.trim()
                weight = BigDecimal.valueOf(request.weight).setScale(2)
                sortOrder = (request.sortOrder ?: (subject.categories.count().toInt() + 1)).toShort()
            }.toResource()
        }
        call.respond(HttpStatusCode.Created, DataResponse(body))
    }

    /** POST /api/v1/categories/{id}/items  ->  201 con la nota (0.0 a 5.0) o pendiente si no trae score. */
    suspend fun storeItem(call: ApplicationCall) {
        val user = call.authUser()
        val request = call.validated<GradeItemRequest>()
        val body = dbQuery {
            val category = GradeCategory.findById(call.longParam("id", "El corte")) ?: throw NotFoundException("El corte")
            authorize(SubjectPolicy.update(user, category.subject))
            GradeItem.new {
                this.category = category
                name = request.name!!.trim()
                score = request.score?.let { BigDecimal.valueOf(it).setScale(1) }
                weight = request.weight?.let { BigDecimal.valueOf(it).setScale(2) }
                gradedAt = request.gradedAt?.let(Validator::parseDate)
            }.toResource()
        }
        call.respond(HttpStatusCode.Created, DataResponse(body))
    }

    /** GET /api/v1/subjects/{id}/summary: promedio, acumulado y nota necesaria (Reglamento, Arts. 39 y 44). */
    suspend fun summary(call: ApplicationCall) {
        val user = call.authUser()
        val body = dbQuery {
            val subject = findSubject(call)
            authorize(SubjectPolicy.view(user, subject))
            GradeService.summaryOf(subject)
        }
        call.respond(DataResponse(body))
    }
}
