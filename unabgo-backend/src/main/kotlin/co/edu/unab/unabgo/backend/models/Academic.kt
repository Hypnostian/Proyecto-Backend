package co.edu.unab.unabgo.backend.models

// Modelos académicos: programa (1:N perfiles), materia (1:N bloques y cortes), corte (1:N notas).

import co.edu.unab.unabgo.backend.database.GradeCategories
import co.edu.unab.unabgo.backend.database.GradeItems
import co.edu.unab.unabgo.backend.database.Programs
import co.edu.unab.unabgo.backend.database.ScheduleBlocks
import co.edu.unab.unabgo.backend.database.StudentProfiles
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
