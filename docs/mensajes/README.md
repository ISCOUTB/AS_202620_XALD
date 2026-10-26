# Mensajes y consistencia — Sincronización de transacciones

Taller aplicado de la semana 12 (`arqsw:taller-mensajes`). Decisión registrada en
[ADR-0014](../adr/0014-sincronizacion-asincrona-con-reintentos.md).

## 0. Operación elegida

**Sincronizar una transacción registrada en el dispositivo con el Backend XALD.** Es una operación
real del proyecto y la única donde hay desacoplamiento temporal:

1. `ProcesarNotificacionUseCase` guarda la transacción en `:corefinanciero` y la encola en
   `:syncqueue` (`XALDAPP/app/src/main/java/ProcesarNotificacionUseCase.kt`). El usuario ya la ve.
2. Más tarde, en segundo plano y cuando haya red, la cola la envía a
   `POST /api/v1/transacciones` (`backend/app/main.py`).

Entre 1 y 2 pueden pasar minutos u horas (Offline-First, [ADR-0001](../adr/0001-patron-offline-first.md)).
No es un prototipo aislado: el código vive en los módulos del sistema.

| Rol | Dónde |
|---|---|
| Productor | `XALDAPP/syncqueue/src/main/java/ColaSincronizacion.kt` (`encolarTransaccion`, `procesarPendientes`) |
| Canal | HTTP, `TransporteHttpSincronizacion.kt` (cola local en el dispositivo, sin broker) |
| Consumidor | `backend/app/main.py` (`registrar_transaccion`) |

No se usa RabbitMQ ni Kafka: la "cola" es la cola local del dispositivo y el consumidor es el
backend. Un broker no resuelve el problema real, que es que el productor está desconectado.

## 1. Contrato del mensaje

* **Esquema:** [`transaccion-registrada.schema.json`](transaccion-registrada.schema.json) (JSON Schema),
  el mismo que `TransaccionDTO` en [`docs/api/openapi.yaml`](../api/openapi.yaml) y `backend/app/dtos.py`.
* **Ejemplos:** [`ejemplos/`](ejemplos/) — uno válido v1, uno compatible con un campo nuevo y uno incompatible.
* **Metadatos de entrega** (cabeceras, no cuerpo):

| Cabecera | Valor | Para qué |
|---|---|---|
| `X-Correlation-Id` | UUID creado al encolar (`MensajeSincronizacion.idCorrelacion`) | Seguir el mensaje de productor a consumidor. **No cambia entre reintentos.** |
| `X-Version-Esquema` | `1` | Versión mayor del contrato. Sin cabecera, el backend asume `1`. |
| `X-API-Key` | secreto | Autenticación ([ADR-0004](../adr/0004-seguridad-y-cifrado.md)). |

* **Tipo de mensaje:** `transaccion.registrada` (`TIPO_TRANSACCION_REGISTRADA` en `MensajeSincronizacion.kt`).

## 2. Criterio de compatibilidad

Regla: **el consumidor ignora lo que no conoce, y el productor nunca quita lo que el consumidor exige.**
Pydantic ignora campos extra por defecto, así que el backend v1 acepta mensajes con campos nuevos.

| Cambio | ¿Compatible? | Qué se hace |
|---|---|---|
| Añadir un campo **opcional** | Sí | Misma versión `1`. El consumidor viejo lo ignora. Ejemplo: `ejemplos/transaccion-registrada-v1-con-campo-nuevo.json`. |
| Añadir un valor a `origen_datos` | Sí para el productor viejo, **no** para el consumidor viejo | Primero se despliega el backend, después la app. |
| Añadir un campo al DTO de Kotlin con valor por defecto | Sí | Así se añadieron `moneda`, `categoria` y `origenDatos` a `PayloadSincronizacionDTO`. |
| Quitar o renombrar un campo obligatorio | **No** | Sube a versión `2`. Ejemplo: `ejemplos/transaccion-registrada-incompatible-sin-moneda.json` → 422. |
| Cambiar el tipo o el formato de un campo (p. ej. `fecha_transaccion` a epoch) | **No** | Versión `2`. |
| Cambiar el significado de `id_transaccion` o `fecha_transaccion` | **No** | Versión `2` y revisar [ADR-0011](../adr/0011-resolucion-conflictos-lww.md): son la clave de deduplicación. |

