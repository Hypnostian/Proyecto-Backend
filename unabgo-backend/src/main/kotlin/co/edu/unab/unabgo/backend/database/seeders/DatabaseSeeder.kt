package co.edu.unab.unabgo.backend.database.seeders

// Seeders (como el DatabaseSeeder de Laravel): llenan la base con los datos de referencia (roles,
// programas reales de la UNAB, rutas, preguntas frecuentes reales de Banu) y con datos de prueba de
// las factories (usuarios, materias, notas y avisos). Se ejecutan con ./gradlew migrateFresh.

import co.edu.unab.unabgo.backend.auth.UserRole
import co.edu.unab.unabgo.backend.database.RouteStops
import co.edu.unab.unabgo.backend.database.factories.Factories
import co.edu.unab.unabgo.backend.models.BusRoute
import co.edu.unab.unabgo.backend.models.BusStop
import co.edu.unab.unabgo.backend.models.Faq
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.models.Role
import co.edu.unab.unabgo.backend.models.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.ChronoUnit

interface Seeder {
    fun run()
}

/** Seeder principal: orden de ejecución de todos los seeders, en una sola transacción. */
class DatabaseSeeder(private val factories: Factories = Factories()) : Seeder {

    data class Summary(val counts: Map<String, Long>)

    override fun run() {
        transaction {
            RoleSeeder().run()
            ProgramSeeder().run()
            UserSeeder(factories).run()
            BusSeeder(factories).run()
            AcademicSeeder(factories).run()
            ContentSeeder(factories).run()
        }
    }
}

class RoleSeeder : Seeder {
    override fun run() {
        UserRole.entries.forEach { role ->
            Role.new {
                name = role.name
                description = role.description
            }
        }
    }
}

/** Los 228 programas reales de unab.edu.co (datos públicos verificados por la app UNAB GO!). */
class ProgramSeeder : Seeder {
    @Serializable
    private data class ProgramSeed(
        val slug: String, val name: String, val faculty: String, val level: String,
        val modality: String? = null, val price_cop: Long? = null, val snies: String? = null, val url: String
    )

    override fun run() {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        resourceJson<List<ProgramSeed>>("seed/programs.json").forEach { p ->
            Program.new {
                slug = p.slug.take(160)
                name = p.name.take(200)
                faculty = p.faculty.take(150)
                level = p.level.take(40)
                modality = p.modality?.take(40)
                priceCop = p.price_cop
                snies = p.snies?.take(20)
                url = p.url.take(300)
                verifiedAt = if (p.price_cop != null) now else null
            }
        }
    }
}

/** Usuarios de demostración de cada rol y estudiantes de prueba con su perfil (1:1). */
class UserSeeder(private val f: Factories) : Seeder {
    override fun run() {
        f.user(UserRole.ADMINISTRADOR, "Administrador UNAB GO", ADMIN_EMAIL)
        f.user(UserRole.GESTOR, "Bienestar Universitario", BIENESTAR_EMAIL)
        f.user(UserRole.GESTOR, "Transporte UNAB", TRANSPORTE_EMAIL)

        val programs = Program.all().filter { it.level == "Profesional" }.ifEmpty { Program.all().toList() }
        val demo = f.user(UserRole.ESTUDIANTE, "Estudiante de Prueba", STUDENT_EMAIL)
        f.studentProfile(demo, programs.firstOrNull { it.name.contains("Sistemas") } ?: programs.firstOrNull())
        repeat(RANDOM_STUDENTS) {
            val student = f.user(UserRole.ESTUDIANTE)
            f.studentProfile(student, programs.getOrNull(f.random.nextInt(programs.size.coerceAtLeast(1))))
        }
    }

    companion object {
        const val ADMIN_EMAIL = "demo.admin@unab.edu.co"
        const val BIENESTAR_EMAIL = "demo.bienestar@unab.edu.co"
        const val TRANSPORTE_EMAIL = "demo.transporte@unab.edu.co"
        const val STUDENT_EMAIL = "demo.estudiante@unab.edu.co"
        const val RANDOM_STUDENTS = 11
    }
}

