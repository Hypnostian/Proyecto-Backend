// Backend de UNAB GO! (proyecto final de Desarrollo y Arquitectura Backend): API REST en Kotlin con Ktor,
// ORM Exposed, migraciones Flyway y base de datos PostgreSQL (H2 en local y en las pruebas).
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    application
}

group = "co.edu.unab.unabgo"
version = "0.2.0"

repositories {
    mavenCentral()
}

val ktor = "3.3.3"
val exposed = "0.61.0"
val flyway = "11.8.2"

dependencies {
    // Servidor HTTP
    implementation("io.ktor:ktor-server-core:$ktor")
    implementation("io.ktor:ktor-server-netty:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("io.ktor:ktor-server-auth:$ktor")
    implementation("io.ktor:ktor-server-status-pages:$ktor")
    implementation("io.ktor:ktor-server-call-logging:$ktor")
    implementation("io.ktor:ktor-server-default-headers:$ktor")

    // Persistencia: ORM, migraciones, pool de conexiones y motores
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed")
    implementation("org.flywaydb:flyway-core:$flyway")
    implementation("org.flywaydb:flyway-database-postgresql:$flyway")
    implementation("com.zaxxer:HikariCP:6.3.0")
    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.h2database:h2:2.3.232")

    // Contraseñas y datos de prueba (factories)
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("net.datafaker:datafaker:2.4.3")

    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
}

application {
    mainClass.set("co.edu.unab.unabgo.backend.ApplicationKt")
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed"); showStandardStreams = false }
}

// Comandos de consola, equivalentes a los de "php artisan":
//   ./gradlew migrate        -> php artisan migrate
//   ./gradlew migrateFresh   -> php artisan migrate:fresh --seed
//   ./gradlew dbSeed         -> php artisan db:seed
fun consoleTask(name: String, description: String, vararg command: String) = tasks.register<JavaExec>(name) {
    group = "base de datos"
    this.description = description
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("co.edu.unab.unabgo.backend.console.ConsoleKt")
    args(*command)
    workingDir = projectDir
}
consoleTask("migrate", "Ejecuta las migraciones pendientes", "migrate")
consoleTask("migrateFresh", "Borra la base, la vuelve a crear y la llena con datos de prueba", "migrate:fresh", "--seed")
consoleTask("dbSeed", "Llena la base con los datos de prueba", "db:seed")
