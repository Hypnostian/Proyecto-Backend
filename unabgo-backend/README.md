# UNAB GO! Backend

API REST del proyecto **UNAB GO!**, la aplicación móvil que reúne los servicios académicos, institucionales
y de bienestar de la Universidad Autónoma de Bucaramanga. Proyecto final de **Desarrollo y Arquitectura
Backend**

Está hecho en **Kotlin con Ktor** (tecnología equivalente a Laravel):
ORM **Exposed**, migraciones **Flyway**, base de datos **PostgreSQL** (o **H2** en local, sin instalar nada) y
tokens de acceso propios al estilo **Laravel Sanctum**. El frontend es la app Android de UNAB GO! (Kotlin).

## Requisitos

- JDK 21 o superior (`java -version`)
- Nada más: Gradle se descarga solo con `./gradlew` (en Windows, `gradlew.bat`)
- Opcional: Docker, para usar PostgreSQL en vez de H2

## Instalación y ejecución

```bash
# 1. Configuración (por defecto usa H2 en ./data, no hace falta cambiar nada)
cp .env.example .env

# 2. Crear la base de datos y llenarla con datos de prueba  (= php artisan migrate:fresh --seed)
./gradlew migrateFresh

# 3. Levantar el servidor en http://localhost:8080
./gradlew run

# 4. Pruebas automatizadas (base de datos H2 en memoria, migraciones en cada prueba)
./gradlew test
```

Otros comandos de base de datos:

| Laravel | Este proyecto |
|---|---|
| `php artisan migrate` | `./gradlew migrate` |
| `php artisan migrate:fresh --seed` | `./gradlew migrateFresh` |
| `php artisan db:seed` | `./gradlew dbSeed` |

Para usar PostgreSQL: `docker compose up -d` y en `.env`
`DB_URL=jdbc:postgresql://localhost:5432/unabgo`, `DB_USER=unabgo`, `DB_PASSWORD=unabgo`.

## Usuarios de prueba (solo desarrollo)

Todos con la contraseña **`Unab2026*`** (los crea `migrateFresh`):

| Rol | Correo |
|---|---|
| ADMINISTRADOR | `demo.admin@unab.edu.co` |
| GESTOR (Bienestar) | `demo.bienestar@unab.edu.co` |
| GESTOR (Transporte) | `demo.transporte@unab.edu.co` |
| ESTUDIANTE | `demo.estudiante@unab.edu.co` (y 11 estudiantes más generados por las factories) |

Ejemplo:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"demo.estudiante@unab.edu.co","password":"Unab2026*","device_name":"curl"}'
# Respuesta: { "data": { "token": "1.xxxx", "token_type": "Bearer", ... } }

