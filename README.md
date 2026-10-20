# WiFiSense-backend

API REST en Java + Spring Boot. Es el **único intermediario** del sistema: el frontend solo habla con este servicio,
y este servicio es el único que accede a PostgreSQL y al servicio de IA.

```text
Frontend ──HTTP/REST + JWT──► Backend ──JDBC──► PostgreSQL (WiFiSense-database)
                                 └─────HTTP + API key──► IA (WiFiSense-ai)
```

## Tecnologías

Java 21 · Spring Boot 3.5 · Spring Web · Spring Data JPA / Hibernate · Bean Validation · Spring Security
(OAuth2 Resource Server con JWT HS256) · PostgreSQL JDBC · Actuator · JUnit 5 · Mockito · AssertJ · Maven

## Arquitectura

| Paquete | Responsabilidad |
|---|---|
| `controller` | Endpoints REST y manejo global de errores (RFC 7807). Sin lógica de negocio. |
| `dto` | Records de entrada (validados) y salida. Las entidades nunca salen por la API. |
| `model` | Entidades JPA con comportamiento de dominio (`Network.applyDetectedStatus`, `Alert.resolve`). |
| `repository` | Interfaces Spring Data. |
| `service` | Casos de uso: redes, dispositivos, mediciones, alertas, reportes, monitoreo programado. |
| `network` | Fuentes de datos (Factory Method), `decorator/` (Decorator) y `state/` (State). |
| `analysis` | Estrategias de análisis (Strategy), cliente de IA y `NetworkAnalysisFacade` (Facade). |
| `event` | Eventos y observadores (Observer). |
| `security` | JWT, carga de usuarios, reglas de autorización y CORS. |
| `config` | Beans transversales (`Clock`). |

Los patrones, el problema que resuelve cada uno y los que se descartaron están en **[PATTERNS.md](PATTERNS.md)**.

## POO y SOLID en el código

- **Encapsulación:** las entidades no tienen setters públicos; el estado cambia con métodos de dominio que
  validan las transiciones (`Alert.acknowledge()` falla si la alerta no está abierta).
- **Abstracción / polimorfismo:** `NetworkDataSource`, `AnalysisStrategy`, `NetworkState`, `NetworkEventListener`.
- **Composición sobre herencia:** los decoradores envuelven fuentes; la fachada compone estrategias y servicios.
  La herencia se usa solo donde hay una relación "es un" real (`DataSourceDecorator`, `DataSourceCreator`).
- **S:** lectura (`MeasurementService`) y escritura (`MeasurementCollectionService`) separadas; validación,
  logs y métricas en decoradores distintos.
- **O:** nuevas fuentes, estrategias u observadores se agregan como clases nuevas.
- **L:** cualquier `AnalysisStrategy` o `NetworkDataSource` funciona donde se espera la interfaz (probado en tests).
- **I:** interfaces de 1–3 métodos.
- **D:** la fachada depende de `AnalysisStrategy` y `AiAnalysisClient`, no de implementaciones; todo por constructor.

## API

Todas las rutas `/api/**` requieren `Authorization: Bearer <token>` salvo las de inicio de sesión, registro, renovación
y cierre de sesión. «equipo» = ADMIN, ANALYST o VIEWER. Para un usuario final (`USER`) las rutas del equipo responden **404**, igual que una ruta inexistente.

