package co.edu.unab.unabgo.backend.services.integrations

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Un programa publicado en unab.edu.co (tipo de contenido "project" de WordPress, URL /programas/{slug}/). */
data class WpProgram(
    val slug: String,
    val title: String,
    val link: String,
    val excerpt: String,
    /** Nombres de las categorías (facultad o unidad: "Facultad de Ingeniería", "UNAB Tec"...). */
    val categories: List<String>,
    /** Etiquetas como slug (pregrado, posgrado, doctorado, presencial, virtual, dual...). */
    val tags: List<String>
)

/** Sitio web de la universidad (API REST de WordPress, la URL base está en UNAB_WP_URL). */
interface UnabWebService {
    suspend fun fetchPrograms(): List<WpProgram>
}

class WordPressUnabWebService(private val client: HttpClient, baseUrl: String) : UnabWebService {

    private val base = baseUrl.trimEnd('/')

    override suspend fun fetchPrograms(): List<WpProgram> {
        val categories = terms("project_category", "id,name").associate { it.long("id") to it.str("name") }
        val tags = terms("project_tag", "id,slug").associate { it.long("id") to it.str("slug") }
        return pages("project", "id,slug,link,title,excerpt,project_category,project_tag", "&status=publish").mapNotNull { p ->
            val slug = p.str("slug").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            WpProgram(
                slug = slug,
                title = decodeHtml(p.rendered("title")).trim(),
                link = p.str("link"),
                excerpt = decodeHtml(p.rendered("excerpt").replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim(),
                categories = p.ids("project_category").mapNotNull { categories[it] }.map(::decodeHtml),
                tags = p.ids("project_tag").mapNotNull { tags[it] }
            )
        }
    }

    private suspend fun terms(type: String, fields: String) = pages(type, fields, "")

    /** Recorre las páginas de 100 en 100; WordPress responde 400 cuando se pide una página que no existe. */
    private suspend fun pages(type: String, fields: String, extra: String): List<JsonObject> {
        val all = mutableListOf<JsonObject>()
        for (page in 1..MAX_PAGES) {
            val response = try {
                client.get("$base/$type?per_page=$PER_PAGE&page=$page&_fields=$fields$extra") { header("Accept", "application/json") }
            } catch (e: Exception) {
                throw ExternalServiceException("No se pudo consultar unab.edu.co.", e)
            }
            if (response.status == HttpStatusCode.BadRequest && page > 1) break
            if (!response.status.isSuccess()) throw ExternalServiceException("unab.edu.co respondió HTTP ${response.status.value}.")
            val items = try {
                Json.parseToJsonElement(response.bodyAsText()).jsonArray
            } catch (e: Exception) {
                throw ExternalServiceException("unab.edu.co devolvió un formato inesperado.", e)
            }
            all += items.filterIsInstance<JsonObject>()
            if (items.size < PER_PAGE) break
        }
        return all
    }

    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.content.orEmpty()
    private fun JsonObject.long(key: String) = str(key).toLongOrNull() ?: -1L
    private fun JsonObject.rendered(key: String) = (this[key] as? JsonObject)?.get("rendered")?.jsonPrimitive?.content.orEmpty()
    private fun JsonObject.ids(key: String) = ((this[key] as? JsonArray) ?: JsonArray(emptyList())).mapNotNull { (it as? JsonPrimitive)?.content?.toLongOrNull() }

    companion object {
        private const val PER_PAGE = 100
        private const val MAX_PAGES = 30

        private val ENTITIES = mapOf(
            "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&apos;" to "'", "&nbsp;" to " ",
            "&#8211;" to "–", "&#8212;" to "—", "&#8217;" to "'", "&#8216;" to "'", "&#8220;" to "\"", "&#8221;" to "\""
        )

        /** WordPress entrega los títulos con entidades HTML (&amp;, &#8211;...). */
        fun decodeHtml(text: String): String {
            var out = text
            ENTITIES.forEach { (k, v) -> out = out.replace(k, v) }
            return Regex("&#(x?)([0-9a-fA-F]+);").replace(out) { m ->
                val code = m.groupValues[2].toIntOrNull(if (m.groupValues[1].isEmpty()) 10 else 16)
                if (code != null && code in 32..0x10FFFF) String(Character.toChars(code)) else m.value
            }
        }
    }
}
