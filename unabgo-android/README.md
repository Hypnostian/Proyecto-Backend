# UNAB GO Cliente (Android)

Aplicación Android que consume la API de `../unabgo-backend` y recorre los flujos completos de los tres roles
(estudiante, gestor y administrador). Es el frontend del proyecto final de Desarrollo y Arquitectura Backend.
Está hecha en Kotlin con Jetpack Compose (Material 3), una sola actividad, Navigation Compose, un ViewModel por área,
un cliente HTTP pequeño con OkHttp y kotlinx.serialization, y DataStore para la sesión, la dirección del servidor y el
idioma. No usa Firebase ni analítica; el único componente de Google es ML Kit Translation, que traduce la interfaz dentro
del teléfono.

## Requisitos

- JDK 21 para ejecutar Gradle (el archivo `gradle/gradle-daemon-jvm.properties` pide JDK 21 aunque Android Studio traiga otro).
- Android SDK con la plataforma 36 (la ruta va en `local.properties`, línea `sdk.dir`, que no se sube al repositorio).
- Un emulador o un teléfono con Android 8.0 (API 26) o superior.
- El backend corriendo (ver más abajo).

## Cómo ejecutar

1. Levantar el backend (en `../unabgo-backend`):

   ```bash
   cp .env.example .env
   ./gradlew migrateFresh   # crea la base de datos y los usuarios de prueba
   ./gradlew run            # http://localhost:8080
   ```

2. Compilar e instalar la app (también se puede abrir la carpeta en Android Studio y pulsar Run):

   ```bash
   export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
   ./gradlew :app:assembleDebug
   # APK: app/build/outputs/apk/debug/app-debug.apk
   ```

### En el emulador

No hay que cambiar nada: la dirección por defecto es `http://10.0.2.2:8080`, que es el computador anfitrión visto
desde el emulador (8080 es `APP_PORT` del backend).

### En un teléfono físico

1. El teléfono y el computador deben estar en la misma red Wi-Fi.
2. Averiguar la IP del computador (`ipconfig` en Windows) y permitir el puerto 8080 en el firewall.
3. En la app, en el engranaje de la pantalla de inicio de sesión (o del inicio), escribir
   `http://192.168.1.20:8080` (con la IP real) y pulsar **Probar conexión**. Debe decir "Conexión correcta".

El HTTP sin cifrar solo está permitido en la versión debug (`app/src/debug/AndroidManifest.xml`). La versión release
solo acepta direcciones `https`.

## Cuentas de prueba

Las crea `./gradlew migrateFresh` en el backend. En la pantalla de inicio de sesión, la sección "Cuentas de prueba"
llena el formulario con un toque.

| Rol | Usuario (correo) | Contraseña | Color de la app |
|---|---|---|---|
| Estudiante | `demo.estudiante@unab.edu.co` | `Unab2026*` | Morado |
| Gestor (Bienestar) | `demo.bienestar@unab.edu.co` | `Unab2026*` | Naranja |
| Gestor (Transporte) | `demo.transporte@unab.edu.co` | `Unab2026*` | Naranja |
| Administrador | `demo.admin@unab.edu.co` | `Unab2026*` | Azul |

Con "Crear una cuenta" se registra un estudiante nuevo (correo `@unab.edu.co`) eligiendo su carrera y su semestre.

## Qué puede hacer cada rol

- **Estudiante**: sus materias (crear, editar con horario, borrar con confirmación), cortes y notas, el resumen que calcula el
  backend (nota actual y lo que necesita para aprobar), su perfil con carrera y semestre, Mi plan de estudios, la tarjeta
  "Mi próxima clase", rutas favoritas, avisos, profesores, programas, Banu y buses.
- **Gestor**: todo lo público, más crear, editar y retirar avisos (con el interruptor de publicar), administrar las
  preguntas frecuentes de Banu (crear, editar, desactivar) y editar una ruta de bus.
- **Administrador**: todo lo del gestor, más la lista de usuarios con filtro por rol y paginación, el cambio de rol
  (el backend responde 403 si intenta cambiar el suyo y la app lo explica) y la sincronización de programas
  (muestra el resultado o el 503 cuando el servidor no la tiene configurada).

Todas las pantallas tienen estados de carga, vacío y error (con el texto `error` del backend), los botones se
deshabilitan mientras se envía, las listas se refrescan tirando hacia abajo y un 401 devuelve al inicio de sesión.

## Aspecto y navegación

Diseño claro y limpio, con tema oscuro propio, que sigue los colores institucionales del manual de marca
(morado `#8600DD`, naranja `#F79800`, azul `#00A3E0`, verde `#95C11F`, tinta `#2F024C`). Los colores dependen del rol de la sesión
y viven en un solo lugar (`ui/theme/RolePalette.kt`, función `rolePalette(role)`, entregada con un `CompositionLocal`):

