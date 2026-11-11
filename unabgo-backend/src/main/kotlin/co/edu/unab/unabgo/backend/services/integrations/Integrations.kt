package co.edu.unab.unabgo.backend.services.integrations

// Capa de servicios de integración con APIs externas. Los controladores dependen de estas interfaces,
// nunca de un cliente HTTP. En la entrega 3 se agregan las implementaciones reales (Firebase Cloud
// Messaging, plataforma GPS de las Rutas UNAB, proveedores de IA y WordPress de unab.edu.co).

import org.slf4j.LoggerFactory

/** Notificaciones push a los estudiantes (entrega 3: Firebase Cloud Messaging HTTP v1). */
fun interface PushNotifier {
    /** Envía la notificación al tema de la categoría; true si se envió. */
    fun send(topic: String, title: String, body: String): Boolean
}

/** Implementación de desarrollo: registra el envío en el log. */
class LogPushNotifier : PushNotifier {
    private val log = LoggerFactory.getLogger("unabgo.push")
    override fun send(topic: String, title: String, body: String): Boolean {
        log.info("Push al tema {}: {}", topic, title)
        return true
    }
}
