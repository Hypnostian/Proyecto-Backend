# UNAB GO! Backend

API REST del proyecto **UNAB GO!**, la aplicación móvil que reúne los servicios académicos, institucionales
y de bienestar de la Universidad Autónoma de Bucaramanga. Proyecto final de **Desarrollo y Arquitectura
Backend**.

Está hecho en **Kotlin con Ktor** (tecnología equivalente a Laravel):
ORM **Exposed**, migraciones **Flyway**, base de datos **PostgreSQL** (o **H2** en local, sin instalar nada) y
tokens de acceso propios al estilo **Laravel Sanctum**. El frontend es la app Android de
[`../unabgo-android`](../unabgo-android/README.md) (Kotlin con Jetpack Compose).

## Requisitos

- JDK 21 (`java -version`); el proyecto compila a bytecode de Java 21
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

# 4. Pruebas automatizadas (base de datos H2 en memoria, migraciones en cada prueba, APIs externas simuladas)
./gradlew test
```

El archivo `.env` (copia de `.env.example`) es la única configuración: base de datos, tokens y las claves de los
servicios externos. No se sube al repositorio. Cada servicio externo es opcional; si falta su variable, el
endpoint que lo necesita responde `503` con un mensaje claro y el resto de la API sigue funcionando.

Otros comandos de base de datos:

| Laravel | Este proyecto |
|---|---|
| `php artisan migrate` | `./gradlew migrate` |
| `php artisan migrate:fresh --seed` | `./gradlew migrateFresh` |
| `php artisan db:seed` | `./gradlew dbSeed` |

Para usar PostgreSQL: `docker compose up -d` y en `.env`
`DB_URL=jdbc:postgresql://localhost:5432/unabgo`, `DB_USER=unabgo`, `DB_PASSWORD=unabgo`.

## Usuarios de prueba (solo desarrollo)

Los crea `migrateFresh`:

| Rol | Usuario (correo) | Contraseña |
|---|---|---|
| ADMINISTRADOR | `demo.admin@unab.edu.co` | `Unab2026*` |
| GESTOR (Bienestar) | `demo.bienestar@unab.edu.co` | `Unab2026*` |
| GESTOR (Transporte) | `demo.transporte@unab.edu.co` | `Unab2026*` |
| ESTUDIANTE | `demo.estudiante@unab.edu.co` (Ingeniería de Sistemas) | `Unab2026*` |

Además se generan 11 estudiantes con las factories (misma contraseña, correos aleatorios que se ven en
`GET /api/v1/admin/users`).

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

- 7 migraciones Flyway en `src/main/resources/db/migration` (válidas en PostgreSQL y H2).
- 17 modelos y 2 tablas intermedias, mapeados con Exposed en `src/main/kotlin/.../models`:
  `Role`, `User`, `StudentProfile`, `AccessToken`, `Program`, `Subject`, `ScheduleBlock`, `GradeCategory`,
  `GradeItem`, `BusRoute`, `BusStop`, `Announcement`, `Faq`, `Professor`, `StudyPlan`, `PlanSemester`, `PlanCourse` + `route_stops` y `favorite_routes`.
- Relaciones:
  - **1:1** `User` ↔ `StudentProfile` (`optionalBackReferencedOn`)
  - **1:N** `Role` → `User`, `User` → `Subject` → `ScheduleBlock` / `GradeCategory` → `GradeItem`, `User` → `Announcement` / `Faq` / `AccessToken`, `Program` → `StudentProfile` (`referrersOn`), `StudyPlan` → `PlanSemester` → `PlanCourse`
  - **N:1** `StudyPlan` → `Program` (`optionalReferencedOn`, el plan se vincula al programa por slug o nombre)
  - **N:M** `BusRoute` ↔ `BusStop` (con orden) y `User` ↔ `BusRoute` favoritas (`via`)
- La asignación masiva se controla con clases de solicitud: solo los campos declarados en
  `http/requests` se pueden asignar (equivalente a `$fillable`).

### Datos: factories y seeders

- `database/factories/Factories.kt`: factories con **Datafaker** en español (usuarios, perfiles, materias,
  horarios, cortes, notas, avisos y preguntas), con semilla fija para que siempre generen lo mismo.