| Método | Ruta | Rol |
|---|---|---|
| GET | `/api/auth/challenge` | público: reto de un solo uso para cifrar la contraseña |
| POST | `/api/auth/login` `{"username","challengeId","encryptedPassword"}` | público: el usuario se reconoce sin importar mayúsculas; devuelve `token` y `refreshToken` |
| POST | `/api/auth/register` `{"username","password"}` | público: crea un usuario final (`USER`) y devuelve su sesión |
| POST | `/api/auth/refresh` `{"refreshToken"}` | público: cambia el refresh token (sirve una vez) por una sesión nueva; 401 si la sesión terminó |
| POST | `/api/auth/logout` `{"refreshToken"}` | público: cierra esa sesión (204) |
| GET | `/api/auth/me` | cualquier cuenta |
| GET / POST / PUT / DELETE | `/api/my/networks[/{id}]` `{"name","provider","contractedMbps"}` | USER: solo sus propias redes (máximo 3) |
| GET | `/api/my/speedtest/ping`, `/api/my/speedtest/download?bytes=` | USER: latencia y bajada de la prueba de conexión |
| POST | `/api/my/speedtest/upload` (`application/octet-stream`) | USER: subida de la prueba (se lee y se descarta) |
| GET / POST | `/api/my/networks/{id}/tests` `{"latencyMs","jitterMs","downloadMbps","uploadMbps"}` | USER: historial y resultado calificado por la IA |
| POST | `/api/auth/password/forgot` `{"email"}` | Público: envía por correo un enlace para cambiar la contraseña. Siempre responde lo mismo (202) |
| POST | `/api/auth/password/reset` `{"token", "challengeId", "encryptedPassword"}` | Público: contraseña nueva con el enlace (204); cierra todas las sesiones |
| GET | `/api/my/account` | USER: usuario, correo de recuperación y celular |
| PUT | `/api/my/account/contact`, `/api/my/account/password` | USER: cambiar correo/celular o contraseña, confirmando con la contraseña actual |
| POST | `/api/my/networks/{id}/agent/pairing` | USER: código de 8 caracteres (15 min, un uso) para instalar el agente |
| POST | `/api/agent/pair` `{"code"}` | Instalador del agente: cambia el código por la clave (sin sesión, límite de intentos). El mismo instalador sirve para Mi Wi-Fi y para el sensor de una red del equipo: el código decide cuál |
| GET | `/api/my/networks/{id}/agent` | USER: último reporte del agente, historial, equipos conectados (con fabricante, nombre, modelo, tipo de equipo —celular, computador, TV…—, si usan MAC privada y cuál es el computador del agente y cuál el router), auditoría de seguridad y canal recomendado |
| POST / DELETE | `/api/my/networks/{id}/agent/key` | USER: crea (la clave se muestra una sola vez, `Cache-Control: no-store`) o revoca la clave del agente |
| POST | `/api/agent/reports` (cabecera `X-Agent-Key`) | Agente WiFiSense: sin sesión; la clave decide la red (de un usuario o del equipo). 201, 401 o 429 con `Retry-After` |
| GET / POST / PATCH / DELETE | `/api/users`, `/api/users/{id}/role`, `/api/users/{id}` | ADMIN (solo cuentas del equipo, máximo 3) |
| GET / POST | `/api/locations`, `/api/locations/{id}/zones`, `/api/zones` | GET equipo · POST ADMIN |
| GET / POST / PUT / DELETE | `/api/networks[/{id}]` | GET equipo · resto ADMIN |
| GET | `/api/networks/sources` | equipo: fuentes que se pueden elegir al registrar una red (en producción solo `AGENT`) |
| GET | `/api/networks/{id}/sensor` | equipo: si la red tiene sensor, si está reportando, último reporte, versión y sistema |
| POST / DELETE | `/api/networks/{id}/sensor/pairing`, `/api/networks/{id}/sensor` | ADMIN: código de 8 caracteres (15 min, un uso) para instalar el sensor · quitar el sensor (su clave deja de servir) |
| GET | `/api/networks/{id}/sensor/overview` | equipo: último reporte del sensor con la auditoría de seguridad, la calidad y el mejor canal, historial y equipos conectados (fabricante, nombre, dirección privada), como Mi Wi-Fi |
| PUT | `/api/networks/{id}/sensor/devices/{fingerprint}` `{"label","authorized"}` | ADMIN: nombrar un equipo o marcarlo como no autorizado |
| GET | `/api/networks/{id}/sensor/devices/{fingerprint}/sessions?days=7` | equipo: cuándo estuvo conectado ese equipo (1 a 30 días): cada periodo, cuántas conexiones y cuánto tiempo |
| GET | `/api/my/networks/{id}/agent/devices/{fingerprint}/sessions?days=7` | USER: lo mismo para un equipo de su red |
| POST | `/api/my/networks/{id}/agent/identify` · `/api/networks/{id}/sensor/identify` | USER (su red) · ADMIN: "¿Cuál es este equipo?": durante 10 minutos el agente o sensor reporta cada minuto (la respuesta de cada reporte lleva `nextReportSeconds: 60`), para ver qué equipo desaparece al apagar su Wi-Fi |
| GET | `/api/reports/networks/{id}/weekly?week=AAAA-MM-DD` | equipo: reporte de lunes a domingo (hora de Colombia) de la semana que contiene esa fecha: totales frente a la semana anterior, cada día, la peor hora, protocolos, equipos, seguridad y lo más importante en palabras |
| GET / POST | `/api/devices?networkId&limit`, `/api/devices/{id}/sessions?limit` | GET equipo (máximo 500, los más recientes primero) · POST ADMIN |
| GET | `/api/networks/{id}/measurements?from&to&limit` | equipo |
| POST | `/api/networks/{id}/measurements/collect` | ADMIN, ANALYST (una red con sensor responde 503: la mide su sensor) |
| GET | `/api/networks/{id}/traffic`, `/api/networks/{id}/protocols` | equipo |
| POST | `/api/networks/{id}/analyses` `{"type":"THRESHOLD|STATISTICAL|ANOMALY_DETECTION"}` | ADMIN, ANALYST |
| GET | `/api/analyses?networkId&limit`, `/api/analyses/strategies`, `/api/anomalies` | equipo |
| GET | `/api/anomalies/causes?days=30` | equipo: anomalías agrupadas por causa probable (1 a 365 días) |
| GET | `/api/ai/model` | equipo: modelo en producción y comparación con LOF y One-Class SVM |
| GET / POST | `/api/ai/training` | GET equipo: mediciones reales disponibles para entrenar e historial de versiones del modelo · POST ADMIN: entrenar ahora (409 si hay menos de 300 mediciones) |
| GET / PATCH | `/api/alerts?status`, `/api/alerts/{id}/acknowledge`, `/api/alerts/{id}/resolve` | GET equipo · PATCH ADMIN, ANALYST |
| GET | `/api/dashboard/summary` | equipo |
| GET | `/api/dashboard/status` | equipo: estado de la IA, la base de datos, el monitoreo y alertas abiertas |
| GET | `/api/home-networks` | ADMIN, ANALYST: redes reales de los usuarios (agente, seguridad, calidad, anomalías, canal). Solo ADMIN ve el dueño completo; nunca datos de sus equipos |
| GET | `/api/networks/{id}/quality` | equipo: calidad 0–100 de la última medición, calificada por la IA |
| GET | `/api/observability/data-sources`, `/api/observability/activity` | ADMIN |
| GET | `/api/reports/networks/{id}?from&to`, `/api/reports/comparison?networkIds=1,2` | equipo |
| GET | `/actuator/health` | público |

