package co.edu.unab.unabgo.cliente

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import co.edu.unab.unabgo.cliente.data.api.ApiClient
import co.edu.unab.unabgo.cliente.data.repo.*
import co.edu.unab.unabgo.cliente.data.session.DataStoreSessionStore
import co.edu.unab.unabgo.cliente.data.session.DataStoreSettingsStore
import co.edu.unab.unabgo.cliente.data.session.SessionStore
import co.edu.unab.unabgo.cliente.data.session.SettingsStore
import co.edu.unab.unabgo.cliente.i18n.AppTranslator
import co.edu.unab.unabgo.cliente.i18n.DataStoreLanguageStore
import co.edu.unab.unabgo.cliente.i18n.FileTranslationCache
import co.edu.unab.unabgo.cliente.i18n.LanguageController
import co.edu.unab.unabgo.cliente.i18n.MlKitTranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "unabgo_cliente")

/** Contenedor de dependencias de la app (sin librerías de inyección: todo se arma aquí, en un solo lugar). */
class AppContainer(context: Context) {
    private val sessionStore = DataStoreSessionStore(context.dataStore)
    private val settingsStore = DataStoreSettingsStore(context.dataStore, BuildConfig.DEFAULT_BASE_URL)
    private val languageStore = DataStoreLanguageStore(context.dataStore)

    init {
        // Lectura única al arrancar; después todo se sirve desde memoria.
        runBlocking {
            sessionStore.load()
            settingsStore.load()
            languageStore.load()
        }
    }

    /** Ámbito de la app: tareas que deben seguir aunque se cierre la pantalla que las inició. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val session: SessionStore = sessionStore
    val settings: SettingsStore = settingsStore
    val allowCleartext: Boolean = BuildConfig.DEBUG

    private val api = ApiClient(
        baseUrl = { settings.baseUrl.value },
        token = { session.session.value?.token },
        onUnauthorized = { session.clear() }
    )

    val auth: AuthRepository = ApiAuthRepository(api, session)
    val subjects: SubjectRepository = ApiSubjectRepository(api)
    val catalog: CatalogRepository = ApiCatalogRepository(api)
    val studyPlans: StudyPlanRepository = ApiStudyPlanRepository(api)
    val announcements: AnnouncementRepository = ApiAnnouncementRepository(api)
    val bus: BusRepository = ApiBusRepository(api)
    val banu: BanuRepository = ApiBanuRepository(api)
    val faqs: FaqRepository = ApiFaqRepository(api)
    val admin: AdminRepository = ApiAdminRepository(api)
    val summary: SummaryRepository = ApiSummaryRepository(api)

    // Traducción de la interfaz en el teléfono (ML Kit) y el idioma de cada usuario.
    private val translationEngine = MlKitTranslationEngine(context)
    val translator = AppTranslator(translationEngine, FileTranslationCache(File(context.filesDir, "i18n")), appScope)
    val languages = LanguageController(translator, languageStore, session, auth, appScope)

    init {
        languages.start()
        // Si se eligió un idioma sin wifi, la descarga empieza sola al conectarse a una red wifi.
        translationEngine.watchWifi { appScope.launch { translator.retry() } }
    }
}

class UnabGoApp : android.app.Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