- `database/seeders/DatabaseSeeder.kt`: seeder principal que ejecuta `RoleSeeder`, `ProgramSeeder`
  (los 228 programas reales de unab.edu.co), `UserSeeder`, `BusSeeder` (las 4 rutas del GPS de las Rutas
  UNAB y las sedes), `AcademicSeeder` y `ContentSeeder` (las 58 preguntas frecuentes reales de Banu) `ProfessorSeeder` (profesores reales con datos
  públicos de APOLO, ver más abajo) y `StudyPlanSeeder` (los 133 planes de estudio de unab.edu.co con 618 semestres y
  3951 cursos, ver más abajo). `./gradlew migrateFresh` imprime las filas de cada tabla, incluidas `study_plans`,
  `plan_semesters` y `plan_courses`.

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
  `HealthController`, `BanuController`, `GpsWebhookController`, `ProfessorController`, `StudyPlanController`, `ScheduleController`.
- Validación tipo **FormRequest** en `http/Validation.kt` y `http/requests`, con mensajes en español.
- Respuestas JSON en snake_case: `{ "data": ..., "meta": ... }` y errores
  `{ "error", "code", "fields" }` con códigos 200, 201, 202, 204, 400, 401, 403, 404, 409, 429, 500 y 503.

## Endpoints

Son 41 endpoints. La referencia completa (campos, reglas de validación, ejemplos de respuesta y errores) está en [`docs/API.md`](docs/API.md).

| Método | URI | Acceso | Estado |
|---|---|---|---|
| GET | `/api/v1/health` | Público | ✔ |
| POST | `/api/v1/auth/register` | Público | ✔ Con carrera, semestre e idioma |
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
| GET | `/api/v1/schedule/next` | Estudiante | ✔ Próxima clase |
| GET | `/api/v1/programs`, `/api/v1/programs/{slug}` | Público | ✔ `has_plan=true`: carreras con plan |
| GET | `/api/v1/bus/routes` | Público | ✔ |
| PUT | `/api/v1/bus/routes/{id}` | Gestor | ✔ |
| GET | `/api/v1/announcements` | Público | ✔ |
| POST/PUT/DELETE | `/api/v1/announcements[/{id}]` | Gestor / autor | ✔ |
| GET/POST/PUT/DELETE | `/api/v1/faqs[/{id}]` | Gestor | ✔ |
| GET | `/api/v1/admin/users` | Administrador | ✔ |
| PATCH | `/api/v1/admin/users/{id}/role` | Administrador | ✔ |
| GET | `/api/v1/bus/live` | Público | ✔ API GPS / webhook |
| POST | `/api/v1/auth/google` | Público | ✔ Firebase / Google |
| POST | `/api/v1/banu/ask` | Público (límite por IP) | ✔ FAQ + planes + IA |
| POST | `/api/v1/admin/programs/sync` | Administrador | ✔ WordPress |
| POST | `/api/v1/webhooks/gps` | Firma HMAC | ✔ Webhook |
| GET | `/api/v1/professors`, `/api/v1/professors/{slug}` | Público | ✔ Datos de APOLO |
| GET | `/api/v1/study-plans`, `/api/v1/study-plans/{slug}` | Público | ✔ Planes de estudio |

Todas las respuestas exitosas usan `{ "data": ..., "meta": ... }` y los errores
`{ "error": "mensaje", "code": "CODIGO", "fields": { "campo": "mensaje" } }`.

## Integraciones con APIs externas (entrega 3)

Cada integración es una interfaz en `services/integrations` con una implementación real (cliente HTTP de Ktor).
Los controladores y los modelos nunca hacen peticiones HTTP: solo llaman a los servicios. Las integraciones se
inyectan en `Application.module(config, integrations)`, así las pruebas las reemplazan con `MockEngine`.

| Servicio | Interfaz → implementación | Variables del `.env` |
|---|---|---|
| Plataforma GPS de las Rutas UNAB | `GpsFeedService` → `HttpGpsFeedService` | `GPS_FEED_URL` |
| Webhook de dispositivos a bordo | `GpsWebhookController` | `GPS_WEBHOOK_SECRET` |
| Notificaciones push (Firebase Cloud Messaging HTTP v1) | `PushNotifier` → `FcmPushNotifier` (o `LogPushNotifier`) | `FIREBASE_PROJECT_ID`, `FIREBASE_CREDENTIALS` |
| Inicio de sesión con Google (Firebase Auth) | `GoogleTokenVerifier` → `FirebaseIdTokenVerifier` | `FIREBASE_PROJECT_ID` |
| IA de Banu (API compatible con OpenAI) | `LlmClient` → `OpenAiCompatibleLlmClient` | `BANU_LLM_URL`, `BANU_LLM_KEY`, `BANU_LLM_MODEL` |
| Sitio web unab.edu.co (API REST de WordPress) | `UnabWebService` → `WordPressUnabWebService` | `UNAB_WP_URL` |