Errores: `application/problem+json` con `detail` y, en validación, `errors` por campo.

## Seguridad

- **Dos tipos de cuenta.** El **equipo** (ADMIN, ANALYST, VIEWER) lo crea solo el administrador, con un máximo de 3
  (`wifisense.accounts.max-users`). Los **usuarios finales** (`USER`) se registran solos en `/api/auth/register`; no
  cuentan en ese límite y no pueden elegir su rol. Nombres como `admin` o `soporte` están reservados.
- **Funciones del equipo ocultas.** Para un usuario final, cualquier ruta del equipo responde 404 con el mismo cuerpo que
  una ruta inexistente (`StaffOnlyAccessDeniedHandler`), así que no puede saber que existen. Los miembros del equipo
  sin permiso reciben un 403 normal.
- **Mis redes (usuario final).** `/api/my/**` es exclusivo del rol `USER` (el equipo recibe 403). El dueño siempre
  sale del token, nunca del cuerpo ni de la URL: pedir o modificar la red de otra persona responde 404, igual que una
  red inexistente (`HomeNetworkService`). Máximo 3 redes por usuario, nombre único por dueño (también en la base de
  datos) y validación de cada campo con mensajes en español.
- **Prueba de conexión acotada.** Cada byte descargado sale del servidor y se cobra como tráfico, así que la bajada y
  la subida tienen un máximo de 8 MB y cada usuario tiene un cupo de 20 transferencias y 6 pruebas guardadas cada
  10 minutos (`SpeedTestThrottle`, `wifisense.speedtest.*`); al pasarlo recibe 429 con un mensaje en español. Los
  valores que envía el navegador se validan con los mismos rangos que la base de datos. Si la IA no responde, la
  prueba se guarda igual, sin calificación.
