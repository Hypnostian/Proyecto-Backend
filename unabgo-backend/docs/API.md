# Referencia de la API REST de UNAB GO!

Base: `http://localhost:8080/api/v1` (desde el emulador de Android: `http://10.0.2.2:8080/api/v1`).
Total: **41 endpoints** (los 39 anteriores y los 2 de planes de estudio, `GET /study-plans` y `GET /study-plans/{slug}`). Fuente de verdad: `http/routes/ApiRoutes.kt`.

## Convenciones

- Todo viaja en JSON con nombres en `snake_case`. Las peticiones con cuerpo llevan `Content-Type: application/json`.
- Autenticación: `Authorization: Bearer {id}.{secreto}`. El token se obtiene en `POST /auth/login`, `/auth/register` o `/auth/google` y se muestra una sola vez.
- Respuesta exitosa: `{ "data": ..., "meta": ... }`. `meta` es `null`, salvo en listados paginados:
  `{ "page": 1, "per_page": 15, "total": 6, "last_page": 1 }`. Los listados aceptan `?page=` y `?per_page=` (máximo 50).
  `GET /health` es la única excepción: devuelve el objeto sin sobre.
- Las respuestas `204` no llevan cuerpo.
- Fechas en ISO 8601 UTC (`2026-11-15T22:02:34Z`); las fechas simples como `AAAA-MM-DD`; las horas del horario como `HH:mm`.
- `day_of_week`: 1 = lunes, 7 = domingo.

### Formato de error

```json
{ "error": "Los datos enviados no son válidos.", "code": "VALIDATION_ERROR", "fields": { "email": "El correo es obligatorio." } }
```

`fields` solo trae contenido en los errores de validación (en los demás es `null`).

| Estado | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Algún campo no cumple las reglas, o un bloque de horario se cruza con otro (`fields` indica cuál). Esta API usa 400, no 422. |
| 400 | `BAD_REQUEST` | JSON mal formado o encabezados inválidos. |
| 401 | `UNAUTHENTICATED` | Falta el token, está vencido o fue revocado; credenciales incorrectas; firma del webhook inválida. |
| 403 | `FORBIDDEN` | El rol no tiene la habilidad (Gate) o el recurso es de otro usuario (Policy). |
| 404 | `NOT_FOUND` | El recurso o la ruta no existe (también un id no numérico). |
| 409 | `CONFLICT` | Correo ya registrado. |
| 429 | `TOO_MANY_REQUESTS` | Demasiadas preguntas a Banu desde la misma IP. |
| 500 | `SERVER_ERROR` | Error interno (sin detalles técnicos). |
| 503 | `SERVICE_UNAVAILABLE` | Un servicio externo no está configurado o no responde. |

Códigos de éxito: 200, 201 (creación), 202 (webhook aceptado) y 204 (sin contenido).

## Roles y quién puede llamar

| Acceso | Significado |
|---|---|
| Público | Sin token. |
| Token | Cualquier usuario autenticado. |
| Estudiante | Rol ESTUDIANTE (Gate `MANAGE_ACADEMIC`). |
| Dueño | Estudiante, y el recurso debe ser suyo (`SubjectPolicy`); si es de otro, 403. |
| Gestor | GESTOR o ADMINISTRADOR. |
| Autor o administrador | `AnnouncementPolicy`: el autor del aviso o un ADMINISTRADOR. |
| Administrador | Solo ADMINISTRADOR. |

## Cuentas de prueba (solo desarrollo)

Las crea `./gradlew migrateFresh`; todas con la contraseña `Unab2026*`.

| Rol | Correo |
|---|---|
| ADMINISTRADOR | `demo.admin@unab.edu.co` |
| GESTOR (Bienestar) | `demo.bienestar@unab.edu.co` |
| GESTOR (Transporte) | `demo.transporte@unab.edu.co` |
| ESTUDIANTE | `demo.estudiante@unab.edu.co` (con materias, horario y notas) |

---

## 1. Estado

