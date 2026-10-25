# Cross-cutting Concepts
## 8.1 Context Map (Mapa de Contextos)
Cada módulo del proyecto representa un **Bounded Context** (Contexto Delimitado) con responsabilidades claras y un vocabulario propio, evitando la filtración de lógica o datos hacia otros dominios.

| Módulo | Contexto | Responsabilidad |
| :--- | :--- | :--- |
| **`:app`** | Presentación | Interfaz gráfica (Jetpack Compose), orquesta las llamadas a los demás módulos y muestra saldo/reportes al usuario. |
| **`:parser`** | Ingesta | Recibe el SMS crudo del sistema operativo y lo interpreta mediante el Motor de Parseo (Regex). |
| **`:aigemini`** | Categorización | Traduce las respuestas de la API externa de Gemini al formato de dominio propio mediante una Capa Anticorrupción (ACL). |
| **`:corefinanciero`** | Núcleo Financiero | Dueño único de la transacción persistida; expone su interfaz pública para que otros módulos consulten sin tocar la base de datos. |
| **`:syncqueue`** | Sincronización | Gestiona la cola de transacciones pendientes y coordina el envío de lotes hacia el Backend XALD. |
| **Backend XALD** | Externo | Servicio fuera de los módulos Gradle móviles. Recibe los lotes de `:syncqueue` vía REST/HTTPS. |

---

## 8.2 Diccionario de Lenguaje Ubicuo

| Término (ES) | Módulo (Contexto) | Tipo | Significado |
| :--- | :--- | :--- | :--- |
| `TransaccionProcesadaDTO` | `:parser` | Dato en memoria (DTO) | Objeto temporal que representa una transacción interpretada por el Motor de Parseo antes de ser persistida. Sin identidad de base de datos. |
| `ParseoSms` | `:parser` | Componente (Regex) | Componente interno que aplica expresiones regulares al SMS crudo del sistema operativo para extraer datos financieros. |
| `CategorizadorGemini` | `:aigemini` | Traductor (ACL) | Capa Anticorrupción que adapta la respuesta de la API externa de Gemini al dominio local, aislando el sistema de cambios externos. |
| `TransaccionEntidad` | `:corefinanciero` | Entidad persistida (BD) | Representación permanente de la transacción en SQLite/Room. Fuente primaria de verdad (*Offline-First*) cifrada con AES-256. |
| `InformacionFinanciera` | `:corefinanciero` | Interfaz pública | Contrato que expone el Núcleo Financiero a otros módulos (`:app`, `:syncqueue`) para consultar datos de forma segura. |
| `PayloadSincronizacionDTO` | `:corefinanciero` $\rightarrow$ `:syncqueue` | Dato en tránsito (DTO) | Objeto de solo lectura empaquetado por el núcleo financiero. Sirve de contrato seguro para enviar datos al Backend sin exponer la tabla original. |
| `ColaSincronizacion` | `:syncqueue` | Gestor (Cola) | Componente que administra las transacciones pendientes de envío al Backend XALD, asegurando resistencia a desconexiones y tránsito seguro vía TLS 1.3. |
| `ServicioParser` | `:parser` | Interfaz pública | Contrato que expone el contexto de Ingesta. Recibe el texto crudo del SMS y devuelve un `TransaccionProcesadaDTO`, manteniendo oculta la implementación `ParseoSms` mediante el modificador `internal`. |
| `ServicioCategorizacion` | `:aigemini` | Interfaz pública | Contrato que expone el contexto de Categorización. Recibe el nombre del comercio y devuelve un `CategoriaResultado`, sin permitir que tipos propios de la API externa crucen la frontera del módulo. |
| `CategoriaResultado` | `:aigemini` | Dato en memoria (DTO) | Resultado ya traducido por la Capa Anticorrupción. Contiene el nombre de la categoría y el nivel de confianza expresados en el vocabulario del dominio propio, no en el del proveedor externo. |
| `GestorCoreFinanciero` | `:corefinanciero` | Componente (Persistencia) | Implementación interna del Núcleo Financiero. Genera el identificador único de cada transacción, convierte los datos recibidos en `TransaccionEntidad` y empaqueta los pendientes como `PayloadSincronizacionDTO`. |
| `ColaSincronizacionService` | `:syncqueue` | Interfaz pública | Contrato que expone el contexto de Sincronización. Permite encolar un `PayloadSincronizacionDTO` y consultar los pendientes, manteniendo oculta la implementación `ColaSincronizacion`. |
| `ProcesarNotificacionUseCase` | `:app` | Orquestador (Caso de uso) | Componente de la capa de aplicación que coordina el ciclo completo de una transacción invocando únicamente los contratos públicos de los cuatro contextos de dominio, sin conocer sus implementaciones internas. |

---

## 8.3 Tipos de Relación entre Contextos (DDD)

