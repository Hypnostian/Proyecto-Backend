package co.edu.unab.unabgo.backend.http.requests

// Clases de solicitud (FormRequest). Los nombres JSON van en snake_case (password_confirmation, ...).
// Solo los campos declarados aquí se pueden asignar: es la "lista blanca" equivalente a $fillable.

import co.edu.unab.unabgo.backend.auth.UserRole
import co.edu.unab.unabgo.backend.http.FormRequest
import co.edu.unab.unabgo.backend.http.Validator
import co.edu.unab.unabgo.backend.models.Announcement
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val name: String? = null,
    val email: String? = null,
    val password: String? = null,
    val passwordConfirmation: String? = null,
    val deviceName: String? = null,
    /** Carrera elegida en el registro (opcional): queda en el perfil y define el plan de estudios propio. */
    val programId: Long? = null,
    val semester: Int? = null,
    val language: String? = null
) : FormRequest {
    override fun Validator.rules() {
        required("name", name, "El nombre es obligatorio.")
        maxLength("name", name, 120)
        required("email", email, "El correo es obligatorio.")
        email("email", email)
        institutionalEmail("email", email)
        maxLength("email", email, 160)
        required("password", password, "La contraseña es obligatoria.")
        rule("password", password == null || password.length >= 8, "La contraseña debe tener al menos 8 caracteres.")
        rule("password", password == null || (password.any(Char::isLetter) && password.any(Char::isDigit)),
            "La contraseña debe tener letras y números.")
        rule("password_confirmation", passwordConfirmation == password, "La confirmación no coincide con la contraseña.")
        between("semester", semester, 1, 12)
        inSet("language", language, LANGUAGES)
    }
}

/** Idiomas de la app (el cliente traduce la interfaz a cualquiera de ellos). */
val LANGUAGES = listOf("es", "en", "fr", "pt", "ko")

@Serializable
data class LoginRequest(
    val email: String? = null,
    val password: String? = null,
    val deviceName: String? = null
) : FormRequest {
    override fun Validator.rules() {
        required("email", email, "El correo es obligatorio.")
        email("email", email)
        required("password", password, "La contraseña es obligatoria.")
    }
}

@Serializable
data class UpdateProfileRequest(
    val programId: Long? = null,
    val studentCode: String? = null,
    val semester: Int? = null,
    val language: String? = null,
    val reminderMinutes: Int? = null
) : FormRequest {
    override fun Validator.rules() {
        maxLength("student_code", studentCode, 20)
        between("semester", semester, 1, 12)
        inSet("language", language, LANGUAGES)
        rule("reminder_minutes", reminderMinutes == null || reminderMinutes in setOf(0, 30, 60), "El recordatorio debe ser 0, 30 o 60 minutos.")
    }
}

@Serializable
data class ScheduleBlockInput(
    val dayOfWeek: Int? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val room: String? = null
)

@Serializable
data class SubjectRequest(
    val name: String? = null,
    val credits: Int? = null,
    val teacher: String? = null,
    val color: String? = null,
    val blocks: List<ScheduleBlockInput> = emptyList()
) : FormRequest {
    override fun Validator.rules() {
        required("name", name, "El nombre de la materia es obligatorio.")
        maxLength("name", name, 120)
        required("credits", credits, "Los créditos son obligatorios.")
        between("credits", credits, 0, 10)
        maxLength("teacher", teacher, 120)
        required("color", color, "El color es obligatorio.")
        color("color", color)
        rule("blocks", blocks.size <= 7, "Una materia tiene máximo 7 bloques por semana.")
        blocks.forEachIndexed { i, b ->
            val p = "blocks.$i"
            required("$p.day_of_week", b.dayOfWeek, "El día es obligatorio.")
            between("$p.day_of_week", b.dayOfWeek, 1, 7)
            required("$p.start_time", b.startTime, "La hora de inicio es obligatoria.")
            time("$p.start_time", b.startTime)
            required("$p.end_time", b.endTime, "La hora de fin es obligatoria.")
            time("$p.end_time", b.endTime)
            val start = b.startTime?.let(Validator::parseTime)
            val end = b.endTime?.let(Validator::parseTime)
            rule("$p.end_time", start == null || end == null || end.isAfter(start), "La hora de fin debe ser posterior a la de inicio.")
            maxLength("$p.room", b.room, 40)
        }
    }
}

@Serializable
data class GradeCategoryRequest(
    val name: String? = null,
    val weight: Double? = null,
    val sortOrder: Int? = null
) : FormRequest {
    override fun Validator.rules() {
        required("name", name, "El nombre del corte es obligatorio.")
        maxLength("name", name, 60)
        required("weight", weight, "El peso es obligatorio.")
        between("weight", weight, 0, 100)
        between("sort_order", sortOrder, 1, 20)
    }
}

