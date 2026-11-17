package co.edu.unab.unabgo.backend.services

// Servicios de dominio: aquí viven las reglas de negocio; los controladores solo orquestan.

import co.edu.unab.unabgo.backend.auth.PasswordHasher
import co.edu.unab.unabgo.backend.auth.TokenService
import co.edu.unab.unabgo.backend.auth.UserRole
import co.edu.unab.unabgo.backend.database.Users
import co.edu.unab.unabgo.backend.http.ConflictException
import co.edu.unab.unabgo.backend.http.ForbiddenException
import co.edu.unab.unabgo.backend.http.UnauthorizedException
import co.edu.unab.unabgo.backend.http.ValidationException
import co.edu.unab.unabgo.backend.http.Validator
import co.edu.unab.unabgo.backend.http.requests.LoginRequest
import co.edu.unab.unabgo.backend.http.requests.RegisterRequest
import co.edu.unab.unabgo.backend.http.requests.ScheduleBlockInput
import co.edu.unab.unabgo.backend.http.resources.NextClassResource
import co.edu.unab.unabgo.backend.models.Announcement
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.models.Role
import co.edu.unab.unabgo.backend.models.ScheduleBlock
import co.edu.unab.unabgo.backend.models.StudentProfile
import co.edu.unab.unabgo.backend.models.Subject
import co.edu.unab.unabgo.backend.models.User
import co.edu.unab.unabgo.backend.services.integrations.GoogleIdentity
import co.edu.unab.unabgo.backend.services.integrations.PushNotifier
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
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
        val program = request.programId?.let {
            Program.findById(it) ?: throw ValidationException(mapOf("program_id" to "La carrera elegida no existe."))
        }
        StudentProfile.new {
            this.user = user
            this.program = program
            request.semester?.let { semester = it.toShort() }
            request.language?.let { language = it }
        }
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

    /**
     * Inicio de sesión con Google (Firebase): solo correos @unab.edu.co. Si ya existe una cuenta con ese correo
     * se enlaza con el uid de Google; si no, se crea un estudiante sin contraseña.
     */
    fun loginWithGoogle(identity: GoogleIdentity, deviceName: String?): Pair<User, TokenService.IssuedToken> {
        if (!identity.emailVerified) throw ForbiddenException("Google no verificó este correo.")
        if (!identity.email.endsWith("@unab.edu.co")) throw ForbiddenException("Solo pueden ingresar cuentas institucionales @unab.edu.co.")
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val user = User.find { Users.googleUid eq identity.uid }.singleOrNull() ?: User.findByEmail(identity.email)?.also { it.googleUid = identity.uid }
            ?: User.new {
                role = Role.of(UserRole.ESTUDIANTE)
                name = (identity.name?.takeIf { it.isNotBlank() } ?: identity.email.substringBefore('@')).take(120)
                email = identity.email
                passwordHash = null
                googleUid = identity.uid
                active = true
                createdAt = now
                updatedAt = now
            }.also { StudentProfile.new { this.user = it } }
        if (!user.active) throw UnauthorizedException("La cuenta está desactivada.")
        return user to tokens.issue(user, deviceName ?: "google")
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

    /** Zona horaria del campus: los bloques del horario se guardan en hora local de Bucaramanga. */
    val CAMPUS_ZONE: java.time.ZoneId = java.time.ZoneId.of("America/Bogota")

    /**
     * Próxima clase del estudiante a partir de [now] (hora local del campus). Si hay una clase en curso, esa es la
     * próxima; si ya pasaron todas las de la semana, devuelve la primera de la semana siguiente. Null si no tiene bloques.
     */
    fun nextClass(owner: User, now: java.time.LocalDateTime): NextClassResource? {
        val blocks = owner.subjects.flatMap { s -> s.blocks.map { s to it } }
        if (blocks.isEmpty()) return null
        val candidates = (0..7).flatMap { offset ->
            val date = now.toLocalDate().plusDays(offset.toLong())
            blocks.filter { (_, b) -> b.dayOfWeek.toInt() == date.dayOfWeek.value }
                .map { (s, b) -> Triple(s, b, date.atTime(b.startTime)) }
                .filter { (_, b, start) -> offset > 0 || date.atTime(b.endTime) > now || start > now }
        }
        val (subject, block, start) = candidates.minByOrNull { it.third } ?: return null
        val minutes = java.time.Duration.between(now, start).toMinutes()
        return NextClassResource(
            subjectId = subject.id.value,
            subjectName = subject.name,
            color = subject.color,
            teacher = subject.teacher,
            blockId = block.id.value,
            date = start.toLocalDate().toString(),
            dayOfWeek = block.dayOfWeek.toInt(),
            startTime = block.startTime.toString(),
            endTime = block.endTime.toString(),
            room = block.room,
            inProgress = start <= now,
            startsInMinutes = minutes.coerceAtLeast(0)
        )
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

/** Avisos: al publicarse se envía una sola notificación push (Firebase Cloud Messaging). */
class AnnouncementService(private val push: PushNotifier) {

    suspend fun publishIfNeeded(announcement: Announcement, publish: Boolean) {
        if (!publish || announcement.publishedAt != null) return
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        announcement.publishedAt = now
        if (announcement.pushSentAt == null && push.send(announcement.category, announcement.title, announcement.body)) {
            announcement.pushSentAt = now
        }
    }
}
