package co.edu.unab.unabgo.backend.services

// Reglas de notas del Reglamento Estudiantil de Pregrado de la UNAB (las mismas de la app UNAB GO!):
//  - escala de 0.0 a 5.0, se aprueba con 3.0
//  - Art. 39: la nota final va en unidades y décimas, sin aproximar (2.96 -> 2.9)
//  - Art. 44: cada corte debe tener al menos dos notas
//  - Arts. 47 y 48: se habilita con final entre 2.5 y 2.9; definitiva = 60 % curso + 40 % habilitación

import co.edu.unab.unabgo.backend.models.Subject
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

@Serializable
data class GradeSummary(
    val subjectId: Long,
    val average: Double?,
    val accumulated: Double,
    val evaluatedPercent: Double,
    val neededToPass: Double?,
    val finalGrade: Double?,
    val passedAlready: Boolean,
    val cannotPass: Boolean,
    val habilitationNeeded: Double?,
    val warnings: List<String>
)

object GradeService {
    const val PASS = 3.0
    const val MAX = 5.0
    private const val HABILITATION_MIN = 2.5
    private const val COURSE_SHARE = 0.6

    /** Art. 39: décimas sin aproximar. */
    fun truncateTenths(v: Double): Double = floor(v * 10 + 1e-6) / 10

    /** Lo mínimo que hay que sacar se sube a la décima (2.57 -> 2.6). */
    fun ceilTenths(v: Double): Double = ceil(v * 10 - 1e-6) / 10

    data class Item(val score: Double?, val weight: Double?)
    data class Category(val name: String, val weight: Double, val items: List<Item>)

    /**
     * Pesos efectivos (en %) de las notas de un corte: las notas sin peso se reparten por partes
     * iguales lo que falta para 100 %, estén calificadas o pendientes.
     */
    fun weightsOf(items: List<Item>): List<Double> {
        val fixed = items.sumOf { it.weight?.takeIf { w -> w > 0 } ?: 0.0 }.coerceAtMost(100.0)
        val free = items.count { (it.weight ?: 0.0) <= 0 }
        val share = if (free > 0) (100.0 - fixed).coerceAtLeast(0.0) / free else 0.0
        return items.map { it.weight?.takeIf { w -> w > 0 } ?: share }
    }

    fun summarize(subjectId: Long, categories: List<Category>): GradeSummary {
        var points = 0.0      // puntos sobre 5 ya ganados
        var evaluated = 0.0   // % de la nota final ya calificado
        val warnings = mutableListOf<String>()
        for (cat in categories) {
            if (cat.items.size < 2) warnings += "${cat.name}: el reglamento pide al menos 2 notas por corte (Art. 44)."
            val weights = weightsOf(cat.items)
            val share = cat.weight / 100.0
            cat.items.forEachIndexed { i, item ->
                val score = item.score ?: return@forEachIndexed
                points += score * weights[i] / 100.0 * share
                evaluated += weights[i] / 100.0 * cat.weight
            }
        }
        val totalWeight = categories.sumOf { it.weight }
        if (totalWeight < 99.5) warnings += "Los cortes suman ${round2(totalWeight)} %; deberían sumar 100 %."
        evaluated = evaluated.coerceAtMost(100.0)
        val average = if (evaluated > 0) points / (evaluated / 100.0) else null
        val remaining = 100.0 - evaluated
        val needed = if (evaluated > 0 && remaining > 0.5) ceilTenths((PASS - points) / (remaining / 100.0)) else null
        val finalGrade = average?.takeIf { evaluated >= 99.5 }?.let(::truncateTenths)
        val habilitation = finalGrade?.takeIf { it >= HABILITATION_MIN && it < PASS }
            ?.let { ceilTenths((PASS - COURSE_SHARE * it) / (1 - COURSE_SHARE)) }
        return GradeSummary(
            subjectId = subjectId,
            average = average?.let(::round2),
            accumulated = round2(points),
            evaluatedPercent = round2(evaluated),
            neededToPass = needed?.coerceAtLeast(0.0),
            finalGrade = finalGrade,
            passedAlready = points >= PASS - 0.005,
            cannotPass = needed != null && needed > MAX + 0.005,
            habilitationNeeded = habilitation,
            warnings = warnings
        )
    }

    /** Resumen de una materia guardada (se llama dentro de una transacción). */
    fun summaryOf(subject: Subject): GradeSummary = summarize(
        subject.id.value,
        subject.categories.sortedBy { it.sortOrder }.map { c ->
            Category(c.name, c.weight.toDouble(), c.items.map { Item(it.score?.toDouble(), it.weight?.toDouble()) })
        }
    )

    private fun round2(v: Double) = round(v * 100) / 100
}
