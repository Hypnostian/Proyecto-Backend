package co.edu.unab.unabgo.backend.models

// Modelos de identidad: roles, usuarios, perfil del estudiante (1:1) y tokens de acceso (1:N).

import co.edu.unab.unabgo.backend.auth.UserRole
import co.edu.unab.unabgo.backend.database.AccessTokens
import co.edu.unab.unabgo.backend.database.Announcements
import co.edu.unab.unabgo.backend.database.FavoriteRoutes
import co.edu.unab.unabgo.backend.database.Faqs
import co.edu.unab.unabgo.backend.database.Roles
import co.edu.unab.unabgo.backend.database.StudentProfiles
import co.edu.unab.unabgo.backend.database.Subjects
import co.edu.unab.unabgo.backend.database.Users
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Role(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<Role>(Roles) {
        fun of(role: UserRole): Role = find { Roles.name eq role.name }.single()
    }

    var name by Roles.name
    var description by Roles.description

    /** 1:N — usuarios con este rol. */
    val users by User referrersOn Users.role

    val asEnum: UserRole get() = UserRole.valueOf(name)
}

class User(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<User>(Users) {
        fun findByEmail(email: String): User? = find { Users.email eq email.trim().lowercase() }.singleOrNull()
    }

    var role by Role referencedOn Users.role
    var name by Users.name
    var email by Users.email
    var passwordHash by Users.passwordHash
    var googleUid by Users.googleUid
    var active by Users.active
    var createdAt by Users.createdAt
    var updatedAt by Users.updatedAt

    /** 1:1 — perfil académico (solo estudiantes). */
    val profile by StudentProfile optionalBackReferencedOn StudentProfiles.user

    /** 1:N — materias, tokens, avisos y preguntas frecuentes del usuario. */
    val subjects by Subject referrersOn Subjects.user
    val tokens by AccessToken referrersOn AccessTokens.user
    val announcements by Announcement referrersOn Announcements.author
    val faqs by Faq referrersOn Faqs.createdBy

    /** N:M — rutas de bus favoritas (tabla favorite_routes). */
    var favoriteRoutes by BusRoute via FavoriteRoutes

    val userRole: UserRole get() = role.asEnum
}

class StudentProfile(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StudentProfile>(StudentProfiles)

    var user by User referencedOn StudentProfiles.user
    var program by Program optionalReferencedOn StudentProfiles.program
    var studentCode by StudentProfiles.studentCode
    var semester by StudentProfiles.semester
    var language by StudentProfiles.language
    var reminderMinutes by StudentProfiles.reminderMinutes
}

class AccessToken(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<AccessToken>(AccessTokens)

    var user by User referencedOn AccessTokens.user
    var name by AccessTokens.name
    var tokenHash by AccessTokens.tokenHash
    var abilities by AccessTokens.abilities
    var lastUsedAt by AccessTokens.lastUsedAt
    var expiresAt by AccessTokens.expiresAt
    var createdAt by AccessTokens.createdAt
}