| Origen | Destino | Tipo de Relación (DDD) | Justificación Teleológica |
| :--- | :--- | :--- | :--- |
| `:parser` | `:corefinanciero` | **Customer-Supplier** | `:parser` entrega `TransaccionProcesadaDTO` y `:corefinanciero` lo transforma a `TransaccionEntidad` antes de guardar. Hay mapeo directo entre dominios. |
| `:aigemini` | Gemini API (Externo) | **Anti-Corruption Layer (ACL)** | `CategorizadorGemini` aísla el modelo de dominio interno de las variaciones y tipos crudos de la API externa. |
| `:corefinanciero` | `:app` | **Customer-Supplier** | `:app` consume los servicios financieros mediante la interfaz pública `InformacionFinanciera`, respetando el contrato expuesto por el proveedor. |
| `:corefinanciero` | `:syncqueue` | **Customer-Supplier** | `:corefinanciero` despacha un `PayloadSincronizacionDTO` de solo lectura a `:syncqueue` a través de su interfaz pública, evitando accesos directos a la base de datos. |
| `:syncqueue` | Backend XALD (Externo) | **Customer-Supplier** | `:syncqueue` entrega lotes formateados al Backend mediante REST/HTTPS; el servidor actúa como el proveedor remoto del estado consolidado. |

---

## 8.4 Enlace a Atributos de Calidad (`docs/aspectos.md`)
* **Offline-First (A-01):** `TransaccionEntidad` es la fuente primaria de verdad local. `ColaSincronizacion` retiene los cambios locales mientras no exista conectividad a internet.
* **Cifrado AES-256:** Cifra la persistencia en reposo de `TransaccionEntidad` dentro del contenedor del módulo `:corefinanciero`.
* **Cifrado TLS 1.3:** Protege la comunicación en tránsito en dos fronteras externas: desde `CategorizadorGemini` hacia la API de Gemini, y desde `ColaSincronizacion` hacia el Backend XALD.

---


---

## 8.5 Consistencia de Datos: Operación → Garantía

Esta tabla declara, para cada operación real del sistema, qué garantía de consistencia da y en qué estado está en el código. El modelo es Offline-First (ADR-0001): el dispositivo es la fuente de verdad inmediata y el backend se pone al día de forma eventual.

| Operación | Dónde está | Garantía de consistencia | Estado en el código |
| :--- | :--- | :--- | :--- |
| `guardarTransaccion(monto, comercio)` | `:corefinanciero` (`GestorCoreFinanciero`) | **Escritura local inmediata y lectura de lo propio:** si devuelve `true`, la siguiente lectura en el mismo dispositivo ya ve la transacción. Rechaza `monto <= 0`. El UUID se genera al guardar. | Implementada. Hoy guarda en una lista en memoria (`mutableListOf`); Room cifrada con AES-256 está pendiente. |
| `obtenerUltimaTransaccion()` / `obtenerPendientesSincronizacion()` | `:corefinanciero` | **Consistencia fuerte local:** lee del mismo almacén donde se escribió, sin red de por medio. | Implementada (misma limitación de memoria). |
| `encolarTransaccion(payload)` | `:syncqueue` (`ColaSincronizacion`) | **Orden FIFO local, entrega al menos una vez:** el payload permanece en la cola hasta que se confirme su envío. | Parcial. La cola es una lista en memoria y `obtenerSiguienteParaSincronizar()` no retira el elemento; falta retirarlo al recibir `202`. |
| `POST /api/v1/transacciones` | Backend XALD (`registrar_transaccion`) | **Idempotente por `id_transaccion` y Last-Write-Wins:** si llegan dos versiones del mismo UUID, se conserva la de `fecha_transaccion` más reciente, sin importar el orden de llegada. Entre dispositivos la consistencia es **eventual**. | Implementada en `main.py`. El estado (`ultima_version`) está en memoria y se reinicia con cada despliegue (ADR-0008, ADR-0011). |
| `GET /metrics` | Backend XALD | **Sin garantía entre reinicios:** los contadores son del proceso y vuelven a cero al reiniciarse (ADR-0010). | Implementada. |
| `GET /health` | Backend XALD | No toca estado. | Implementada. |

**Lo que el sistema NO garantiza:** que dos dispositivos vean los mismos datos al mismo tiempo, ni que el backend conserve las versiones tras un reinicio, ni que el orden de llegada coincida con el orden real de los hechos. La fuente de verdad del usuario es siempre su dispositivo.

## 8.6 Verificación de Dos Operaciones contra la Implementación

**Operación 1 — `POST /api/v1/transacciones` (Last-Write-Wins).** Garantía declarada: gana la versión más reciente, sin importar el orden de llegada.

