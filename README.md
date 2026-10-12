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

Todas las rutas `/api/**` requieren `Authorization: Bearer <token>` salvo login y registro. «equipo» = ADMIN, ANALYST
o VIEWER. Para un usuario final (`USER`) las rutas del equipo responden **404**, igual que una ruta inexistente.

| Método | Ruta | Rol |
|---|---|---|
| GET | `/api/auth/challenge` | público: reto de un solo uso para cifrar la contraseña |
| POST | `/api/auth/login` `{"username","challengeId","encryptedPassword"}` | público |
| POST | `/api/auth/register` `{"username","password"}` | público: crea un usuario final (`USER`) y devuelve su token |
| GET | `/api/auth/me` | cualquier cuenta |
| GET / POST / PUT / DELETE | `/api/my/networks[/{id}]` `{"name","provider","contractedMbps"}` | USER: solo sus propias redes (máximo 3) |
| GET / POST / PATCH / DELETE | `/api/users`, `/api/users/{id}/role`, `/api/users/{id}` | ADMIN (solo cuentas del equipo, máximo 3) |
| GET / POST | `/api/locations`, `/api/locations/{id}/zones`, `/api/zones` | GET equipo · POST ADMIN |
| GET / POST / PUT / DELETE | `/api/networks[/{id}]` | GET equipo · resto ADMIN |
| GET / POST | `/api/devices`, `/api/devices/{id}/sessions` | GET equipo · POST ADMIN |
| GET | `/api/networks/{id}/measurements?from&to&limit` | equipo |
| POST | `/api/networks/{id}/measurements/collect` | ADMIN, ANALYST |
| GET | `/api/networks/{id}/traffic`, `/api/networks/{id}/protocols` | equipo |
| POST | `/api/networks/{id}/analyses` `{"type":"THRESHOLD|STATISTICAL|ANOMALY_DETECTION"}` | ADMIN, ANALYST |
| GET | `/api/analyses?networkId&limit`, `/api/analyses/strategies`, `/api/anomalies` | equipo |
| GET / PATCH | `/api/alerts?status`, `/api/alerts/{id}/acknowledge`, `/api/alerts/{id}/resolve` | GET equipo · PATCH ADMIN, ANALYST |
| GET | `/api/dashboard/summary` | equipo |
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
- **Registro limitado:** máximo 5 cuentas por hora desde una misma dirección (`RegistrationThrottle`).
- **Contraseña cifrada en el navegador (además de HTTPS).** La web pide un reto de un solo uso
  (`GET /api/auth/challenge`: id + llave pública RSA) y envía `{challengeId, encryptedPassword}` cifrado con RSA-OAEP
  SHA-256 (`PasswordCipher`). Así la contraseña no aparece legible en la pestaña Red de F12 ni en proxies, y un
  payload copiado no sirve: cada reto funciona una vez y vence en 2 minutos. Con `PLAIN_PASSWORDS_ALLOWED=false` el
  backend rechaza contraseñas en texto plano.
- **Contraseñas:** se guardan solo como hash **BCrypt**; nunca se devuelven ni se registran en logs. Política
  (`PasswordPolicy`): 10 a 12 caracteres, al menos una mayúscula, un número y un carácter especial, sin espacios.
- **Usuario:** 3 a 40 caracteres: un correo (`ana@campus.edu.co`) o un nombre (letras, números, `.`, `-`, `_`).
- **Login:** cualquier fallo responde lo mismo, `Datos incorrectos.`; entradas fuera de los límites se rechazan sin
  consultar la base de datos. Tras 5 fallos del mismo usuario y dirección (o 20 de una misma dirección) se bloquea
  10 minutos (`LoginAttemptService`).
- **Tamaño de solicitudes:** cuerpos de más de 16 KB se rechazan con 413 (`RequestSizeLimitFilter`).
- **Errores:** mensajes en español, sin trazas ni detalles internos (`GlobalExceptionHandler`).
- **Secretos:** solo en variables de entorno de la plataforma; nunca en el repositorio ni en el frontend.

En la pestaña *Red* de F12 ya no se ve la contraseña: solo el `challengeId` y un texto cifrado distinto en cada
intento. Quien controle el navegador donde se escribe podría aún leer el campo mientras se escribe; esa protección
no existe en ninguna página web.

## Base de datos

El esquema pertenece a **WiFiSense-database** (migraciones Flyway). Este servicio no crea ni altera tablas
(`ddl-auto: none`); aplica primero las migraciones de ese repositorio.

## Integración con la IA

`AnomalyDetectionStrategy` arma la ventana de mediciones (más el tráfico cruzado por timestamp) y la envía con
`HttpAiAnalysisClient` a `POST {AI_SERVICE_URL}/api/v1/anomalies/detect` con la cabecera `X-API-Key`. La respuesta
se guarda en `ai_predictions` y marca si los datos eran simulados. Si la IA no responde, la API devuelve 503 y las
estrategias de umbrales y estadística siguen funcionando.

## Variables de entorno

| Variable | Ejemplo | Uso |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/wifisense` | Conexión JDBC (formato `jdbc:`) |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | | Credenciales |
| `AI_SERVICE_URL` | `http://localhost:8000` | URL interna del servicio de IA |
| `AI_API_KEY` | | Debe coincidir con la del servicio de IA |
| `JWT_SECRET` | 32+ caracteres aleatorios | Firma de tokens; el arranque falla si falta |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | definidos por el equipo, nunca en el repositorio | Cuenta de administrador; la contraseña debe cumplir la política |
| `PLAIN_PASSWORDS_ALLOWED` | `true` (por defecto) / `false` | `false`: solo se aceptan contraseñas cifradas por la web |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Orígenes del frontend, separados por coma |
| `MONITORING_ENABLED` | `true` | Recolección y análisis automáticos |

Copia `.env.example`, complétalo y expórtalo en tu terminal o IDE. Nunca subas `.env`.

## Ejecución

```bash
# 1. Base de datos lista con las migraciones de WiFiSense-database
# 2. Servicio de IA en ejecución (opcional para THRESHOLD/STATISTICAL)
export $(grep -v '^#' .env | xargs)
mvn spring-boot:run          # API en http://localhost:8080
mvn test                     # pruebas unitarias y de controladores
docker build -t wifisense-backend .
```

No hay usuarios de demostración. Al arrancar se crea (o actualiza) el administrador definido en
`ADMIN_USERNAME` / `ADMIN_PASSWORD`; él crea las otras cuentas desde la página Usuarios.

## Pruebas

`src/test/java` (convención Maven): fuentes de datos y decoradores, transiciones de estado, las tres estrategias,
la fachada con mocks, observadores, ciclo de vida de alertas y un `@WebMvcTest` que verifica seguridad por rol,
validación y errores 404.

## Pendiente (≈20 %)

Fuentes Router API (con Adapter por fabricante), SNMP y captura de tráfico; refresh tokens; pruebas de integración
con Testcontainers; pruebas de carga y seguridad avanzadas; paginación completa en listados.

Despliegue: ver [DEPLOYMENT.md](DEPLOYMENT.md).