Un consumidor que recibe una versión mayor que no conoce responde **400** (`verificar_version_esquema`
en `main.py`). Para el cliente un 400 es un fallo permanente: el mensaje va a la cola de fallidos y no se
reintenta para siempre. Como la app se distribuye por APK sin tienda ([ADR-0009](../adr/0009-distribucion-app.md)),
el backend debe aceptar la versión vieja mientras haya dispositivos que la usen.

**Verificado por:** `backend/tests/test_mensajes.py` (`test_compatible_*`, `test_incompatible_*`) y
`XALDAPP/syncqueue/src/test/java/com/proyecto/xald/syncqueue/ContratoMensajeTest.kt`, que comprueba que el
JSON del cliente tiene exactamente los campos del ejemplo v1. Si un lado renombra un campo, falla una de las dos.

## 3. Garantía de entrega: al menos una vez

Un mensaje solo sale de la cola cuando el backend responde 2xx (`ResultadoEnvio.Confirmado`).

**Consecuencias asumidas:**

1. **El backend recibirá duplicados.** Si el backend procesa y la respuesta se pierde (timeout), el cliente
   no sabe que llegó y la reenvía. Se compensa con deduplicación en los dos extremos (§4). El efecto neto
   sobre los datos es el de "exactamente una vez", pero la red no lo garantiza.
2. **El contador `xald_transacciones_recibidas_total` cuenta llegadas, no transacciones.** Se incrementa
   antes de la comprobación de idempotencia (arc42 §8). Para contar transacciones únicas hay que restar
   `xald_sync_duplicados_total`.
3. **Sin orden global.** Si el mensaje A falla y se reprograma, B (encolado después) puede llegar antes.
   No se bloquea la cola por un mensaje atascado. Entre transacciones distintas el orden no importa; entre
   dos versiones de la misma lo resuelve Last-Write-Wins por `fecha_transaccion`, no el orden de llegada.
4. **La entrega puede tardar indefinidamente** mientras no haya red. "Sin conexión" no consume intentos.
5. **Un mensaje en la cola de fallidos no llega nunca** hasta que alguien lo reintente
   (`reintentarFallido`). Es una pérdida visible y recuperable, no silenciosa.

**Por qué no "como máximo una vez":** perdería la transacción cada vez que se cae la red, y ESC-05 exige
0 transacciones perdidas. **Por qué no "exactamente una vez" en la red:** exigiría una confirmación
transaccional de dos fases entre un teléfono intermitente y el servidor.

## 4. Duplicados: deduplicación con la misma clave en los dos extremos

**Clave:** `id_transaccion` + `fecha_transaccion` (`claveDeduplicacion()` en `MensajeSincronizacion.kt`).
La misma identidad con otra fecha **no** es duplicado: es una versión nueva que resuelve LWW.

| Extremo | Dónde | Qué hace |
|---|---|---|
| Productor | `ColaSincronizacion.encolarTransaccion` | Si la clave ya está pendiente, en fallidos o confirmada, no crea otro mensaje y registra `encolado_duplicado_ignorado`. Cubre "el mismo SMS procesado dos veces". |
| Consumidor | `registrar_transaccion` en `backend/app/main.py` | Mismo id y misma fecha → 202 sin cambiar `ultima_version`, incrementa `xald_sync_duplicados_total` y registra `reenvio_duplicado_ignorado`. Cubre "el cliente reintentó tras perder la respuesta". |

## 5. Reintentos, backoff y cola de fallidos

`PoliticaReintentos.kt` (valores por defecto):

