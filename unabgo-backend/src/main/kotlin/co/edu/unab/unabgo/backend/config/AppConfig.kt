package co.edu.unab.unabgo.backend.config

import java.io.File

/**
 * Configuración leída de variables de entorno y, si existe, del archivo .env (equivalente al .env de
 * Laravel). Las variables del sistema tienen prioridad sobre el archivo.
 */
data class AppConfig(
    val env: String = "local",
    val port: Int = 8080,
    val dbUrl: String = DEFAULT_DB_URL,
    val dbUser: String = "sa",
    val dbPassword: String = "",
    val tokenTtlDays: Long = 30
) {
    val isProduction: Boolean get() = env == "production"

    override fun toString() = "AppConfig(env=$env, port=$port, db=${dbUrl.substringBefore(';')}, tokenTtlDays=$tokenTtlDays)"

    companion object {
        const val DEFAULT_DB_URL =
            "jdbc:h2:file:./data/unabgo;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;AUTO_SERVER=TRUE"

        fun load(dotEnv: File = File(".env"), environment: Map<String, String> = System.getenv()): AppConfig {
            val file = readDotEnv(dotEnv)
            fun get(key: String): String? = environment[key]?.takeIf { it.isNotBlank() } ?: file[key]?.takeIf { it.isNotBlank() }
            return AppConfig(
                env = get("APP_ENV") ?: "local",
                port = get("APP_PORT")?.toIntOrNull() ?: 8080,
                dbUrl = get("DB_URL") ?: DEFAULT_DB_URL,
                dbUser = get("DB_USER") ?: "sa",
                dbPassword = get("DB_PASSWORD") ?: "",
                tokenTtlDays = get("TOKEN_TTL_DAYS")?.toLongOrNull()?.coerceIn(1, 365) ?: 30
            )
        }

        /** Lee líneas CLAVE=valor; ignora comentarios y líneas vacías. */
        fun readDotEnv(file: File): Map<String, String> {
            if (!file.isFile) return emptyMap()
            return file.readLines().mapNotNull { raw ->
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#") || '=' !in line) return@mapNotNull null
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').substringBefore(" #").trim().removeSurrounding("\"")
                key to value
            }.toMap()
        }
    }
}