@Serializable
data class GradeItemRequest(
    val name: String? = null,
    val score: Double? = null,
    val weight: Double? = null,
    val gradedAt: String? = null
) : FormRequest {
    override fun Validator.rules() {
        required("name", name, "El nombre de la nota es obligatorio.")
        maxLength("name", name, 80)
        between("score", score, 0.0, 5.0)
        rule("score", score == null || Math.round(score * 10) / 10.0 == score, "La nota lleva máximo una décima (por ejemplo, 3.5).")
        between("weight", weight, 0, 100)
        date("graded_at", gradedAt)
    }
}

@Serializable
data class AnnouncementRequest(
    val title: String? = null,
    val body: String? = null,
    val category: String? = null,
    val publish: Boolean = false
) : FormRequest {
    override fun Validator.rules() {
        required("title", title, "El título es obligatorio.")
        maxLength("title", title, 140)
        required("body", body, "El contenido es obligatorio.")
        maxLength("body", body, 5000)
        required("category", category, "La categoría es obligatoria.")
        inSet("category", category, Announcement.CATEGORIES)
    }
}

@Serializable
data class FaqRequest(
    val question: String? = null,
    val keywords: String? = null,
    val answer: String? = null,
    val sourceUrl: String? = null,
    val active: Boolean = true
) : FormRequest {
    override fun Validator.rules() {
        required("question", question, "La pregunta es obligatoria.")
        maxLength("question", question, 200)
        required("keywords", keywords, "Escribe al menos una forma de hacer la pregunta.")
        required("answer", answer, "La respuesta es obligatoria.")
        required("source_url", sourceUrl, "La fuente oficial es obligatoria.")
        rule("source_url", sourceUrl == null || sourceUrl.startsWith("https://"), "La fuente debe ser un enlace https.")
        maxLength("source_url", sourceUrl, 300)
    }
}

@Serializable
data class UpdateRoleRequest(val role: String? = null) : FormRequest {
    override fun Validator.rules() {
        required("role", role, "El rol es obligatorio.")
        inSet("role", role, UserRole.entries.map { it.name })
    }
}

@Serializable
data class BusRouteRequest(
    val name: String? = null,
    val color: String? = null,
    val active: Boolean = true,
    val stopIds: List<Long> = emptyList()
) : FormRequest {
    override fun Validator.rules() {
        required("name", name, "El nombre de la ruta es obligatorio.")
        maxLength("name", name, 80)
        required("color", color, "El color es obligatorio.")
        color("color", color)
        rule("stop_ids", stopIds.size >= 2, "Una ruta necesita al menos 2 paradas.")
        rule("stop_ids", stopIds.toSet().size == stopIds.size, "Las paradas no se pueden repetir.")
    }
}

@Serializable
data class GoogleLoginRequest(val idToken: String? = null, val deviceName: String? = null) : FormRequest {
    override fun Validator.rules() {
        required("id_token", idToken, "El id_token de Google es obligatorio.")
        maxLength("id_token", idToken, 4096)
        maxLength("device_name", deviceName, 60)
    }
}

@Serializable
data class BanuAskRequest(val pregunta: String? = null) : FormRequest {
    override fun Validator.rules() {
        required("pregunta", pregunta, "Escribe tu pregunta.")
        minLength("pregunta", pregunta, 3)
        maxLength("pregunta", pregunta?.trim(), 500)
    }
}

/** Posición enviada por el dispositivo a bordo (webhook firmado con HMAC). */
@Serializable
data class GpsWebhookRequest(
    val deviceId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val speedKmh: Double? = null,
    val heading: Double? = null,
    val ignition: Boolean? = null,
    val timestamp: String? = null
) : FormRequest {
    override fun Validator.rules() {
        required("device_id", deviceId, "El identificador del dispositivo es obligatorio.")
        maxLength("device_id", deviceId, 40)
        required("lat", lat, "La latitud es obligatoria.")
        between("lat", lat, -90, 90)
        required("lng", lng, "La longitud es obligatoria.")
        between("lng", lng, -180, 180)
        between("speed_kmh", speedKmh, 0, 250)
        between("heading", heading, 0, 360)
        rule("timestamp", timestamp == null || runCatching { java.time.OffsetDateTime.parse(timestamp) }.isSuccess,
            "El campo timestamp debe ser una fecha ISO-8601 con zona (2026-10-27T11:06:32-05:00).")
    }
}

/** Consulta de GET /schedule/next: "at" (opcional) es la fecha y hora local de Bucaramanga, AAAA-MM-DDTHH:mm. */
data class NextClassQuery(val at: String? = null) : FormRequest {
    override fun Validator.rules() {
        rule("at", at == null || parseLocalDateTime(at) != null, "El campo at debe tener el formato AAAA-MM-DDTHH:mm.")
    }

    companion object {
        fun parseLocalDateTime(value: String): java.time.LocalDateTime? =
            try { java.time.LocalDateTime.parse(value) } catch (_: java.time.format.DateTimeParseException) { null }
    }
}
