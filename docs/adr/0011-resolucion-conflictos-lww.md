# ADR-0011: Resolución de Conflictos con Last-Write-Wins, UUID de Dispositivo y Timestamp del Cliente

* *Estatus:* Decidido
* *Fecha:* Octubre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Semana 9 — consistencia de datos

## Contexto y Problema

Por el modelo Offline-First (ADR-0001), una transacción nace en el dispositivo y se envía al Backend XALD más tarde. Dos dispositivos del mismo usuario pueden modificar la misma transacción antes de sincronizar, y el backend debe decidir cuál versión se conserva (ESC-05, RT-05). Esa decisión obliga a elegir tres cosas a la vez: quién genera la identidad de la transacción, qué dato sirve como "versión" y qué regla desempata.

## Opciones Evaluadas

* **Identidad generada por el servidor + versión asignada por el servidor:** el servidor es la única fuente del orden, pero obliga a que el dispositivo espere una respuesta antes de tener un id, lo que rompe ESC-01 (registrar sin conexión).
* **Vector clocks o CRDT:** resuelven conflictos sin depender de relojes, pero exigen guardar metadatos de versión por dispositivo y una lógica de fusión que excede RO-01 (equipo y tiempo de un semestre).
* **UUID generado en el dispositivo + Last-Write-Wins por timestamp del cliente (Adoptada):** el dispositivo crea el id al registrar (`UUID.randomUUID()` en `GestorCoreFinanciero`) y el backend compara `fecha_transaccion`; gana la más reciente.

## Decisión Tomada

1. El identificador de la transacción es un **UUID generado en el dispositivo** en el momento del registro y no cambia nunca (`id_transaccion`, `TransaccionDTO`).
2. La **versión** de una transacción es su `fecha_transaccion` (ISO 8601, UTC), tomada del reloj del dispositivo.
3. El backend conserva la versión con la fecha más reciente. Con fechas iguales conserva la que ya tenía (la comparación es estrictamente mayor, `main.py`).
4. Reenviar una transacción ya recibida no crea un registro nuevo: la identidad es el UUID, así que el envío es idempotente.

## Consecuencias

### Positivas:

* El dispositivo puede registrar y sincronizar sin esperar al servidor (ESC-01).
* Reenviar el mismo payload es seguro, porque la idempotencia sale del propio UUID.
* La regla cabe en pocas líneas y se verifica con `backend/tests/test_lww.py` y `backend/scripts/medir_esc05.py`.

### Por qué es la decisión más costosa de revertir:

Es la única decisión de datos que queda **escrita en cada dispositivo instalado**, no solo en el servidor. Las otras decisiones recientes se cambian redesplegando: el hosting (ADR-0008) se mueve a otro proveedor con el mismo `Dockerfile` y las métricas (ADR-0010) se reemplazan sin tocar los datos. Esta no.

Costo de reversión, si algún día se cambia a versiones asignadas por el servidor o a vector clocks:

* **Esquema local:** cada APK instalado tiene transacciones con UUID y fecha del cliente. Cambiar la noción de versión exige una migración de la base de datos de cada dispositivo.
* **Distribución:** la app se instala como APK directo, sin tienda (ADR-0009), así que no se puede forzar la actualización. Durante un tiempo convivirían dispositivos con la regla vieja y la nueva contra el mismo backend.
* **Contrato público:** cambia `TransaccionDTO` y `docs/api/openapi.yaml`, y con ellos el cliente de `:syncqueue` y el payload de `:corefinanciero`.
* **Historia irrecuperable:** las versiones que LWW descartó no se guardan, así que no se pueden reconstruir con otra regla.
* **Verificación:** hay que reescribir ESC-05, `test_lww.py`, `medir_esc05.py` y la métrica `xald_sync_conflictos_resueltos_total`.

### Riesgos y Mitigación:

* **Riesgo:** LWW depende de que el reloj del dispositivo sea confiable. Un reloj adelantado hace que su versión gane siempre. Este es el riesgo de mayor probabilidad de ESC-05 (Sección 11).
* **Mitigación:** ninguna formalizada. Queda declarado como deuda: una opción futura es que el backend registre su propia hora de recepción y rechace fechas muy por delante de ella.
* **Riesgo:** el estado de versiones (`ultima_version` en `main.py`) vive en memoria y se pierde en cada despliegue o cuando Render duerme el servicio (ADR-0008).
* **Mitigación:** pendiente la persistencia remota. Mientras tanto la garantía vale solo dentro de un mismo periodo de vida del proceso.

## Puntos Abiertos

* Persistencia remota del estado de versiones (clave única por `id_transaccion`).
* Política ante relojes desfasados.

**Trazabilidad:**
* Implementación: [`backend/app/main.py`](../../backend/app/main.py), [`GestorCoreFinanciero.kt`](../../XALDAPP/corefinanciero/src/main/java/com/proyecto/xald/corefinanciero/GestorCoreFinanciero.kt)
* Pruebas: [`backend/tests/test_lww.py`](../../backend/tests/test_lww.py), [`backend/scripts/medir_esc05.py`](../../backend/scripts/medir_esc05.py)
* Decisiones relacionadas: [ADR-0001](0001-patron-offline-first.md), [ADR-0008](0008-plataforma-de-despliegue.md), [ADR-0009](0009-distribucion-app.md)
* Restricción y escenario: RT-05, ESC-05 ([arc42 §2](../arc42/02-Architecture%20constraints.md), [arc42 §10](../arc42/10-Quality%20Requirements.md))