### GET /health
Público. Estado del servicio y de la base.
```json
{ "status": "ok", "database": "ok", "version": "0.3.0" }
```
Errores: 500.

## 2. Autenticación y perfil

### POST /auth/register
Público. Crea un estudiante, su perfil y emite su token. Respuesta 201 (misma forma que el login). La carrera y el semestre
quedan en el perfil desde el registro: con ellos el cliente muestra solo el plan de estudios de esa carrera.

| Campo | Reglas |
|---|---|
| `name` | obligatorio, máx. 120 |
| `email` | obligatorio, correo válido terminado en `@unab.edu.co`, máx. 160 |
| `password` | obligatoria, mínimo 8, con letras y números |
| `password_confirmation` | igual a `password` |
| `device_name` | opcional |
| `program_id` | opcional, id de un programa existente (400 con el campo `program_id` si no existe; no se crea la cuenta) |
| `semester` | opcional, 1 a 12 |
| `language` | opcional, `es`, `en`, `fr`, `pt` o `ko` (idioma en que el usuario usa la app) |

```json
{ "name": "Ana Gómez", "email": "ana.gomez@unab.edu.co", "password": "clave2026", "password_confirmation": "clave2026",
  "program_id": 188, "semester": 6, "language": "en" }
```
Errores: 400, 409 (correo ya registrado).

### POST /auth/login
Público. Campos: `email` (obligatorio, correo válido), `password` (obligatoria), `device_name` (opcional). Respuesta 200:
```json
{ "data": { "token": "1.jg-CtBNfudDgzu9...", "token_type": "Bearer", "expires_at": "2026-12-15T22:03:01Z",
  "user": { "id": 4, "name": "Estudiante de Prueba", "email": "demo.estudiante@unab.edu.co", "role": "ESTUDIANTE", "active": true,
    "profile": { "student_code": "U00203799", "semester": 1, "language": "es", "reminder_minutes": 0,
      "program": { "id": 188, "name": "Ingeniería de Sistemas", "faculty": "Facultad de Ingeniería",
        "level": "Profesional", "study_plan_slug": "ingenieria-de-sistemas" } },
    "created_at": "2026-11-15T22:02:35Z" } }, "meta": null }
```
Errores: 400, 401 (credenciales incorrectas o cuenta desactivada).

### POST /auth/google
Público. Cambia un ID token de Firebase (cuenta institucional) por un token de la API. Campos: `id_token` (obligatorio, máx. 4096), `device_name` (máx. 60). Respuesta 200 igual al login. Errores: 400, 401 (token inválido), 403 (correo que no es `@unab.edu.co`), 503 (sin `FIREBASE_PROJECT_ID`).

### POST /auth/logout
Token. Revoca el token actual. Respuesta 204. Errores: 401.

### GET /me
Token. Datos del usuario, su rol y su perfil (forma de `user` del login). `profile.program.study_plan_slug` es el plan de
estudios de su carrera (`null` si la carrera no tiene plan) y `profile.language` el idioma que eligió: el cliente lo aplica al
iniciar sesión, así cada persona encuentra la app en el idioma en que la dejó. Errores: 401.

### PUT /me/profile
Token. Actualiza solo los campos enviados (por ejemplo, `{"language":"en"}` cambia solo el idioma). Sirve para los tres
roles: si el usuario no tiene perfil, se crea. Devuelve el usuario (200).

| Campo | Reglas |
|---|---|
| `program_id` | id de un programa existente (404 si no existe) |
| `student_code` | máx. 20 |
| `semester` | 1 a 12 |
| `language` | `es`, `en`, `fr`, `pt` o `ko` |
| `reminder_minutes` | 0, 30 o 60 |

Errores: 400, 401, 404.

### GET /me/favorite-routes
Token. Rutas favoritas del usuario (lista de rutas, ver `GET /bus/routes`). Errores: 401.

### POST /me/favorite-routes/{routeId}
Token. Marca la ruta como favorita. Respuesta 201 con la lista de favoritas. Errores: 401, 404.

