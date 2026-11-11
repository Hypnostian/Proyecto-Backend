package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.services.GradeService
import co.edu.unab.unabgo.backend.services.GradeService.Category
import co.edu.unab.unabgo.backend.services.GradeService.Item
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Reglas de notas del Reglamento Estudiantil de Pregrado de la UNAB. */
class GradeServiceTest {

    @Test
    fun notaFinalEnDecimasSinAproximar() {
        assertEquals(2.9, GradeService.truncateTenths(2.96))
        assertEquals(3.0, GradeService.truncateTenths(3.04))
    }

    @Test
    fun notaNecesariaParaPasar() {
        val s = GradeService.summarize(1, listOf(
            Category("Corte 1", 50.0, listOf(Item(4.2, null), Item(4.2, null))),
            Category("Corte 2", 50.0, listOf(Item(null, null), Item(null, null)))
        ))
        assertEquals(4.2, s.average)
        assertEquals(50.0, s.evaluatedPercent)
        assertEquals(1.8, s.neededToPass)
        assertNull(s.finalGrade)
    }

    @Test
    fun finalHabilitacionYAdvertencias() {
        val s = GradeService.summarize(2, listOf(Category("Único", 100.0, listOf(Item(2.96, null)))))
        assertEquals(2.9, s.finalGrade)
        assertEquals(3.2, s.habilitationNeeded)          // 0.6 * 2.9 + 0.4 * h >= 3.0
        assertTrue(s.warnings.any { "Art. 44" in it })     // un corte con una sola nota
    }
}
