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
    val tokenTtlDays: Long = 30,
    // Servicios externos (entrega 3). Vacío = integración desactivada.
    val gpsFeedUrl: String = "",
    val gpsWebhookSecret: String = "",
    val firebaseProjectId: String = "",
    val firebaseCredentials: String = "",
    val banuLlmUrl: String = "",
    val banuLlmKey: String = "",
    val banuLlmModel: String = "",
    val banuRateLimitPerMinute: Int = 20,
    val unabWpUrl: String = ""
) {
    val isProduction: Boolean get() = env == "production"

    val banuLlmConfigured: Boolean get() = banuLlmUrl.isNotBlank() && banuLlmKey.isNotBlank() && banuLlmModel.isNotBlank()

    /** Nunca incluye claves ni URLs con credenciales: solo dice qué integraciones están activas. */
    override fun toString() = "AppConfig(env=$env, port=$port, db=${dbUrl.substringBefore(';')}, tokenTtlDays=$tokenTtlDays, " +
        "gps=${gpsFeedUrl.isNotBlank()}, webhook=${gpsWebhookSecret.isNotBlank()}, firebase=${firebaseProjectId.isNotBlank()}, " +
        "banuIa=$banuLlmConfigured, wp=${unabWpUrl.isNotBlank()})"

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
                tokenTtlDays = get("TOKEN_TTL_DAYS")?.toLongOrNull()?.coerceIn(1, 365) ?: 30,
                gpsFeedUrl = get("GPS_FEED_URL") ?: "",
                gpsWebhookSecret = get("GPS_WEBHOOK_SECRET") ?: "",
                firebaseProjectId = get("FIREBASE_PROJECT_ID") ?: "",
                firebaseCredentials = get("FIREBASE_CREDENTIALS") ?: "",
                banuLlmUrl = get("BANU_LLM_URL") ?: "",
                banuLlmKey = get("BANU_LLM_KEY") ?: "",
                banuLlmModel = get("BANU_LLM_MODEL") ?: "",
                banuRateLimitPerMinute = get("BANU_RATE_LIMIT_PER_MINUTE")?.toIntOrNull()?.coerceIn(1, 10_000) ?: 20,
                unabWpUrl = get("UNAB_WP_URL") ?: ""
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