curl http://localhost:8080/api/v1/subjects -H "Authorization: Bearer 1.xxxx"
```

## Qué incluye:

### Modelos y migraciones

- 5 migraciones Flyway en `src/main/resources/db/migration` (válidas en PostgreSQL y H2).
- 13 modelos y 2 tablas intermedias, mapeados con Exposed en `src/main/kotlin/.../models`:
  `Role`, `User`, `StudentProfile`, `AccessToken`, `Program`, `Subject`, `ScheduleBlock`, `GradeCategory`,
  `GradeItem`, `BusRoute`, `BusStop`, `Announcement`, `Faq` + `route_stops` y `favorite_routes`.
- Relaciones:
  - **1:1** `User` ↔ `StudentProfile` (`optionalBackReferencedOn`)
  - **1:N** `Role` → `User`, `User` → `Subject` → `ScheduleBlock` / `GradeCategory` → `GradeItem`, `User` → `Announcement` / `Faq` / `AccessToken`, `Program` → `StudentProfile` (`referrersOn`)
  - **N:M** `BusRoute` ↔ `BusStop` (con orden) y `User` ↔ `BusRoute` favoritas (`via`)
- La asignación masiva se controla con clases de solicitud: solo los campos declarados en
  `http/requests` se pueden asignar (equivalente a `$fillable`).

### Datos: factories y seeders

- `database/factories/Factories.kt`: factories con **Datafaker** en español (usuarios, perfiles, materias,
  horarios, cortes, notas, avisos y preguntas), con semilla fija para que siempre generen lo mismo.
- `database/seeders/DatabaseSeeder.kt`: seeder principal que ejecuta `RoleSeeder`, `ProgramSeeder`
  (los 228 programas reales de unab.edu.co), `UserSeeder`, `BusSeeder` (las 4 rutas del GPS de las Rutas
  UNAB y las sedes), `AcademicSeeder` y `ContentSeeder` (las 58 preguntas frecuentes reales de Banu).

### Autenticación y autorización

- **Tokens al estilo Sanctum** (`auth/TokenService.kt`): el cliente recibe `id.secreto` una sola vez y en la
  tabla `access_tokens` solo se guarda la huella SHA-256; contraseñas con BCrypt; vencimiento configurable.
- **3 roles**: ESTUDIANTE, GESTOR y ADMINISTRADOR (`auth/Roles.kt`).
- **Gates** (`Gate.allows/authorize` y la enumeración `Ability`) y **Policies** (`SubjectPolicy`,
  `AnnouncementPolicy`, `FaqPolicy`, `BusRoutePolicy`, `UserPolicy`) en `auth/Policies.kt`. Ejemplos: un
  estudiante solo ve sus materias; un gestor solo edita sus avisos; el administrador gestiona usuarios pero no
  lee notas.

### Controladores y rutas

- Rutas en `http/routes/ApiRoutes.kt` (equivalente a `routes/api.php`), con prefijo `/api/v1`.
- Controladores por recurso en `http/controllers`: `AuthController`, `SubjectController`,
  `ProgramController`, `BusController`, `AnnouncementController`, `FaqController`, `AdminUserController`,
  `HealthController`.
- Validación tipo **FormRequest** en `http/Validation.kt` y `http/requests`, con mensajes en español.
- Respuestas JSON en snake_case: `{ "data": ..., "meta": ... }` y errores
  `{ "error", "code", "fields" }` con códigos 200, 201, 204, 400, 401, 403, 404, 409 y 500.

## Endpoints

| Método | URI | Acceso | Estado |
|---|---|---|---|
| GET | `/api/v1/health` | Público | ✔ |
| POST | `/api/v1/auth/register` | Público | ✔ |
| POST | `/api/v1/auth/login` | Público | ✔ |
| POST | `/api/v1/auth/logout` | Token | ✔ |
| GET | `/api/v1/me` | Token | ✔ |
| PUT | `/api/v1/me/profile` | Token | ✔ |
| GET/POST/DELETE | `/api/v1/me/favorite-routes[/{routeId}]` | Token | ✔ |
| GET/POST | `/api/v1/subjects` | Estudiante | ✔ |
| GET/PUT/DELETE | `/api/v1/subjects/{id}` | Dueño | ✔ |
| POST | `/api/v1/subjects/{id}/categories` | Dueño | ✔ |
| POST | `/api/v1/categories/{id}/items` | Dueño | ✔ |
| GET | `/api/v1/subjects/{id}/summary` | Dueño | ✔ |
| GET | `/api/v1/programs`, `/api/v1/programs/{slug}` | Público | ✔ |
| GET | `/api/v1/bus/routes` | Público | ✔ |
| PUT | `/api/v1/bus/routes/{id}` | Gestor | ✔ |
| GET | `/api/v1/announcements` | Público | ✔ |
| POST/PUT/DELETE | `/api/v1/announcements[/{id}]` | Gestor / autor | ✔ |
| GET/POST/PUT/DELETE | `/api/v1/faqs[/{id}]` | Gestor | ✔ |
| GET | `/api/v1/admin/users` | Administrador | ✔ |
| PATCH | `/api/v1/admin/users/{id}/role` | Administrador | ✔ |
| GET | `/api/v1/bus/live` | Público | Entrega 3 (API GPS) |
| POST | `/api/v1/auth/google` | Público | Entrega 3 (Google) |
| POST | `/api/v1/banu/ask` | Público | Entrega 3 (IA) |
| POST | `/api/v1/admin/programs/sync` | Administrador | Entrega 3 (WordPress) |
| POST | `/api/v1/webhooks/gps` | Firma HMAC | Entrega 3 |

## Estructura

```
src/main/kotlin/co/edu/unab/unabgo/backend/
  Application.kt          arranque, plugins y autenticación por token
  config/                 lectura de .env
  console/                comandos migrate, migrate:fresh --seed, db:seed
  database/               conexión, Tables.kt (mapeo), factories/ y seeders/
  models/                 modelos con sus relaciones
  auth/                   roles, tokens, Gates y Policies
  http/                   controllers/, requests/, resources/, routes/, errores y validación
  services/               reglas de negocio (notas, horario, avisos) e integrations/
src/main/resources/
  db/migration/           migraciones Flyway
  seed/                   datos reales de programas y preguntas frecuentes
src/test/kotlin/          pruebas con JUnit y H2 en memoria
```

## Ajustes respecto al diccionario de datos de la primera entrega

- Las fechas se guardan como `TIMESTAMP` en UTC (en vez de `TIMESTAMPTZ`) para que las migraciones sirvan
  igual en PostgreSQL y en H2.
- `roles.id` es `INTEGER` y la columna de orden se llama `sort_order` (en vez de `position`, que es palabra
  reservada en algunos motores).
- El token usa `.` para separar el id del secreto, porque `|` no es válido en el encabezado `Authorization`.
