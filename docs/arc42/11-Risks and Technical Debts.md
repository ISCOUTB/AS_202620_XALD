# Risks and Technical Debts

Esta sección consolida los riesgos y deudas técnicas que ya quedaron documentados individualmente en cada ADR (Sección 9) y en la Sección 6.

| Riesgo / Deuda técnica | Origen | Mitigación actual |
| --- | --- | --- |
| Complejidad adicional por el motor de resolución de conflictos (LWW), validación redundante y manejo de colas de sincronización | ADR-0001 | Asumida como costo necesario del enfoque Offline-First; se verifica con ESC-05 |
| Vulnerabilidad ante *phishing* (SMS falsos procesados como compras reales) y mensajes que Regex no logra leer | ADR-0002 | Ninguna formalizada todavía — pendiente de definir cómo se detecta un SMS fraudulento |
| Necesidad de mantenimiento continuo si un banco cambia el formato de sus mensajes | ADR-0002 | Mitigado en parte por el registro de reglas modular (ver ESC-03); sigue siendo trabajo manual del equipo |
| Incompatibilidad absoluta con dispositivos iOS | ADR-0003 | Aceptada como restricción permanente del alcance (RT-01), no hay mitigación planeada |
| El sistema no soporta ataques avanzados a gran escala ni alta concurrencia masiva | ADR-0004 | Aceptada porque el alcance actual es solo grupos reducidos de prueba |
| Menor profundidad en el análisis financiero avanzado para el usuario final | ADR-0005 | Aceptada como parte del recorte a MVP; podría revisarse en una futura iteración |
| Riesgo de acoplamiento indeseado entre paquetes por importación directa de clases | ADR-0006 | Mitigado con el modificador `internal` de Kotlin para los componentes que no son parte de la interfaz pública del módulo |
| La reclasificación automática de transacciones "Sin Categorizar" (mencionada en ESC-02) depende de un componente que **todavía no está implementado** en el código | Sección 6 — Runtime View | Sin mitigación todavía; queda pendiente de diseño e implementación |
| La resolución de conflictos entre dispositivos (ESC-05) depende de que los relojes de los dispositivos sean razonablemente confiables | Árbol de utilidad — Sección 10 | Ninguna formalizada; es el escenario de mayor riesgo técnico del proyecto (Riesgo: Alta) |



## Modos de fallo de las integraciones

Cada integración entre componentes que cruza la red puede fallar de tres maneras: no responde, responde tarde o responde dos veces. La tabla asigna una mitigación a cada modo y declara en qué estado está en el código, para no presentar como implementado lo que solo está diseñado.