| Rol | Color principal | Texto sobre el color |
|---|---|---|
| Estudiante | morado `#8600DD` | blanco |
| Gestor | naranja `#F79800` | tinta `#2F024C` (el blanco no tiene contraste sobre naranja) |
| Administrador | azul `#00679F` (variante profunda del azul) | blanco |
| Sin sesión | institucional neutro: morado profundo y la franja de cuatro colores | blanco |

El color se aplica al encabezado (degradado), botones principales, ítem seleccionado de la barra inferior, chips, indicadores de
progreso y acentos de las tarjetas. El verde queda para los estados de éxito. El logo es un monograma de cuatro cuadrantes dibujado con Compose.

- **Barra inferior por rol**: Inicio, Académico (estudiante), Gestión (gestor) o Administración (administrador), Universidad y Perfil.
- **Inicio**: encabezado con degradado, saludo según la hora, nombre y rol, tarjeta "Mi próxima clase" con cuenta regresiva animada,
  tarjeta "Mi carrera" (carrera y semestre del estudiante) y accesos rápidos.
- **Movimiento**: entrada escalonada de tarjetas, transiciones de pantalla (fundido y desplazamiento), esqueletos con brillo al cargar,
  contadores y anillo de nota animados. Si el usuario quita las animaciones del sistema, todo aparece sin movimiento.

## Resumen de inicio

El inicio del gestor y del administrador muestra una sección "Resumen" con tarjetas animadas (el número cuenta hasta su valor
y mientras carga se ve un esqueleto brillante). Gestor: avisos publicados, preguntas activas de Banu, rutas activas y buses en vivo.
Administrador: usuarios, programas, planes de estudio y avisos. Cada total sale de la API (`meta.total` con `per_page=1` o el conteo
de la lista), se recarga al volver al inicio y la tarjeta se oculta si su llamada falla (por ejemplo, `/bus/live` sin plataforma GPS).

## Carrera, Mi plan de estudios y Explorar planes

- **Registro con carrera y semestre.** Al abrir "Crear cuenta" se carga la lista de carreras con plan de estudios
  (`GET /programs?has_plan=true`), con búsqueda y filtro por nivel (abre en Profesional). Apenas se elige una carrera aparece su
  plan (semestres y créditos) y los semestres para escoger van del 1 al último del plan. Sin carrera o sin semestre no se envía.
- **Mi plan de estudios** (estudiante): muestra solo el plan de la carrera del perfil, con el semestre actual resaltado en naranja
  institucional ("Vas en sexto semestre", barra de avance, créditos de los semestres anteriores, el semestre actual abierto en la
  lista y su barra naranja en la gráfica). Se actualiza solo si se cambia la carrera o el semestre en el perfil. Sin carrera,
  invita a elegirla.
- **Explorar planes de estudio**: los planes de todos los programas, con un acceso a "Mi plan" y la marca "Tu carrera" en el propio.
- El inicio del estudiante tiene la tarjeta "Mi carrera" (carrera y semestre) y Mi perfil usa el mismo selector de carrera y semestre.

## Idioma y traductor

- La interfaz se traduce en el teléfono con **ML Kit Translation** de Google (español, inglés, francés, portugués y coreano). Los
  textos no salen a internet; cada idioma necesita un paquete de unos 30 MB que se descarga **una sola vez y solo por wifi**.
- El cambio es automático: al elegir el idioma la app se traduce sin reiniciar. Si no hay wifi, sigue en español y avisa; la
  descarga empieza sola al conectarse a una red wifi (se vigila la conexión).
- Lo traducido se guarda por idioma en el teléfono: la app abre traducida y funciona sin conexión.
- El idioma queda en el perfil del servidor (`language`): al cerrar sesión y volver a entrar, en este o en otro teléfono, la app
  abre en el idioma que dejó esa persona. Sin sesión se usa el último idioma del teléfono (botón de idioma en el inicio de sesión).
- No se traducen nombres de personas, correos, números, siglas ni nombres propios como Banu y UNAB.

## Planes de estudio (detalle)

Pantallas de planes disponibles para los tres roles (Inicio, pestaña Universidad y, para el estudiante, Académico):

- Lista con búsqueda (espera 400 ms después de escribir), filtros por nivel en orden académico (Profesional, Tecnología, Técnico,
  Especialización, Maestría y Doctorado, más cualquier otro nivel que llegue en los datos) y tarjetas con programa, créditos totales y número de semestres.
- Detalle: resumen (créditos y semestres), gráfica de barras de créditos por semestre dibujada con Compose (hasta 12 semestres caben sin desplazar), semestres que se
  expanden con animación y listan materias y créditos, botón para abrir el plan oficial (`plan_url`) en el navegador y vista de texto
  legible para los planes que vienen como texto.