- **Agente WiFiSense.** El agente de la casa no tiene sesión: se identifica con una clave de 256 bits (`wsa_…`) que
  solo se muestra al crearla; en la base se guarda su SHA-256 (`AgentKeyService`). Una clave mal formada, inexistente
  o revocada responde siempre el mismo 401. La clave decide la red del reporte (el cuerpo nunca lo dice), cada clave
  puede reportar como máximo una vez cada 50 s (`AgentReportThrottle`, 429 con `Retry-After`) y cada campo se valida
  con los mismos rangos que la base. Las MAC nunca llegan: solo huellas con sal. Los reportes y equipos de más de 30
  días se borran cada noche (`AgentRetentionJob`). Las llamadas a la IA se hacen fuera de la transacción y, si la IA
  no responde, el reporte se guarda sin veredictos.
- **Redes del equipo con sensor (sin datos simulados).** Una red del equipo se mide con su **sensor**: el agente
  WiFiSense instalado en un computador conectado a ella (fuente `AGENT`, V34–V35). El administrador genera un código
  en la página Redes, el instalador lo cambia por una clave propia de esa red (`network_sensors`, solo SHA-256) y cada
  reporte se guarda como una medición real con su tráfico y protocolos, seguida de un análisis por umbrales
  (`SensorReportService`). Una clave de sensor solo sirve para su red, puede reportar como máximo una vez cada 50 s y
  quitar el sensor la invalida al instante. Las fuentes simuladas no se pueden elegir ni se recolectan en producción
  (`DataSourcePolicy`); solo existen con `DEMO_DATA_SOURCES=true` para una demostración local.
  Desde V36 cada reporte del sensor también se guarda como en Mi Wi-Fi (`network_sensor_reports`): la IA audita la
  seguridad, califica la calidad y recomienda el canal (`AgentInsights`, compartido con el agente de Mi Wi-Fi), y los
  equipos quedan en `network_sensor_devices` solo con la huella con sal de su MAC (`SensorDeviceService`). Un equipo
  nuevo, uno marcado como no autorizado que sigue conectado o una seguridad crítica abren una alerta del equipo (estas
  dos últimas solo si no hay ya una abierta igual). Reportes y equipos de más de 30 días se borran cada noche.
  Si el computador del sensor está en otro Wi-Fi (el nombre que reporta no es el de la red), el reporte no se guarda
  como de esa red y el panel muestra en qué Wi-Fi está (`network_sensors.last_ssid`, V37).
- **Historial de conexiones** (`DeviceSessionTracker`, V37). Cada reporte abre un periodo para un equipo que no estaba
  conectado y alarga el de uno que sí. Un celular en reposo a veces no responde unos minutos, así que un periodo se
  cierra solo cuando el equipo lleva más de 12 minutos sin verse (`wifisense.agent.session-gap`, unos dos reportes) y
  termina en la última vez que se vio. Sirve para Mi Wi-Fi y para las redes del equipo; se borra a los 30 días.
- **Recuperar la contraseña.** El enlace lleva 256 bits aleatorios; en la base solo queda su SHA-256 (V21), sirve
  una vez y vence en 30 minutos. La respuesta es la misma exista o no la cuenta, y el correo sale en segundo plano
  (`MailDispatcher`), así que ni el texto ni el tiempo de respuesta revelan quién está registrado. Máximo 5
  solicitudes cada 15 minutos por dirección y 3 enlaces por hora por correo (`RecoveryThrottle`). El token va en el
  fragmento `#t=` de la URL, que el navegador no envía a ningún servidor. Los correos salen por la API HTTP de
  Brevo (`BrevoEmailSender`).
