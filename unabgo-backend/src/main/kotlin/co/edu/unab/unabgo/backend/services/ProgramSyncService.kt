package co.edu.unab.unabgo.backend.services

// Sincroniza el catálogo de programas con unab.edu.co (WordPress): crea los que faltan y actualiza
// nombre, facultad, enlace y modalidad de los que ya existen. No toca precios ni código SNIES.

import co.edu.unab.unabgo.backend.database.Programs
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.services.integrations.WpProgram
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

@Serializable
data class SyncResult(val fetched: Int, val created: Int, val updated: Int, val unchanged: Int)

class ProgramSyncService {

    /** Se llama dentro de una transacción. */
    fun sync(remote: List<WpProgram>): SyncResult {
        var created = 0
        var updated = 0
        var unchanged = 0
        remote.distinctBy { it.slug }.forEach { wp ->
            val slug = wp.slug.take(160)
            val name = wp.title.ifBlank { slug }.take(200)
            val faculty = wp.categories.joinToString("; ").take(150).ifBlank { null }
            val modality = modalityOf(wp)?.take(40)
            val url = wp.link.ifBlank { "https://unab.edu.co/programas/${wp.slug}/" }.take(300)
            val existing = Program.find { Programs.slug eq slug }.singleOrNull()
            if (existing == null) {
                Program.new {
                    this.slug = slug
                    this.name = name
                    this.faculty = faculty ?: "Sin facultad"
                    this.level = levelOf(wp)
                    this.modality = modality
                    this.url = url
                }
                created++
            } else {
                var changed = false
                if (existing.name != name) { existing.name = name; changed = true }
                if (existing.url != url) { existing.url = url; changed = true }
                if (faculty != null && existing.faculty != faculty) { existing.faculty = faculty; changed = true }
                if (modality != null && existing.modality != modality) { existing.modality = modality; changed = true }
                if (changed) updated++ else unchanged++
            }
        }
        return SyncResult(remote.size, created, updated, unchanged)
    }

    private fun levelOf(wp: WpProgram): String = when {
        "doctorado" in wp.tags -> "Doctorado"
        "posgrado" in wp.tags -> "Posgrado"
        "pregrado" in wp.tags -> "Profesional"
        "tecnologias" in wp.tags -> "Tecnología"
        else -> "Programa"
    }

    private fun modalityOf(wp: WpProgram): String? = when {
        "presencial" in wp.tags -> "Presencial"
        "virtual" in wp.tags -> "Virtual"
        "dual" in wp.tags -> "Dual"
        else -> wp.excerpt.takeIf { it.isNotBlank() && it.length <= 40 }
    }
}
