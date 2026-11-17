package co.edu.unab.unabgo.backend

import co.edu.unab.unabgo.backend.database.seeders.StudyPlanSeeder
import co.edu.unab.unabgo.backend.database.seeders.StudyPlanSeeder.CourseSeed
import co.edu.unab.unabgo.backend.database.seeders.StudyPlanSeeder.PlanSeed
import co.edu.unab.unabgo.backend.database.seeders.StudyPlanSeeder.SemesterSeed
import co.edu.unab.unabgo.backend.models.Program
import co.edu.unab.unabgo.backend.models.StudyPlan
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Planes de estudio: GET /study-plans, /study-plans/{slug} y study_plan_slug en /programs/{slug}. */
class StudyPlanTest {

    private val ordinals = listOf("Primer", "Segundo", "Tercer", "Cuarto", "Quinto")

    /** Plan con 5 semestres de 2 materias (16 créditos cada uno): 80 créditos en total. */
    fun sistemas() = PlanSeed(
        slug = "ingenieria-de-sistemas", program_name = "Ingeniería de Sistemas", level = "Profesional", total_credits = 80,
        plan_url = "https://admisiones.unab.edu.co/plan/sistemas", source_url = "https://unab.edu.co/programas/ingenieria-de-sistemas/",
        semesters = ordinals.mapIndexed { i, o ->
            SemesterSeed(i + 1, "$o Semestre", 16, listOf(CourseSeed("Materia ${i + 1}A", 10), CourseSeed("Materia ${i + 1}B", 6)))
        }
    )

    fun texto() = PlanSeed(
        slug = "comunicacion-social", program_name = "Comunicación Social", level = "Profesional", structure = "texto",
        plan_url = "https://drive.test/comunicacion", source_url = "https://unab.edu.co/programas/comunicacion-social/",
        text = "Formación por ciclos y 12 rutas."
    )

    private fun tecnologia() = PlanSeed(
        slug = "desarrollo-de-software", program_name = "Desarrollo de Software", level = "Tecnología", total_credits = 30,
        plan_url = "https://admisiones.unab.edu.co/plan/software",
        semesters = listOf(SemesterSeed(1, "Primer Semestre", 15, listOf(CourseSeed("Programación", 5))))
    )

    private fun load(vararg plans: PlanSeed) = transaction { StudyPlanSeeder().load(plans.toList()) }

    private fun JsonElement.str(key: String) = jsonObject[key]!!.jsonPrimitive.content

    @Test
    fun listaLosPlanesOrdenadosPorNombre() = apiTest(seed = false) {
        load(sistemas(), texto(), tecnologia())
        val (status, body) = client.getAuth("/api/v1/study-plans").json()
        assertEquals(200, status)
        val items = body["data"]!!.jsonArray
        assertEquals(listOf("Comunicación Social", "Desarrollo de Software", "Ingeniería de Sistemas"), items.map { it.str("program_name") })
        val first = items[2].jsonObject
        assertEquals("ingenieria-de-sistemas", first["slug"]!!.jsonPrimitive.content)
        assertEquals("Profesional", first["level"]!!.jsonPrimitive.content)
        assertEquals("semestres", first["structure"]!!.jsonPrimitive.content)
        assertEquals("80", first["total_credits"]!!.jsonPrimitive.content)
        assertEquals("5", first["semesters_count"]!!.jsonPrimitive.content)
        assertEquals("https://admisiones.unab.edu.co/plan/sistemas", first["plan_url"]!!.jsonPrimitive.content)
        assertEquals("0", items[0].jsonObject["semesters_count"]!!.jsonPrimitive.content)
    }