- **Sesiones con refresh token** (`SessionService`, tabla `refresh_tokens` de V30). Cada inicio de sesión entrega un
  token de acceso (JWT) y un refresh token de 256 bits; en la base solo queda su SHA-256. El refresh token **sirve una
  vez**: `/api/auth/refresh` lo gasta en una sola sentencia atómica y entrega el siguiente de la misma familia. Si
  alguien presenta otra vez uno ya usado, es una copia: se revoca toda esa familia y el ladrón y el dueño tienen que
  iniciar sesión de nuevo (dentro de 10 s se considera el mismo navegador reintentando y solo se rechaza). Una sesión
  sin usar durante 14 días vence (`SESSION_REFRESH_TTL`); los vencidos se borran cada noche. En las rutas públicas de
  `/api/auth` se ignora la cabecera `Authorization`: un token de acceso vencido que el navegador aún envíe no impide
  renovar la sesión.
- **Sesiones que se cierran.** Al restablecer o cambiar la contraseña se guarda `password_changed_at` (V20), se
  revocan todos los refresh tokens de la cuenta y `SessionTokenValidator` rechaza los JWT emitidos antes; también los
  de cuentas borradas o desactivadas, y los que llevan un **rol distinto al actual** (un administrador que baja a
  VIEWER deja de serlo en la siguiente petición, no cuando vence su token). Cerrar sesión revoca el refresh token.
- **Instalar el agente con un código.** Mi Wi-Fi muestra un código de 8 caracteres (alfabeto sin 0/O ni 1/I/L,
  ≈10¹² combinaciones), válido 15 minutos y una sola vez, guardado como SHA-256 (V22). El instalador lo cambia por
  la clave del agente; cada dirección tiene 10 intentos cada 10 minutos.
- **Registro limitado:** máximo 5 cuentas por hora desde una misma dirección (`RegistrationThrottle`).
- **Contraseña cifrada en el navegador (además de HTTPS).** La web pide un reto de un solo uso
  (`GET /api/auth/challenge`: id + llave pública RSA) y envía `{challengeId, encryptedPassword}` cifrado con RSA-OAEP
  SHA-256 (`PasswordCipher`). Así la contraseña no aparece legible en la pestaña Red de F12 ni en proxies, y un
  payload copiado no sirve: cada reto funciona una vez y vence en 2 minutos. Con `PLAIN_PASSWORDS_ALLOWED=false` el
  backend rechaza contraseñas en texto plano.
- **Contraseñas:** se guardan solo como hash **BCrypt**; nunca se devuelven ni se registran en logs. Política
  (`PasswordPolicy`): 10 a 12 caracteres, al menos una mayúscula, un número y un carácter especial, sin espacios.
- **Usuario:** 3 a 40 caracteres: un correo (`ana@campus.edu.co`) o un nombre (letras, números, `.`, `-`, `_`).
  `Carlos` y `carlos` son la misma cuenta: el inicio de sesión no distingue mayúsculas (índice único
  `lower(username)` de V28) y la sesión lleva el nombre tal como se guardó. Los correos se guardan en minúsculas.
  Nadie puede registrarse con el nombre del administrador (`ADMIN_USERNAME`), y si ese nombre ya fuera de un usuario
  final, el arranque no lo convierte en administrador.
- **Login:** cualquier fallo responde lo mismo, `Datos incorrectos.`; entradas fuera de los límites se rechazan sin
  consultar la base de datos. Tras 5 fallos del mismo usuario y dirección (o 20 de una misma dirección) se bloquea
  10 minutos (`LoginAttemptService`).
- **Tamaño de solicitudes:** cuerpos de más de 16 KB se rechazan con 413 (`RequestSizeLimitFilter`), también cuando
  llegan por partes sin `Content-Length`: el cuerpo se cuenta mientras se lee (`LimitedBodyRequest`). La subida de la
  prueba de conexión acepta hasta 8 MB y el reporte del agente hasta 128 KB (256 equipos con nombre, modelo y tipo).
- **Errores:** mensajes en español, sin trazas ni detalles internos (`GlobalExceptionHandler`).
- **Secretos:** solo en variables de entorno de la plataforma; nunca en el repositorio ni en el frontend.

En la pestaña *Red* de F12 ya no se ve la contraseña: solo el `challengeId` y un texto cifrado distinto en cada
intento. Quien controle el navegador donde se escribe podría aún leer el campo mientras se escribe; esa protección
no existe en ninguna página web.

