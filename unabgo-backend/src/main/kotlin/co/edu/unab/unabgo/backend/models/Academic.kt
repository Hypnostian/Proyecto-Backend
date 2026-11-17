package co.edu.unab.unabgo.backend.models

// Modelos académicos: programa (1:N perfiles), materia (1:N bloques y cortes), corte (1:N notas).

import co.edu.unab.unabgo.backend.database.GradeCategories
import co.edu.unab.unabgo.backend.database.GradeItems
import co.edu.unab.unabgo.backend.database.PlanCourses
import co.edu.unab.unabgo.backend.database.PlanSemesters
import co.edu.unab.unabgo.backend.database.Professors
import co.edu.unab.unabgo.backend.database.Programs
import co.edu.unab.unabgo.backend.database.ScheduleBlocks
import co.edu.unab.unabgo.backend.database.StudentProfiles
import co.edu.unab.unabgo.backend.database.StudyPlans
import co.edu.unab.unabgo.backend.database.Subjects
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.id.EntityID

class Program(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Program>(Programs)

    var slug by Programs.slug
    var name by Programs.name
    var faculty by Programs.faculty
    var level by Programs.level
    var modality by Programs.modality
    var priceCop by Programs.priceCop
    var snies by Programs.snies
    var url by Programs.url
    var verifiedAt by Programs.verifiedAt

    /** 1:N — estudiantes que cursan el programa. */
    val students by StudentProfile optionalReferrersOn StudentProfiles.program

    /** 1:N — planes de estudio vinculados (normalmente uno). */
    val studyPlans by StudyPlan optionalReferrersOn StudyPlans.program
}

class Subject(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Subject>(Subjects)

    var user by User referencedOn Subjects.user
    var name by Subjects.name
    var credits by Subjects.credits
    var teacher by Subjects.teacher
    var color by Subjects.color
    var createdAt by Subjects.createdAt

    /** 1:N — bloques del horario semanal y cortes de la materia. */
    val blocks by ScheduleBlock referrersOn ScheduleBlocks.subject
    val categories by GradeCategory referrersOn GradeCategories.subject
}

class ScheduleBlock(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<ScheduleBlock>(ScheduleBlocks)

    var subject by Subject referencedOn ScheduleBlocks.subject
    var dayOfWeek by ScheduleBlocks.dayOfWeek
    var startTime by ScheduleBlocks.startTime
    var endTime by ScheduleBlocks.endTime
    var room by ScheduleBlocks.room
}

class GradeCategory(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<GradeCategory>(GradeCategories)

    var subject by Subject referencedOn GradeCategories.subject
    var name by GradeCategories.name
    var weight by GradeCategories.weight
    var sortOrder by GradeCategories.sortOrder

    /** 1:N — notas del corte. */
    val items by GradeItem referrersOn GradeItems.category
}

class GradeItem(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<GradeItem>(GradeItems)

    var category by GradeCategory referencedOn GradeItems.category
    var name by GradeItems.name
    var score by GradeItems.score
    var weight by GradeItems.weight
    var gradedAt by GradeItems.gradedAt
}

/** Profesor con sus datos públicos de APOLO (portal de investigación de la UNAB). */
class Professor(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<Professor>(Professors)

    var slug by Professors.slug
    var name by Professors.name
    var title by Professors.title
    var unit by Professors.unit
    var unitUrl by Professors.unitUrl
    var apoloUrl by Professors.apoloUrl
    var cvlacUrl by Professors.cvlacUrl
}

/** Plan de estudios de un programa: 1:N semestres (que a su vez tienen 1:N cursos) y N:1 programa. */
class StudyPlan(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<StudyPlan>(StudyPlans) {
        /** Texto para buscar: minúsculas, sin tildes y con cualquier signo convertido en un espacio. */
        fun normalize(text: String): String = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "").replace("[^a-z0-9ñ]+".toRegex(), " ").trim()
    }

    var slug by StudyPlans.slug
    var programName by StudyPlans.programName
    var searchName by StudyPlans.searchName
    var level by StudyPlans.level
    var structure by StudyPlans.structure
    var totalCredits by StudyPlans.totalCredits
    var planUrl by StudyPlans.planUrl
    var sourceUrl by StudyPlans.sourceUrl
    var program by Program optionalReferencedOn StudyPlans.program
    var planText by StudyPlans.planText
    var updatedAt by StudyPlans.updatedAt

    val semesters by PlanSemester referrersOn PlanSemesters.plan
}

class PlanSemester(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PlanSemester>(PlanSemesters)

    var plan by StudyPlan referencedOn PlanSemesters.plan
    var number by PlanSemesters.number
    var name by PlanSemesters.name
    var credits by PlanSemesters.credits

    val courses by PlanCourse referrersOn PlanCourses.semester
}

class PlanCourse(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<PlanCourse>(PlanCourses)

    var semester by PlanSemester referencedOn PlanCourses.semester
    var name by PlanCourses.name
    var credits by PlanCourses.credits
    var sortOrder by PlanCourses.sortOrder
}
