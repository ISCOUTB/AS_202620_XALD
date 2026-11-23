# Building Block View

La vista de bloques de construcción muestra la descomposición de XALD en dos niveles, alineados directamente con los niveles C1 y C2 del modelo C4 documentado en `docs/c4/c4.md`.

| Bloque | Responsabilidad |
|---|---|
| **Aplicación XALD** | Es lo que ve y usa el usuario; ahí pasa todo el proceso de capturar, procesar, guardar y mostrar la información. |
| **Backend XALD** | Sincroniza y respalda las transacciones de la Aplicación XALD; vive dentro de la frontera del sistema, pero como contenedor independiente. |

> **Nota de alcance:** la API de Gemini y el SO Android/SMS no se listan como bloques del sistema porque, según el C1 (`docs/c4/c4.md`), son sistemas externos fuera de la frontera de XALD — ya están documentados como actores externos en la Sección 3 (Context and Scope).

## 5.1 Nivel 1 — Sistema XALD (corresponde al C1 de `docs/c4/c4.md`)

Vista de caja blanca del sistema completo: dentro de la frontera "Sistema XALD" conviven dos contenedores principales: la Aplicación Móvil XALD y el Backend XALD, conectados a través del **conector 4 (Sincronización REST / HTTPS)**. Ambos completan el ciclo de vida de una transacción, siendo la Aplicación Móvil el único contenedor con el que el usuario interactúa directamente (conector 3), el que recepta notificaciones bancarias (conector 1) y el que consulta la API externa de IA (conector 2).

| 1. Aplicación Móvil XALD (Contenedor Android) | 2. Backend XALD (Contenedor Servidor) |
|---|---|
| • Ingesta e interpretación de SMS (`:parser` - ParseoSms) | • Servidor API REST / Endpoints HTTP |
| • Categorización inteligente via IA (`:aigemini` - CategorizadorGemini) | • Recepción y consulta de transacciones (`POST` y `GET /api/v1/transacciones`) |
| • Base de datos local cifrada AES-256 (`:corefinanciero` - TransaccionEntidad) | • Motor de resolución de conflictos de sincronización (LWW) |
| • Gestor de cola offline en tránsito (`:syncqueue` - ColaSincronizacion) | • Persistencia remota (Base de Datos PostgreSQL / Respaldo) |
| • Interfaz de usuario y orquestación (`:app` - Vistas XML) | |

**Aplicación Móvil XALD:** Captura, procesa y presenta la información financiera de forma local bajo un esquema Offline-First. Se encarga de la ingesta de SMS, el parseo por expresiones regulares, la categorización adaptativa con IA, la persistencia cifrada (AES-256) en SQLite/Room y la interfaz de gestión.

**Backend XALD:** Expone la API REST sobre HTTPS, autenticada con `X-API-Key`; recibe las transacciones que envía la app, aplica *Last-Write-Wins* (LWW) sobre PostgreSQL y permite consultar una transacción por id. La app no descarga cambios remotos.

## 5.2 Nivel 2 — Aplicación Móvil Android (corresponde al C2 de `docs/c4/c4.md`)

Descomposición del contenedor "Aplicación Móvil Android" en sus módulos internos, tal como aparecen en el C2: el módulo `:app` actúa como orquestador central y delega en cuatro submódulos independientes. La tabla incluye además su correspondencia con el esqueleto de código ya escrito.

| Módulo (C2) | Función | Carpeta en el esqueleto | Módulo de Inicialización |
|---|---|---|---|
| `:app` | Interfaz gráfica (vistas XML), Dashboard y orquestador principal | `XALDAPP/app/` | XaldApplication |
| `:parser` | Motor de expresiones regulares y validación del remitente (lista permitida, ADR-0016) | `XALDAPP/parser/` | ParserModule |
| `:aigemini` | Cliente HTTP y SDK de Google Gemini para categorización de comercios (ACL) | `XALDAPP/aigemini/` | AiGeminiModule |
| `:corefinanciero` | Almacenamiento local cifrado (SQLite/Room con AES-256) | `XALDAPP/corefinanciero/` | CoreFinancieroModule |
| `:syncqueue` | Gestor de la cola de sincronización asíncrona (timestamps + UUIDs) | `XALDAPP/syncqueue/` | SyncQueueModule |

`XaldApplication` crea el core, la cola y el programador de sincronización al arrancar; `SmsReceiver` arma `ProcesarNotificacionUseCase` con los contratos públicos de los cuatro contextos de dominio y lo ejecuta por cada SMS. La persistencia remota vive solo en el Backend XALD (ver 5.1); la app la alcanza a través de `:syncqueue`.

* **`:app`** (Interfaz gráfica, Dashboard y orquestador principal): implementado con vistas XML y el `SmsReceiver`; recibe el SMS del sistema operativo (conector 1) y coordina el resto de los módulos, además de exponer la UI y los reportes al usuario (conector 3).
* **`:parser`** (Motor de expresiones regulares y validación del remitente): su `Regex Engine` interpreta el texto con las reglas bancarias y rechaza los remitentes no autorizados. La categorización no se hace dentro de `:parser`: la orquesta `:app`.
* **`:corefinanciero`** (Almacenamiento local cifrado): guarda las transacciones en SQLite/Room con cifrado AES-256, y expone el saldo y el historial al módulo `:app`.
* **`:syncqueue`** (Gestor de la cola de sincronización asíncrona): encola las transacciones pendientes usando timestamps + UUIDs y las sincroniza con el Backend XALD (conector 4) cuando hay conexión disponible.
* **`:aigemini`** (Cliente HTTP y SDK de Google Gemini): consulta la API de Gemini (conector 2) para categorizar los comercios que el motor local no puede resolver. En la ingesta se usan reglas locales (`CategorizadorLocal`); Gemini se consulta en segundo plano desde `CategorizacionIaWorker`.
