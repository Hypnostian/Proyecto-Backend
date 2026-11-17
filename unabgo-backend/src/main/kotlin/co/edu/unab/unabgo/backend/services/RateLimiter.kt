package co.edu.unab.unabgo.backend.services

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/** Límite sencillo por clave (la IP) en una ventana fija de un minuto, en memoria. */
class RateLimiter(
    private val maxPerWindow: Int,
    private val windowMillis: Long = 60_000,
    private val clock: Clock = Clock.systemUTC()
) {
    private class Window(val startedAt: Long, var count: Int)

    private val windows = ConcurrentHashMap<String, Window>()

    /** true si la petición cabe en el límite; false si ya se pasó (responder 429). */
    fun allow(key: String): Boolean {
        val now = clock.millis()
        if (windows.size > MAX_KEYS) windows.entries.removeIf { now - it.value.startedAt >= windowMillis }
        val window = windows.compute(key) { _, w ->
            if (w == null || now - w.startedAt >= windowMillis) Window(now, 1) else w.also { it.count++ }
        }!!
        return window.count <= maxPerWindow
    }

    private companion object {
        const val MAX_KEYS = 10_000
    }
}