- Atajo "Preguntar a Banu por el primer semestre" (en Mi plan, "por mi semestre"), que abre Banu con la pregunta ya enviada.
- En el detalle de un programa aparece "Ver plan de estudios" cuando el programa trae `study_plan_slug`.
- Banu muestra el origen de la respuesta (`faq`, `plan` o `ia`; un valor desconocido no rompe la pantalla) y espera hasta 40 s la respuesta.

## Pantallas y endpoints

| Pantalla | Endpoints |
|---|---|
| Conexión (ajustes) | `GET /health` |
| Inicio de sesión | `POST /auth/login` |
| Registro (con carrera y semestre) | `POST /auth/register`, `GET /programs?has_plan=true&level=&q=`, `GET /study-plans/{slug}` |
| Cerrar sesión | `POST /auth/logout` |
| Inicio, tarjeta Mi próxima clase | `GET /schedule/next` |
| Mis materias | `GET /subjects`, `DELETE /subjects/{id}` |
| Detalle de materia (cortes, notas, resumen) | `GET /subjects/{id}`, `GET /subjects/{id}/summary`, `POST /subjects/{id}/categories`, `POST /categories/{id}/items` |
| Crear o editar materia | `POST /subjects`, `PUT /subjects/{id}`, `GET /subjects/{id}` |
| Mi perfil | `GET /me`, `PUT /me/profile`, `GET /programs?has_plan=true` |
| Mi plan de estudios | `GET /me`, `GET /study-plans/{slug}` |
| Idioma | `PUT /me/profile` (`language`) |
| Avisos | `GET /announcements?category=&page=`, `POST /announcements`, `PUT /announcements/{id}`, `DELETE /announcements/{id}` |
| Profesores y detalle | `GET /professors?q=&page=`, `GET /professors/{slug}` |
| Programas y detalle | `GET /programs?q=&page=`, `GET /programs/{slug}` (incluye `study_plan_slug`) |
| Planes de estudio y detalle | `GET /study-plans?q=&level=&page=&per_page=`, `GET /study-plans/{slug}` |
| Rutas UNAB | `GET /bus/routes`, `GET /bus/live`, `GET /me/favorite-routes`, `POST /me/favorite-routes/{id}`, `DELETE /me/favorite-routes/{id}`, `PUT /bus/routes/{id}` |
| Banu | `POST /banu/ask` |
| Preguntas de Banu (gestor) | `GET /faqs`, `POST /faqs`, `PUT /faqs/{id}`, `DELETE /faqs/{id}` |
| Usuarios (administrador) | `GET /admin/users?role=&page=`, `PATCH /admin/users/{id}/role` |
| Sincronizar programas (administrador) | `POST /admin/programs/sync` |

Todos los caminos van bajo `/api/v1`. No se usan `POST /auth/google` (requiere Firebase, que este cliente no incluye)
ni `POST /webhooks/gps` (lo llaman los dispositivos a bordo, no una app de usuario).

## Estructura

```
app/src/main/java/co/edu/unab/unabgo/cliente/
  data/api/      ApiClient, ApiResult y ApiError, DTOs y configuración JSON (snake_case)
  data/repo/     un repositorio por área (interfaz e implementación)
  data/session/  sesión y dirección del servidor (DataStore)
  i18n/          traductor (ML Kit en el teléfono), caché de traducciones e idioma de cada usuario
  ui/common/     UiState, FormState y la lista paginada
  ui/theme/      colores institucionales, paleta por rol (`RolePalette`) y tema claro y oscuro
  ui/components/ logo, encabezado, tarjetas, movimiento (entrada escalonada, esqueletos, contadores), estados y formularios
  ui/screens/    pantallas y su ViewModel
  ui/nav/        rutas y navegación
```

## Pruebas

```bash
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"
./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process :app:testDebugUnitTest
```

Son 102 pruebas JVM: el traductor (tandas, caché, espera de wifi y descarga, textos que no se traducen, nombres propios,
traducciones lentas), el idioma de cada usuario al entrar y salir, el registro con carrera y semestre, Mi plan de estudios
(solo la carrera del perfil, sin carrera, sin conexión), el avance del semestre, la paleta por rol (colores, contraste, pestañas), el resumen de inicio del gestor y del administrador (totales y tarjetas que se ocultan si su llamada falla), planes de estudio (lista, detalle estructurado y en texto, 404,
ViewModel con búsqueda, filtros y error), el programa con `study_plan_slug`, Banu con origen `plan`, lectura del sobre `{ data, meta }` y de los errores, el cliente contra MockWebServer (login, 401,
validación con campos, paginación, 204, red caída), el manejo de la sesión (un 401 la borra), la dirección del servidor,
y los ViewModel principales con repositorios falsos.
