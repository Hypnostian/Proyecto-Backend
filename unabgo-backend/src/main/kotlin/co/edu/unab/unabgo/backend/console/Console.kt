package co.edu.unab.unabgo.backend.console

// Comandos de consola (equivalentes a php artisan). Se ejecutan con las tareas de Gradle:
//   ./gradlew migrate        (migrate)
//   ./gradlew migrateFresh   (migrate:fresh --seed)
//   ./gradlew dbSeed         (db:seed)

import co.edu.unab.unabgo.backend.config.AppConfig
import co.edu.unab.unabgo.backend.database.AccessTokens
import co.edu.unab.unabgo.backend.database.Announcements
import co.edu.unab.unabgo.backend.database.AppDatabase
import co.edu.unab.unabgo.backend.database.BusRoutes
import co.edu.unab.unabgo.backend.database.BusStops
import co.edu.unab.unabgo.backend.database.DatabaseFactory
import co.edu.unab.unabgo.backend.database.FavoriteRoutes
import co.edu.unab.unabgo.backend.database.Faqs
import co.edu.unab.unabgo.backend.database.GradeCategories
import co.edu.unab.unabgo.backend.database.GradeItems
import co.edu.unab.unabgo.backend.database.Programs
import co.edu.unab.unabgo.backend.database.Roles
import co.edu.unab.unabgo.backend.database.RouteStops
import co.edu.unab.unabgo.backend.database.ScheduleBlocks
import co.edu.unab.unabgo.backend.database.StudentProfiles
import co.edu.unab.unabgo.backend.database.Subjects
import co.edu.unab.unabgo.backend.database.Users
import co.edu.unab.unabgo.backend.database.seeders.DatabaseSeeder
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.TimeZone
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val config = AppConfig.load()
    val command = args.firstOrNull() ?: "help"
    println("UNAB GO! backend: $command ($config)")
    DatabaseFactory.connect(config).use { db ->
        when (command) {
            "migrate" -> println("Migraciones ejecutadas: ${DatabaseFactory.migrate(db)}")
            "migrate:fresh" -> {
                if (config.isProduction) {
                    System.err.println("migrate:fresh está deshabilitado en producción.")
                    exitProcess(1)
                }
                println("Tablas borradas. Migraciones ejecutadas: ${DatabaseFactory.fresh(db)}")
                if ("--seed" in args) seed(db)
            }
            "db:seed" -> seed(db)
            else -> {
                println("Comandos: migrate | migrate:fresh [--seed] | db:seed")
                exitProcess(if (command == "help") 0 else 1)
            }
        }
    }
}

private fun seed(db: AppDatabase) {
    DatabaseSeeder().run()
    println("Datos sembrados:")
    countRows(db).forEach { (table, count) -> println("  %-18s %5d".format(table, count)) }
}

/** Filas por tabla, para mostrar el resultado de los seeders. */
fun countRows(db: AppDatabase): Map<String, Long> = transaction(db.database) {
    listOf(
        Roles, Users, StudentProfiles, AccessTokens, Programs, Subjects, ScheduleBlocks, GradeCategories,
        GradeItems, BusRoutes, BusStops, RouteStops, FavoriteRoutes, Announcements, Faqs
    ).associate { it.tableName to it.selectAll().count() }
}
