# Guion de sustentación de WiFiSense

Guía para presentar el proyecto en unos 15 minutos más preguntas: qué decir, qué mostrar y en qué orden, las cifras
que conviene tener a mano y respuestas a las preguntas más probables. Las cifras son las del 19/10/2026; si cambian,
actualízalas aquí.

## 1. El problema y la idea (1 minuto)

En una casa o en una sede nadie sabe **quién está conectado al Wi-Fi**, **por qué la red está lenta** ni **si es
segura**. El router lo sabe, pero cada marca lo muestra distinto y casi nadie entra a verlo. WiFiSense instala un
**agente** en un computador conectado a la red; cada 5 minutos mide la conexión, ve los equipos conectados y lo envía a
una página donde la IA detecta anomalías, explica la causa probable, audita la seguridad y recomienda el mejor canal.

Dos públicos: **usuarios finales** (Mi Wi-Fi, su casa) y el **equipo** (redes de una sede, cada una con su sensor).

## 2. Arquitectura (2 minutos)

```text
Página (React + TypeScript, Vercel)
   │ HTTPS + JWT
Backend (Spring Boot 3, Java 21, Railway) ──► Servicio de IA (FastAPI + scikit-learn, Railway, red privada)
   │                       ▲
PostgreSQL 16 (Railway)    └── Agente / sensor (Python, instalador de Windows)
```

- **4 repositorios**, uno por componente, cada uno con sus pruebas y su GitHub Actions en cada Pull Request.
- **Solo el backend** habla con la base de datos y con la IA. La página nunca ve la IA ni la base.
- **Las MAC nunca salen del computador** del agente: viaja una huella SHA-256 con sal.
- Patrones de diseño (detalle en [PATTERNS.md](../PATTERNS.md)): **Factory Method** (fuentes de datos), **Decorator**
  (métricas, logs y validación alrededor de cada fuente), **Strategy** (análisis por umbrales, estadístico o con IA),
  **State** (estado de cada red), **Observer** (alertas y registro de actividad) y **Facade** (`NetworkAnalysisFacade`).

## 3. Demostración (8 minutos)

Ten abiertas las pestañas antes de empezar y una sesión iniciada en cada rol.

| # | Quién | Qué mostrar | Qué decir |
|---|---|---|---|
| 1 | Integrante 1 | Iniciar sesión con F12 → Red abierto | La contraseña no aparece: viaja cifrada con RSA-OAEP con un reto de un solo uso, además de HTTPS |
| 2 | Integrante 1 | Mi Wi-Fi → Agente: equipos conectados | Cada equipo con su tipo (celular, TV, impresora…), modelo, «Este computador» y «Router»; nunca la MAC |
| 3 | Integrante 1 | «¿Cuál es este equipo?» con un celular | El agente pasa a reportar cada minuto; apagas el Wi-Fi del celular y la página ve cuál desapareció |
| 4 | Integrante 2 | Seguridad, mejor canal, historial (reloj) | La IA audita cifrado y puertos del router, y recomienda el canal con menos vecinos |
| 5 | Integrante 2 | Equipo → Redes → panel del sensor | Una red de la sede con su sensor: mediciones reales, tráfico y protocolos |
| 6 | Integrante 2 | Reportes → Reporte semanal → CSV y PDF | Lunes a domingo en hora de Colombia, lo importante en frases |
| 7 | Jaider | Anomalías y Análisis | Isolation Forest marca la medición rara y el diagnóstico dice la causa probable (congestión, interferencia…) |
| 8 | Jaider | Modelo de IA → Entrenamiento con datos reales | El modelo nuevo se compara con el anterior y solo lo reemplaza si es mejor; historial de versiones |
| 9 | Jaider | GitHub → Actions en verde | Cada cambio pasa por pruebas automáticas antes de llegar a Railway o Vercel |

**Plan B:** si la red del salón no deja conectar el agente, muestra la red de la casa (los datos ya están en la página)
y el video corto del asistente grabado antes.

