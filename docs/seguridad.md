# Revisión de seguridad de WiFiSense (Avance 17)

Revisión final del sistema completo (página, backend, servicio de IA, agente y base de datos) frente al
**OWASP Top 10 (2021)**. Para cada riesgo: qué hace WiFiSense, dónde está y qué prueba automática lo comprueba. Al
final, lo que se encontró y se corrigió en esta revisión y los riesgos que quedan.

## Cómo está armado

```text
Navegador ──HTTPS──► Página (Vercel, solo archivos estáticos)
    │
    └──HTTPS + JWT──► Backend (Railway) ──red privada + X-API-Key──► Servicio de IA (Railway)
                         │
Agente / sensor ─HTTPS + clave wsa_─┘──► PostgreSQL (Railway, rol wifisense_app con permisos mínimos)
```

Solo el backend habla con la base de datos y con la IA; la página nunca (lo comprueba `tests/security.test.ts` de la
página). Los secretos (`JWT_SECRET`, contraseña de la base, `AI_API_KEY`, `ADMIN_PASSWORD`, `BREVO_API_KEY`,
`ANTHROPIC_API_KEY`) viven solo en las variables de Railway.

## OWASP Top 10

| Riesgo | Qué hace WiFiSense | Comprobado por |
|---|---|---|
| **A01 Control de acceso** | Roles ADMIN, ANALYST, VIEWER y USER en `SecurityConfig`; las rutas del equipo responden 404 a un usuario final (no sabe que existen); el dueño de una red de Mi Wi-Fi sale del token, nunca del cuerpo ni de la URL (la red de otro responde 404); la clave de un agente o sensor decide su red | `@WebMvcTest` de cada controlador (rol por rol), `MyNetworkControllerTest`, `AgentControllerTest`, pruebas de integración por HTTP |
| **A02 Fallas criptográficas** | HTTPS en todo; contraseñas con BCrypt; la contraseña además viaja cifrada con RSA-OAEP (reto de un solo uso de 2 minutos); claves de agente, códigos, refresh tokens y enlaces de recuperación de 256 bits guardados solo como SHA-256; las MAC nunca salen del computador (huella SHA-256 con sal) | `PasswordCipherTest`, `AgentKeyServiceTest`, `SessionServiceTest`, pruebas de la base (`integridad_test.sql`, `sensor_test.sql`) |
| **A03 Inyección** | JPA y `JdbcTemplate` siempre con parámetros (`?`), nunca SQL armado con texto del usuario; validación de cada campo con los mismos rangos que la base (`@Valid`, `CHECK` en PostgreSQL); la página no usa `dangerouslySetInnerHTML` y su CSP no permite scripts en línea | Pruebas de validación de cada DTO (`forgedValuesAreRejectedBeforeReachingTheService`), restricciones probadas en `schema_test.sql` |
| **A04 Diseño inseguro** | Límites por usuario y por dirección: inicio de sesión (5 fallos → 10 min), registro (5 por hora), recuperación (5 cada 15 min), códigos del agente (10 cada 10 min), reportes del agente (1 cada 50 s por clave), prueba de conexión (20 transferencias cada 10 min); tamaño máximo de cada solicitud; respuestas iguales exista o no una cuenta | `LoginAttemptServiceTest`, `RegistrationThrottleTest`, `RecoveryThrottleTest`, `AgentReportThrottleTest`, `RequestSizeLimitFilterTest` |
| **A05 Configuración insegura** | Errores en español sin trazas (`GlobalExceptionHandler`); actuator solo expone `health`; CORS solo para la URL de Vercel; la página envía CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, HSTS y `Permissions-Policy`; el servicio de IA no publica su documentación (`docs_url=None`) | `@WebMvcTest` de los controladores (respuestas de error), `security.test.ts` (cabeceras de `vercel.json`) |
| **A06 Componentes vulnerables** | Versiones fijas (`pom.xml`, `requirements.txt`, `package-lock.json`); Dependabot propone cada mes las actualizaciones de los 4 repositorios; la página corre `npm audit` y la IA `pip-audit` en cada Pull Request | Workflows `pagina.yml` y `servicio-ia.yml`; revisión del 19/10/2026: 0 vulnerabilidades conocidas en npm y en pip |
| **A07 Identificación y autenticación** | Política de contraseñas (10–12, mayúscula, número y especial); token de acceso de 15 min y refresh token de un solo uso (si se reusa uno, se cierra toda la sesión); cambiar la contraseña o el rol invalida los tokens anteriores; el token vive solo en la pestaña (`sessionStorage`) | `SessionServiceTest`, `SessionTokenValidatorTest`, integración `SessionFlowIntegrationTest`, `security.test.ts` |
| **A08 Integridad de software y datos** | Las migraciones solo las aplica el dueño de la base; el rol de la aplicación no puede cambiar el esquema; el instalador del agente se construye en GitHub Actions desde el código del repositorio (nadie sube un `.exe` a mano); el agente solo acepta `https://` | `rol_aplicacion_test.sql`, workflow `agente-windows.yml`, `test_agent.py` |
| **A09 Registro y monitoreo** | Alertas del equipo (equipo nuevo, equipo no autorizado, seguridad crítica, anomalías) con correo; logs sin contraseñas, tokens ni claves; los intentos de inicio de sesión fallidos quedan limitados y registrados | `AgentAlertServiceTest`, `SensorReportServiceTest` |
| **A10 SSRF** | El backend solo llama a dos destinos fijos de su configuración (IA y Brevo); ninguna URL viene del usuario. El agente solo lee la descripción UPnP de la **misma IP privada** que la anunció, con un máximo de 64 KB | `test_device_discovery.py` (`test_a_description_is_read_only_from_the_same_device_in_the_local_network`) |

