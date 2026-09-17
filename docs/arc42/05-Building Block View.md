# Building Block View
La vista de bloques de construcción muestra la descomposición de XALD en dos niveles, alineados directamente con los niveles **C1** y **C2** del modelo C4 documentado en `docs/c4/c4.md`, y con el esqueleto de arranque ya implementado (`Bootstrapper` + módulos `AppModule`).

| Bloque | Responsabilidad |
| :--- | :--- |
| **Aplicación XALD** | Es lo que ve y usa el usuario; ahí pasa todo el proceso de capturar, procesar, guardar y mostrar la información. |
| **Backend XALD** | Sincroniza y respalda las transacciones de la Aplicación XALD; vive dentro de la frontera del sistema, pero como contenedor independiente. |

*Nota de alcance: la API de Gemini y el SO Android/SMS no se listan como bloques del sistema porque, según el C1 (`docs/c4/c4.md`), son sistemas externos fuera de la frontera de XALD — ya están documentados como actores externos en la Sección 3 (Context and Scope).*

### 5.1 Nivel 1 — Sistema XALD (corresponde al C1 de `docs/c4/c4.md`)

Vista de caja blanca del sistema completo: dentro de la frontera "Sistema XALD" conviven dos contenedores principales: la **Aplicación Móvil XALD** y el **Backend XALD**, conectados a través del **conector 4 (Sincronización REST / TLS 1.3)**. Ambos completan el ciclo de vida de una transacción, siendo la Aplicación Móvil el único contenedor con el que el usuario interactúa directamente (conector 3), el que recepta notificaciones bancarias (conector 1) y el que consulta la API externa de IA (conector 2).

| 1. Aplicación Móvil XALD (Contenedor Android) | 2. Backend XALD (Contenedor Servidor) |
| :--- | :--- |
| • Ingesta e interpretación de SMS (`:parser` - `ParseoSms`) | • Servidor API REST / Endpoints HTTP |
| • Categorización inteligente via IA (`:aigemini` - `CategorizadorGemini`) | • Procesamiento y consolidación de reportes |
| • Base de datos local cifrada AES-256 (`:corefinanciero` - `TransaccionEntidad`) | • Motor de resolución de conflictos de sincronización (LWW) |
| • Gestor de cola offline en tránsito (`:syncqueue` - `ColaSincronizacion`) | • Persistencia remota (Base de Datos PostgreSQL / Respaldo) |
| • Interfaz de usuario y orquestación (`:app` - Jetpack Compose) | |

1. **Aplicación Móvil XALD:** Captura, procesa y presenta la información financiera de forma local bajo un esquema *Offline-First*. Se encarga de la ingesta de SMS, el parseo por expresiones regulares, la categorización adaptativa con IA, la persistencia cifrada (AES-256) en `SQLite/Room` y la interfaz de gestión.
2. **Backend XALD:** Expone la API REST protegida por TLS 1.3, procesa reportes globales y ejecuta la sincronización de datos entre dispositivos mediante la estrategia *Last-Write-Wins* (LWW), manteniendo la persistencia remota como respaldo consolidado del usuario..

---

### 5.2 Nivel 2 — Aplicación Móvil Android (corresponde al C2 de `docs/c4/c4.md`)

Descomposición del contenedor "Aplicación Móvil Android" en sus módulos internos, tal como aparecen en el C2: el módulo `:app` actúa como orquestador central y delega en cuatro submódulos independientes. La tabla incluye además su correspondencia con el esqueleto de código ya escrito.

| Módulo (C2) | Función | Carpeta en el esqueleto | Módulo de Inicialización |
| :--- | :--- | :--- | :--- |
| **`:app`** | Interfaz gráfica (Jetpack Compose), Dashboard y orquestador principal | `XALDAPP/app/` | `XaldApplication` |
| **`:parser`** | Receptor de eventos (BroadcastReceiver) y motor de expresiones regulares (Regex Engine) | `XALDAPP/parser/` | `ParserModule` |
| **`:aigemini`** | Cliente HTTP y SDK de Google Gemini para categorización de comercios (ACL) | `XALDAPP/aigemini/` | `AiGeminiModule` |
| **`:corefinanciero`** | Almacenamiento local cifrado (SQLite/Room con AES-256) | `XALDAPP/corefinanciero/` | `CoreFinancieroModule` |
| **`:syncqueue`** | Gestor de la cola de sincronización asíncrona (timestamps + UUIDs) | `XALDAPP/syncqueue/` | `SyncQueueModule` |

El orden de arranque definido en `ProcesarNotificacionUseCase.kt` respeta esta misma descomposición de 5 Bounded Contexts: `CoreFinancieroModule → ParserModule → AiGeminiModule → SyncQueueModule → UiModule`. Cada módulo implementa el contrato `AppModule` (con un único método `init()`), lo que permite que el `ProcesarNotificacionUseCase` los trate a todos por igual sin conocer sus detalles internos, y que si uno falla, aísle el error sin tumbar el resto de la aplicación.

**Ajuste de consistencia con el C2:** El esqueleto tenía previamente un módulo `RemoteDatabaseModule` dentro del arranque de la app. Con el C2 ya definido, ese bloque no corresponde al lado de la Aplicación Móvil — la persistencia remota vive dentro del contenedor **Backend XALD** (ver 5.1), y la app solo la alcanza a través de `:syncqueue`. Por eso se retira del `ProcesarNotificacionUseCase` de la app y queda documentado únicamente como responsabilidad del Backend XALD.

* **`:app` (Interfaz gráfica, Dashboard y orquestador principal):** implementado con Jetpack Compose; recibe el SMS del sistema operativo (conector 1) y coordina el resto de los módulos, además de exponer la UI y los reportes al usuario (conector 3).
* **`:parser` (Receptor de eventos y motor de expresiones regulares):** su `BroadcastReceiver` capta el SMS entrante y su `Regex Engine` interpreta el texto con reglas locales conocidas, delegando en `:aigemini` los casos ambiguos.
* **`:corefinanciero` (Almacenamiento local cifrado):** guarda las transacciones en SQLite/Room con cifrado AES-256, y expone el saldo y el historial al módulo `:app`.
* **`:syncqueue` (Gestor de la cola de sincronización asíncrona):** encola las transacciones pendientes usando timestamps + UUIDs y las sincroniza con el Backend XALD (conector 4) cuando hay conexión disponible.
* **`:aigemini` (Cliente HTTP y SDK de Google Gemini):** consulta la API de Gemini (conector 2) para categorizar los comercios que el motor local no puede resolver.

