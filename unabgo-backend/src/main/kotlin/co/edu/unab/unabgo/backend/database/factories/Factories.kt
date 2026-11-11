package co.edu.unab.unabgo.backend.database.factories

// Factories (como las de Laravel): crean registros con datos de prueba realistas usando Datafaker en
// español. Se llaman dentro de una transacción. La semilla fija hace que migrateFresh siempre genere
// los mismos datos.

import co.edu.unab.unabgo.backend.auth.PasswordHasher
import co.edu.unab.unabgo.backend.auth.UserRole
import co.edu.unab.unabgo.backend.models.Announcement
import co.edu.unab.unabgo.backend.models.Faq
import co.edu.unab.unabgo.backend.models.GradeCategory
import co.edu.unab.unabgo.backend.models.GradeItem
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.models.Role
import co.edu.unab.unabgo.backend.models.ScheduleBlock
import co.edu.unab.unabgo.backend.models.StudentProfile
import co.edu.unab.unabgo.backend.models.Subject
import co.edu.unab.unabgo.backend.models.User
import net.datafaker.Faker
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.Random

class Factories(seed: Long = 2026) {

    val random = Random(seed)
    val faker = Faker(Locale.forLanguageTag("es"), random)
    private var sequence = 0

    // ------------------------------------------------------------ usuarios

    fun user(role: UserRole, name: String? = null, email: String? = null, password: String = DEFAULT_PASSWORD): User {
        val fullName = name ?: "${faker.name().firstName()} ${faker.name().lastName()}"
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return User.new {
            this.role = Role.of(role)
            this.name = fullName
            this.email = (email ?: institutionalEmail(fullName)).lowercase()
            passwordHash = PasswordHasher.hash(password)
            active = true
            createdAt = now
            updatedAt = now
        }
    }

    fun studentProfile(user: User, program: Program?): StudentProfile = StudentProfile.new {
        this.user = user
        this.program = program
        studentCode = "U00" + (100_000 + random.nextInt(900_000))
        semester = (1 + random.nextInt(10)).toShort()
        language = "es"
        reminderMinutes = listOf<Short>(30, 60, 30, 0)[random.nextInt(4)]
    }

    /** correo de prueba con el dominio institucional (demo.nombre.apellido123@unab.edu.co). */
    private fun institutionalEmail(fullName: String): String {
        val slug = Normalizer.normalize(fullName, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
            .lowercase().replace(Regex("[^a-z ]"), "").trim().replace(Regex("\\s+"), ".")
        return "demo.$slug${++sequence}@unab.edu.co"
    }

    // ------------------------------------------------------------ gestión académica

    fun subject(owner: User, name: String = SUBJECTS[random.nextInt(SUBJECTS.size)]): Subject = Subject.new {
        user = owner
        this.name = name
        credits = (2 + random.nextInt(3)).toShort()
        teacher = "${faker.name().firstName()} ${faker.name().lastName()}"
        color = COLORS[random.nextInt(COLORS.size)]
        createdAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    }

    fun scheduleBlock(subject: Subject, day: Int, start: LocalTime, hours: Long = 2): ScheduleBlock = ScheduleBlock.new {
        this.subject = subject
        dayOfWeek = day.toShort()
        startTime = start
        endTime = start.plusHours(hours)
        room = "Edificio ${'A' + random.nextInt(6)}, salón ${100 + random.nextInt(300)}"
    }

    fun gradeCategory(subject: Subject, name: String, weight: Int, order: Int): GradeCategory = GradeCategory.new {
        this.subject = subject
        this.name = name
        this.weight = BigDecimal(weight).setScale(2)
        sortOrder = order.toShort()
    }

    /** Nota entre 2.0 y 5.0 con una décima; [graded] = false la deja pendiente. */
    fun gradeItem(category: GradeCategory, name: String, graded: Boolean = true): GradeItem = GradeItem.new {
        this.category = category
        this.name = name
        score = if (graded) BigDecimal(2.0 + random.nextDouble() * 3.0).setScale(1, RoundingMode.DOWN) else null
        weight = null
        gradedAt = if (graded) LocalDate.now().minusDays(random.nextInt(60).toLong()) else null
    }

    // ------------------------------------------------------------ contenidos

    fun announcement(author: User, published: Boolean = true): Announcement {
        val (category, title, body) = ANNOUNCEMENTS[random.nextInt(ANNOUNCEMENTS.size)]
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return Announcement.new {
            this.author = author
            this.category = category
            this.title = title
            this.body = body
            createdAt = now.minus((1 + random.nextInt(20)).toLong(), ChronoUnit.DAYS)
            publishedAt = if (published) createdAt.plus(1, ChronoUnit.HOURS) else null
            pushSentAt = publishedAt
        }
    }

    fun faq(author: User, question: String? = null, answer: String? = null): Faq = Faq.new {
        createdBy = author
        this.question = question ?: "¿${faker.lorem().sentence(5).trimEnd('.')}?"
        keywords = faker.lorem().words(4).joinToString("; ")
        this.answer = answer ?: faker.lorem().paragraph(2)
        sourceUrl = "https://unab.edu.co"
        active = true
        updatedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    }

    fun program(name: String = "Programa de prueba ${++sequence}"): Program = Program.new {
        slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "-")
        this.name = name
        faculty = "Facultad de Ingeniería"
        level = "Profesional"
        modality = "Presencial"
        priceCop = 8_000_000L + random.nextInt(4_000_000)
        snies = null
        url = "https://unab.edu.co/programas/$slug/"
        verifiedAt = null
    }

    companion object {
        /** Contraseña de todos los usuarios de prueba (solo desarrollo; está en el README). */
        const val DEFAULT_PASSWORD = "Unab2026*"

        val SUBJECTS = listOf(
            "Bases de Datos", "Cálculo Diferencial", "Programación Orientada a Objetos", "Estructuras de Datos",
            "Física Mecánica", "Ingeniería de Software", "Tecnologías Móviles", "Redes de Computadores",
            "Sistemas Operativos", "Álgebra Lineal", "Desarrollo y Arquitectura Backend", "Inglés IV"
        )
        val COLORS = listOf("#8600DD", "#F08A00", "#0E9F8E", "#E11D48", "#2563EB", "#16A34A")
        val ANNOUNCEMENTS = listOf(
            Triple("bienestar", "Talleres de manejo del estrés", "Bienestar Universitario abre inscripciones para los talleres de manejo del estrés antes de parciales."),
            Triple("transporte", "Cambio de horario de la ruta del viernes", "La ruta entre sedes del viernes saldrá 30 minutos antes por mantenimiento de la vía."),
            Triple("academico", "Fechas de cancelación de materias", "Recuerda revisar en miportalU las fechas límite para cancelar materias este semestre."),
            Triple("evento", "Semana de la Ingeniería", "Charlas, talleres y la muestra de proyectos estudiantiles en el Campus El Jardín."),
            Triple("bienestar", "Jornada de salud visual", "Revisiones gratuitas para estudiantes en el Campus El Jardín."),
            Triple("academico", "Horario extendido de la biblioteca", "La biblioteca amplía su horario durante la semana de exámenes finales.")
        )
    }
}
