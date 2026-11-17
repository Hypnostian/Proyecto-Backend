package co.edu.unab.unabgo.cliente.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import co.edu.unab.unabgo.cliente.data.session.Roles

/** Colores institucionales del manual de marca (equivalentes web de los valores CMYK). */
object Brand {
    val Purple = Color(0xFF8600DD)
    val PurpleDeep = Color(0xFF490077)
    val Orange = Color(0xFFF79800)
    val Cyan = Color(0xFF00A3E0)
    val CyanDeep = Color(0xFF00679F)
    val Green = Color(0xFF95C11F)
    val GreenDeep = Color(0xFF5E7F10)
    val Ink = Color(0xFF2F024C)
    val Neutral = Color(0xFFF6F4F9)
    val White = Color(0xFFFFFFFF)
    val Gray = Color(0xFF666666)

    /** Los cuatro colores en el orden del logo. */
    val Four = listOf(Purple, Orange, Cyan, Green)
}

/**
 * Colores de una sesión según el rol. [primary] pinta botones, ítem seleccionado y rellenos; [onPrimary] es el texto que
 * cumple contraste sobre [primary] (tinta oscura sobre naranja, blanco en morado y azul). [accentText] es el tono del
 * rol para texto e íconos sobre superficies (el naranja puro no tiene contraste sobre blanco). El encabezado usa el
 * degradado [gradientStart] a [gradientEnd] con texto [onGradient].
 */
data class RolePalette(
    val key: String,
    val label: String,
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color,
    val accentText: Color,
    val gradientStart: Color,
    val gradientEnd: Color,
    val onGradient: Color,
    val success: Color,
    val neutral: Boolean = false
) {
    val gradient: List<Color> get() = listOf(gradientStart, gradientEnd)
}

object RoleKeys {
    const val NEUTRAL = "NEUTRAL"
}

private val ManagerAccentLight = Color(0xFF9A5B00)
private val AdminGradientStart = Color(0xFF004B78)

/** Paleta de cada rol: ESTUDIANTE morado, GESTOR naranja, ADMINISTRADOR azul; sin sesión, la institucional neutra. */
fun rolePalette(role: String?, dark: Boolean = false): RolePalette = when (role) {
    Roles.STUDENT -> if (dark) RolePalette(
        Roles.STUDENT, "Estudiante", Color(0xFFD7A3FF), Brand.Ink, Color(0xFF4A1A70), Color(0xFFF0DBFF), Color(0xFFD7A3FF),
        Brand.PurpleDeep, Brand.Purple, Brand.White, Brand.Green
    ) else RolePalette(
        Roles.STUDENT, "Estudiante", Brand.Purple, Brand.White, Color(0xFFF1E0FC), Brand.Ink, Brand.Purple,
        Brand.PurpleDeep, Brand.Purple, Brand.White, Brand.GreenDeep
    )
    Roles.MANAGER -> if (dark) RolePalette(
        Roles.MANAGER, "Gestor", Brand.Orange, Brand.Ink, Color(0xFF5A3A00), Color(0xFFFFE2B8), Brand.Orange,
        Color(0xFFFFB02E), Brand.Orange, Brand.Ink, Brand.Green
    ) else RolePalette(
        Roles.MANAGER, "Gestor", Brand.Orange, Brand.Ink, Color(0xFFFFEBCC), Brand.Ink, ManagerAccentLight,
        Color(0xFFFFB02E), Brand.Orange, Brand.Ink, Brand.GreenDeep
    )
    Roles.ADMIN -> if (dark) RolePalette(
        Roles.ADMIN, "Administrador", Color(0xFF63CBF2), Color(0xFF00243A), Color(0xFF004B73), Color(0xFFD2EFFC), Color(0xFF63CBF2),
        Color(0xFF00334D), Brand.CyanDeep, Brand.White, Brand.Green
    ) else RolePalette(
        Roles.ADMIN, "Administrador", Brand.CyanDeep, Brand.White, Color(0xFFD9F1FB), Brand.Ink, Brand.CyanDeep,
        AdminGradientStart, Brand.CyanDeep, Brand.White, Brand.GreenDeep
    )
    else -> if (dark) RolePalette(
        RoleKeys.NEUTRAL, "UNAB GO Cliente", Color(0xFFD7A3FF), Brand.Ink, Color(0xFF3A1B55), Color(0xFFF0DBFF), Color(0xFFD7A3FF),
        Brand.PurpleDeep, Brand.Purple, Brand.White, Brand.Green, neutral = true
    ) else RolePalette(
        RoleKeys.NEUTRAL, "UNAB GO Cliente", Brand.PurpleDeep, Brand.White, Color(0xFFEFE7F5), Brand.Ink, Brand.PurpleDeep,
        Brand.PurpleDeep, Brand.Purple, Brand.White, Brand.GreenDeep, neutral = true
    )
}

val LocalRolePalette = compositionLocalOf { rolePalette(null) }