Otras variables: `BANU_RATE_LIMIT_PER_MINUTE` (preguntas por minuto y por IP, 20 por defecto). El detalle de
cada variable está comentado en `.env.example`.

### GET /api/v1/bus/live

Lee `GPS_FEED_URL` (arreglo JSON de `{id, placa, lat, lng, sentido, fhEvento, estadoIgnicion, evento}`; la hora
`fhEvento` es hora local de Colombia, UTC-5, sin zona). Las placas `RUTA1`, `RUTA2`, `RUTA02` y `RUTA3` se enlazan con
`bus_routes.code`. Reglas: estado `MOVING` si dos reportes consecutivos dan 3 km/h o más (o, con un solo reporte,
si el evento dice "Movimiento" o "Speed"), `PARKED` si la ignición está apagada o el evento es "Estacionado", y
`STOPPED` en los demás casos; se descartan los reportes de más de 5 minutos (30 si está estacionado); la respuesta se
guarda 5 segundos en caché. Si `GPS_FEED_URL` no está configurada, o el proveedor falla y pasó más de un minuto desde
el último dato bueno, responde `503`. Sin plataforma configurada, `/bus/live` muestra las posiciones recibidas por
el webhook.

### POST /api/v1/webhooks/gps

El dispositivo envía `{"device_id","lat","lng","speed_kmh","heading","ignition","timestamp"}` con el encabezado
`X-Signature` = HMAC-SHA256 en hexadecimal del cuerpo exacto, usando `GPS_WEBHOOK_SECRET`. `401` si la firma no
coincide, `400` si el cuerpo no es válido, `202` si se aceptó. Se guarda en memoria la última posición de cada dispositivo.

```bash
BODY='{"device_id":"RUTA1","lat":7.1167,"lng":-73.1052,"speed_kmh":20,"ignition":true}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$GPS_WEBHOOK_SECRET" | awk '{print $NF}')
curl -X POST http://localhost:8080/api/v1/webhooks/gps -H "Content-Type: application/json" -H "X-Signature: $SIG" -d "$BODY"
```

### POST /api/v1/banu/ask

`{"pregunta": "..."}` (3 a 500 caracteres). Busca primero en las preguntas frecuentes (coincidencia de palabras sin
tildes); si no hay una coincidencia segura y la IA está configurada, la consulta con las preguntas y los programas más
parecidos como contexto. Si la pregunta menciona un programa y palabras como pensum, plan de estudios, materias,
semestre o créditos, Banu usa el plan de estudios de la base: lo envía al modelo como contexto (el semestre pedido o un
resumen) y agrega su `plan_url` a las `fuentes`. Responde
`{"data": {"respuesta", "fuentes": [{"titulo","url"}], "origen": "faq" | "plan" | "ia"}}`.
Es público, con un límite de peticiones por IP (`429`).

#### Configuración de la IA con Ollama Cloud

La IA es opcional. Se activa con tres variables del `.env` (nunca subas la clave real al repositorio):

```
BANU_LLM_URL=https://ollama.com/v1
BANU_LLM_MODEL=gpt-oss:20b
BANU_LLM_KEY=<clave creada en ollama.com/settings/keys>
```

El cliente (`OpenAiCompatibleLlmClient`) llama a `{BANU_LLM_URL}/chat/completions` con `Authorization: Bearer`, `model`,
`messages`, `max_tokens` de 1500 (los modelos con razonamiento gastan parte pensando) y 30 segundos de límite. Sin las
tres variables Banu funciona solo con las preguntas frecuentes y los planes. Si el proveedor responde con error
(HTTP 402 por falta de créditos, 429 por límite de uso, 5xx, tiempo agotado o respuesta vacía) la petición no falla:
Banu responde con `origen` `plan` si la pregunta era de un plan de estudios, o con la pregunta frecuente más parecida
(`faq`), y deja una advertencia en el log. Con la IA activa y respuesta correcta, `origen` es `ia`.

### POST /api/v1/auth/google