| Parámetro | Valor |
|---|---|
| Intentos máximos | 6 |
| Espera tras el fallo n | `30 s × 2^(n−1)` → 30 s, 60 s, 120 s, 240 s, 480 s |
| Tope de una espera | 15 min |
| Tiempo hasta fallidos | ≈ 15,5 min de fallos recuperables seguidos |

Clasificación (`clasificarRespuestaHttp` y `TransporteHttpSincronizacion`):

| Resultado | Casos | Acción |
|---|---|---|
| Confirmado | 2xx | Se retira de la cola. |
| Fallo recuperable | 408, 429, 5xx, timeout, otros `IOException` | Consume un intento y se reprograma con backoff; al agotar intentos va a fallidos. |
| Fallo permanente | 400, 401, 422 y demás 4xx | Directo a fallidos, sin reintentar. |
| Sin conexión | `UnknownHostException`, `ConnectException` | Se reprograma a 30 s **sin consumir intentos** y se corta la pasada. |

**Cola de fallidos (equivalente a DLQ):** la lista `fallidos` de `ColaSincronizacion`, consultable con
`obtenerFallidos()` (guarda `intentos` y `ultimoError`). Solo sale con `reintentarFallido(idCorrelacion)`,
que lo devuelve a la cola con los intentos en cero (re-drive manual). Mientras está ahí, encolar la misma
transacción cuenta como duplicado.

## 6. Trazabilidad de productor a consumidor

El `X-Correlation-Id` se crea al encolar y viaja igual en todos los reintentos. Cliente y backend escriben
una línea JSON por evento con `id_correlacion` e `id_transaccion` (mismo formato, [ADR-0010](../adr/0010-observabilidad.md)).
El backend devuelve el id en la cabecera de respuesta.

Ciclo de vida de un mensaje en los logs:

```
cliente  mensaje_encolado → envio_intento → [envio_reprogramado → envio_intento]* → envio_confirmado | mensaje_a_fallidos
backend  transaccion_recibida | reenvio_duplicado_ignorado | conflicto_resuelto_lww → peticion_http
```

Para seguir un mensaje: `grep '"id_correlacion": "<id>"'` en los logs de Render y en logcat. Ejemplo real
completo en [`evidencia.md`](evidencia.md).

## 7. Pruebas

| Propiedad | Prueba |
|---|---|
| **Duplicado** | `ColaSincronizacionMensajesTest.duplicado_mismaTransaccionEncoladaDosVeces_seEnviaUnaSolaVez` (cliente) y `test_mensajes.py::test_duplicado_mismo_mensaje_reenviado_no_cambia_estado_y_queda_en_el_log` (backend) |
| **Fallo recuperable** | `ColaSincronizacionMensajesTest.falloRecuperable_reintentaConEsperaCreciente_yTerminaConfirmado` (cliente) y `test_mensajes.py::test_fallo_recuperable_respuesta_perdida_el_reintento_da_el_mismo_resultado` (backend) |
| Agota intentos → fallidos | `falloRecuperable_queAgotaLosIntentos_vaALaColaDeFallidos` |
| Permanente → fallidos sin reintento | `falloPermanente_vaDirectoAFallidos_sinReintentar` |
| Sin red no consume intentos | `sinConexion_noConsumeIntentos` |
| Re-drive | `reintentarFallido_loDevuelveALaColaYSeConfirma` |
| Trazabilidad | `trazabilidad_elIdDeCorrelacionNoCambiaEntreReintentos_yApareceEnCadaEvento`, `test_trazabilidad_*` |
| Contrato y compatibilidad | `ContratoMensajeTest`, `test_compatible_*`, `test_incompatible_*` |
| Extremo a extremo (manual) | `ExtremoAExtremoManualTest` (se salta en CI si no hay `XALD_URL_E2E`) |

El CI ([`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)) corre las de Kotlin con
`./gradlew testDebugUnitTest` y las de Python con `pytest`.

Límites del montaje: [`limites-del-experimento.md`](limites-del-experimento.md).