### DELETE /me/favorite-routes/{routeId}
Token. Quita la ruta de favoritas. Respuesta 204. Errores: 401, 404.

## 3. Materias, horario y notas (estudiante)

Objeto materia:
```json
{ "id": 1, "name": "Cálculo Diferencial", "credits": 2, "teacher": "Sara Colunga", "color": "#2563EB",
  "blocks": [ { "id": 1, "day_of_week": 1, "start_time": "07:00", "end_time": "09:00", "room": "Edificio C, salón 388" } ],
  "categories": null }
```
`categories` es `null` en el listado y trae los cortes con sus notas en el detalle y en las respuestas de creación y edición:
`[ { "id": 1, "name": "Corte 1", "weight": 30.0, "sort_order": 1, "items": [ { "id": 1, "name": "Parcial 1", "score": 2.4, "weight": null, "graded_at": "2026-10-12" } ] } ]`.

### GET /subjects
Estudiante. Sus materias ordenadas por nombre (200). Errores: 401, 403.

### POST /subjects
Estudiante. Crea una materia con su horario. Respuesta 201 con la materia.

| Campo | Reglas |
|---|---|
| `name` | obligatorio, máx. 120 |
| `credits` | obligatorio, 0 a 10 |
| `teacher` | opcional, máx. 120 |
| `color` | obligatorio, `#RRGGBB` |
| `blocks` | lista de máximo 7 bloques (puede ir vacía) |
| `blocks[].day_of_week` | obligatorio, 1 a 7 |
| `blocks[].start_time` | obligatorio, `HH:mm` |
| `blocks[].end_time` | obligatorio, `HH:mm`, posterior a `start_time` |
| `blocks[].room` | opcional, máx. 40 |

Los bloques no pueden cruzarse entre sí ni con los de las demás materias del estudiante: se responde 400 con `fields` como `{"blocks.0": "Se cruza con Cálculo Diferencial (07:00–09:00)."}`.
Errores: 400, 401, 403.

### GET /subjects/{id}
Dueño. Detalle con cortes y notas (200). Errores: 401, 403, 404.

### PUT /subjects/{id}
Dueño. Mismos campos y reglas que `POST /subjects`; reemplaza el horario completo (el cruce se revisa ignorando la propia materia). Respuesta 200. Errores: 400, 401, 403, 404.

### DELETE /subjects/{id}
Dueño. Borra la materia con su horario, cortes y notas. Respuesta 204. Errores: 401, 403, 404.

### POST /subjects/{id}/categories
Dueño. Crea un corte. Respuesta 201.

| Campo | Reglas |
|---|---|
| `name` | obligatorio, máx. 60 |
| `weight` | obligatorio, 0 a 100; la suma de los cortes no puede pasar de 100 % |
| `sort_order` | opcional, 1 a 20 |

Errores: 400, 401, 403, 404.

### POST /categories/{id}/items
Dueño de la materia del corte. Registra una nota. Respuesta 201.

| Campo | Reglas |
|---|---|
| `name` | obligatorio, máx. 80 |
| `score` | opcional (sin nota queda pendiente), 0.0 a 5.0, una décima |
| `weight` | opcional, 0 a 100 |
| `graded_at` | opcional, `AAAA-MM-DD` |

Errores: 400, 401, 403, 404.

### GET /subjects/{id}/summary
Dueño. Promedio, acumulado y nota necesaria para aprobar (Reglamento Estudiantil, Arts. 39 y 44).
```json
{ "data": { "subject_id": 1, "average": 2.4, "accumulated": 1.08, "evaluated_percent": 45.0, "needed_to_pass": 3.5,
  "final_grade": null, "passed_already": false, "cannot_pass": false, "habilitation_needed": null, "warnings": [] }, "meta": null }
```
Errores: 401, 403, 404.

### GET /schedule/next
Estudiante. Próxima clase calculada con los bloques de las materias del estudiante (los que se crean con `POST /subjects` y `PUT /subjects/{id}`), en hora local de Bucaramanga (`America/Bogota`).
Si hay una clase en curso, esa es la próxima (`in_progress: true`, `starts_in_minutes: 0`). Si ya pasaron todas las de la semana, devuelve la primera de la semana siguiente.
Si el estudiante no tiene bloques de horario responde **200 con `data: null`**.