`{"id_token": "..."}` con el ID token de Firebase Auth. Se verifica la firma RS256 con las claves públicas de Google,
el emisor `https://securetoken.google.com/{FIREBASE_PROJECT_ID}`, la audiencia y el vencimiento. Solo se aceptan correos
`@unab.edu.co` (`403` si no); si el estudiante no existe se crea y se entrega el token de la API.

### POST /api/v1/admin/programs/sync

El administrador sincroniza los programas desde `UNAB_WP_URL` (tipo de contenido `project` de WordPress: categorías,
etiquetas, título y enlace). Crea los que faltan y actualiza nombre, facultad, enlace y modalidad de los existentes por
`slug`. Responde `{"data": {"fetched","created","updated","unchanged"}}`.

### Profesores (APOLO)

`GET /api/v1/professors?q=&unit=&page=&per_page=` y `GET /api/v1/professors/{slug}`. Los datos son públicos y vienen de
APOLO (apolo.unab.edu.co). `ProfessorSeeder` lee `src/main/resources/seed/professors.json`; si el archivo no existe se
omite sin error. Para generarlo a partir de un archivo de profesores recolectado de APOLO (`unab_profesores.json`):

```bash
python scripts/convert_professors.py ruta/a/unab_profesores.json
./gradlew migrateFresh
```

### Planes de estudio

`GET /api/v1/study-plans?q=&level=&page=&per_page=` y `GET /api/v1/study-plans/{slug}`; `GET /api/v1/programs/{slug}`
incluye `study_plan_slug` cuando el programa tiene un plan vinculado. Los datos son públicos y vienen de unab.edu.co.
Tablas (migración `V7`): `study_plans`, `plan_semesters` y `plan_courses`. `StudyPlanSeeder` lee
`src/main/resources/seed/study_plans.json`; si el archivo no existe se omite sin error. Para regenerarlo a partir de la
archivo de planes recolectado de unab.edu.co (`unab_planes_estudio.json`):

```bash
python scripts/convert_study_plans.py ruta/a/unab_planes_estudio.json
./gradlew migrateFresh
```

## Pruebas automatizadas

```bash
./gradlew test --no-daemon
```

Las pruebas usan H2 en memoria (base nueva con migraciones y seeders en cada prueba) y **Ktor MockEngine** para todas
las APIs externas: no salen a internet ni necesitan claves. Son 114 pruebas en total (incluye `ScheduleNextTest` para `GET /schedule/next`). Cubren todas las rutas de `ApiRoutes.kt` con su caso exitoso y
sus errores principales (400, 401, 403, 404, 409, 429 y 503): autenticación, autorización, materias y notas, rutas del bus,
GPS en vivo, webhook firmado, Banu (FAQ, IA, límite), Google (con una clave RSA generada en la prueba), push de FCM,
sincronización con WordPress, profesores, planes de estudio y Banu con planes (con el proveedor de IA caído, HTTP 402 y 429). Los reportes quedan en `build/reports/tests/test/index.html`.

## Estructura

```
docs/API.md               referencia de la API
src/main/kotlin/co/edu/unab/unabgo/backend/
  Application.kt          arranque, plugins y autenticación por token
  config/                 lectura de .env
  console/                comandos migrate, migrate:fresh --seed, db:seed
  database/               conexión, Tables.kt (mapeo), factories/ y seeders/
  models/                 modelos con sus relaciones
  auth/                   roles, tokens, Gates y Policies
  http/                   controllers/, requests/, resources/, routes/, errores y validación
  services/               reglas de negocio (notas, horario, avisos, Banu, bus en vivo) e integrations/ (APIs externas)
src/main/resources/
  db/migration/           migraciones Flyway
  seed/                   datos reales de programas, preguntas frecuentes, profesores y planes de estudio
src/test/kotlin/          pruebas con JUnit, H2 en memoria y MockEngine
scripts/                  convert_professors.py y convert_study_plans.py (generan los archivos de seed/)
```

## Ajustes respecto al diccionario de datos de la primera entrega

- Las fechas se guardan como `TIMESTAMP` en UTC (en vez de `TIMESTAMPTZ`) para que las migraciones sirvan
  igual en PostgreSQL y en H2.
- `roles.id` es `INTEGER` y la columna de orden se llama `sort_order` (en vez de `position`, que es palabra
  reservada en algunos motores).
- El token usa `.` para separar el id del secreto, porque `|` no es válido en el encabezado `Authorization`.
