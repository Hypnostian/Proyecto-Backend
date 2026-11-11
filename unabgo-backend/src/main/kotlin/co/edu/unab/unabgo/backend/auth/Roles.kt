package co.edu.unab.unabgo.backend.auth

import co.edu.unab.unabgo.backend.auth.UserRole.ADMINISTRADOR
import co.edu.unab.unabgo.backend.auth.UserRole.ESTUDIANTE
import co.edu.unab.unabgo.backend.auth.UserRole.GESTOR

/** Los tres perfiles del sistema (tabla roles). */
enum class UserRole(val description: String) {
    ESTUDIANTE("Estudiante con correo institucional: gestiona sus materias, horario y notas."),
    GESTOR("Funcionario de una dependencia: publica avisos, mantiene las preguntas de Banu y las rutas."),
    ADMINISTRADOR("Responsable técnico: gestiona usuarios y roles y sincroniza el catálogo de programas.")
}

/**
 * Habilidades del sistema (equivalente a los Gates de Laravel). Cada una dice qué roles la tienen;
 * los tokens se emiten con las habilidades del rol del usuario.
 */
enum class Ability(vararg val roles: UserRole) {
    MANAGE_OWN_PROFILE(ESTUDIANTE, GESTOR, ADMINISTRADOR),
    MANAGE_FAVORITES(ESTUDIANTE, GESTOR, ADMINISTRADOR),
    MANAGE_ACADEMIC(ESTUDIANTE),
    MANAGE_ANNOUNCEMENTS(GESTOR, ADMINISTRADOR),
    MANAGE_FAQS(GESTOR, ADMINISTRADOR),
    MANAGE_BUS_ROUTES(GESTOR, ADMINISTRADOR),
    MANAGE_USERS(ADMINISTRADOR),
    SYNC_PROGRAMS(ADMINISTRADOR);

    val key: String get() = name.lowercase().replace('_', '-')

    companion object {
        fun forRole(role: UserRole): List<Ability> = entries.filter { role in it.roles }
        fun fromKey(key: String): Ability? = entries.firstOrNull { it.key == key }
    }
}

/** Usuario autenticado en la petición actual (lo que devuelve el token). */
data class AuthUser(
    val id: Long,
    val name: String,
    val email: String,
    val role: UserRole,
    val tokenId: Long,
    val abilities: Set<Ability>
) {
    /** Equivalente a $user->tokenCan('...') de Sanctum. */
    fun tokenCan(ability: Ability): Boolean = ability in abilities
}