| ID | Integración | Modo de fallo | Qué ocurre | Mitigación asignada | Estado en el código |
|---|---|---|---|---|---|
| **MF-01** | `:syncqueue` → Backend (`POST /api/v1/transacciones`, conector 4) | No responde | El backend está caído, no hay red o Render Free lo tiene dormido; el payload no llega. | La transacción ya se guarda en `:corefinanciero` antes de encolarse (pasos 3 y 4 de `ProcesarNotificacionUseCase`), así que el fallo no la pierde. La cola la conserva y la reenvía al volver la conexión, con reintentos de espera creciente. | **Pendiente.** La app aún no tiene cliente HTTP, y la cola (`ColaSincronizacion`) y el core guardan hoy en memoria (`mutableListOf`). |
| **MF-02** | `:syncqueue` → Backend | Responde tarde | El *cold start* de Render Free (duerme tras 15 min sin tráfico) o un backend lento hace esperar al cliente, que podría rendirse y reintentar mientras el primer intento sigue en curso. | Timeout holgado en el cliente, que cubra el cold start, y una petición de calentamiento a `GET /health` antes de sincronizar. La latencia queda visible en los logs (`duracion_ms`). | **Parcial.** El timeout y el calentamiento están en `backend/scripts/medir_esc05.py` (`timeout=120` y `GET /health`), y `duracion_ms` en el middleware de `backend/app/main.py`. En el cliente de la app: pendiente. |
| **MF-03** | `:syncqueue` → Backend | Responde dos veces | El mismo payload llega dos veces: el cliente reintentó tras perder la respuesta, o la cola reenvía un elemento que no se marcó como enviado (`obtenerSiguienteParaSincronizar()` no lo retira). | Idempotencia por `id_transaccion` (UUID). El backend guarda la última versión por id y resuelve con Last-Write-Wins ([ADR-0011](../adr/0011-resolucion-conflictos-lww.md)), así que reenviar la misma transacción no crea un registro nuevo. | **Implementada en el backend** (`ultima_version` y LWW en `main.py`; `backend/tests/test_lww.py`; 30/30 en `medir_esc05.py`). Límites declarados: el estado vive en memoria y se reinicia con cada despliegue o cuando Render duerme el servicio, y el contador `xald_transacciones_recibidas_total` sí cuenta el reenvío (la métrica se infla, el dato no se duplica). Pendiente: clave única en la persistencia remota y que la cola retire lo ya confirmado. |
| **MF-04** | `:aigemini` → Gemini API (conector 2) | No responde | Gemini está caído o no hay conexión; no hay categoría. | Timeout de 5 s y reintentos con espera creciente (~2 s, ~4 s) según la [Sección 6.2](06-Runtime%20view.md). Si se agotan, la transacción se guarda como «Sin Categorizar» (ESC-02). | **Pendiente (declarada).** `CategorizadorGemini` es un *mock* sin llamada de red ([ADR-0012](../adr/0012-aplazamiento-integracion-gemini.md)). `ProcesarNotificacionUseCase` no captura fallos de `categorizar()` ni asigna «Sin Categorizar». |
| **MF-05** | `:aigemini` → Gemini API | Responde tarde | Gemini responde después de los 5 s. | El corte de 5 s descarta la respuesta tardía y la cuenta como intento fallido. Si se agotan los intentos se aplica «Sin Categorizar», y la reclasificación posterior retoma la categoría. | **Pendiente (declarada).** Mismo motivo que MF-04. Además, la reclasificación automática no existe como componente (Secciones 6.2 y 11). |
| **MF-06** | `:aigemini` → Gemini API | Responde dos veces | Un reintento tras un timeout puede traer dos respuestas para el mismo comercio: la tardía del primer intento y la del segundo. | La llamada solo consulta (sin efecto secundario externo) y la categorización es idempotente: se conserva la primera respuesta aceptada y se ignora la siguiente. | **Pendiente (declarada).** No hay integración real todavía; la regla debe implementarse junto con el cliente real. |

## Modos de fallo: mitigaciones declaradas vs implementadas

*Fecha de contraste con el código: octubre 2026.*

| Modo de fallo | Mitigación declarada | Dónde se declara | Implementación en el código | Estado |
|---|---|---|---|---|
| Gemini **no responde** (timeout) | Corte a 5 s y hasta 3 reintentos con espera creciente (~2 s, ~4 s) | arc42 §6 (ESC-02), §10 | `XALDAPP/aigemini/src/main/java/CategorizadorGemini.kt` es un mock; no hay cliente HTTP, timeout ni reintentos | **Pendiente declarado** ([ADR-0012](../adr/0012-aplazamiento-integracion-gemini.md)) |
| Gemini **falla tras los reintentos** | Guardar la transacción como "Sin Categorizar"; no se pierde el registro | arc42 §6 (ESC-02), §10 | El mock siempre devuelve una categoría, así que esta rama no existe | **Pendiente declarado** |
| Reclasificación posterior al volver el servicio | Reclasificar automáticamente las "Sin Categorizar" | arc42 §6, §11 | No existe el componente | **Pendiente declarado** (fila ya existente en §11) |
| Gemini **responde tarde** (después del corte de 5 s) | Se trata como fallo: la respuesta tardía se descarta y la transacción se atiende en la reclasificación posterior | Esta tabla (nuevo) | No implementado | **Pendiente declarado** |
| Backend **no responde** al sincronizar | La transacción permanece en la cola y se reenvía al reconectar, en orden cronológico | arc42 §1 | `ColaSincronizacion` guarda en memoria (`mutableListOf`); no hay cliente HTTP ni reenvío | **Parcial:** encolado implementado; envío y reintento pendientes ([EVIDENCIA-S9.md](../EVIDENCIA-S9.md), H-3) |
| Backend recibe la **misma transacción dos veces** | LWW por `id_transaccion` e idempotencia por reenvío | [ADR-0011](../adr/0011-resolucion-conflictos-lww.md), apartado de idempotencia en §8 | `backend/app/main.py` (`ultima_version`) con pruebas en `backend/tests/test_lww.py` | **Parcial:** LWW implementado y medido (ESC-05); no distingue duplicado de conflicto |
| Backend **pierde su estado** al reiniciarse | Persistencia del estado, junto con la migración a SQLite/Room | ADR-0011, `aspectos.md` (A-02) | El estado vive solo en memoria | **Pendiente declarado** |

