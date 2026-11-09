# API del Avance 24: capturas técnicas, auditoría, router, nombres y análisis de redes cercanas

Contrato de los endpoints que agrega o cambia el Avance 24. Todas las respuestas de error son
`application/problem+json` con `detail` (y `errors` por campo cuando falla la validación). Los tipos TypeScript de la
página están en `WiFiSense-frontend/src/types.ts` y los contratos con la IA en `src/test/resources/contracts`.

## Reglas comunes

- **Roles:** los decide `SecurityConfig` en el backend, nunca la página. Un usuario final (`USER`) que pide una ruta del
  equipo recibe **404** (igual que una ruta que no existe); un integrante del equipo sin permiso recibe **403**. Los
  intentos sin permiso sobre capturas y cuentas quedan en la auditoría con `outcome = DENIED`.
- **Propiedad:** las rutas `/api/my/**` comprueban que la red sea del usuario de la sesión (si no, 404). Las del agente
  toman la red de su clave (`X-Agent-Key`), nunca del cuerpo.
- **Tareas asíncronas:** un escaneo o una captura se crea con **202** y su `id` es el identificador de la operación; la
  página consulta su estado hasta que termina. El servidor nunca envía comandos al agente: solo el número de la
  operación y sus límites numéricos.

## Capturas técnicas (solo ADMIN, solo redes del equipo con sensor)

Estados: `PENDING` (esperando al sensor) → `RUNNING` (el sensor la tomó) → `COMPLETED`, `FAILED` o `CANCELLED`. Una
captura que el sensor no toma en 3 minutos, o que no entrega en su duración más 90 s, falla como `AGENT_NO_RESPONSE`.
Cada red tiene como mucho una captura esperando o en curso.

| Método y ruta | Rol | Cuerpo / parámetros | Respuestas |
|---|---|---|---|
| `GET /api/networks/{id}/sensor/captures/capabilities` | ADMIN | — | **200** `CaptureCapabilitiesResponse`: sensor instalado y conectado, versión, herramientas usables, modo monitor, los cuatro escenarios (A local, B puerto espejo, C modo monitor, D router) con su estado, `canCapture`, `blockers[]` y los límites · **404** red inexistente |
| `POST /api/networks/{id}/sensor/captures` | ADMIN | `{purpose (10–200), maxSeconds (5–300, el servidor permite hasta CAPTURE_MAX_SECONDS), maxPackets (100–100 000, hasta CAPTURE_MAX_PACKETS)}` | **202** la captura `PENDING` · **400** propósito o límites inválidos · **409** con `reasons[]` cuando no se puede capturar (sin sensor, sensor sin reportar, agente anterior a 2.0.0, sin permiso local, sin herramienta, ya hay una en curso) |
| `GET /api/networks/{id}/sensor/captures?limit=20` | ADMIN | `limit` 1–50 | **200** historial, la más reciente primero |
| `GET /api/networks/{id}/sensor/captures/{captureId}` | ADMIN | — | **200** `CaptureDetail`: `capture`, `totals`, `protocols`, `ports`, `intervals`, `observed[]` (hechos), `inferred[]` (inferencias con `confidence`) y `scope` · **404** si es de otra red. Consultar una completada queda en la auditoría |
| `POST /api/networks/{id}/sensor/captures/{captureId}/cancel` | ADMIN | — | **200**: una `PENDING` queda `CANCELLED`; una `RUNNING` se detiene en la próxima consulta del sensor y conserva lo capturado (`stopReason = CANCELLED`) |
| `GET /api/agent/captures/next` | sensor (`X-Agent-Key`) | — | **200** `{captureId, maxSeconds, maxPackets, maxBytes}` (pasa a `RUNNING`) · **204** nada pendiente · **401** clave inválida · **404** clave de un agente de Mi Wi-Fi · **429** si pregunta más de una vez cada 10 s |
| `GET /api/agent/captures/{captureId}/state` | sensor | — | **200** `{captureId, stop}` · **404** captura de otra red |
| `POST /api/agent/captures` | sensor | `AgentCaptureResult`: solo conteos (ningún campo para una IP, una MAC o contenido) | **200** · **400** si los números no cuadran o pasan los límites · **404** otra red · **409** la captura ya terminó o llegó tarde |
| `POST /api/agent/reports` | agente | el reporte con `capabilities` (2.0.0) | el `ack` puede traer `captureId`: el sensor la toma enseguida con `GET /api/agent/captures/next` |

Tres pérdidas que nunca se mezclan: `droppedByTool` (lo que la herramienta no alcanzó a guardar, puede ser
desconocido), la pérdida de las pruebas de conexión (otra medida, que se envía a la IA como `connectionLossPct`) y una
anomalía (una inferencia con su confianza).

## Auditoría y retención (solo ADMIN)

| Método y ruta | Parámetros | Respuestas |
|---|---|---|
| `GET /api/admin/audit` | `action`, `outcome` (`SUCCESS`, `DENIED`, `FAILED`), `page` ≥ 0, `size` 1–100 | **200** `{items[], page, size, total}`, lo más reciente primero · **400** filtro desconocido |
| `GET /api/admin/audit/actions` | — | **200** `[{code, label}]` |
| `GET /api/admin/policies` | — | **200** cuánto se guarda cada cosa y dónde se cambia (solo lectura) |

Operaciones auditadas: pedir, detener y consultar capturas; crear, cambiar de rol y borrar cuentas; marcar o volver a
autorizar equipos; códigos y claves de sensores y agentes; borrar redes del equipo; y la limpieza nocturna. Las filas
no se pueden modificar y solo se borran las de más de 31 días (la base de datos lo impide).

## Router y nombres de los equipos

| Método y ruta | Rol | Respuestas |
|---|---|---|
| `GET /api/my/networks/{id}/router` | USER dueño | **200** `RouterInfoResponse` · **404** |
| `GET /api/networks/{id}/sensor/router` | ADMIN, ANALYST, VIEWER | **200** `RouterInfoResponse` · **404** |

`RouterInfoResponse`: si el agente identificó el router (fabricante y modelo por UPnP), cuántos nombres respondió su DNS
y las capacidades `READ_NAMES` (disponible si el DNS del router respondió), `LIST_CLIENTS`, `BLOCK_DEVICE` y
`CHANGE_PASSWORD` (no disponibles: no hay integración oficial con ningún router), con los pasos para hacerlo en el router.

Cada equipo de `GET .../agent` y `GET .../sensor/overview` trae ahora `nameSource` (`CONFIRMED`, `ANNOUNCED`, `ROUTER`,
`DNS`) e `identification` (`ALIAS`, `CONFIRMED`, `ANNOUNCED`, `ROUTER`, `DNS`, `NAMED`, `ESTIMATED`, `UNKNOWN`).

## Lectura de la IA de las redes cercanas

| Método y ruta | Rol | Respuestas |
|---|---|---|
| `GET /api/my/networks/{id}/nearby/analysis` | USER dueño | **200** `{scanId, scannedAt, available, message, facts[], inferences[], uncertainty}`; `available = false` con un mensaje cuando no hay escaneo completado o la IA no responde · **404** |