Parámetro opcional `?at=AAAA-MM-DDTHH:mm`: fija la hora local de referencia (útil para pruebas y demostraciones); por defecto es el momento actual.

```json
{ "data": { "subject_id": 1, "subject_name": "Cálculo Diferencial", "color": "#2563EB", "teacher": "Sara Colunga",
  "block_id": 1, "date": "2026-11-16", "day_of_week": 1, "start_time": "07:00", "end_time": "09:00",
  "room": "Edificio C, salón 388", "in_progress": false, "starts_in_minutes": 837 }, "meta": null }
```
Sin clases: `{ "data": null, "meta": null }`.
Errores: 400 (`at` con formato inválido), 401, 403 (no es estudiante).

## 4. Programas académicos

### GET /programs
Público. Paginado. Filtros: `faculty`, `level`, `max_price` (número, COP), `q` (texto en el nombre), `has_plan=true` (solo los
programas con plan de estudios: es la lista de carreras del registro, 130 de 228), `page`, `per_page`. Errores: 400 (`max_price` no numérico).
```json
{ "data": [ { "id": 184, "slug": "administracion-de-empresas", "name": "Administración de Empresas",
  "faculty": "Facultad de Economía y Negocios", "level": "Profesional", "modality": "Presencial", "price_cop": 8355360,
  "snies": "1641", "url": "https://unab.edu.co/programas/administracion-de-empresas/", "verified_at": "2026-11-15T22:02:34Z",
  "study_plan_slug": "administracion-de-empresas" } ],
  "meta": { "page": 1, "per_page": 1, "total": 228, "last_page": 228 } }
```

### GET /programs/{slug}
Público. Un programa (misma forma). `study_plan_slug` es el slug de su plan de estudios (ver `GET /study-plans/{slug}`) o `null` si no tiene uno vinculado. Errores: 404.

## 5. Rutas UNAB (bus)

### GET /bus/routes
Público. Rutas con sus paradas en orden.
```json
{ "data": [ { "id": 3, "code": "RUTA02", "name": "Ruta 02", "color": "#0E9F8E", "active": true,
  "stops": [ { "id": 1, "name": "Campus El Jardín", "lat": 7.116824, "lon": -73.10558, "is_campus": true } ] } ], "meta": null }
```

### GET /bus/live
Público. Buses en vivo (plataforma GPS de las Rutas UNAB o, si no está configurada, posiciones recibidas por el webhook). Caché de 5 segundos.
```json
{ "data": [ { "device_id": "RUTA1", "plate": "RUTA1", "route": { "id": 1, "code": "RUTA1", "name": "Ruta 1", "color": "#8600DD" },
  "lat": 7.1167, "lng": -73.1052, "state": "MOVING", "speed_kmh": 20.0, "heading_deg": 90.0, "heading": "E",
  "reported_at": "2026-11-15T22:00:00Z", "age_seconds": 12, "source": "feed" } ], "meta": null }
```
`state`: `MOVING`, `STOPPED` o `PARKED`. Errores: 503 (sin plataforma configurada ni dispositivos reportando; es lo que ocurre con el `.env` por defecto).

### PUT /bus/routes/{id}
Gestor. Edita la ruta y el orden de sus paradas. Respuesta 200 con la ruta.

| Campo | Reglas |
|---|---|
| `name` | obligatorio, máx. 80 |
| `color` | obligatorio, `#RRGGBB` |
| `active` | booleano, por defecto `true` |
| `stop_ids` | al menos 2 ids de paradas existentes, sin repetir |

Errores: 400, 401, 403, 404.

### POST /webhooks/gps
Firma HMAC. Posición de un dispositivo a bordo. Encabezado `X-Signature` = HMAC-SHA256 en hexadecimal del cuerpo exacto con `GPS_WEBHOOK_SECRET`. Respuesta 202 `{ "data": { "message": "Posición recibida." } }`.

