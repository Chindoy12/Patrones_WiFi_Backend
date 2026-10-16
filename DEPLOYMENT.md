# Despliegue de WiFiSense

Vercel solo sirve el frontend estático. El backend (Java) y la IA (Python) se despliegan como contenedores en una
plataforma que ejecute Docker (Render, Railway, Fly.io, Azure Container Apps…). PostgreSQL es un servicio administrado.

```text
Usuario ─► Frontend (Vercel) ─► Backend (contenedor) ─► PostgreSQL administrado
                                       └─────────────► IA (contenedor, URL no publicada en el frontend)
```

## Deployment box

1. **Create PostgreSQL database.** Crea una instancia administrada (Neon, Supabase, Render, RDS). Aplica las
   migraciones desde `WiFiSense-database`: `DATABASE_URL=postgresql://... ./apply.sh` (o Flyway con
   `FLYWAY_URL=jdbc:postgresql://...`). Ejecuta `--seed` solo en entornos de demostración.
2. **Configure backend environment variables.** `DATABASE_URL` (formato `jdbc:postgresql://host:5432/db?sslmode=require`),
   `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET` (32+ caracteres aleatorios), `AI_API_KEY`,
   `CORS_ALLOWED_ORIGINS` (la URL de Vercel), `ADMIN_USERNAME` y `ADMIN_PASSWORD` (cuenta de administrador).
   Para recuperar contraseñas por correo: `BREVO_API_KEY` (Brevo → SMTP & API → API keys), `MAIL_FROM` (un
   remitente verificado en Brevo) y `FRONTEND_URL` (la URL de Vercel, a donde apunta el enlace). Sin ellas la
   recuperación responde igual pero no envía nada (y el log lo advierte).
3. **Deploy Backend.** Crea un servicio Docker desde `WiFiSense-backend` (usa su `Dockerfile`: compila con Maven y corre en Java 21 LTS). Puerto `8080`
   o la variable `PORT` de la plataforma. Health check: `/actuator/health`.
4. **Deploy AI service.** Crea un servicio Docker desde `WiFiSense-ai`. Variables: `AI_API_KEY` (la misma del
   backend) y opcionalmente `AI_MODEL_PATH`. Health check: `/health`. Si la plataforma lo permite, déjalo en red
   privada.
5. **Configure AI_SERVICE_URL.** En el backend, `AI_SERVICE_URL` = URL interna o pública del servicio de IA, sin
   barra final. Reinicia el backend.
6. **Deploy Frontend on Vercel.** Importa `WiFiSense-frontend`; framework Vite, build `npm run build`,
   salida `dist`. `vercel.json` ya redirige las rutas del SPA a `index.html`.
7. **Configure VITE_API_URL.** En Vercel → Environment Variables: `VITE_API_URL` = URL pública del backend.
   Vuelve a desplegar (Vite la incrusta en el build).
8. **Test Frontend → Backend.** Abre la URL de Vercel, inicia sesión y revisa el panel. Un error de CORS indica que falta la URL de Vercel en `CORS_ALLOWED_ORIGINS`.
9. **Test Backend → Database.** `GET /actuator/health` debe responder `UP`; la página Redes debe listar datos.
10. **Test Backend → AI.** En Análisis ejecuta “Inteligencia artificial”. Un 503 indica `AI_SERVICE_URL` o
    `AI_API_KEY` incorrectos; revisa los logs del backend.

11. **Test Agent → Backend.** En Mi Wi-Fi crea la clave del agente, ejecuta `python wifisense_agent.py setup` y
    `run --once` en un computador de la casa: debe imprimir `Enviado.` y la página mostrar los equipos conectados.
    Un 401 es una clave mal copiada o revocada; un 500 suele ser que faltan las migraciones V17–V19.

12. **Test recuperación.** En la página de inicio de sesión pulsa «¿Olvidaste tu contraseña?», escribe un correo
    registrado y revisa la bandeja (y spam). El enlace abre «Crea una contraseña nueva».

## Comprobación de secretos

Ningún repositorio contiene `.env`. Las variables reales viven solo en el panel de cada plataforma. El frontend
solo conoce `VITE_API_URL`, que es pública por naturaleza. Las claves del agente se guardan solo en el computador del
usuario; la base de datos tiene únicamente su SHA-256.