/** Las rutas que reporta el GPS de las Rutas UNAB, las sedes y su relación N:M. */
class BusSeeder(private val f: Factories) : Seeder {
    override fun run() {
        val jardin = stop("Campus El Jardín", "7.116824", "-73.105580")
        val csu = stop("CSU (Centro de Servicios Universitarios)", "7.110201", "-73.105207")
        val bosque = stop("Campus El Bosque", "7.073290", "-73.109761")
        val routes = listOf(
            route("RUTA1", "Ruta 1", "#8600DD"), route("RUTA2", "Ruta 2", "#F08A00"),
            route("RUTA02", "Ruta 02", "#0E9F8E"), route("RUTA3", "Ruta 3", "#E11D48")
        )
        routes.forEach { r ->
            listOf(jardin, csu, bosque).forEachIndexed { i, s ->
                RouteStops.insert {
                    it[route] = r.id
                    it[stop] = s.id
                    it[sortOrder] = i.toShort()
                }
            }
        }
        // Algunos estudiantes marcan rutas favoritas (N:M)
        User.all().filter { it.userRole == UserRole.ESTUDIANTE }.forEach { u ->
            u.favoriteRoutes = org.jetbrains.exposed.sql.SizedCollection(routes.shuffled(f.random).take(1 + f.random.nextInt(2)))
        }
    }

    private fun stop(name: String, lat: String, lon: String) = BusStop.new {
        this.name = name
        this.lat = BigDecimal(lat)
        this.lon = BigDecimal(lon)
        isCampus = true
    }

    private fun route(code: String, name: String, color: String) = BusRoute.new {
        this.code = code
        this.name = name
        this.color = color
        gpsDeviceId = null
        active = true
    }
}

/** Para cada estudiante: 4 materias con horario sin choques, 3 cortes y sus notas. */
class AcademicSeeder(private val f: Factories) : Seeder {
    private val slots = listOf(LocalTime.of(7, 0), LocalTime.of(9, 0), LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(16, 0))

    override fun run() {
        User.all().filter { it.userRole == UserRole.ESTUDIANTE }.forEach { student ->
            Factories.SUBJECTS.shuffled(f.random).take(4).forEachIndexed { i, name ->
                val subject = f.subject(student, name)
                // Cada materia usa una franja distinta, así nunca se cruzan
                f.scheduleBlock(subject, day = i % 5 + 1, start = slots[i])
                f.scheduleBlock(subject, day = (i + 2) % 5 + 1, start = slots[i])
                val corte1 = f.gradeCategory(subject, "Corte 1", 30, 1)
                f.gradeItem(corte1, "Parcial 1")
                f.gradeItem(corte1, "Quiz 1")
                val corte2 = f.gradeCategory(subject, "Corte 2", 30, 2)
                f.gradeItem(corte2, "Parcial 2")
                f.gradeItem(corte2, "Taller", graded = false)
                val corte3 = f.gradeCategory(subject, "Corte 3", 40, 3)
                f.gradeItem(corte3, "Examen final", graded = false)
                f.gradeItem(corte3, "Proyecto", graded = false)
            }
        }
    }
}

/** Avisos de prueba de los gestores y las 58 preguntas frecuentes reales de Banu. */
class ContentSeeder(private val f: Factories) : Seeder {
    @Serializable
    private data class FaqSeed(val question: String, val keywords: String, val answer: String, val source_url: String)

    override fun run() {
        val bienestar = User.findByEmail(UserSeeder.BIENESTAR_EMAIL)!!
        val transporte = User.findByEmail(UserSeeder.TRANSPORTE_EMAIL)!!
        repeat(5) { f.announcement(bienestar, published = it < 4) }
        repeat(3) { f.announcement(transporte, published = it < 2) }

        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        resourceJson<List<FaqSeed>>("seed/faqs.json").forEach { q ->
            Faq.new {
                createdBy = bienestar
                question = q.question.take(200)
                keywords = q.keywords
                answer = q.answer
                sourceUrl = q.source_url.take(300)
                active = true
                updatedAt = now
            }
        }
    }
}

private val seedJson = Json { ignoreUnknownKeys = true }

private inline fun <reified T> resourceJson(path: String): T {
    val text = Thread.currentThread().contextClassLoader.getResource(path)?.readText(Charsets.UTF_8)
        ?: error("No se encontró el recurso $path")
    return seedJson.decodeFromString(text)
}