## Base de datos

El esquema pertenece a **WiFiSense-database** (migraciones Flyway). Este servicio no crea ni altera tablas
(`ddl-auto: none`); aplica primero las migraciones de ese repositorio.

En producción el backend se conecta como **`wifisense_app`** (`security/rol_aplicacion.sql` de ese repositorio): puede
leer y escribir datos, pero no crear, borrar ni cambiar tablas, y cada consulta tiene un máximo de 30 s. Las pruebas de
integración corren con ese mismo usuario.

## Rendimiento

- **Reportes calculados en PostgreSQL** (`MeasurementStatsRepository`): promedio, mínimo, máximo y percentil 95 de
  cada métrica en una sola consulta con el índice `(network_id, measured_at)`. Antes un reporte de 31 días cargaba en
  memoria todas las mediciones (unas 45 000 por red revisada cada minuto; hasta 6 redes en una comparación).
- **Limpieza nocturna** (`MonitoringRetentionJob`, 04:00 UTC): borra las mediciones, el tráfico, las estadísticas de
  protocolos y los análisis de más de 90 días (`MONITORING_RETENTION`), las alertas resueltas hace más de 90 días
  (las abiertas nunca) y los refresh tokens vencidos. Borra en lotes de 5 000 filas, cada uno en una transacción
  corta, usando los índices por fecha de V27: el monitoreo y los usuarios no esperan detrás de un borrado largo.
- **Ninguna lista sin tope:** equipos y sesiones de tráfico ahora tienen `limit` (máximo 500), igual que mediciones,
  alertas y análisis.
- **Respuestas comprimidas** (gzip) para JSON de más de 2 KB; la prueba de conexión nunca se comprime.
- **Conexiones:** máximo 10 conexiones a la base (`DATABASE_POOL_SIZE`) y una petición espera como máximo 10 s por
  una; al redesplegar, las peticiones en curso terminan antes de apagar (`server.shutdown: graceful`).
- **Pruebas de carga** ([load-tests/README.md](load-tests/README.md), `load-tests/carga.py`, solo Python estándar): en
  un computador de 2 núcleos, 50 personas usando la página a la vez dan 176 peticiones por segundo con p95 de 97 ms y
  ningún error; el límite está en unas 250 peticiones por segundo.

## Integración con la IA

`AnomalyDetectionStrategy` arma la ventana de mediciones (más el tráfico cruzado por timestamp) y la envía con
`HttpAiAnalysisClient` a `POST {AI_SERVICE_URL}/api/v1/anomalies/detect` con la cabecera `X-API-Key`. La respuesta
se guarda en `ai_predictions` y marca si los datos eran simulados. Si la IA no responde, la API devuelve 503 y las
estrategias de umbrales y estadística siguen funcionando.

La IA también entrega la **causa probable** de cada anomalía (`anomalyType`: congestión,
interferencia, cobertura débil, falla del proveedor, tráfico sospechoso o no identificada). Se guarda en
`ai_predictions.anomaly_type` (migración V15) y se valida al leerla (`AnomalyType.fromAi`): un valor inesperado
nunca rompe el análisis y siempre concuerda con el veredicto. `GET /api/ai/model` reúne la descripción del modelo y
su evaluación; mientras la IA la calcula tras reiniciar, responde `evaluationReady: false`.

**IA entrenada con datos reales** (`AiTrainingService`, Avance 16). La IA no lee la base de datos, así que el backend
le envía las mediciones reales de los últimos 30 días (máximo 10 000): las de los sensores de las redes del equipo
(fuente `AGENT`, con su tráfico) y las de los agentes de Mi Wi-Fi. Solo viajan y se guardan los números (latencia,
jitter, pérdida, ancho de banda, señal, tráfico, equipos y paquetes), nunca la red, el usuario ni los equipos. La IA
entrena un modelo nuevo con el 80 % más antiguo, lo compara con el modelo en uso sobre el 20 % más reciente y sobre
esas mismas mediciones con fallas simuladas, y lo pone en uso solo si es mejor (`POST /api/v1/model/train`). Cada
entrenamiento queda en `ai_model_versions` (métricas, decisión y por qué). Se entrena solo cada lunes a las 3:00
(hora de Colombia; `AI_TRAINING_AUTOMATIC=false` lo apaga) o cuando un ADMIN lo pide. Railway borra el disco de la
IA al redesplegarla: cada 10 minutos el backend revisa que la IA use la versión activa y, si no, se la reconstruye
con las mismas mediciones (mismo resultado).

