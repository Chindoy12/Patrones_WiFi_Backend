# Patrones de diseño en WiFiSense-backend

Cada patrón se adoptó porque resolvía un problema concreto. Los patrones que se analizaron y **no** se
implementaron también se explican al final, porque descartar un patrón innecesario es una decisión de diseño.

```text
Controller ──► NetworkAnalysisFacade (Facade)
                   │
                   ├── AnalysisStrategy (Strategy) ── AnomalyDetectionStrategy ── AiAnalysisClient ──► WiFiSense-ai
                   ├── MeasurementCollectionService
                   │        └── NetworkDataSourceProvider ── DataSourceCreator (Factory Method)
                   │                                              └── Metrics → Logging → Validation → fuente concreta (Decorator)
                   ├── Network.applyDetectedStatus() ── NetworkState (State)
                   └── NetworkEventPublisher (Observer) ── AlertObserver, ActivityLogObserver
```

---

## 1. Factory Method

**Problem.** Los datos de una red pueden venir de simulación, del propio servidor, de la API de un router, de SNMP
o de una captura de tráfico. Si el servicio de recolección hiciera `new SimulationDataSource(...)` o un `switch`
por tipo, cada fuente nueva obligaría a modificarlo, y cada fuente tiene dependencias distintas para construirse.

**Solution.** `DataSourceCreator` declara el método de fábrica `createDataSource()`. Cada creador concreto decide
qué producto construir y con qué dependencias. La operación `create()` del creador (final) usa el método de fábrica
y aplica a cualquier producto la misma cadena de decoradores.

**Classes.**
- Creator: `DataSourceCreator`
- Concrete creators: `AgentSensorDataSourceCreator`, `SimulationDataSourceCreator`, `SystemDataSourceCreator`,
  `RouterApiDataSourceCreator`, `SnmpDataSourceCreator`, `TrafficCaptureDataSourceCreator`
- Product: `NetworkDataSource`
- Concrete products: `AgentSensorDataSource`, `SimulationDataSource`, `SystemDataSource`, `RouterApiDataSource`,
  `SnmpDataSource`, `TrafficCaptureDataSource`
- Cliente: `NetworkDataSourceProvider` (elige el creador por `DataSourceType` con un `EnumMap`)

**Where.** `com.wifisense.network`

**Benefit.** Agregar una fuente = una clase producto + una clase creadora anotada con `@Component`. No se modifica
ninguna clase existente (OCP). `MeasurementCollectionService` solo conoce la interfaz `NetworkDataSource` (DIP).

**Why.** Es el patrón que separa *qué* fuente usar de *cómo* construirla. Un `switch` simple funcionaría hoy, pero
el proyecto ya prevé cinco fuentes con dependencias distintas.

**Estado real.** `SimulationDataSource` está completa. `SystemDataSource` es real pero parcial (mide latencia,
jitter y pérdida hacia un host de prueba; no puede medir señal ni ancho de banda). Router API, SNMP y captura de
tráfico están preparadas y lanzan `DataSourceUnavailableException` con un mensaje claro (parte del 20 % pendiente).
`AgentSensorDataSource` es la fuente de las redes del equipo: no se consulta, el sensor (el agente WiFiSense) envía
sus mediciones; por eso su `collect()` explica que la red la mide su sensor y el monitoreo programado no la recorre
(`DataSourcePolicy.collectsAutomatically`). Simulación y sistema solo se pueden elegir con `DEMO_DATA_SOURCES=true`.

---

## 2. Decorator

**Problem.** Toda lectura debe validarse, registrarse en logs y medirse (tiempo y fallos). Ponerlo dentro de cada
fuente duplicaría código cinco veces y mezclaría responsabilidades; la herencia no sirve porque las combinaciones
crecen (`LoggedValidatedSimulationDataSource`…).

**Solution.** Decoradores que implementan `NetworkDataSource`, envuelven otra fuente y añaden una sola
responsabilidad cada uno. La cadena se arma una vez en `DataSourceCreator.create()`:
`MetricsDecorator → LoggingDecorator → ValidationDecorator → fuente concreta`.

