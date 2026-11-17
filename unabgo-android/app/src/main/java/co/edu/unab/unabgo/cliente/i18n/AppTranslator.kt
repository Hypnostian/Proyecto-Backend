package co.edu.unab.unabgo.cliente.i18n

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Idioma de la app: código del backend, nombre en su propio idioma y nombre en español. */
data class AppLanguage(val code: String, val nativeName: String, val spanishName: String)

/** Los textos de la app están escritos en español; los demás idiomas se traducen en el teléfono. */
const val SOURCE_LANGUAGE = "es"

val APP_LANGUAGES = listOf(
    AppLanguage("es", "Español", "Español"),
    AppLanguage("en", "English", "Inglés"),
    AppLanguage("fr", "Français", "Francés"),
    AppLanguage("pt", "Português", "Portugués"),
    AppLanguage("ko", "한국어", "Coreano")
)

fun languageOf(code: String?): AppLanguage = APP_LANGUAGES.firstOrNull { it.code == code } ?: APP_LANGUAGES.first()

/** Estado del traductor para el idioma elegido. */
enum class TranslationStatus {
    /** Español: no hay nada que traducir. */
    OFF,
    /** Revisando si el paquete del idioma ya está en el teléfono. */
    CHECKING,
    /** Descargando el paquete del idioma (solo por wifi). */
    DOWNLOADING,
    /** Falta el paquete y el teléfono no está en wifi: la descarga empieza sola al conectarse. */
    WAITING_WIFI,
    /** Paquete listo: la app se traduce sin conexión. */
    READY,
    /** No se pudo preparar el traductor; se sigue mostrando el español. */
    ERROR
}

/** Motor de traducción (ML Kit en la app, uno falso en las pruebas). */
interface TranslationEngine {
    /** true si el paquete del idioma ya está descargado. */
    suspend fun isReady(language: String): Boolean
    /** Descarga el paquete del idioma; solo se llama cuando hay wifi. */
    suspend fun download(language: String)
    suspend fun translate(language: String, text: String): String
    /** true si el teléfono está conectado por wifi (o cable). */
    fun onWifi(): Boolean
}

/** Traducciones ya hechas, guardadas en el teléfono para que la app abra traducida y sin conexión. */
interface TranslationCache {
    suspend fun load(language: String): Map<String, String>
    suspend fun save(language: String, entries: Map<String, String>)
}

class InMemoryTranslationCache : TranslationCache {
    private val data = mutableMapOf<String, Map<String, String>>()
    override suspend fun load(language: String) = data[language].orEmpty()
    override suspend fun save(language: String, entries: Map<String, String>) { data[language] = entries }
}

/** Traducciones del idioma actual; la interfaz las lee para mostrar cada texto. */
data class TranslationTable(val language: String, val entries: Map<String, String>) {
    fun lookup(text: String): String? = if (language == SOURCE_LANGUAGE) text else entries[text]
}

/**
 * Decide si un texto se traduce: los que no tienen letras (números, horas, créditos), los correos, las
 * direcciones web, las siglas y códigos cortos en mayúsculas y los textos muy largos se dejan igual.
 */
fun shouldTranslate(text: String): Boolean {
    val t = text.trim()
    if (t.isEmpty() || t.length > MAX_TRANSLATABLE_LENGTH) return false
    if (t.none { it.isLetter() }) return false
    if ('@' in t || "://" in t || t.startsWith("www.")) return false
    // Siglas y códigos en mayúsculas (UNAB GO, S1, RUTA1, U0001) se dejan como están.
    if (t.length <= 12 && t.none { it.isLowerCase() }) return false
    return true
}

const val MAX_TRANSLATABLE_LENGTH = 5000

/** Siglas y palabras a las que el traductor les cambia las mayúsculas ("UNAB" sale "Unab", "app" sale "APP"). */
val PROTECTED_TERMS = listOf("UNAB", "app")