Con cada reporte del agente se piden a la IA cuatro veredictos: auditoría de seguridad
(`/api/v1/security/assess`), calidad (`/api/v1/quality/score`), anomalías (`/api/v1/anomalies/detect`) y, si el
agente envió cuántas redes vecinas hay por canal, el **mejor canal Wi-Fi** (`/api/v1/channel/recommend`). Los cuatro
se guardan en `agent_reports.details`; si alguno falla el reporte se guarda igual, sin ese veredicto.

El cliente usa **HTTP/1.1** a propósito: sobre `http://` (la red privada de Railway) el cliente HTTP de Java intenta
pasar a HTTP/2 (h2c), uvicorn lo rechaza y descarta el cuerpo de la petición, y el análisis fallaba con 422.

## Variables de entorno

| Variable | Ejemplo | Uso |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/wifisense` | Conexión JDBC (formato `jdbc:`) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | | Credenciales |
| `AI_SERVICE_URL` | `http://localhost:8000` | URL interna del servicio de IA |
| `AI_API_KEY` | | Debe coincidir con la del servicio de IA |
| `JWT_SECRET` | 32+ caracteres aleatorios | Firma de tokens; el arranque falla si falta |
| `JWT_TTL` | `15m` (por defecto) | Vida del token de acceso; la web lo renueva sola con el refresh token |
| `SESSION_REFRESH_TTL` | `14d` (por defecto) | Una sesión sin usar este tiempo tiene que iniciar sesión de nuevo |
| `MONITORING_RETENTION` | `90d` (por defecto, mínimo `31d`) | Antigüedad de los datos de monitoreo que se borran cada noche |
| `DATABASE_POOL_SIZE` | `10` (por defecto) | Conexiones máximas a la base de datos |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | definidos por el equipo, nunca en el repositorio | Cuenta de administrador; la contraseña debe cumplir la política |
| `PLAIN_PASSWORDS_ALLOWED` | `true` (por defecto) / `false` | `false`: solo se aceptan contraseñas cifradas por la web |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes del frontend, separados por coma |
| `MONITORING_ENABLED` | `true` | Recolección y análisis automáticos |
| `AI_TRAINING_AUTOMATIC` | `true` (por defecto) | Entrena la IA con las mediciones reales cada lunes a las 3:00 (hora de Colombia) |
| `DEMO_DATA_SOURCES` | `false` (por defecto) | Solo en una demostración local: `true` permite registrar redes simuladas. En Railway no se define |
| `BREVO_API_KEY` | `xkeysib-…` (solo en Railway) | Envío del correo de recuperación por la API de Brevo |
| `MAIL_FROM` | un correo verificado como remitente en Brevo | Remitente del correo de recuperación |
| `FRONTEND_URL` | `https://patrones-wi-fi-froned-coral.vercel.app` | A dónde apunta el enlace del correo |
| `MAIL_LOG_LINKS` | `false` | Solo en desarrollo local: `true` escribe el enlace en el log en lugar de enviarlo |

Copia `.env.example`, complétalo y expórtalo en tu terminal o IDE. Nunca subas `.env`.

## Ejecución

```bash
# 1. Base de datos lista con las migraciones de WiFiSense-database
# 2. Servicio de IA en ejecución (opcional para THRESHOLD/STATISTICAL)
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run          # API en http://localhost:8080
mvn test                     # pruebas unitarias y de controladores (las de integración se omiten)
# Pruebas de integración: base con las migraciones (apply.sh --security) y el usuario de la aplicación
WIFISENSE_IT_DATABASE_URL=jdbc:postgresql://localhost:5432/wifisense \
WIFISENSE_IT_DATABASE_USERNAME=wifisense_app WIFISENSE_IT_DATABASE_PASSWORD=<su contraseña local> mvn test
docker build -t wifisense-backend .
```

