package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.auth.UserRole
import co.edu.unab.unabgo.backend.console.countRows
import co.edu.unab.unabgo.backend.database.seeders.UserSeeder
import co.edu.unab.unabgo.backend.models.BusRoute
import co.edu.unab.unabgo.backend.models.Role
import co.edu.unab.unabgo.backend.models.User
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** migrate:fresh --seed y las relaciones de los modelos (1:1, 1:N y N:M). */
class DatabaseTest {

    @Test
    fun migrateFreshSeedLlenaTodasLasTablas() {
        TestDb.fresh(seed = true).use { db ->
            val counts = countRows(db)
            assertEquals(3, counts["roles"])
            assertEquals(15, counts["users"])
            assertEquals(228, counts["programs"])
            assertEquals(58, counts["faqs"])
            listOf("student_profiles", "subjects", "schedule_blocks", "grade_categories", "grade_items",
                "bus_routes", "bus_stops", "route_stops", "favorite_routes", "announcements").forEach {
                assertTrue(counts[it]!! > 0, "la tabla $it quedó vacía")
            }
        }
    }

    @Test
    fun relacionesDeLosModelos() {
        TestDb.fresh(seed = true).use { db ->
            transaction(db.database) {
                val student = User.findByEmail(UserSeeder.STUDENT_EMAIL)!!
                // 1:N rol -> usuarios y usuario -> materias -> bloques, cortes -> notas
                assertEquals(UserRole.ESTUDIANTE, student.userRole)
                assertTrue(Role.of(UserRole.GESTOR).users.count() == 2L)
                val subject = student.subjects.first()
                assertEquals(2, subject.blocks.count().toInt())
                assertEquals(listOf(30.0, 30.0, 40.0), subject.categories.sortedBy { it.sortOrder }.map { it.weight.toDouble() })
                assertEquals(2, subject.categories.first().items.count().toInt())
                // 1:1 usuario -> perfil
                assertNotNull(student.profile)
                assertEquals(student.id, student.profile!!.user.id)
                // N:M rutas <-> paradas y usuarios <-> rutas favoritas
                val route = BusRoute.all().first()
                assertEquals(3, route.stops.count().toInt())
                assertTrue(student.favoriteRoutes.count() >= 1)
                assertTrue(route.stops.first().routes.count() == 4L)
            }
        }
    }
}
