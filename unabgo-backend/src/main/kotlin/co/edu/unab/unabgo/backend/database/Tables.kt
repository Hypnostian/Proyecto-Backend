package co.edu.unab.unabgo.backend.database

// Mapeo de las tablas creadas por las migraciones (src/main/resources/db/migration). Las migraciones
// son la fuente de verdad del esquema; aquí solo se describen las columnas para el ORM Exposed.

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.time
import org.jetbrains.exposed.sql.javatime.timestamp

object Roles : IntIdTable("roles") {
    val name = varchar("name", 30)
    val description = varchar("description", 150).nullable()
}

object Users : LongIdTable("users") {
    val role = reference("role_id", Roles)
    val name = varchar("name", 120)
    val email = varchar("email", 160)
    val passwordHash = varchar("password_hash", 100).nullable()
    val googleUid = varchar("google_uid", 128).nullable()
    val active = bool("active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

object AccessTokens : LongIdTable("access_tokens") {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 60)
    val tokenHash = char("token_hash", 64)
    val abilities = varchar("abilities", 200)
    val lastUsedAt = timestamp("last_used_at").nullable()
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")
}

object Programs : LongIdTable("programs") {
    val slug = varchar("slug", 160)
    val name = varchar("name", 200)
    val faculty = varchar("faculty", 150)
    val level = varchar("level", 40)
    val modality = varchar("modality", 40).nullable()
    val priceCop = long("price_cop").nullable()
    val snies = varchar("snies", 20).nullable()
    val url = varchar("url", 300)
    val verifiedAt = timestamp("verified_at").nullable()
}

object StudentProfiles : LongIdTable("student_profiles") {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val program = optReference("program_id", Programs, onDelete = ReferenceOption.SET_NULL)
    val studentCode = varchar("student_code", 20).nullable()
    val semester = short("semester").nullable()
    val language = varchar("language", 5).default("es")
    val reminderMinutes = short("reminder_minutes").default(30)
}

object Subjects : LongIdTable("subjects") {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 120)
    val credits = short("credits")
    val teacher = varchar("teacher", 120).nullable()
    val color = char("color", 7)
    val createdAt = timestamp("created_at")
}

object ScheduleBlocks : LongIdTable("schedule_blocks") {
    val subject = reference("subject_id", Subjects, onDelete = ReferenceOption.CASCADE)
    val dayOfWeek = short("day_of_week")
    val startTime = time("start_time")
    val endTime = time("end_time")
    val room = varchar("room", 40).nullable()
}

object GradeCategories : LongIdTable("grade_categories") {
    val subject = reference("subject_id", Subjects, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 60)
    val weight = decimal("weight", 5, 2)
    val sortOrder = short("sort_order").default(1)
}

object GradeItems : LongIdTable("grade_items") {
    val category = reference("category_id", GradeCategories, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 80)
    val score = decimal("score", 2, 1).nullable()
    val weight = decimal("weight", 5, 2).nullable()
    val gradedAt = date("graded_at").nullable()
}

object BusRoutes : LongIdTable("bus_routes") {
    val code = varchar("code", 20)
    val name = varchar("name", 80)
    val color = char("color", 7)
    val gpsDeviceId = varchar("gps_device_id", 40).nullable()
    val active = bool("active").default(true)
}

object BusStops : LongIdTable("bus_stops") {
    val name = varchar("name", 120)
    val lat = decimal("lat", 9, 6)
    val lon = decimal("lon", 9, 6)
    val isCampus = bool("is_campus").default(false)
}

/** Tabla intermedia N:M entre rutas y paradas, con el orden de la parada. */
object RouteStops : Table("route_stops") {
    val route = reference("route_id", BusRoutes, onDelete = ReferenceOption.CASCADE)
    val stop = reference("stop_id", BusStops, onDelete = ReferenceOption.CASCADE)
    val sortOrder = short("sort_order").default(0)
    override val primaryKey = PrimaryKey(route, stop)
}

/** Tabla intermedia N:M entre usuarios y sus rutas favoritas. */
object FavoriteRoutes : Table("favorite_routes") {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val route = reference("route_id", BusRoutes, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(user, route)
}

object Announcements : LongIdTable("announcements") {
    val author = reference("author_id", Users)
    val title = varchar("title", 140)
    val body = text("body")
    val category = varchar("category", 30)
    val publishedAt = timestamp("published_at").nullable()
    val pushSentAt = timestamp("push_sent_at").nullable()
    val createdAt = timestamp("created_at")
}

object Faqs : LongIdTable("faqs") {
    val createdBy = reference("created_by", Users)
    val question = varchar("question", 200)
    val keywords = text("keywords")
    val answer = text("answer")
    val sourceUrl = varchar("source_url", 300)
    val active = bool("active").default(true)
    val updatedAt = timestamp("updated_at")
}
