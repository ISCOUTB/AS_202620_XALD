# ADR-0014: Sincronización Asíncrona por Cola Local con Reintentos, frente a Envío Síncrono

* *Estatus:* Decidido
* *Fecha:* Octubre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Tercer corte — Semana 12, mensajes y consistencia

## Contexto y Problema

Cuando llega un SMS bancario, `ProcesarNotificacionUseCase` registra la transacción en el dispositivo y
luego hay que llevarla al Backend XALD. Hasta ahora la cola de `:syncqueue` solo guardaba payloads: no
enviaba, no reintentaba y no retiraba nada (arc42 §8, OP-04 "Parcial"; §11, planes P-01 y P-02).

Hay que decidir **cómo** llega la transacción al backend. Las fuerzas son:

* ESC-01: registrar una transacción sin conexión.
* ESC-05: 0 transacciones perdidas al sincronizar.
* El backend corre en el plan gratuito de Render, que duerme el servicio y tarda en despertar ([ADR-0008](0008-plataforma-de-despliegue.md)).
* RO-01: equipo de dos personas y un semestre.

## Opciones Evaluadas

### A. Envío síncrono en el momento del registro

`ejecutar(sms)` hace `POST /api/v1/transacciones` y espera la respuesta antes de terminar.

### B. Cola local en el dispositivo con envío en segundo plano, reintentos y cola de fallidos (Adoptada)

`ejecutar(sms)` guarda y encola, y termina. `procesarPendientes()` envía después, reintenta con backoff y
aparta en una cola de fallidos lo que no se puede entregar. Detalle en [`docs/mensajes/README.md`](../mensajes/README.md).

### C. Broker de mensajes (RabbitMQ, Kafka o un servicio administrado) entre la app y el backend

Descartada sin comparar en detalle: el broker vive del lado del servidor, así que el teléfono sin red
tampoco puede publicar en él. Seguiría haciendo falta la cola local de B, y se añade un servicio que
no cabe en RO-02 (costo $0).

## Comparación A frente a B

| Criterio | A. Síncrono | B. Cola local asíncrona |
|---|---|---|
| **Acoplamiento temporal** | Total: registrar exige que el backend esté arriba **en ese instante**. Si Render duerme o no hay red, el registro falla o hay que decidir qué hacer con él. | Ninguno: el registro termina sin el backend. El backend puede estar caído horas. |
| **Acoplamiento de disponibilidad** | Disponibilidad del registro = disponibilidad de red × disponibilidad del backend. | Disponibilidad del registro = la del teléfono. |
| **Latencia percibida por el usuario** | Viaje de red + procesamiento. Con Render dormido, la primera petición puede tardar decenas de segundos. Hay que mostrar un cargando. | Escritura local: lo que tarde `guardarTransaccion`. |
| **Latencia de extremo a extremo** (hasta que el dato está en el backend) | La mínima posible cuando todo funciona: un viaje. | Mayor y variable: depende de cuándo corre `procesarPendientes`, de la red y del backoff (hasta ≈ 15,5 min antes de ir a fallidos). |
| **Consistencia entre dispositivo y backend** | Fuerte si el envío salió bien: el usuario sabe que el backend lo tiene. | Eventual. Durante la ventana, el backend está atrás del dispositivo (ya aceptado en arc42 §8). |
| **Duplicados** | También aparecen (un timeout deja al cliente sin saber si llegó), pero el usuario tendría que reintentar a mano. | Aparecen por diseño ("al menos una vez") y se absorben con deduplicación en los dos extremos. |
| **Qué pasa cuando falla** | El error sube a la UI. O se pierde la transacción (rompe ESC-05) o se guarda para reintentar luego... que es reinventar B. | Reintento automático; si se agota, cola de fallidos visible y re-drive manual. |
| **Complejidad** | Baja: una llamada HTTP. | Mayor: sobre del mensaje, política, clasificación de errores, cola de fallidos, correlación y sus pruebas. |
| **Depurar un caso** | Una petición, una respuesta. | Hay que seguir varios intentos en el tiempo; por eso el `X-Correlation-Id` es obligatorio. |

## Decisión Tomada

Se adopta **B**. El argumento decisivo es que **A no cumple ESC-01**: con red intermitente, el envío
síncrono obliga a fallar el registro o a guardarlo para después, y "guardar para después y reintentar"
es exactamente B, pero sin política. Pagar la complejidad de B es la consecuencia directa de ser Offline-First
([ADR-0001](0001-patron-offline-first.md)), no una preferencia tecnológica.

A sigue siendo lo correcto en otro caso: si una operación necesita una respuesta del servidor para
continuar (por ejemplo, validar una licencia o iniciar sesión), debe ser síncrona y fallar de forma visible.

## Consecuencias

### Positivas
* Registrar no depende de la red ni de que Render esté despierto.
* Ninguna transacción se pierde en silencio: o se confirma, o queda pendiente, o queda en fallidos.
* Los reintentos son seguros porque el backend ya era idempotente ([ADR-0011](0011-resolucion-conflictos-lww.md), arc42 §8.5).

### Negativas (asumidas)
* El backend va atrás del dispositivo. El usuario no tiene confirmación de que su dato está en el servidor.
* El backend recibe duplicados y el contador de recepción los cuenta.
* No hay orden global de llegada.
* Más código y más pruebas en `:syncqueue`.

### Riesgos
* La cola vive en memoria: si Android mata el proceso, se pierden los pendientes. Ver
  [límites](../mensajes/limites-del-experimento.md).
* Nada invoca `procesarPendientes()` periódicamente todavía (falta el `Worker` de WorkManager).

## Trazabilidad

* Implementación: `XALDAPP/syncqueue/src/main/java/` (`ColaSincronizacion.kt`, `PoliticaReintentos.kt`,
  `TransporteSincronizacion.kt`, `TransporteHttpSincronizacion.kt`, `RegistroSincronizacion.kt`), `backend/app/main.py`.
* Contrato: `docs/mensajes/transaccion-registrada.schema.json`, `docs/api/openapi.yaml`.
* Pruebas: `XALDAPP/syncqueue/src/test/java/com/proyecto/xald/syncqueue/`, `backend/tests/test_mensajes.py`.
* Escenarios: ESC-01, ESC-05. Planes de arc42 §11 que cierra en parte: P-01 (cliente HTTP) y P-02 (retirar lo confirmado).
* Decisiones relacionadas: [ADR-0001](0001-patron-offline-first.md), [ADR-0008](0008-plataforma-de-despliegue.md),
  [ADR-0010](0010-observabilidad.md), [ADR-0011](0011-resolucion-conflictos-lww.md).