## 4. Cifras para tener a mano

| Qué | Cifra |
|---|---|
| Pruebas automáticas | Backend 351 (12 contra PostgreSQL real), IA 239, página 133, base de datos 166 comprobaciones |
| Detección de anomalías (datos sintéticos etiquetados) | Isolation Forest F1 0,90, 2 % de falsas alarmas; causa probable correcta en 94 % |
| Con datos reales (prueba local) | El modelo sintético daba 100 % de falsas alarmas en una red pequeña; el entrenado con datos reales, 3,7 % |
| Carga del backend (2 núcleos) | 50 personas a la vez: 176 peticiones/s, p95 97 ms, 0 errores. Límite: unas 250 peticiones/s |
| Carga de la IA | Unas 70 peticiones/s (alcanza para miles de sensores; cada uno pide 4 veredictos cada 5 minutos) |
| Base de datos | Un mes de 3 redes (78 000 mediciones): las últimas 50 mediciones en 0,2 ms, una semana por día en 4 ms |

Detalles: [load-tests/README.md](../load-tests/README.md), [seguridad.md](seguridad.md) y, en el repositorio de la IA,
`docs/evaluacion_modelos.md`, `docs/entrenamiento_real.md` y `docs/pruebas_de_carga.md`.

## 5. Preguntas probables

**¿Por qué Isolation Forest y no una red neuronal?** No hay fallas reales etiquetadas para entrenar un clasificador.
Isolation Forest es no supervisado, rápido con 8 variables y su puntaje es fácil de explicar. Se comparó con Local
Outlier Factor y One-Class SVM en los mismos datos: los tres quedan muy cerca (F1 entre 0,887 y 0,898) e
Isolation Forest tuvo el mejor F1 con pocas falsas alarmas (1,9 %) y detecta en 22 µs por medición.

**¿Cómo saben que la IA funciona si no hay fallas reales?** Se evalúa con fallas simuladas a propósito (con intensidad
aleatoria, las suaves son difíciles) y, con datos reales, midiendo las falsas alarmas sobre el comportamiento normal
de cada red. El modelo real solo entra en uso si gana en esa prueba. Es una limitación y la decimos.

**¿Pueden desconectar a un intruso?** No: eso lo controla el router. WiFiSense lo detecta, avisa por correo y guía a
cambiar la contraseña del Wi-Fi o bloquearlo en el router.

**¿Qué pasa si roban la clave de un agente?** Solo sirve para enviar reportes de esa red (no para leer nada), una vez
cada 50 segundos. Se revoca desde la página y el agente queda sin efecto al instante.

**¿Cómo protegen las contraseñas?** BCrypt en la base, cifradas con RSA en el navegador, política de 10–12 caracteres
con mayúscula, número y especial, y bloqueo de 10 minutos tras 5 intentos fallidos.

**¿Por qué el sensor no ve el tráfico de toda la red?** Un computador normal solo ve su propio tráfico; ver el de toda
la red exige el router (SNMP o su API). El diseño ya lo prevé (fábricas `RouterApiDataSourceCreator` y
`SnmpDataSourceCreator`), pero los routers de la casa y de la sede no lo ofrecen.

**¿Cuánta gente aguanta?** Ver la sección 4: con 50 personas a la vez responde en menos de 0,1 s (p95) en un computador
de 2 núcleos; el uso real (3 cuentas del equipo, sensores cada 5 minutos) está muy por debajo.

## 6. Limitaciones (dilas antes de que las pregunten)

- El tipo de cada equipo es una suposición a partir de lo que anuncia; por eso existe «¿Cuál es este equipo?».
- El sensor mide el tráfico de su propio computador, no el de toda la red.
- Las fallas para evaluar la IA son simuladas; falta que el equipo marque fallas reales.
- Un equipo que entra y sale en menos de 5 minutos puede no quedar en el historial.