    @Test
    fun buscaPorNombreConYSinTildes() = apiTest(seed = false) {
        load(sistemas(), texto(), tecnologia())
        listOf("ingenieria", "INGENIERÍA", "ingeniería de sistemas", "sistemas").forEach {
            val items = client.getAuth("/api/v1/study-plans?q=${it.replace(" ", "%20")}").json().second["data"]!!.jsonArray
            assertEquals(listOf("ingenieria-de-sistemas"), items.map { p -> p.str("slug") }, it)
        }
        listOf("comunicacion", "Comunicación").forEach {
            assertEquals("comunicacion-social", client.getAuth("/api/v1/study-plans?q=$it").json().second["data"]!!.jsonArray.single().str("slug"))
        }
        assertEquals(0, client.getAuth("/api/v1/study-plans?q=astronomia").json().second["data"]!!.jsonArray.size)
    }

    @Test
    fun filtraPorNivel() = apiTest(seed = false) {
        load(sistemas(), texto(), tecnologia())
        val tec = client.getAuth("/api/v1/study-plans?level=Tecnolog%C3%ADa").json().second["data"]!!.jsonArray
        assertEquals(listOf("desarrollo-de-software"), tec.map { it.str("slug") })
        assertEquals(2, client.getAuth("/api/v1/study-plans?level=profesional").json().second["data"]!!.jsonArray.size)
        assertEquals(1, client.getAuth("/api/v1/study-plans?level=profesional&q=sistemas").json().second["data"]!!.jsonArray.size)
    }

    @Test
    fun paginaConMeta() = apiTest(seed = false) {
        load(sistemas(), texto(), tecnologia())
        val (_, body) = client.getAuth("/api/v1/study-plans?per_page=2").json()
        assertEquals(2, body["data"]!!.jsonArray.size)
        val meta = body["meta"]!!.jsonObject
        assertEquals("3", meta["total"]!!.jsonPrimitive.content)
        assertEquals("2", meta["last_page"]!!.jsonPrimitive.content)
        assertEquals("2", meta["per_page"]!!.jsonPrimitive.content)
        val second = client.getAuth("/api/v1/study-plans?per_page=2&page=2").json().second["data"]!!.jsonArray
        assertEquals(listOf("ingenieria-de-sistemas"), second.map { it.str("slug") })
    }

