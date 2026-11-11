package co.edu.unab.unabgo.backend.models

// Contenidos que publican las dependencias: avisos y preguntas frecuentes de Banu (1:N con su autor).

import co.edu.unab.unabgo.backend.database.Announcements
import co.edu.unab.unabgo.backend.database.Faqs
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Announcement(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Announcement>(Announcements) {
        val CATEGORIES = setOf("academico", "bienestar", "transporte", "evento")
    }

    var author by User referencedOn Announcements.author
    var title by Announcements.title
    var body by Announcements.body
    var category by Announcements.category
    var publishedAt by Announcements.publishedAt
    var pushSentAt by Announcements.pushSentAt
    var createdAt by Announcements.createdAt
}

class Faq(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Faq>(Faqs)

    var createdBy by User referencedOn Faqs.createdBy
    var question by Faqs.question
    var keywords by Faqs.keywords
    var answer by Faqs.answer
    var sourceUrl by Faqs.sourceUrl
    var active by Faqs.active
    var updatedAt by Faqs.updatedAt
}
