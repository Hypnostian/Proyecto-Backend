package co.edu.unab.unabgo.backend.http.resources

// Recursos JSON (como los API Resources de Laravel): definen exactamente qué se devuelve de cada
// modelo. Nunca se exponen la contraseña ni la huella de los tokens. Se llaman dentro de una transacción.

import co.edu.unab.unabgo.backend.database.BusStops
import co.edu.unab.unabgo.backend.database.RouteStops
import co.edu.unab.unabgo.backend.models.Announcement
import co.edu.unab.unabgo.backend.models.BusRoute
import co.edu.unab.unabgo.backend.models.BusStop
import co.edu.unab.unabgo.backend.models.Faq
import co.edu.unab.unabgo.backend.models.GradeCategory
import co.edu.unab.unabgo.backend.models.GradeItem
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.models.ScheduleBlock
import co.edu.unab.unabgo.backend.models.StudentProfile
import co.edu.unab.unabgo.backend.models.Subject
import co.edu.unab.unabgo.backend.models.User
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.selectAll

/** Respuesta exitosa: { "data": ..., "meta": ... } */
@Serializable
data class DataResponse<T>(val data: T, val meta: PageMeta? = null)

@Serializable
data class PageMeta(val page: Int, val perPage: Int, val total: Long, val lastPage: Int)

@Serializable
data class MessageResponse(val message: String)

@Serializable
data class UserResource(
    val id: Long,
    val name: String,
    val email: String,
    val role: String,
    val active: Boolean,
    val profile: ProfileResource? = null,
    val createdAt: String
)

@Serializable
data class ProfileResource(
    val studentCode: String?,
    val semester: Int?,
    val language: String,
    val reminderMinutes: Int,
    val program: ProgramSummary?
)

@Serializable
data class ProgramSummary(val id: Long, val name: String, val faculty: String)

@Serializable
data class TokenResource(val token: String, val tokenType: String = "Bearer", val expiresAt: String, val user: UserResource)

@Serializable
data class ProgramResource(
    val id: Long,
    val slug: String,
    val name: String,
    val faculty: String,
    val level: String,
    val modality: String?,
    val priceCop: Long?,
    val snies: String?,
    val url: String,
    val verifiedAt: String?
)

@Serializable
data class ScheduleBlockResource(val id: Long, val dayOfWeek: Int, val startTime: String, val endTime: String, val room: String?)

@Serializable
data class GradeItemResource(val id: Long, val name: String, val score: Double?, val weight: Double?, val gradedAt: String?)

@Serializable
data class GradeCategoryResource(val id: Long, val name: String, val weight: Double, val sortOrder: Int, val items: List<GradeItemResource>)

@Serializable
data class SubjectResource(
    val id: Long,
    val name: String,
    val credits: Int,
    val teacher: String?,
    val color: String,
    val blocks: List<ScheduleBlockResource>,
    val categories: List<GradeCategoryResource>? = null
)

@Serializable
data class BusStopResource(val id: Long, val name: String, val lat: Double, val lon: Double, val isCampus: Boolean)

@Serializable
data class BusRouteResource(val id: Long, val code: String, val name: String, val color: String, val active: Boolean, val stops: List<BusStopResource>)

@Serializable
data class AnnouncementResource(
    val id: Long,
    val title: String,
    val body: String,
    val category: String,
    val author: String,
    val publishedAt: String?,
    val pushSentAt: String?,
    val createdAt: String
)

@Serializable
data class FaqResource(val id: Long, val question: String, val keywords: String, val answer: String, val sourceUrl: String, val active: Boolean)

// ---------------------------------------------------------------- conversiones de modelo a recurso

fun User.toResource(withProfile: Boolean = true) = UserResource(
    id = id.value,
    name = name,
    email = email,
    role = role.name,
    active = active,
    profile = if (withProfile) profile?.toResource() else null,
    createdAt = createdAt.toString()
)

fun StudentProfile.toResource() = ProfileResource(
    studentCode = studentCode,
    semester = semester?.toInt(),
    language = language,
    reminderMinutes = reminderMinutes.toInt(),
    program = program?.let { ProgramSummary(it.id.value, it.name, it.faculty) }
)

fun Program.toResource() = ProgramResource(
    id.value, slug, name, faculty, level, modality, priceCop, snies, url, verifiedAt?.toString()
)

fun ScheduleBlock.toResource() = ScheduleBlockResource(id.value, dayOfWeek.toInt(), startTime.toString(), endTime.toString(), room)

fun GradeItem.toResource() = GradeItemResource(id.value, name, score?.toDouble(), weight?.toDouble(), gradedAt?.toString())

fun GradeCategory.toResource() = GradeCategoryResource(
    id.value, name, weight.toDouble(), sortOrder.toInt(), items.sortedBy { it.id.value }.map { it.toResource() }
)

fun Subject.toResource(withGrades: Boolean = false) = SubjectResource(
    id = id.value,
    name = name,
    credits = credits.toInt(),
    teacher = teacher,
    color = color,
    blocks = blocks.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime })).map { it.toResource() },
    categories = if (withGrades) categories.sortedBy { it.sortOrder }.map { it.toResource() } else null
)

fun BusStop.toResource() = BusStopResource(id.value, name, lat.toDouble(), lon.toDouble(), isCampus)

/** Ruta con sus paradas en orden (relación N:M con la columna sort_order de route_stops). */
fun BusRoute.toResource(): BusRouteResource {
    val ordered = (RouteStops innerJoin BusStops).selectAll()
        .where { RouteStops.route eq this@toResource.id }
        .orderBy(RouteStops.sortOrder)
        .map { BusStop.wrapRow(it).toResource() }
    return BusRouteResource(id.value, code, name, color, active, ordered)
}

fun Announcement.toResource() = AnnouncementResource(
    id.value, title, body, category, author.name, publishedAt?.toString(), pushSentAt?.toString(), createdAt.toString()
)

fun Faq.toResource() = FaqResource(id.value, question, keywords, answer, sourceUrl, active)
