package co.edu.unab.unabgo.backend.auth

// Gates y Policies (como en Laravel): el Gate responde si el rol tiene una habilidad; cada Policy
// revisa además la propiedad del recurso. Si no se cumple, la API responde 403 Forbidden.

import co.edu.unab.unabgo.backend.http.ForbiddenException
import co.edu.unab.unabgo.backend.models.Announcement
import co.edu.unab.unabgo.backend.models.Subject
import co.edu.unab.unabgo.backend.models.User

object Gate {
    fun allows(user: AuthUser, ability: Ability): Boolean = user.role in ability.roles && user.tokenCan(ability)

    fun denies(user: AuthUser, ability: Ability): Boolean = !allows(user, ability)

    /** Equivalente a Gate::authorize(): lanza 403 si no tiene la habilidad. */
    fun authorize(user: AuthUser, ability: Ability) {
        if (denies(user, ability)) throw ForbiddenException("Tu rol no tiene permiso para esta acción.")
    }
}

/** Equivalente a $this->authorize(...) en un controlador. */
fun authorize(allowed: Boolean, message: String = "No tienes permiso sobre este recurso.") {
    if (!allowed) throw ForbiddenException(message)
}

/** Un estudiante solo ve y modifica sus propias materias (ni el administrador puede leer notas ajenas). */
object SubjectPolicy {
    fun viewAny(user: AuthUser) = Gate.allows(user, Ability.MANAGE_ACADEMIC)
    fun create(user: AuthUser) = Gate.allows(user, Ability.MANAGE_ACADEMIC)
    fun view(user: AuthUser, subject: Subject) = Gate.allows(user, Ability.MANAGE_ACADEMIC) && subject.user.id.value == user.id
    fun update(user: AuthUser, subject: Subject) = view(user, subject)
    fun delete(user: AuthUser, subject: Subject) = view(user, subject)
}

/** Gestores y administradores publican avisos; solo el autor o un administrador los edita o retira. */
object AnnouncementPolicy {
    fun create(user: AuthUser) = Gate.allows(user, Ability.MANAGE_ANNOUNCEMENTS)
    fun update(user: AuthUser, announcement: Announcement) =
        create(user) && (user.role == UserRole.ADMINISTRADOR || announcement.author.id.value == user.id)
    fun delete(user: AuthUser, announcement: Announcement) = update(user, announcement)
}

/** Las preguntas frecuentes de Banu las administran los gestores y el administrador. */
object FaqPolicy {
    fun manage(user: AuthUser) = Gate.allows(user, Ability.MANAGE_FAQS)
}

/** Las rutas y paradas las mantienen los gestores y el administrador. */
object BusRoutePolicy {
    fun update(user: AuthUser) = Gate.allows(user, Ability.MANAGE_BUS_ROUTES)
}

/** Solo el administrador gestiona usuarios, y no puede quitarse el rol a sí mismo. */
object UserPolicy {
    fun viewAny(user: AuthUser) = Gate.allows(user, Ability.MANAGE_USERS)
    fun changeRole(user: AuthUser, target: User) = viewAny(user) && target.id.value != user.id
}
