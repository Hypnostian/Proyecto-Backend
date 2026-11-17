package co.edu.unab.unabgo.backend.services

// Banu, el asistente de la app: primero busca la respuesta en las preguntas frecuentes (coincidencia de
// palabras sin tildes) y solo si no hay una coincidencia segura le pide a un modelo de lenguaje que
// redacte la respuesta usando como contexto las preguntas y los programas más parecidos. Si la pregunta es
// sobre el plan de estudios de un programa (pensum, materias, semestres, créditos) usa los planes de la base:
// con IA se los pasa como contexto y sin IA, o si el proveedor falla, responde directamente con los datos.

import co.edu.unab.unabgo.backend.database.Faqs
import co.edu.unab.unabgo.backend.database.dbQuery
import co.edu.unab.unabgo.backend.models.Faq
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.models.StudyPlan
import co.edu.unab.unabgo.backend.services.integrations.ExternalServiceException
import co.edu.unab.unabgo.backend.services.integrations.LlmClient
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.slf4j.LoggerFactory
import java.text.Normalizer

@Serializable
data class BanuSource(val titulo: String, val url: String)

/** Respuesta de Banu. [origen] es "faq" (preguntas frecuentes), "plan" (planes de estudio) o "ia" (modelo de lenguaje). */
@Serializable
data class BanuAnswer(val respuesta: String, val fuentes: List<BanuSource>, val origen: String)

class BanuService(private val llm: LlmClient?) {

    private val log = LoggerFactory.getLogger("unabgo.banu")

    private data class ScoredFaq(val faq: Faq, val score: Double, val overlap: Int)
    private data class ScoredProgram(val program: Program, val score: Double)

    private class PlanCourseData(val name: String, val credits: Int?)
    private class PlanSemesterData(val number: Int, val name: String, val credits: Int?, val courses: List<PlanCourseData>)
    private class PlanData(
        val programName: String, val totalCredits: Int?, val planUrl: String,
        val semesters: List<PlanSemesterData>, val text: String?
    )

    suspend fun ask(question: String): BanuAnswer {
        val tokens = tokens(question)
        val asksPlan = asksAboutPlan(question)
        val semester = if (asksPlan) askedSemester(question) else null
        // Todo lo que toca la base se resuelve dentro de la transacción y sale como datos simples
        val found = dbQuery {
            val faqs = Faq.find { Faqs.active eq true }.map { faq ->
                val variants = listOf(faq.question) + faq.keywords.split(';', '\n')
                val best = variants.map { match(tokens, tokens(it)) }.maxByOrNull { it.first } ?: (0.0 to 0)
                ScoredFaq(faq, best.first, best.second)
            }.sortedByDescending { it.score }
            val programs = if (tokens.isEmpty()) emptyList() else Program.all().mapNotNull { p ->
                val pt = tokens(p.name)
                val overlap = pt.intersect(tokens).size
                if (overlap == 0 || pt.isEmpty()) null else ScoredProgram(p, overlap.toDouble() / pt.size)
            }.filter { it.score >= 0.5 }.sortedByDescending { it.score }.take(MAX_PROGRAMS)
            val plan = findPlan(tokens)
            Triple(
                faqs.take(MAX_FAQS).map { Candidate(it.faq.question, it.faq.answer, it.faq.sourceUrl, it.score, it.overlap) },
                programs.map { p ->
                    val d = listOfNotNull(p.program.faculty, p.program.level, p.program.modality, p.program.priceCop?.let { "valor desde $it COP" })
                    Candidate(p.program.name, d.joinToString(", "), p.program.url, p.score, 0)
                },
                plan
            )
        }
        val faqs = found.first
        val programs = found.second
        val plan = found.third

        if (plan != null && asksPlan) {
            val source = BanuSource("Plan de estudios de ${plan.programName}", plan.planUrl)
            if (llm != null) {
                try {
                    val answer = llm.complete(SYSTEM_PROMPT, planPrompt(question, plan, semester))
                    return BanuAnswer(answer, listOf(source), "ia")
                } catch (e: ExternalServiceException) {
                    log.warn("El proveedor de IA falló, se responde con los datos del plan de estudios: {}", e.message)
                }
            }
            return BanuAnswer(planAnswer(question, plan, semester), listOf(source), "plan")
        }

        val best = faqs.firstOrNull()
        if (best != null && best.score >= CONFIDENT && (best.overlap >= 2 || best.score >= 0.99)) return fromFaq(best)

        val context = faqs.filter { it.score >= RELATED } + programs
        if (llm != null && context.isNotEmpty()) {
            try {
                val answer = llm.complete(SYSTEM_PROMPT, userPrompt(question, faqs.filter { it.score >= RELATED }, programs, plan))
                val sources = context.map { BanuSource(it.title, it.url) } +
                    listOfNotNull(plan?.let { BanuSource("Plan de estudios de ${it.programName}", it.planUrl) })
                return BanuAnswer(answer, sources.distinctBy { it.url }, "ia")
            } catch (e: ExternalServiceException) {
                log.warn("El proveedor de IA falló, se responde con las preguntas frecuentes: {}", e.message)
            }
        }
        if (best != null && best.score >= FALLBACK_FAQ) return fromFaq(best)
        return BanuAnswer(
            "No encontré información sobre eso en las preguntas frecuentes de la UNAB. Consulta el sitio oficial para más detalles.",
            listOf(BanuSource("Sitio oficial de la UNAB", "https://unab.edu.co")),
            "faq"
        )
    }

