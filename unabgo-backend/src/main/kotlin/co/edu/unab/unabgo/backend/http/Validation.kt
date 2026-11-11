package co.edu.unab.unabgo.backend.http

// Validación de solicitudes, equivalente a los FormRequest de Laravel: cada clase de solicitud
// declara sus reglas y, si alguna falla, la API responde 400 con el mensaje de cada campo.

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

class Validator {
    val errors = linkedMapOf<String, String>()

    /** Registra el error del campo si la condición no se cumple (solo el primer error de cada campo). */
    fun rule(field: String, ok: Boolean, message: String) {
        if (!ok && field !in errors) errors[field] = message
    }

    fun required(field: String, value: Any?, message: String = "El campo $field es obligatorio.") =
        rule(field, value != null && (value !is String || value.isNotBlank()), message)

    fun maxLength(field: String, value: String?, max: Int) =
        rule(field, value == null || value.length <= max, "El campo $field admite máximo $max caracteres.")

    fun between(field: String, value: Number?, min: Number, max: Number) =
        rule(field, value == null || value.toDouble() in min.toDouble()..max.toDouble(), "El campo $field debe estar entre $min y $max.")

    fun email(field: String, value: String?) =
        rule(field, value == null || EMAIL.matches(value.trim()), "El campo $field debe ser un correo válido.")

    fun institutionalEmail(field: String, value: String?) =
        rule(field, value == null || value.trim().lowercase().endsWith("@unab.edu.co"), "Usa tu correo institucional @unab.edu.co.")

    fun color(field: String, value: String?) =
        rule(field, value == null || HEX.matches(value), "El campo $field debe ser un color #RRGGBB.")

    fun time(field: String, value: String?) = rule(field, value == null || parseTime(value) != null, "El campo $field debe tener el formato HH:mm.")

    fun date(field: String, value: String?) = rule(field, value == null || parseDate(value) != null, "El campo $field debe tener el formato AAAA-MM-DD.")

    fun inSet(field: String, value: String?, allowed: Collection<String>) =
        rule(field, value == null || value in allowed, "El campo $field debe ser uno de: ${allowed.joinToString(", ")}.")

    companion object {
        private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        private val HEX = Regex("^#[0-9A-Fa-f]{6}$")
        fun parseTime(value: String): LocalTime? = try { LocalTime.parse(value) } catch (_: DateTimeParseException) { null }
        fun parseDate(value: String): LocalDate? = try { LocalDate.parse(value) } catch (_: DateTimeParseException) { null }
    }
}

/** Una solicitud que se valida a sí misma (como FormRequest::rules()). */
interface FormRequest {
    fun Validator.rules()
}

fun FormRequest.validate() {
    val validator = Validator()
    validator.rules()
    if (validator.errors.isNotEmpty()) throw ValidationException(validator.errors)
}

/** Recibe el cuerpo JSON y lo valida; responde 400 si no cumple las reglas. */
suspend inline fun <reified T : FormRequest> ApplicationCall.validated(): T = receive<T>().also { it.validate() }