/**
 * Nombres que el traductor cambia por otra palabra ("Banu" sale "Bang"): el texto se traduce por partes,
 * alrededor del nombre, y el nombre queda tal cual.
 */
val NAME_TERMS = listOf("Banu")

private fun wordRegex(term: String) = Regex("(?<![\\p{L}])" + Regex.escape(term) + "(?![\\p{L}])", RegexOption.IGNORE_CASE)

/** Traduce [text] sin tocar los nombres de [NAME_TERMS] ni las mayúsculas de [PROTECTED_TERMS]. */
suspend fun translateKeepingNames(text: String, translate: suspend (String) -> String): String {
    val name = NAME_TERMS.firstOrNull { wordRegex(it).containsMatchIn(text) }
    if (name == null) {
        val (prepared, terms) = protectTerms(text)
        return restoreTerms(translate(prepared), terms)
    }
    val out = StringBuilder()
    wordRegex(name).split(text).forEachIndexed { i, part ->
        if (i > 0) out.append(name)
        if (part.none { it.isLetter() }) out.append(part)
        else {
            out.append(part.takeWhile { it.isWhitespace() })
            out.append(translateKeepingNames(part.trim(), translate))
            out.append(part.takeLastWhile { it.isWhitespace() })
        }
    }
    return out.toString()
}

/**
 * Antes de traducir, cada nombre propio va en mayúsculas (el traductor deja quietas las siglas); después se le
 * devuelve su forma original. Devuelve el texto preparado y los nombres que contenía.
 */
fun protectTerms(text: String): Pair<String, List<String>> {
    var out = text
    val found = PROTECTED_TERMS.filter { term ->
        val rx = wordRegex(term)
        if (rx.containsMatchIn(out)) { out = rx.replace(out, term.uppercase()); true } else false
    }
    return out to found
}

fun restoreTerms(translated: String, terms: List<String>): String = terms.fold(translated) { acc, term ->
    acc.replace(wordRegex(term), term)
}

/**
 * Traductor de toda la interfaz. Los textos se piden a medida que aparecen en pantalla, se traducen en el
 * teléfono (ML Kit, sin enviar nada a internet) y se publican en tandas en [table]. Lo traducido se guarda
 * por idioma, así que al volver a abrir la app o sin conexión se muestra de inmediato.
 */