| Campo | Reglas |
|---|---|
| `device_id` | obligatorio, máx. 40 |
| `lat` | obligatoria, -90 a 90 |
| `lng` | obligatoria, -180 a 180 |
| `speed_kmh` | opcional, 0 a 250 |
| `heading` | opcional, 0 a 360 |
| `ignition` | opcional, booleano |
| `timestamp` | opcional, ISO 8601 con zona |

Errores: 400, 401 (firma ausente o inválida), 503 (sin `GPS_WEBHOOK_SECRET`).

## 6. Avisos

Objeto aviso: `{ "id": 4, "title": "...", "body": "...", "category": "transporte", "author": "Bienestar Universitario", "published_at": "2026-11-12T23:02:37Z", "push_sent_at": "2026-11-12T23:02:37Z", "created_at": "2026-11-12T22:02:37Z" }`.

### GET /announcements
Público. Avisos publicados, el más reciente primero. Filtro `?category=`, paginado.

### POST /announcements
Gestor. Respuesta 201. Con `publish: true` se publica y se envía una notificación push (FCM).

| Campo | Reglas |
|---|---|
| `title` | obligatorio, máx. 140 |
| `body` | obligatorio, máx. 5000 |
| `category` | obligatoria: `academico`, `bienestar`, `transporte` o `evento` |
| `publish` | booleano, por defecto `false` |

Errores: 400, 401, 403.

### PUT /announcements/{id}
Autor o administrador. Mismos campos; respuesta 200. Errores: 400, 401, 403, 404.

### DELETE /announcements/{id}
Autor o administrador. Respuesta 204. Errores: 401, 403, 404.

## 7. Banu y preguntas frecuentes

### POST /banu/ask
Público, con límite de peticiones por IP. Campo `pregunta` (obligatorio, 3 a 500 caracteres). Responde con una pregunta frecuente, con los datos del plan de estudios (si la pregunta es sobre pensum, materias, semestres o créditos de un programa) o, si hay IA configurada, con ella. Con IA, el plan se envía al modelo como contexto; si el proveedor falla (por ejemplo HTTP 402 por falta de créditos o 429 por límite), Banu responde igual con los datos del plan o con la pregunta frecuente más parecida, sin error.
```json
{ "data": { "respuesta": "En Punto U Digital puedes adelantar gestiones...", "fuentes": [ { "titulo": "¿Dónde hago trámites de matrícula?", "url": "https://unab.edu.co/ayudamatricula/" } ], "origen": "faq" }, "meta": null }
```
`origen`: `faq` (pregunta frecuente), `plan` (datos de los planes de estudio, sin IA) o `ia` (modelo de lenguaje). `fuentes` incluye el `plan_url` cuando se usó un plan. Errores: 400, 429.
```json
{ "data": { "respuesta": "El quinto semestre de Ingeniería de Sistemas tiene 17 créditos y estas materias: Desarrollo backend (3 créditos); ...",
  "fuentes": [ { "titulo": "Plan de estudios de Ingeniería de Sistemas", "url": "https://admisiones.unab.edu.co/modulos/planEstudio/planEstudio.php?token=..." } ],
  "origen": "plan" }, "meta": null }
```

### GET /faqs
Gestor. Lista las preguntas frecuentes: `{ "id", "question", "keywords", "answer", "source_url", "active" }`. Errores: 401, 403.

### POST /faqs
Gestor. Respuesta 201.

| Campo | Reglas |
|---|---|
| `question` | obligatoria, máx. 200 |
| `keywords` | obligatorio |
| `answer` | obligatoria |
| `source_url` | obligatoria, enlace `https://`, máx. 300 |
| `active` | booleano, por defecto `true` |

Errores: 400, 401, 403.

### PUT /faqs/{id}
Gestor. Mismos campos; respuesta 200. Errores: 400, 401, 403, 404.