* Se cumple en `backend/app/main.py`, función `registrar_transaccion`: `previa = ultima_version.get(transaccion.id_transaccion)` (línea 113) y la actualización solo ocurre si `transaccion.fecha_transaccion > previa` (líneas 114-115). Cada colisión suma `xald_sync_conflictos_resueltos_total` y deja el log `conflicto_resuelto_lww` con `gano="entrante"` o `"existente"` (líneas 116-121).
* Se verifica en `backend/tests/test_lww.py`: `test_version_reciente_gana_si_llega_despues`, `test_version_antigua_no_sobrescribe_a_la_reciente` (la vieja llega después y no gana), `test_primera_version_no_cuenta_como_conflicto` y `test_medicion_esc05_30_transacciones_en_conflicto`.
* Límite: la garantía vale mientras el proceso siga vivo, porque `ultima_version` está en memoria.

**Operación 2 — `guardarTransaccion` (escritura local).** Garantía declarada: solo se guarda una transacción válida, con identidad única, y queda lista para sincronizar.

* Se cumple en `GestorCoreFinanciero.kt`: `if (monto <= 0.0) return false` rechaza montos inválidos, `UUID.randomUUID()` crea la identidad y `sincronizado = false` la marca como pendiente. Después `obtenerPendientesSincronizacion()` filtra `!it.sincronizado` y arma el `PayloadSincronizacionDTO`.
* Coincide con el contrato del backend, donde `TransaccionDTO` exige `monto > 0` (`backend/app/dtos.py`).
* Límite: el almacén es una lista en memoria y no sobrevive al cierre de la app, así que la durabilidad local aún no está cumplida hasta que se implemente Room.

## 8.7 Diseño de Caché

**Estado actual:** el sistema no tiene ninguna caché implementada. Lo que sigue es un **diseño propuesto**, no código existente. Las cachés de este diseño se justifican por RO-02 (costo $0, el plan gratuito de Gemini limita las solicitudes) y por ESC-01 (respuesta rápida al registrar).

| Qué se cachea | Dónde | Cuánto dura | Por qué |
| :--- | :--- | :--- | :--- |
| Categoría por comercio (`comercio` → `CategoriaResultado`) | Tabla local en Room, dentro de `:aigemini`/`:corefinanciero` | Hasta que el usuario corrija la categoría; revisión máxima de 30 días. | Evita volver a consultar a Gemini por un comercio ya conocido: menos llamadas y menos consumo de la capa gratuita. |
| Saldo consolidado y totales del reporte | En memoria dentro de `:app` (estado de la pantalla) | Mientras viva la pantalla o hasta la siguiente escritura. | Evita recalcular la suma de todas las transacciones en cada redibujado. |
| Backend XALD | **No hay caché.** | — | El único estado es `ultima_version`, que es la fuente de decisión de LWW, no una copia. Cachearlo duplicaría la verdad. |

El plazo de 30 días es una propuesta del equipo y se ajustará con datos reales de uso.

## 8.8 Política de Invalidación tras Escritura

**Regla declarada:** *toda escritura invalida la caché derivada de ella antes de devolver el control.* Es invalidación por escritura (write-invalidate), no por tiempo, salvo el límite de 30 días de la categoría.

| Escritura | Qué se invalida | Cómo |
| :--- | :--- | :--- |
| `guardarTransaccion(...)` | Saldo consolidado y totales del reporte | `:app` descarta el valor en memoria al recibir `true` y lo recalcula en la siguiente lectura. |
| El usuario corrige la categoría de una transacción | La entrada `comercio → categoría` de esa transacción | Se sobrescribe con la categoría elegida por el usuario, que tiene prioridad sobre la de Gemini. |
| Reclasificación posterior de «Sin Categorizar» (Sección 6.2) | Totales por categoría | Se descartan al cambiar la categoría. |
| Respuesta `202` del backend | Ninguna caché | La sincronización no cambia lo que el usuario ve: su dispositivo ya tiene la versión actual. |

**Implementación:** ninguna. Es un plan que se implementa junto con las cachés de 8.7. Cuando exista Room, la invalidación del saldo puede apoyarse en que Room avise los cambios de la tabla.

## 8.9 Garantías del Flujo por Eventos

El sistema no usa un broker de mensajes (ni Kafka ni RabbitMQ). El único flujo por eventos es el que va del dispositivo al backend por la cola de sincronización (`:syncqueue`), y su garantía es:

**Entrega al menos una vez, con consumo idempotente (efecto neto: una sola vez).**

* **Qué asume:**
  1. Cada transacción tiene un UUID único desde el origen (`id_transaccion`).
  2. La cola solo retira un elemento cuando el backend responde `202`; si no responde, se reenvía.
  3. Reenviar la misma transacción no cambia el resultado, porque el backend la identifica por UUID y aplica LWW (ADR-0011).
  4. Los relojes de los dispositivos son razonablemente confiables.
* **Qué NO garantiza:** orden global entre transacciones distintas ni exactamente una vez a nivel de red. Si el mismo payload llega dos veces, el contador `xald_transacciones_recibidas_total` sí lo cuenta dos veces, aunque el dato no se duplica (MF-03, Sección 11).
* **Estado:** el reenvío idempotente está implementado en el backend. Falta que la cola retire lo confirmado y que el cliente HTTP exista (Sección 11, MF-01 y MF-03).