class AppTranslator(
    private val engine: TranslationEngine,
    private val cache: TranslationCache,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val batchWindowMs: Long = 120,
    private val saveDelayMs: Long = 1500
) {
    private val _table = MutableStateFlow(TranslationTable(SOURCE_LANGUAGE, emptyMap()))
    val table: StateFlow<TranslationTable> get() = _table
    private val _status = MutableStateFlow(TranslationStatus.OFF)
    val status: StateFlow<TranslationStatus> get() = _status
    val language: String get() = _table.value.language

    private val requests = Channel<String>(Channel.UNLIMITED)
    private val requested = mutableSetOf<String>()
    /** Textos que el motor no pudo traducir: no se vuelven a pedir hasta cambiar de idioma. */
    private val failed = mutableSetOf<String>()
    private val lock = Any()
    private var prepareJob: Job? = null
    private var saveJob: Job? = null
    private var worker: Job? = null

    /** Cambia el idioma: carga lo ya traducido y prepara el paquete (por wifi si falta). */
    fun setLanguage(code: String) {
        val target = languageOf(code).code
        if (target == _table.value.language && _status.value != TranslationStatus.ERROR) return
        prepareJob?.cancel()
        synchronized(lock) { requested.clear(); failed.clear() }
        if (target == SOURCE_LANGUAGE) {
            _table.value = TranslationTable(SOURCE_LANGUAGE, emptyMap())
            _status.value = TranslationStatus.OFF
            return
        }
        _table.value = TranslationTable(target, emptyMap())
        _status.value = TranslationStatus.CHECKING
        prepareJob = scope.launch {
            val saved = withContext(io) { runCatching { cache.load(target) }.getOrDefault(emptyMap()) }
            if (_table.value.language == target) _table.value = TranslationTable(target, saved + _table.value.entries)
            prepare(target)
        }
    }

    /** Vuelve a intentar la descarga (por ejemplo, cuando el teléfono se conecta a una red wifi). */
    fun retry() {
        val target = _table.value.language
        if (target == SOURCE_LANGUAGE) return
        if (_status.value != TranslationStatus.WAITING_WIFI && _status.value != TranslationStatus.ERROR) return
        prepareJob?.cancel()
        prepareJob = scope.launch { prepare(target) }
    }

    private suspend fun prepare(target: String) {
        try {
            val ready = withContext(io) { engine.isReady(target) }
            if (!ready) {
                if (!engine.onWifi()) {
                    _status.value = TranslationStatus.WAITING_WIFI
                    return
                }
                _status.value = TranslationStatus.DOWNLOADING
                withContext(io) { engine.download(target) }
            }
            if (_table.value.language != target) return
            _status.value = TranslationStatus.READY
            startWorker()
            // Los textos pedidos mientras se descargaba el paquete se traducen ahora.
            val pending = synchronized(lock) { requested.toList().also { requested.clear() } }
            pending.forEach { request(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (_table.value.language == target) _status.value = TranslationStatus.ERROR
        }
    }

    /** Pide la traducción de [text]; mientras llega, la interfaz muestra el original. */
    fun request(text: String) {
        val table = _table.value
        if (table.language == SOURCE_LANGUAGE || !shouldTranslate(text) || table.entries.containsKey(text)) return
        val isNew = synchronized(lock) { text !in failed && requested.add(text) }
        if (isNew && _status.value == TranslationStatus.READY) requests.trySend(text)
    }

    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            val batch = mutableMapOf<String, String>()
            while (true) {
                val first = requests.receive()
                val batchLanguage = _table.value.language
                translateInto(first, batch)
                // Junta lo que siga llegando para publicar una sola vez (menos recomposiciones). La espera tiene
                // límite, la traducción no: así una frase larga nunca se corta a la mitad.
                while (batch.size < MAX_BATCH) {
                    val next = withTimeoutOrNull(batchWindowMs) { requests.receive() } ?: break
                    translateInto(next, batch)
                }
                publish(batch, batchLanguage)
                batch.clear()
            }
        }
    }

    private suspend fun translateInto(text: String, batch: MutableMap<String, String>) {
        val target = _table.value.language
        if (target == SOURCE_LANGUAGE || _status.value != TranslationStatus.READY) {
            // Se vuelve a pedir cuando la pantalla lo muestre otra vez.
            synchronized(lock) { requested.remove(text) }
            return
        }
        val result = try {
            withContext(io) { translateKeepingNames(text) { engine.translate(target, it) } }
        } catch (e: kotlinx.coroutines.CancellationException) {
            synchronized(lock) { requested.remove(text) }
            throw e
        } catch (e: Exception) {
            null
        }
        synchronized(lock) {
            requested.remove(text)
            if (result.isNullOrBlank()) failed.add(text)
        }
        // Si el idioma cambió mientras se traducía, el resultado ya no sirve.
        if (!result.isNullOrBlank() && _table.value.language == target) batch[text] = result
    }

    private companion object {
        /** Máximo de traducciones por publicación: con muchas, la pantalla se va actualizando por partes. */
        const val MAX_BATCH = 40
    }

    private fun publish(batch: Map<String, String>, batchLanguage: String) {
        if (batch.isEmpty()) return
        val current = _table.value
        if (current.language == SOURCE_LANGUAGE || current.language != batchLanguage) return
        _table.value = current.copy(entries = current.entries + batch)
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(saveDelayMs)
            val snapshot = _table.value
            if (snapshot.language != SOURCE_LANGUAGE) withContext(io) { runCatching { cache.save(snapshot.language, snapshot.entries) } }
        }
    }
}
