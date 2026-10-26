# Límites del experimento — Sincronización por cola

Qué demuestra el montaje del taller de la semana 12 y qué no. Ver [README](README.md) y
[ADR-0014](../adr/0014-sincronizacion-asincrona-con-reintentos.md).

## Qué sí se demuestra

* Las propiedades declaradas (deduplicación, backoff, cola de fallidos, sin red no consume intentos,
  correlación) con pruebas automatizadas que fallan si se quita cada mecanismo.
* Un recorrido real cliente Kotlin → backend FastAPI por HTTP, con un fallo inyectado, en el que el id de
  correlación aparece en los logs de los dos lados ([evidencia](evidencia.md)).

## Qué no cubre

| Límite | Consecuencia | Qué haría falta en producción |
|---|---|---|
| **La cola vive en memoria** (`ColaSincronizacion`) | Si Android mata el proceso, se pierden los pendientes y los fallidos. Hoy la "garantía al menos una vez" solo vale dentro de una vida del proceso. | Persistir la cola en Room/SQLCipher (`A-02`, `A-04`), con el cambio de estado y el encolado en la misma transacción local (patrón *outbox*). |
| **Nadie invoca `procesarPendientes()` solo** | En la app real no se envía nada todavía; en las pruebas lo invoca el test. | Un `CoroutineWorker` de WorkManager con restricción `NetworkType.CONNECTED`, periódico y también encadenado tras cada encolado. |
| **La deduplicación del backend vive en memoria** (`ultima_version`) | Tras un reinicio o cuando Render duerme el servicio, un reenvío se trata como primera recepción. | Tabla con clave única `(id_transaccion)` y columna de versión (plan P-04). |
| **`clavesConfirmadas` crece sin límite** | En memoria es despreciable por ahora; persistido, crecería siempre. | Guardar la marca `sincronizado = true` en la propia transacción en vez de un conjunto aparte. |
| **`ProcesarNotificacionUseCase` no usa el resultado del envío** | Nada marca `sincronizado = true` en `:corefinanciero`. | Un callback de confirmación del `:syncqueue` hacia `:corefinanciero` (plan P-02). |
| **Sin jitter en el backoff** | Si muchos teléfonos fallan a la vez, reintentan sincronizados. Con un solo usuario no se nota. | Jitter completo (espera aleatoria entre 0 y el valor exponencial). |
| **El fallo recuperable se simula** | Las pruebas usan transportes falsos y la prueba extremo a extremo "pierde" la primera respuesta a propósito. No se probó contra Render dormido ni con red real cortada. | Pruebas con un proxy de fallos (toxiproxy) o en un dispositivo en modo avión. |
| **La cola de fallidos no tiene interfaz** | Solo se ve por código (`obtenerFallidos()`) y por el log `mensaje_a_fallidos`. | Pantalla "pendientes de sincronizar" con botón de reintentar, y una métrica de tamaño de la cola. |
| **No hay métrica del lado cliente** | La trazabilidad es por logs; no hay contadores de reintentos o de fallidos en el teléfono. | Exportar contadores a la telemetría que se elija, o enviarlos al backend en la siguiente sincronización. |
| **Relojes desfasados** | La clave de deduplicación y LWW dependen de `fecha_transaccion` del dispositivo ([ADR-0011](../adr/0011-resolucion-conflictos-lww.md)). | Hora de recepción del servidor como cota (ya registrado como deuda). |
| **Las pruebas de Kotlin de este taller se corrieron localmente con el compilador de Kotlin 2.0.21 y JUnit 4.13.2**, no con el Gradle de Android | Pueden aparecer diferencias menores al compilar con AGP 9 / Kotlin 2.2. | El run de CI del commit entregado es la evidencia que vale. |

No se exige un broker ni contenedores: la consigna admite un simulador local, y aquí el "broker" es la
cola del propio dispositivo, que es donde está el problema real.