**Classes.**
- Component: `NetworkDataSource`
- Concrete components: las cinco fuentes
- Decorator: `DataSourceDecorator` (abstracto, delega por defecto)
- Concrete decorators: `ValidationDecorator`, `LoggingDecorator`, `MetricsDecorator`

**Where.** `com.wifisense.network.decorator`

**Benefit.** `SimulationDataSource` no se modificó para obtener validación, logs ni métricas (OCP, SRP). Las
métricas se exponen en `GET /api/observability/data-sources` y se ven en el panel del frontend.

**Why.** Es la forma de añadir comportamiento transversal a objetos que comparten interfaz sin tocarlos. El orden
importa: métricas va por fuera para contar también los fallos de validación.

---

## 3. Strategy

**Problem.** Hay varias formas de juzgar la salud de una red: límites fijos, desviación estadística respecto al
propio historial y un modelo de IA. Un método con `if (type == ...)` crecería con cada técnica nueva.

**Solution.** Interfaz `AnalysisStrategy` con `analyze(AnalysisContext)`. La fachada recibe todas las estrategias
por inyección y las indexa en un `EnumMap<AnalysisType, AnalysisStrategy>`; el usuario elige el tipo en tiempo de
ejecución.

**Classes.** `AnalysisStrategy`, `ThresholdAnalysisStrategy`, `StatisticalAnalysisStrategy`,
`AnomalyDetectionStrategy`, `AnalysisContext`, `AnalysisOutcome`

**Where.** `com.wifisense.analysis`

**Benefit.** Una estrategia nueva es una clase nueva; la fachada no cambia (OCP). Las tres son intercambiables a
través de la interfaz (LSP). El monitoreo automático usa `THRESHOLD` (barato) y el analista puede pedir `ANOMALY_DETECTION`.

**Why.** Encapsula algoritmos intercambiables con la misma entrada y salida, que es exactamente el caso.

---

## 4. State

**Problem.** El comportamiento de una red depende de su salud: cada cuánto se monitorea, qué severidad tiene la
alerta al entrar en un estado, si se cierran las alertas abiertas y cómo se transiciona. Con `if (status == CRITICAL)`
repartidos por el código, cambiar una regla obliga a buscar todos los sitios.

**Solution.** Cada estado es un objeto que implementa `NetworkState`. `Network` guarda el `NetworkStatus` (columna
`status`) y delega en `NetworkState.of(status)`.

| Estado | Intervalo de recolección | Alerta al entrar | Cierra alertas | Transición especial |
|---|---|---|---|---|
| `NormalState` | 5 min | INFO (recuperación) | Sí | — |
| `WarningState` | 2 min | WARNING | No | — |
| `CriticalState` | 1 min | CRITICAL | No | Una lectura NORMAL lo lleva a WARNING, no a NORMAL |

**Classes.** `NetworkState`, `NormalState`, `WarningState`, `CriticalState`, `Network`

**Where.** `com.wifisense.network.state`, `Network.applyDetectedStatus()`, `Network.isCollectionDue()`

**Benefit.** Las reglas de cada estado están en un solo archivo. `MonitoringJob` y `AlertObserver` piden al estado
lo que necesitan sin preguntar cuál es.

**Why.** Las transiciones con histéresis (recuperarse pasando por WARNING) son comportamiento propio del estado.

---

## 5. Observer

**Problem.** Tras un análisis pasan varias cosas: crear alertas, cerrar alertas, registrar actividad. Si la fachada
llamara a cada servicio, quedaría acoplada a todos y crecería con cada reacción nueva.

**Solution.** `NetworkEventPublisher` (sujeto) notifica a todos los `NetworkEventListener` (observadores) inyectados
por Spring. Los eventos son records de una jerarquía sellada: `MeasurementCollectedEvent`, `AnalysisCompletedEvent`,
`NetworkStatusChangedEvent`.

