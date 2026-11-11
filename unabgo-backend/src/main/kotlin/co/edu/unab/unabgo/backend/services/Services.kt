package co.edu.unab.unabgo.backend.services

// Servicios de dominio: aquí viven las reglas de negocio; los controladores solo orquestan.

import co.edu.unab.unabgo.backend.auth.PasswordHasher
import co.edu.unab.unabgo.backend.auth.TokenService
import co.edu.unab.unabgo.backend.auth.UserRole
import co.edu.unab.unabgo.backend.http.ConflictException
import co.edu.unab.unabgo.backend.http.UnauthorizedException
import co.edu.unab.unabgo.backend.http.ValidationException
import co.edu.unab.unabgo.backend.http.Validator
import co.edu.unab.unabgo.backend.http.requests.LoginRequest
import co.edu.unab.unabgo.backend.http.requests.RegisterRequest
import co.edu.unab.unabgo.backend.http.requests.ScheduleBlockInput
import co.edu.unab.unabgo.backend.models.Announcement
import co.edu.unab.unabgo.backend.models.Role
import co.edu.unab.unabgo.backend.models.ScheduleBlock
import co.edu.unab.unabgo.backend.models.StudentProfile
import co.edu.unab.unabgo.backend.models.Subject
import co.edu.unab.unabgo.backend.models.User
import co.edu.unab.unabgo.backend.services.integrations.PushNotifier
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Registro e inicio de sesión (se llama dentro de una transacción). */
class AuthService(private val tokens: TokenService) {

    fun register(request: RegisterRequest): Pair<User, TokenService.IssuedToken> {
        val email = request.email!!.trim().lowercase()
        if (User.findByEmail(email) != null) throw ConflictException("Ya existe una cuenta con ese correo.")
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val user = User.new {
            role = Role.of(UserRole.ESTUDIANTE)
            name = request.name!!.trim()
            this.email = email
            passwordHash = PasswordHasher.hash(request.password!!)
            active = true
            createdAt = now
            updatedAt = now
        }
        StudentProfile.new { this.user = user }
        return user to tokens.issue(user, request.deviceName ?: "app")
    }

    fun login(request: LoginRequest): Pair<User, TokenService.IssuedToken> {
        val user = User.findByEmail(request.email!!)
        if (user == null || !PasswordHasher.verify(request.password!!, user.passwordHash)) {
            throw UnauthorizedException("Correo o contraseña incorrectos.")
        }
        if (!user.active) throw UnauthorizedException("La cuenta está desactivada.")
        return user to tokens.issue(user, request.deviceName ?: "app")
    }
}

/** Horario: un estudiante no puede tener dos clases que se crucen el mismo día. */
object ScheduleService {

    private data class Slot(val day: Int, val start: LocalTime, val end: LocalTime, val label: String)

    private fun overlaps(a: Slot, b: Slot) = a.day == b.day && a.start < b.end && b.start < a.end

    /** Revisa los bloques nuevos entre sí y contra las demás materias del estudiante. */
    fun assertNoConflicts(owner: User, blocks: List<ScheduleBlockInput>, ignoreSubjectId: Long? = null) {
        val incoming = blocks.mapIndexed { i, b ->
            Slot(b.dayOfWeek!!, Validator.parseTime(b.startTime!!)!!, Validator.parseTime(b.endTime!!)!!, "blocks.$i")
        }
        val errors = linkedMapOf<String, String>()
        for (i in incoming.indices) for (j in i + 1 until incoming.size) {
            if (overlaps(incoming[i], incoming[j])) errors.putIfAbsent(incoming[j].label, "Este bloque se cruza con otro bloque de la misma materia.")
        }
        val existing = owner.subjects.filter { it.id.value != ignoreSubjectId }.flatMap { s ->
            s.blocks.map { Slot(it.dayOfWeek.toInt(), it.startTime, it.endTime, s.name) }
        }
        for (slot in incoming) existing.firstOrNull { overlaps(it, slot) }?.let {
            errors.putIfAbsent(slot.label, "Se cruza con ${it.label} (${it.start}–${it.end}).")
        }
        if (errors.isNotEmpty()) throw ValidationException(errors)
    }

    fun replaceBlocks(subject: Subject, blocks: List<ScheduleBlockInput>) {
        subject.blocks.forEach { it.delete() }
        blocks.forEach { b ->
            ScheduleBlock.new {
                this.subject = subject
                dayOfWeek = b.dayOfWeek!!.toShort()
                startTime = Validator.parseTime(b.startTime!!)!!
                endTime = Validator.parseTime(b.endTime!!)!!
                room = b.room?.trim()?.ifEmpty { null }
            }
        }
    }
}

/** Avisos: al publicarse se envía una sola notificación push (Firebase Cloud Messaging en la entrega 3). */
class AnnouncementService(private val push: PushNotifier) {

    fun publishIfNeeded(announcement: Announcement, publish: Boolean) {
        if (!publish || announcement.publishedAt != null) return
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        announcement.publishedAt = now
        if (announcement.pushSentAt == null && push.send(announcement.category, announcement.title, announcement.body)) {
            announcement.pushSentAt = now
        }
    }
}