    @Test
    fun detalleConSemestresYCursos() = apiTest(seed = false) {
        load(sistemas())
        val (status, body) = client.getAuth("/api/v1/study-plans/ingenieria-de-sistemas").json()
        assertEquals(200, status)
        val data = body.data()
        assertEquals("Ingeniería de Sistemas", data["program_name"]!!.jsonPrimitive.content)
        assertEquals("80", data["total_credits"]!!.jsonPrimitive.content)
        assertEquals("https://unab.edu.co/programas/ingenieria-de-sistemas/", data["source_url"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, data["text"])
        assertEquals(JsonNull, data["program"])
        val semesters = data["semesters"]!!.jsonArray
        assertEquals(listOf(1, 2, 3, 4, 5), semesters.map { it.str("number").toInt() })
        val quinto = semesters[4].jsonObject
        assertEquals("Quinto Semestre", quinto["name"]!!.jsonPrimitive.content)
        assertEquals("16", quinto["credits"]!!.jsonPrimitive.content)
        assertEquals(listOf("Materia 5A", "Materia 5B"), quinto["courses"]!!.jsonArray.map { it.str("name") })
        assertEquals("10", quinto["courses"]!!.jsonArray[0].jsonObject["credits"]!!.jsonPrimitive.content)
    }

    @Test
    fun detalleDePlanSoloTexto() = apiTest(seed = false) {
        load(texto())
        val data = client.getAuth("/api/v1/study-plans/comunicacion-social").json().second.data()
        assertEquals("texto", data["structure"]!!.jsonPrimitive.content)
        assertEquals(0, data["semesters"]!!.jsonArray.size)
        assertEquals(JsonNull, data["total_credits"])
        assertEquals("Formación por ciclos y 12 rutas.", data["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun unSlugInexistenteEs404() = apiTest(seed = false) {
        val (status, body) = client.getAuth("/api/v1/study-plans/no-existe").json()
        assertEquals(404, status)
        assertEquals("NOT_FOUND", body["code"]!!.jsonPrimitive.content)
    }

    @Test
    fun elPlanSeVinculaConSuProgramaPorSlugOPorNombre() = apiTest(seed = false) {
        transaction {
            fun program(slug: String, name: String) = Program.new {
                this.slug = slug; this.name = name; faculty = "Ingeniería"; level = "Profesional"; url = "https://unab.edu.co/programas/$slug/"
            }
            program("ingenieria-de-sistemas", "Ingeniería de Sistemas")
            program("carrera-comunicacion", "Comunicación Social")
            program("desarrollo-de-software", "Desarrollo de Software")
        }
        load(sistemas(), texto(), tecnologia().copy(slug = "otro-slug"))
        val bySlug = client.getAuth("/api/v1/study-plans/ingenieria-de-sistemas").json().second.data()["program"]!!.jsonObject
        assertEquals("ingenieria-de-sistemas", bySlug["slug"]!!.jsonPrimitive.content)
        assertEquals("Ingeniería de Sistemas", bySlug["name"]!!.jsonPrimitive.content)
        val byName = client.getAuth("/api/v1/study-plans/comunicacion-social").json().second.data()["program"]!!.jsonObject
        assertEquals("carrera-comunicacion", byName["slug"]!!.jsonPrimitive.content)
        assertEquals("carrera-comunicacion", transaction { StudyPlan.all().first { it.slug == "comunicacion-social" }.program!!.slug })

        // El programa publica el slug de su plan y conserva sus campos de siempre
        val program = client.getAuth("/api/v1/programs/ingenieria-de-sistemas").json().second.data()
        assertEquals("ingenieria-de-sistemas", program["study_plan_slug"]!!.jsonPrimitive.content)
        assertEquals("Ingeniería de Sistemas", program["name"]!!.jsonPrimitive.content)
        assertTrue(program.containsKey("price_cop"))
        assertEquals("otro-slug", client.getAuth("/api/v1/programs/desarrollo-de-software").json().second.data()["study_plan_slug"]!!.jsonPrimitive.content)
    }

    @Test
    fun laListaDeCarrerasParaElRegistroSoloTraeLasQueTienenPlan() = apiTest(seed = false) {
        transaction {
            fun program(slug: String, name: String) = Program.new {
                this.slug = slug; this.name = name; faculty = "Ingeniería"; level = "Profesional"; url = "https://unab.edu.co/programas/$slug/"
            }
            program("ingenieria-de-sistemas", "Ingeniería de Sistemas")
            program("medicina", "Medicina")
        }
        load(sistemas())
        val (_, all) = client.getAuth("/api/v1/programs").json()
        assertEquals(2, all["meta"]!!.jsonObject["total"]!!.jsonPrimitive.content.toInt())
        val (_, withPlan) = client.getAuth("/api/v1/programs?has_plan=true").json()
        val items = withPlan["data"]!!.jsonArray
        assertEquals(1, items.size)
        assertEquals("ingenieria-de-sistemas", items[0].jsonObject["study_plan_slug"]!!.jsonPrimitive.content)
    }

    @Test
    fun unProgramaSinPlanTieneElSlugNulo() = apiTest(seed = false) {
        transaction {
            Program.new { slug = "medicina"; name = "Medicina"; faculty = "Salud"; level = "Profesional"; url = "https://unab.edu.co/programas/medicina/" }
        }
        val data = client.getAuth("/api/v1/programs/medicina").json().second.data()
        assertEquals(JsonNull, data["study_plan_slug"])
    }

    @Test
    fun elSeederCargaLosPlanesReales() = apiTest {
        val (_, body) = client.getAuth("/api/v1/study-plans?per_page=50").json()
        val total = body["meta"]!!.jsonObject["total"]!!.jsonPrimitive.content.toInt()
        assertTrue(total > 0, "seed/study_plans.json debe cargar planes")
        val sistemas = client.getAuth("/api/v1/study-plans?q=sistemas").json().second["data"]!!.jsonArray
        assertTrue(sistemas.isNotEmpty())
    }
}
