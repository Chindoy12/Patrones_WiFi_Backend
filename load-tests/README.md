# Pruebas de carga del backend

`carga.py` simula a varias personas del equipo usando la página a la vez (panel, estado, redes, mediciones, calidad,
panel del sensor, reporte semanal, alertas, anomalías y modelo de IA, sobre las 3 redes con datos más recientes) y,
opcionalmente, a varios sensores enviando reportes. Solo usa la librería estándar de Python (3.9 o superior): no hay
que instalar nada.

Objetivo: **p95 menor que 1 segundo y menos de 1 % de errores**. El script termina con código 0 si lo cumple y 1 si no.

## Cómo correrla

En local, con el backend en `http://localhost:8080` y una cuenta del equipo (las credenciales van solo en variables de
entorno, nunca en un archivo):

```cmd
set WIFISENSE_USER=<usuario del equipo>
set WIFISENSE_PASSWORD=<contraseña>
python load-tests\carga.py --url http://localhost:8080 --usuarios 20 --segundos 60 --salida resultado.json
```

- `--usuarios`: personas a la vez. Cada una abre una página y espera hasta `--pausa` segundos (1 por defecto) antes
  de la siguiente, como alguien que lee.
- Sensores: con `WIFISENSE_AGENT_KEYS=clave1,clave2,…` (claves de **redes de prueba**, nunca las de una red real)
  cada uno envía un reporte cada 55 s. El backend acepta uno cada 50 s por clave: un `429` cuenta aparte, no como error.
- Si el servidor solo acepta contraseñas cifradas (`PLAIN_PASSWORDS_ALLOWED=false`), usa `WIFISENSE_TOKEN` con el token
  de una sesión abierta en lugar de usuario y contraseña.

Contra Railway hay que agregar `--remoto`: la prueba se limita a 20 usuarios y 120 segundos para no gastar el plan ni
molestar a los usuarios reales, y solo lee (nunca crea cuentas, redes ni claves). Hazla en un horario sin uso.

## Resultados (local, 19/10/2026)

Backend y servicio de IA en el mismo computador de **2 núcleos y 8 GB** que generaba la carga, PostgreSQL 16 con
una red de 2 000 mediciones reales y 8 redes de prueba con su sensor reportando cada 55 s.

| Escenario | Peticiones | Por segundo | Errores | p50 | p95 | p99 | ¿Cumple? |
|---|---:|---:|---:|---:|---:|---:|---|
| 20 personas (pausa de hasta 1 s) + 8 sensores | 2 362 | 39 | 0 % | 12 ms | 33 ms | 135 ms | Sí |
| 50 personas (pausa de hasta 0,5 s) + 8 sensores | 10 657 | 176 | 0 % | 22 ms | 97 ms | 211 ms | Sí |
| 100 personas sin pausa (estrés) + 8 sensores | 15 440 | 256 | 0 % | 361 ms | 902 ms | 1 228 ms | Justo |

Por página, con 20 personas el p95 va de 16 ms (redes, anomalías) a 44 ms (panel); el reporte de un sensor tarda más
(p50 300 ms, p95 630 ms) porque en el mismo momento pide a la IA la seguridad, la calidad, el canal y las anomalías.

**Lectura:** el equipo de WiFiSense son 3 cuentas y cada sensor reporta cada 5 minutos, así que el uso real está muy
por debajo del primer escenario. Con 100 personas pidiendo páginas sin parar (unas 256 por segundo), el computador
de 2 núcleos llega a su límite: nada falla, pero las respuestas suben a casi 1 segundo. En ese punto lo que más pesa
es el procesador (el backend, la IA, PostgreSQL y el propio generador compartían los 2 núcleos).

Los resultados de Railway dependen del plan contratado; para medirlos usa `--remoto` y anota aquí la fecha y el
resultado.

## Qué no cubre

- No mide la página (Vercel la sirve como archivos estáticos desde su red de distribución).
- No crea usuarios finales: Mi Wi-Fi usa las mismas consultas que el panel del sensor, que sí se mide.
- El servicio de IA tiene su propia prueba de carga en el repositorio de la IA (`load-tests/carga_ia.py`).