### DELETE /faqs/{id}
Gestor. La desactiva (Banu deja de usarla). Respuesta 204. Errores: 401, 403, 404.

## 8. Profesores (datos públicos de APOLO)

### GET /professors
Público. Paginado. Filtros `q` (nombre) y `unit` (unidad académica).
```json
{ "data": [ { "id": 1, "slug": "abby-tatiana-vargas-miranda", "name": "Abby Tatiana Vargas Miranda", "title": null,
  "unit": "Comunicación Social", "unit_url": "https://apolo.unab.edu.co/es/organisations/comunicaci%C3%B3n-social/",
  "apolo_url": "https://apolo.unab.edu.co/es/persons/abby-tatiana-vargas-miranda/", "cvlac_url": null } ],
  "meta": { "page": 1, "per_page": 1, "total": 1537, "last_page": 1537 } }
```

### GET /professors/{slug}
Público. Un profesor. Errores: 404.

## 9. Planes de estudio

Datos públicos de unab.edu.co (133 planes de pregrado, tecnologías, técnicos, especializaciones, maestrías y doctorados, con 618 semestres y 3951 cursos en el seed). `level` usa las mismas etiquetas que los programas: Profesional, Tecnología, Técnico, Especialización, Maestría o Doctorado. Hay dos estructuras: `semestres` (con el detalle de cursos por semestre) y `texto` (planes que solo existen como documento: no tienen semestres y traen el `text` del documento).

### GET /study-plans
Público. Paginado, ordenado por nombre del programa. Filtros: `q` (nombre del programa, sin distinguir tildes ni mayúsculas), `level` (por ejemplo `Profesional`, sin distinguir mayúsculas), `page`, `per_page`.
```json
{ "data": [ { "slug": "ingenieria-de-sistemas", "program_name": "Ingeniería de Sistemas", "level": "Profesional",
  "structure": "semestres", "total_credits": 151, "semesters_count": 9,
  "plan_url": "https://drive.google.com/file/d/..." } ],
  "meta": { "page": 1, "per_page": 15, "total": 1, "last_page": 1 } }
```
`level` puede ser `null` y `total_credits` es `null` en los planes de solo texto.

### GET /study-plans/{slug}
Público. El plan completo. Los semestres vienen ordenados por `number` y los cursos en el orden del plan. En un plan de solo texto `semesters` es `[]` y `text` trae el documento; en uno con semestres `text` es `null`. `program` es el programa de `/programs` al que se vinculó el plan, o `null`. Errores: 404.
```json
{ "data": { "slug": "ingenieria-de-sistemas", "program_name": "Ingeniería de Sistemas", "level": "Profesional",
  "structure": "semestres", "total_credits": 151, "plan_url": "https://drive.google.com/file/d/...",
  "source_url": "https://unab.edu.co/programas/ingenieria-de-sistemas/",
  "program": { "id": 188, "name": "Ingeniería de Sistemas", "slug": "ingenieria-de-sistemas" },
  "semesters": [ { "number": 1, "name": "Primer semestre", "credits": 16,
    "courses": [ { "name": "Álgebra lineal", "credits": 3 }, { "name": "Cálculo diferencial", "credits": 4 } ] } ],
  "text": null }, "meta": null }
```

## 10. Administración

### GET /admin/users
Administrador. Usuarios paginados, filtro `?role=ESTUDIANTE|GESTOR|ADMINISTRADOR`. Errores: 401, 403.

### PATCH /admin/users/{id}/role
Administrador. Campo `role` (obligatorio: `ESTUDIANTE`, `GESTOR` o `ADMINISTRADOR`). Respuesta 200 con el usuario. El administrador no puede cambiar su propio rol (403). Errores: 400, 401, 403, 404.

### POST /admin/programs/sync
Administrador. Sincroniza los programas desde unab.edu.co (WordPress). Sin cuerpo.
```json
{ "data": { "fetched": 228, "created": 0, "updated": 3, "unchanged": 225 }, "meta": null }
```
Errores: 401, 403, 503 (sin `UNAB_WP_URL` o sitio caído).
