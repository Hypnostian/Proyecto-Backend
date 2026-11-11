package co.edu.unab.unabgo.backend.database

import co.edu.unab.unabgo.backend.config.AppConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.io.Closeable

/** Conexión a la base de datos (pool HikariCP) registrada en Exposed. */
class AppDatabase(val dataSource: HikariDataSource, val database: Database) : Closeable {
    override fun close() = dataSource.close()
}

object DatabaseFactory {

    fun connect(config: AppConfig): AppDatabase {
        val dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.dbUrl
            username = config.dbUser
            password = config.dbPassword
            maximumPoolSize = 10
            isAutoCommit = false
            poolName = "unabgo"
            validate()
        })
        val database = Database.connect(dataSource)
        TransactionManager.defaultDatabase = database
        return AppDatabase(dataSource, database)
    }

    private fun flyway(db: AppDatabase, allowClean: Boolean): Flyway = Flyway.configure()
        .dataSource(db.dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(!allowClean)
        .load()

    /** php artisan migrate */
    fun migrate(db: AppDatabase): Int = flyway(db, allowClean = false).migrate().migrationsExecuted

    /** php artisan migrate:fresh: borra todas las tablas y vuelve a ejecutar las migraciones. */
    fun fresh(db: AppDatabase): Int {
        val flyway = flyway(db, allowClean = true)
        flyway.clean()
        return flyway.migrate().migrationsExecuted
    }
}

/** Ejecuta un bloque en una transacción sin bloquear el hilo de la petición. */
suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T = newSuspendedTransaction(Dispatchers.IO) { block() }