**Classes.** `NetworkEventPublisher`, `NetworkEventListener`, `AlertObserver`, `ActivityLogObserver`, `NetworkEvent`

**Where.** `com.wifisense.event`

**Benefit.** Un observador nuevo (por ejemplo, notificación por correo) es una clase nueva. La publicación es
síncrona y dentro de la misma transacción, así que una alerta nunca queda guardada sin su análisis.

**Why.** Un evento, varias reacciones independientes. Se descartó un `AnalysisObserver` que lanzara análisis al
recibir mediciones: crearía una dependencia circular (fachada → publicador → observador → fachada). El
`MonitoringJob` orquesta recolección y análisis de forma explícita, que es más fácil de seguir.

---

## 6. Facade

**Problem.** Analizar una red implica: buscar la red, leer la ventana de mediciones, recolectar si no hay datos,
cruzar tráfico, ejecutar la estrategia, guardar resultado y predicción, actualizar el estado y publicar eventos.
El controlador no debería conocer nada de eso.

**Solution.** `NetworkAnalysisFacade.analyze(networkId, type, requestedBy)` coordina el subsistema completo.

**Classes.** `NetworkAnalysisFacade` y los subsistemas que coordina (repositorios, `MeasurementCollectionService`,
estrategias, `NetworkEventPublisher`).

**Where.** `com.wifisense.analysis.NetworkAnalysisFacade`; la usan `AnalysisController` y `MonitoringJob`.

**Benefit.** Dos clientes distintos (REST y tarea programada) ejecutan exactamente el mismo flujo con una llamada.

**Why.** Simplifica una operación compleja sin ocultar el subsistema a quien sí lo necesite.

---

## Patrones analizados y no implementados

| Patrón | Decisión | Motivo |
|---|---|---|
| Adapter | Preparado, no implementado | Tendrá sentido cuando exista `RouterApiDataSource` real: cada fabricante (MikroTik, UniFi…) tiene su API y un adaptador la traducirá a `NetworkSnapshot`. Hoy no hay API externa que adaptar. |
| Builder | No | Los objetos con muchos campos son `record` inmutables o entidades con fábricas estáticas (`Measurement.from`). Un builder añadiría clases sin resolver nada. |
| Proxy | No | El acceso a la IA ya está aislado por la interfaz `AiAnalysisClient`. Un proxy de caché no tiene sentido: cada análisis usa datos nuevos. |
| Command | No | Las acciones (reconocer o resolver alerta) son métodos de dominio simples; no hay deshacer ni cola de comandos. |
| Template Method | Parcial | `DataSourceCreator.create()` es un método plantilla pequeño (paso fijo: decorar; paso variable: `createDataSource()`), que es como GoF describe el Factory Method. |
| Composite | No | Ubicación → zona → red es una jerarquía fija de tres niveles modelada con relaciones; no se necesita tratar hojas y grupos de forma uniforme. |

## Estructuras de datos usadas (y por qué)

| Estructura | Dónde | Necesidad real |
|---|---|---|
| `List` | Ventana de mediciones (`AnalysisContext`), historial | Orden cronológico |
| `EnumMap` | Estrategias por tipo, creadores por fuente, redes por estado | Búsqueda O(1) por enum |
| `HashMap` | Tráfico por timestamp en `AnomalyDetectionStrategy`, predicciones por análisis | Unir dos consultas sin N+1 |
| `ConcurrentHashMap` | `DataSourceMetrics`, instancias de fuentes | Acceso concurrente (peticiones + tarea programada) |
| `ArrayDeque` (cola FIFO acotada) | `ActivityLogObserver` | Conservar solo los 100 eventos más recientes |
| `LinkedHashSet` | `ReportService.compare` | Quitar redes repetidas conservando el orden |
| `TreeMap` | Desviaciones en `StatisticalAnalysisStrategy`, errores de validación | Salida ordenada y estable |

No se usaron Stack, Tree ni Graph porque ningún problema actual los requiere. Un grafo de topología de
dispositivos queda como mejora futura cuando exista una fuente que descubra enlaces entre equipos.
