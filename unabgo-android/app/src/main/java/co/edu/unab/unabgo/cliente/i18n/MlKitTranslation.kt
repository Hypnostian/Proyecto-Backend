package co.edu.unab.unabgo.cliente.i18n

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import co.edu.unab.unabgo.cliente.data.api.ApiJson
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Espera el resultado de una tarea de Google Play Services sin bloquear el hilo. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/**
 * Traductor de ML Kit: traduce en el teléfono, sin enviar los textos a ningún servidor. Cada idioma necesita un
 * paquete (unos 30 MB) que se descarga una sola vez y solo por wifi; después funciona sin conexión.
 */
class MlKitTranslationEngine(context: Context) : TranslationEngine {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val clients = mutableMapOf<String, Translator>()

    private fun mlKitCode(language: String): String = when (language) {
        "en" -> TranslateLanguage.ENGLISH
        "fr" -> TranslateLanguage.FRENCH
        "pt" -> TranslateLanguage.PORTUGUESE
        "ko" -> TranslateLanguage.KOREAN
        else -> TranslateLanguage.SPANISH
    }

    private fun client(language: String): Translator = synchronized(clients) {
        clients.getOrPut(language) {
            Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.SPANISH).setTargetLanguage(mlKitCode(language)).build()
            )
        }
    }

    override suspend fun isReady(language: String): Boolean {
        val manager = RemoteModelManager.getInstance()
        val source = manager.isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.SPANISH).build()).await()
        val target = manager.isModelDownloaded(TranslateRemoteModel.Builder(mlKitCode(language)).build()).await()
        return source && target
    }

    override suspend fun download(language: String) {
        client(language).downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build()).await()
    }

    override suspend fun translate(language: String, text: String): String = client(language).translate(text).await()

    override fun onWifi(): Boolean {
        val caps = connectivity?.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** Avisa cada vez que el teléfono queda conectado por wifi, para retomar una descarga pendiente. */
    fun watchWifi(onWifi: () -> Unit) {
        connectivity?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) onWifi()
            }
        })
    }
}

private const val CACHE_VERSION = 3

/** Traducciones guardadas como JSON en la memoria interna de la app, un archivo por idioma. */
class FileTranslationCache(private val dir: File) : TranslationCache {
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    // La versión cambia cuando cambia la forma de traducir, para no mostrar traducciones viejas guardadas.
    private fun file(language: String) = File(dir, "traducciones_${CACHE_VERSION}_$language.json")

    override suspend fun load(language: String): Map<String, String> {
        val f = file(language)
        if (!f.exists()) return emptyMap()
        return runCatching { ApiJson.decodeFromString(serializer, f.readText()) }.getOrDefault(emptyMap())
    }

    override suspend fun save(language: String, entries: Map<String, String>) {
        dir.mkdirs()
        val tmp = File(dir, "traducciones_${CACHE_VERSION}_$language.tmp")
        tmp.writeText(ApiJson.encodeToString(serializer, entries))
        if (!tmp.renameTo(file(language))) {
            file(language).delete()
            tmp.renameTo(file(language))
        }
    }
}