No hay usuarios de demostración. Al arrancar se crea (o actualiza) el administrador definido en
`ADMIN_USERNAME` / `ADMIN_PASSWORD`; él crea las otras cuentas desde la página Usuarios.

## Pruebas

`src/test/java` (convención Maven), 351 pruebas:

- **Unitarias y de controladores (339):** fuentes de datos y decoradores, transiciones de estado, las tres
  estrategias, la fachada con mocks, observadores, ciclo de vida de alertas y `@WebMvcTest` que verifica seguridad por
  rol, validación y errores 404, el agente (claves, límite de reportes, equipos nuevos y con MAC privada, canal
  recomendado, IA caída), el panel del equipo con datos reales, las sesiones (rotación, copia detectada, cambio de
  contraseña y de rol), la limpieza nocturna por lotes y el sensor de las redes del equipo (código, clave, reporte
  convertido en medición con tráfico y protocolos, fuentes permitidas, equipos, auditoría y alertas del sensor), el
  tipo de cada equipo y el modo "¿Cuál es este equipo?" (reportes cada minuto) y el entrenamiento de la IA.
- **De integración con PostgreSQL real (12, paquete `integration`):** el backend completo contra una base creada con
  las migraciones de WiFiSense-database, conectado como `wifisense_app`. Prueban por HTTP el registro, el inicio de
  sesión sin importar mayúsculas, la renovación, la copia de un refresh token, el cierre de sesión, el cambio de
  contraseña y de rol; y en SQL que un refresh token se gasta una sola vez, que las estadísticas del reporte calculadas
  por PostgreSQL son iguales a las de Java y que la limpieza borra solo lo viejo (las alertas abiertas se quedan).
  Además, el recorrido completo del sensor: registrar la red, rechazar una simulada, instalar con el código, reportar
  y encontrar la medición, el tráfico, los protocolos y los equipos en la base; marcar un equipo como no autorizado;
  quitar el sensor deja la clave sin efecto. Y el historial de conexiones (con la tolerancia de 12 minutos) y el
  reporte semanal con los días de Colombia (una medición de las 23:30 del lunes cuenta el lunes, aunque en UTC ya sea
  martes). Y el entrenamiento de la IA: solo mediciones reales (nunca simuladas), mínimo 300, historial de versiones
  y reconstrucción de la versión activa cuando la IA se reinicia.
  Se ejecutan cuando existe `WIFISENSE_IT_DATABASE_URL`.

**GitHub Actions** (`.github/workflows/backend.yml`) corre en cada Pull Request y en `main`: crea PostgreSQL 16 con las
migraciones del repositorio de base de datos y el rol `wifisense_app`, ejecuta todas las pruebas (falla si las de
integración no corrieron) y construye la imagen Docker que despliega Railway. Las migraciones salen de la rama de la
base de datos con el mismo nombre que la del Pull Request cuando existe (una migración nueva que aún no tiene merge) y,
si no, de `main`. Dependabot (`.github/dependabot.yml`) propone cada mes las actualizaciones de Maven, de las acciones
y de la imagen de Docker.

## Documentos

- [PATTERNS.md](PATTERNS.md): los patrones de diseño, por qué y dónde.
- [DEPLOYMENT.md](DEPLOYMENT.md): desplegar en Railway paso a paso.
- [docs/seguridad.md](docs/seguridad.md): revisión de seguridad frente al OWASP Top 10, lo corregido y los riesgos que
  quedan.
- [docs/sustentacion.md](docs/sustentacion.md): guion de la sustentación, cifras y preguntas probables.
- [load-tests/README.md](load-tests/README.md): cómo correr la prueba de carga y sus resultados.

## Pendiente

Fuentes Router API y SNMP: el diseño ya las tiene (`RouterApiDataSourceCreator`, `SnmpDataSourceCreator`), pero los
routers de la casa y de la sede no las ofrecen, así que el sensor mide el tráfico del computador donde está instalado,
no el de toda la red.

Despliegue: ver [DEPLOYMENT.md](DEPLOYMENT.md).