## Encontrado y corregido en esta revisión

1. **Reportes de redes grandes rechazados.** El backend rechazaba cualquier cuerpo de más de 16 KB. Desde el agente
   1.7.0 cada equipo lleva nombre, modelo y tipo (unos 200 bytes), así que una red con más de unos 75 equipos (la de
   una universidad, por ejemplo) habría recibido 413 y el reporte se habría perdido. Ahora el reporte del agente
   acepta hasta 128 KB (256 equipos, el máximo que el agente envía y que el backend valida); el resto sigue en 16 KB.
2. **Servicio de IA sin límite de tamaño.** Ahora rechaza con 413 las solicitudes de más de 8 MB antes de leerlas.
3. **Prueba inestable.** `SpeedTestControllerTest` fallaba más o menos 1 de cada 150 veces por una carrera entre dos
   hilos sobre la respuesta simulada de MockMvc (no afectaba al servidor real). Arreglada y repetida 300 veces sin fallas.
4. **Pruebas automáticas que faltaban.** La página y el servicio de IA no tenían GitHub Actions en cada Pull Request;
   ahora sí. Y el backend toma las migraciones de la rama de la base de datos con el mismo nombre si todavía no tiene
   merge: así un Pull Request del backend que necesita una migración nueva ya no falla por el orden de los merges.

## Riesgos que quedan (y por qué se aceptan)

- **Un computador con el agente puede ser engañado** por un equipo que anuncie un nombre falso por mDNS. Solo afecta a
  cómo se muestra ese equipo (el tipo es una suposición y así se dice en la página); nunca da acceso a nada.
- **WiFiSense no puede expulsar equipos de la red**: eso lo controla el router. La página guía al usuario a cambiar la
  contraseña del Wi-Fi o a bloquear el equipo en el router.
- **Quien controla el navegador** (una extensión maliciosa) puede leer lo que se escribe en la página. Ninguna página
  web puede evitarlo.
- **Las fallas con las que se evalúa la IA son simuladas** sobre mediciones reales; un modelo nuevo solo reemplaza al
  anterior si mejora en esa misma prueba.

## Lista para cada despliegue

- [ ] Ninguna variable secreta en el código, en el README ni en capturas (buscar `JWT_SECRET`, `sk-ant-`, `xkeysib-`).
- [ ] `PLAIN_PASSWORDS_ALLOWED=false` en Railway cuando la página ya cifra las contraseñas.
- [ ] El backend se conecta como `wifisense_app`, no como `postgres` (`tests/verificar_*.sql` en `OK`).
- [ ] Public Access de Postgres apagado después de usar DBeaver.
- [ ] Copia de seguridad antes de cada migración (`WiFiSense-database/docs/respaldo.md`).
- [ ] Los checks de GitHub Actions en verde en los 4 repositorios antes de sincronizar los forks.