| La integración con Gemini es un *mock* (`CategorizadorGemini.kt`): no hay llamada real, timeout ni reintentos, y la categorización no usa IA | ADR-0012 | Aplazada de forma explícita; el contrato `ServicioCategorizacion` aísla el punto de reemplazo |
| La cola de sincronización guarda en memoria y no tiene cliente HTTP, por lo que aún no envía transacciones al backend | `EVIDENCIA-S9.md` (H-3), `aspectos.md` (A-03) | Sin mitigación todavía; pendiente de implementación |
| El backend guarda `ultima_version` solo en memoria: se pierde al reiniciar o cuando Render Free duerme el servicio | ADR-0011 | Aceptada para este corte; persistencia pendiente (A-02) |
| Los reenvíos idénticos se cuentan como conflictos y no se distinguen de un conflicto real | Apartado de idempotencia (§8) | Diseño definido; cambio en `main.py` pendiente |
| La llave de Gemini no tiene mecanismo de gestión definido y falta decidir si la integración se extrae al backend | ADR-0012, ADR-0013 | Integración real aplazada; reevaluar según los disparadores del ADR-0013 |

## Modos de fallo de la integración

Esta tabla recoge los tres modos de fallo que puede tener cada integración externa del sistema (Gemini y Backend XALD), con su mitigación y su estado real en el código.

| Integración | Modo de fallo | Qué ocurre | Mitigación | Estado |
|---|---|---|---|---|
| Gemini | **No responde** | No hay respuesta en 5 s; la transacción queda sin categoría | Hasta 3 reintentos con espera creciente (~2 s, ~4 s). Si todos fallan, se guarda como "Sin Categorizar" y no se pierde (ESC-02) | Pendiente: la integración es un mock ([ADR-0012](../adr/0012-aplazamiento-integracion-gemini.md)) |
| Gemini | **Responde tarde** | La respuesta llega después del corte de 5 s y podría pisar una categoría ya asignada | Se trata como fallo: la respuesta tardía se descarta y la transacción se atiende en la reclasificación posterior | Pendiente |
| Gemini | **Responde dos veces** | Un reintento y el intento original responden ambos, con dos categorías para la misma transacción | Solo se aplica la categoría si la transacción sigue "Sin Categorizar"; la segunda respuesta se ignora | Pendiente |
| Backend XALD | **No responde** | No llega un 2xx; la transacción sigue sin sincronizar | Permanece en la cola y se reenvía al reconectar, en orden cronológico | Parcial: encolado implementado; envío y reintento pendientes (no hay cliente HTTP) |
| Backend XALD | **Responde tarde** | La respuesta llega cuando el cliente ya expiró y reenvía una transacción que el backend sí registró | Idempotencia por `id_transaccion`: el reenvío no duplica el registro | Parcial: LWW implementado; separar duplicado de conflicto está pendiente |
| Backend XALD | **Responde dos veces** (recibe la misma transacción dos veces) | Llegan dos peticiones con el mismo `id_transaccion` | Idempotencia por `id_transaccion` y resolución LWW ([ADR-0011](../adr/0011-resolucion-conflictos-lww.md)) | Parcial: verificado con `test_lww.py` y ESC-05; el estado vive en memoria |