    private data class Candidate(val title: String, val text: String, val url: String, val score: Double, val overlap: Int)

    private fun fromFaq(c: Candidate) = BanuAnswer(c.text, listOf(BanuSource(c.title, c.url)), "faq")

    private fun userPrompt(question: String, faqs: List<Candidate>, programs: List<Candidate>, plan: PlanData?) = buildString {
        if (faqs.isNotEmpty()) {
            appendLine("Preguntas frecuentes oficiales:")
            faqs.forEach { appendLine("- P: ${it.title}\n  R: ${it.text}") }
        }
        if (programs.isNotEmpty()) {
            appendLine("Programas académicos relacionados:")
            programs.forEach { appendLine("- ${it.title} (${it.text}) ${it.url}") }
        }
        if (plan != null) appendLine(planContext(plan, null, compact = true))
        appendLine()
        append("Pregunta del estudiante: ").append(question.trim())
    }

    // ------------------------------------------------------------------ planes de estudio

    /** El plan cuyo nombre se parece más a la pregunta: debe cubrir al menos el 60 % de las palabras del nombre. */
    private fun findPlan(questionTokens: Set<String>): PlanData? {
        if (questionTokens.isEmpty()) return null
        data class Scored(val plan: StudyPlan, val score: Double, val overlap: Int, val size: Int)
        val best = StudyPlan.all().mapNotNull { p ->
            val pt = tokens(p.programName)
            val overlap = pt.intersect(questionTokens).size
            if (overlap == 0 || pt.isEmpty()) null else Scored(p, overlap.toDouble() / pt.size, overlap, pt.size)
        }.filter { it.score >= PLAN_MATCH }
            .sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.overlap }.thenBy { it.size }.thenBy { it.plan.id.value })
            .firstOrNull()?.plan ?: return null
        return PlanData(
            best.programName, best.totalCredits, best.planUrl,
            best.semesters.sortedBy { it.number }.map { s ->
                PlanSemesterData(s.number.toInt(), s.name, s.credits, s.courses.sortedBy { it.sortOrder }.map { PlanCourseData(it.name, it.credits) })
            },
            best.planText?.take(MAX_PLAN_TEXT)
        )
    }

    private fun creditsText(credits: Int?) = when (credits) {
        null -> ""
        1 -> " (1 crédito)"
        else -> " ($credits créditos)"
    }

    private fun courseList(s: PlanSemesterData) = s.courses.joinToString("; ") { it.name + creditsText(it.credits) }

    /** Datos del plan para el prompt: el semestre pedido, o un resumen de todos. */
    private fun planContext(plan: PlanData, semester: Int?, compact: Boolean = false): String = buildString {
        appendLine("Plan de estudios de ${plan.programName}" + (plan.totalCredits?.let { " (total $it créditos)" } ?: "") + ": ${plan.planUrl}")
        val chosen = semester?.let { n -> plan.semesters.filter { it.number == n } }.orEmpty()
        when {
            plan.semesters.isEmpty() -> if (!compact) plan.text?.let { appendLine("Texto oficial del plan:\n$it") }
            chosen.isNotEmpty() -> chosen.forEach { appendLine("${it.name}${creditsText(it.credits)}: ${courseList(it)}") }
            compact -> appendLine("${plan.semesters.size} semestres.")
            else -> plan.semesters.forEach { appendLine("${it.name}${creditsText(it.credits)}: ${courseList(it)}") }
        }
    }.trimEnd()

    private fun planPrompt(question: String, plan: PlanData, semester: Int?) = buildString {
        appendLine("Datos oficiales del plan de estudios:")
        appendLine(planContext(plan, semester))
        if (semester != null && plan.semesters.isNotEmpty() && plan.semesters.none { it.number == semester }) {
            appendLine("El plan no tiene un semestre número $semester (tiene ${plan.semesters.size}).")
        }
        appendLine()
        append("Pregunta del estudiante: ").append(question.trim())
    }

    /** Respuesta directa con los datos del plan, sin modelo de lenguaje. */
    private fun planAnswer(question: String, plan: PlanData, semester: Int?): String {
        val name = plan.programName
        if (plan.semesters.isEmpty()) {
            return "El plan de estudios de $name está publicado como documento, sin el detalle por semestres. Consúltalo completo en ${plan.planUrl}"
        }
        if (semester != null) {
            val s = plan.semesters.firstOrNull { it.number == semester }
                ?: return "El plan de $name tiene ${plan.semesters.size} semestres, no hay un semestre número $semester. Consulta el plan completo en ${plan.planUrl}"
            val total = s.credits ?: s.courses.mapNotNull { it.credits }.sum()
            return "El ${ordinal(s.number)} semestre de $name tiene $total créditos y estas materias: ${courseList(s)}."
        }
        val asksCredits = stripAccents(question.lowercase()).contains("credito")
        val total = plan.totalCredits ?: plan.semesters.mapNotNull { it.credits }.sum().takeIf { it > 0 }
        val head = if (total != null) "El plan de $name tiene $total créditos en ${plan.semesters.size} semestres" else "El plan de $name tiene ${plan.semesters.size} semestres"
        if (asksCredits) return "$head. Más detalle en ${plan.planUrl}"
        val perSemester = plan.semesters.joinToString("; ") { "${it.number}: ${it.courses.size} materias" + creditsText(it.credits) }
        return "$head ($perSemester). Pregúntame por un semestre, por ejemplo \"materias del quinto semestre de $name\", o consulta el plan completo en ${plan.planUrl}"
    }

    companion object {
        private const val PLAN_MATCH = 0.6
        private const val MAX_PLAN_TEXT = 4000
        private const val CONFIDENT = 0.6
        private const val RELATED = 0.2
        private const val FALLBACK_FAQ = 0.35
        private const val MAX_FAQS = 3
        private const val MAX_PROGRAMS = 3

        private const val SYSTEM_PROMPT =
            "Eres Banu, el asistente de la app UNAB GO! de la Universidad Autónoma de Bucaramanga. " +
                "Responde en español, en máximo 4 frases (si te piden las materias de un semestre, lístalas todas), usando únicamente el contexto que recibes. " +
                "Si el contexto no alcanza para responder, dilo y sugiere consultar unab.edu.co. No inventes datos, fechas ni precios."

        private val STOPWORDS = setOf(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "al", "y", "o", "u", "e", "en", "a", "que", "es", "son",
            "se", "me", "mi", "mis", "tu", "tus", "su", "sus", "por", "para", "con", "sin", "lo", "le", "les", "como", "cual", "cuales",
            "cuando", "donde", "hay", "ser", "esta", "este", "esto", "ya", "si", "no", "muy", "mas", "puedo", "quiero", "necesito", "saber"
        )

        private val ORDINALS = listOf(
            "primer" to 1, "primero" to 1, "segundo" to 2, "tercer" to 3, "tercero" to 3, "cuarto" to 4, "quinto" to 5,
            "sexto" to 6, "septimo" to 7, "octavo" to 8, "noveno" to 9, "decimo" to 10, "undecimo" to 11, "duodecimo" to 12
        )
        private val PLAN_WORDS = listOf("pensum", "plan de estudio", "materia", "asignatura", "curso", "semestre", "credito", "malla curricular")

        /** True si la pregunta habla del plan de estudios (pensum, materias, semestres, créditos). */
        fun asksAboutPlan(question: String): Boolean {
            val text = stripAccents(question.lowercase())
            return PLAN_WORDS.any { text.contains(it) }
        }

        /** Número del semestre por el que se pregunta ("quinto semestre", "semestre 5", "5to semestre"), o null. */
        fun askedSemester(question: String): Int? {
            val text = stripAccents(question.lowercase())
            if (!text.contains("semestre")) return null
            ORDINALS.firstOrNull { Regex("\\b${it.first}\\b").containsMatchIn(text) }?.let { return it.second }
            val digits = Regex("semestre\\s*(?:no\\.?\\s*|numero\\s*)?(\\d{1,2})\\b").find(text)
                ?: Regex("\\b(\\d{1,2})\\s*(?:°|º|o|to|vo|mo|no|er|do|ro|ero)?\\s*semestre").find(text)
            return digits?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..12 }
        }

        private fun ordinal(n: Int) = listOf("primer", "segundo", "tercer", "cuarto", "quinto", "sexto", "séptimo", "octavo", "noveno", "décimo", "undécimo", "duodécimo")
            .getOrNull(n - 1) ?: "$n°"

        fun stripAccents(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "")

        /** Palabras significativas del texto: sin tildes, en minúscula, sin conectores y con el plural simplificado. */
        fun tokens(text: String): Set<String> = stripAccents(text.lowercase())
            .replace("[^a-z0-9ñ]+".toRegex(), " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in STOPWORDS && (it.length > 1 || it[0].isDigit()) }
            .map { if (it.length > 4 && it.endsWith("s")) it.dropLast(1) else it }
            .toSet()

        /** Parecido (0 a 1) entre la pregunta y una variante: media armónica de cobertura y precisión, y palabras en común. */
        fun match(question: Set<String>, variant: Set<String>): Pair<Double, Int> {
            if (question.isEmpty() || variant.isEmpty()) return 0.0 to 0
            val common = question.intersect(variant).size
            if (common == 0) return 0.0 to 0
            val coverage = common.toDouble() / question.size
            val precision = common.toDouble() / variant.size
            return (2 * coverage * precision / (coverage + precision)) to common
        }
    }
}
