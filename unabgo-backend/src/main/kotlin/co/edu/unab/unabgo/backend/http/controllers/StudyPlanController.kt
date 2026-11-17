package co.edu.unab.unabgo.backend.http.controllers

// Planes de estudio de los programas de la UNAB (públicos): listado con búsqueda y detalle por slug.

import co.edu.unab.unabgo.backend.database.StudyPlans
import co.edu.unab.unabgo.backend.database.dbQuery
import co.edu.unab.unabgo.backend.http.NotFoundException
import co.edu.unab.unabgo.backend.http.pageRequest
import co.edu.unab.unabgo.backend.http.resources.DataResponse
import co.edu.unab.unabgo.backend.http.resources.toResource
import co.edu.unab.unabgo.backend.http.resources.toSummary
import co.edu.unab.unabgo.backend.models.StudyPlan
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.lowerCase

class StudyPlanController {

    /** GET /api/v1/study-plans?q=&level=&page=&per_page= (q ignora tildes y mayúsculas). */
    suspend fun index(call: ApplicationCall) {
        val q = call.request.queryParameters
        val page = call.pageRequest()
        val body = dbQuery {
            var filter: Op<Boolean> = Op.TRUE
            q["q"]?.let { StudyPlan.normalize(it) }?.takeIf { it.isNotEmpty() }?.let { filter = filter and (StudyPlans.searchName like "%$it%") }
            q["level"]?.trim()?.takeIf { it.isNotEmpty() }?.let { filter = filter and (StudyPlans.level.lowerCase() eq it.lowercase()) }
            val query = StudyPlan.find(filter)
            val total = query.count()
            val items = query.orderBy(StudyPlans.programName to SortOrder.ASC, StudyPlans.slug to SortOrder.ASC)
                .limit(page.perPage).offset(page.offset).map { it.toSummary() }
            DataResponse(items, page.meta(total))
        }
        call.respond(body)
    }

    /** GET /api/v1/study-plans/{slug} */
    suspend fun show(call: ApplicationCall) {
        val slug = call.parameters["slug"].orEmpty()
        val body = dbQuery { (StudyPlan.find { StudyPlans.slug eq slug }.singleOrNull() ?: throw NotFoundException("El plan de estudios")).toResource() }
        call.respond(DataResponse(body))
    }
}
