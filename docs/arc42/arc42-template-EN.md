# Architecture Decisions

Las decisiones arquitectónicas del proyecto se registran como ADR (Architecture Decision Record) individuales en `docs/adr/`, siguiendo la convención de nombre `NNNN-titulo-en-kebab-case.md`. Cada decisión responde a un objetivo de negocio o de calidad de la Sección 1, y varias se verifican mediante los escenarios de calidad de la Sección 10.

| ID | Título | Decisión | Relacionado con |
| --- | --- | --- | --- |
| [ADR-0001](../adr/0001-patron-offline-first.md) | Adopción de Patrón de Arquitectura Offline-First | Persistencia primero en base de datos local cifrada (SQLite/Room); los datos se envían al backend de forma asíncrona mediante una cola de sincronización cuando hay red. | Objetivo de calidad 1 (Disponibilidad) · RT-02 · ESC-01 |
| [ADR-0002](../adr/0002-parsing-hibrido.md) | Estrategia de Parsing Híbrido (Regex + librerías open source) | Usar un receptor de eventos local (RECEIVE_SMS) con un motor de expresiones regulares, en vez de una API bancaria oficial o un modelo de IA completo. | Objetivo de calidad 5 (Modificabilidad) · RT-04 · RO-02 · ESC-03 |
| [ADR-0003](../adr/0003-restriccion-os.md) | Restricción de Plataforma a Android y Exclusión de iOS | Limitar el cliente exclusivamente al ecosistema Android, usando BroadcastReceiver con el permiso RECEIVE_SMS. | Objetivo de negocio OB-01 · RT-01 |
| [ADR-0004](../adr/0004-seguridad-y-cifrado.md) | Modelo de Seguridad Acotado y Cifrado de Datos | Enfocar la seguridad en dos capas: cifrado local en reposo (AES-256 vía Android Keystore) y cifrado en tránsito (HTTPS/TLS). | Objetivo de calidad 3 (Seguridad básica) · RT-03 · RL-01 · ESC-04 |
| [ADR-0005](../adr/0005-reduccion-de-funcionalidades.md) | Alcance Reducido en el Módulo de Analítica y Reportes (MVP) | Reducir el módulo de reportes a lo esencial (saldos consolidados, gráficos básicos, lista de movimientos), dejando fuera el motor avanzado de analítica y predicción. | RO-01 |
| [ADR-0006](../adr/0006-seleccion-de-estilo-arquitectonico.md) | Selección de Estilo Arquitectónico — Monolito Modular | Adoptar un monolito modular organizado por paquetes de dominio (`parser`, `corefinanciero`, `syncqueue`, `aigemini`), en vez de arquitectura por capas o hexagonal. | RO-01 · Objetivo de calidad 5 (Modificabilidad) |
| [ADR-0007](../adr/0007-contratos-por-modulo.md) | Contratos Públicos por Módulo y Orquestación Centralizada | Cada módulo expone una única interfaz pública (`internal` en Kotlin); toda la coordinación entre contextos ocurre en `:app` mediante `ProcesarNotificacionUseCase`. | Auditoría de violaciones semana 6 (V-01 a V-07) |
| [ADR-0008](../adr/0008-plataforma-de-despliegue.md) | Selección de Plataforma de Despliegue para el Backend XALD | Desplegar el Backend XALD como Web Service Docker en el plan Free de Render, definido como código en `render.yaml`, sin tarjeta de crédito. | RO-02 · RO-03 · Sección 7 (Deployment View) |
| [ADR-0009](../adr/0009-distribucion-app.md) | Distribución de la Aplicación Móvil como APK Directo | Distribuir el APK generado por Gradle directamente vía GitHub Releases (sideloading), sin publicar en Google Play en este corte. | RO-01 · RO-02 · RO-03 · RT-01 |
| [ADR-0010](../adr/0010-observabilidad.md) | Observabilidad con Logs JSON y Métricas Prometheus Embebidas | Exponer logs estructurados en JSON (stdout) y un endpoint `/metrics` en formato Prometheus dentro del propio backend, sin plataforma de monitoreo externa. | RO-02 · RO-03 · ESC-05 |
| [ADR-0011](../adr/0011-resolucion-conflictos-lww.md) | Implementación de Resolución de Conflictos Last-Write-Wins (LWW) | El backend conserva la última versión por `id_transaccion` y resuelve los conflictos por fecha de transacción; los reenvíos idénticos se tratan de forma idempotente (§8.5). | RT-05 · ESC-05 · MF-03 |
| [ADR-0012](../adr/0012-aplazamiento-integracion-gemini.md) | Aplazamiento de la Integración Real con la API de Gemini | Mantener `CategorizadorGemini` como *mock* detrás del contrato `ServicioCategorizacion` hasta definir cómo se protege la llave. | ESC-02 · MF-04 a MF-06 |
| [ADR-0013](../adr/0013-extraer-o-no-integracion-gemini.md) | Extraer o no extraer la integración con Gemini como servicio independiente | **No extraer** en este corte: `:aigemini` sigue en el monolito modular; se evaluaron explícitamente "no extraer" y "extraer al backend", con disparadores para reevaluar. | ADR-0006 · ADR-0007 · ESC-02 · Sección 11 |

# Quality Requirements

Esta sección desarrolla los 5 objetivos de calidad definidos en la Sección 1 (Disponibilidad, Resiliencia, Seguridad básica, Consistencia eventual y Modificabilidad). Primero se muestra el árbol de utilidad, que los prioriza según su impacto en el negocio y su riesgo técnico, y después los 5 escenarios de calidad (ESC-01 a ESC-05) que los hacen medibles, cada uno enlazado a su objetivo de negocio y a la restricción arquitectónica que lo origina.

## Quality Scenarios

Cada escenario sigue las seis partes que exige arc42: fuente, estímulo, artefacto, entorno, respuesta y medida de respuesta. Cada medida declara explícitamente su umbral, la carga bajo la cual se evalúa y la herramienta de verificación.

### ESC-01 · Registro de transacción sin conexión

| Parte | Contenido |
|---|---|
| **Fuente** | Entidad bancaria (mensaje SMS) |
| **Estímulo** | Llega una notificación de transacción al dispositivo |
| **Artefacto** | Ingestion Module y Data & Sync Module |
| **Entorno** | Operación normal, dispositivo en modo avión (sin conexión) |
| **Respuesta** | El sistema extrae los datos, registra la transacción en el almacenamiento local cifrado y la marca como pendiente de sincronizar |
| **Medida** | **Umbral:** ≤ 2 s desde la recepción del SMS hasta la persistencia confirmada · **Carga:** 20 SMS consecutivos con 1 s de separación · **Herramienta:** prueba instrumentada con `adb shell am broadcast` y medición por *timestamp* en el log |

**Objetivo de calidad:** 1 (Disponibilidad) · **Objetivo de negocio:** OB-02 · **Restricción:** RT-02

### ESC-02 · Indisponibilidad del servicio de categorización

| Parte | Contenido |
|---|---|
| **Fuente** | Google Gemini API (servicio externo de categorización) |
| **Estímulo** | La petición falla o excede el tiempo de espera |
| **Artefacto** | Processing & Parser Module (Gemini API Client) |
| **Entorno** | Con conexión disponible, servicio externo degradado o caído |
| **Respuesta** | La transacción ya registrada se conserva, se marca como "Sin Categorizar" y se reclasifica automáticamente cuando el servicio vuelve a responder |
| **Medida** | **Umbral:** 0 transacciones perdidas; corte a los 5 s; máximo 3 reintentos con espera creciente · **Carga:** 50 transacciones con el servicio simulado como no disponible · **Herramienta:** servidor simulado (*mock*) que devuelve error 503, verificación por conteo en base de datos |

**Objetivo de calidad:** 2 (Resiliencia) · **Objetivo de negocio:** OB-01 · **Restricción:** RO-02

### ESC-03 · Incorporación de una nueva entidad bancaria

| Parte | Contenido |
|---|---|
| **Fuente** | Equipo de desarrollo |
| **Estímulo** | Una entidad bancaria cambia el formato de sus mensajes o se requiere soportar una entidad no contemplada |
| **Artefacto** | Processing & Parser Module (Regex Engine) |
| **Entorno** | Tiempo de desarrollo |
| **Respuesta** | Se agrega una regla de lectura nueva sin modificar el código de las entidades ya soportadas |
| **Medida** | **Umbral:** 1 archivo nuevo y 0 modificaciones fuera del registro de reglas; esfuerzo ≤ 4 h · **Carga:** incorporación de una entidad real no soportada · **Herramienta:** `git diff --stat` sobre el *commit* de la incorporación |

**Objetivo de calidad:** 5 (Modificabilidad) · **Objetivo de negocio:** OB-04 · **Restricción:** RT-04

### ESC-04 · Protección de la información almacenada

| Parte | Contenido |
|---|---|
| **Fuente** | Atacante con acceso físico al dispositivo |
| **Estímulo** | Intento de lectura directa del archivo de base de datos |
| **Artefacto** | Data & Sync Module (SQLite/Room con AES-256) |
| **Entorno** | Dispositivo perdido, robado o comprometido |
| **Respuesta** | El contenido resulta ilegible sin la clave, resguardada en el Android Keystore |
| **Medida** | **Umbral:** 0 campos financieros legibles en texto plano · **Carga:** base de datos con 500 transacciones · **Herramienta:** extracción del archivo con `adb pull` e inspección con `strings` y `sqlite3` |

**Objetivo de calidad:** 3 (Seguridad básica) · **Objetivo de negocio:** OB-03 · **Restricciones:** RT-03 y RL-01

### ESC-05 · Resolución de conflictos al sincronizar

| Parte | Contenido |
|---|---|
| **Fuente** | Usuario con la aplicación en más de un dispositivo |
| **Estímulo** | La misma transacción se modifica en dos dispositivos mientras ambos están sin conexión |
| **Artefacto** | Data & Sync Module (Sync Queue) y Backend XALD (motor LWW) |
| **Entorno** | Restablecimiento de la conexión en ambos dispositivos |
| **Respuesta** | Se aplica la política Last-Write-Wins tomando la marca de tiempo más reciente, sin duplicar ni sobrescribir saldos |
| **Medida** | **Umbral:** 100 % de conflictos resueltos automáticamente, 0 transacciones distintas perdidas · **Carga:** 30 transacciones en conflicto simultáneo · **Herramienta:** dos emuladores con relojes sincronizados, verificación por comparación de estado final contra el esperado |

**Objetivo de calidad:** 4 (Consistencia eventual) · **Objetivo de negocio:** OB-02 · **Restricción:** RT-05

## Árbol de utilidad

Notación: **(Impacto en el negocio, Riesgo técnico)** en escala Alto / Medio / Bajo.

```
Utilidad del sistema XALD
│
├── DISPONIBILIDAD
│   └── ESC-01 · Registro sin conexión ......................... (A, A)
│         Propuesta de valor central; su fallo invalida el producto.
│
├── RESILIENCIA
│   └── ESC-02 · Fallo del servicio de categorización .......... (A, M)
│         Perder una transacción rompe la confianza;
│         la mitigación es conocida y de bajo costo.
│
├── SEGURIDAD
│   └── ESC-04 · Protección de datos almacenados ............... (A, M)
│         Obligación legal (RL-01); el riesgo baja al usar
│         mecanismos estándar de la plataforma.
│
├── CONSISTENCIA EVENTUAL
│   └── ESC-05 · Conflictos al sincronizar ..................... (M, A)
│         Riesgo alto por la complejidad; impacto medio
│         porque solo afecta a usuarios multidispositivo.
│
└── MODIFICABILIDAD
    └── ESC-03 · Nueva entidad bancaria ........................ (M, M)
          Afecta la cobertura, no la operación.
```

**Prioridad de atención:** ESC-01 → ESC-02 → ESC-04 → ESC-05 → ESC-03

Los escenarios calificados **(A, A)** y **(A, M)** son los que condicionan las decisiones arquitectónicas registradas en los ADR.

# Risks and Technical Debts

Esta sección consolida los riesgos y deudas técnicas que ya quedaron documentados individualmente en cada ADR (Sección 9) y en la Sección 6, junto con los modos de fallo de las integraciones identificados en la semana 11.

| Riesgo / Deuda técnica | Origen | Mitigación actual |
| --- | --- | --- |
| Complejidad adicional por el motor de resolución de conflictos (LWW), validación redundante y manejo de colas de sincronización | ADR-0001 | Asumida como costo necesario del enfoque Offline-First; se verifica con ESC-05 |
| Vulnerabilidad ante *phishing* (SMS falsos procesados como compras reales) y mensajes que Regex no logra leer | ADR-0002 | Ninguna formalizada todavía — pendiente de definir cómo se detecta un SMS fraudulento |
| Necesidad de mantenimiento continuo si un banco cambia el formato de sus mensajes | ADR-0002 | Mitigado en parte por el registro de reglas modular (ver ESC-03); sigue siendo trabajo manual del equipo |
| Incompatibilidad absoluta con dispositivos iOS | ADR-0003 | Aceptada como restricción permanente del alcance (RT-01), no hay mitigación planeada |
| El sistema no soporta ataques avanzados a gran escala ni alta concurrencia masiva | ADR-0004 | Aceptada porque el alcance actual es solo grupos reducidos de prueba |
| Menor profundidad en el análisis financiero avanzado para el usuario final | ADR-0005 | Aceptada como parte del recorte a MVP; podría revisarse en una futura iteración |
| Riesgo de acoplamiento indeseado entre paquetes por importación directa de clases | ADR-0006 | Mitigado con el modificador `internal` de Kotlin para los componentes que no son parte de la interfaz pública del módulo |
| La reclasificación automática de transacciones "Sin Categorizar" (mencionada en ESC-02) depende de un componente que **todavía no está implementado** en el código | Sección 6 — Runtime View | Sin mitigación todavía; ver plan P-05 |
| La resolución de conflictos entre dispositivos (ESC-05) depende de que los relojes de los dispositivos sean razonablemente confiables | Árbol de utilidad — Sección 10 | Ninguna formalizada; es el escenario de mayor riesgo técnico del proyecto (Riesgo: Alta) |
| La integración con Gemini es un *mock* (`CategorizadorGemini.kt`): no hay llamada real, timeout ni reintentos, y la categorización no usa IA | ADR-0012 | Aplazada de forma explícita; el contrato `ServicioCategorizacion` aísla el punto de reemplazo. Ver MF-04 a MF-06 y plan P-03 |
| La cola de sincronización guarda en memoria y no tiene cliente HTTP, por lo que aún no envía transacciones al backend | `EVIDENCIA-S9.md` (H-3), `aspectos.md` (A-03) | Sin mitigación todavía. Ver MF-01, MF-02 y plan P-01 |
| El backend guarda `ultima_version` solo en memoria: se pierde al reiniciar o cuando Render Free duerme el servicio | ADR-0011 | Aceptada para este corte; persistencia pendiente (A-02). Ver plan P-04 |
| El payload de Android (`PayloadSincronizacionDTO`) no incluye `moneda`, `categoria` ni `origen_datos`, que el backend exige en `TransaccionDTO`: la primera sincronización real respondería 422 | `aspectos.md` (A-03) | Sin mitigación todavía; se alinea el contrato dentro del plan P-01 |
| La llave de Gemini no tiene mecanismo de gestión definido | ADR-0012, ADR-0013 | Integración real aplazada; si la llave no puede protegerse en el cliente se dispara la reevaluación del [ADR-0013](../adr/0013-extraer-o-no-integracion-gemini.md) |

## 11.1 Modos de fallo de las integraciones

Cada integración entre componentes que cruza la red puede fallar de tres maneras: no responde, responde tarde o responde dos veces. La tabla asigna una mitigación a cada modo y declara en qué estado está en el código, para no presentar como implementado lo que solo está diseñado.

| ID | Integración | Modo de fallo | Qué ocurre | Mitigación asignada | Estado en el código | Prueba |
|---|---|---|---|---|---|---|
| **MF-01** | `:syncqueue` → Backend (`POST /api/v1/transacciones`, conector 4) | No responde | El backend está caído, no hay red o Render Free lo tiene dormido; el payload no llega. | La transacción ya se guarda en `:corefinanciero` antes de encolarse (pasos 3 y 4 de `ProcesarNotificacionUseCase`), así que el fallo no la pierde. La cola la conserva y la reenvía al volver la conexión, con reintentos de espera creciente. | **Pendiente (P-01).** La app aún no tiene cliente HTTP, y la cola (`ColaSincronizacion`) y el core guardan hoy en memoria (`mutableListOf`). | Sin prueba |
| **MF-02** | `:syncqueue` → Backend | Responde tarde | El *cold start* de Render Free (duerme tras 15 min sin tráfico) o un backend lento hace esperar al cliente, que podría rendirse y reintentar mientras el primer intento sigue en curso. | Timeout holgado en el cliente, que cubra el cold start, y una petición de calentamiento a `GET /health` antes de sincronizar. Si el cliente se rinde y reintenta, el reenvío lo absorbe la idempotencia de MF-03. La latencia queda visible en los logs (`duracion_ms`). | **Parcial (P-01).** El timeout y el calentamiento están en `backend/scripts/medir_esc05.py` (`timeout=120` y `GET /health`), y `duracion_ms` en el middleware de `backend/app/main.py`. En el cliente de la app: pendiente. | Sin prueba automatizada; `medir_esc05.py` lo ejercita contra Render |
| **MF-03** | `:syncqueue` → Backend | Responde dos veces | El mismo payload llega dos veces: el cliente reintentó tras perder la respuesta, o la cola reenvía un elemento que no se marcó como enviado (`obtenerSiguienteParaSincronizar()` no lo retira). | Idempotencia por `id_transaccion` (UUID), diseñada en [§8.5](08-Cross-cutting%20Concepts.md). Un reenvío idéntico responde 202 sin cambiar el estado y se cuenta en `xald_sync_duplicados_total`; un conflicto real se resuelve con Last-Write-Wins ([ADR-0011](../adr/0011-resolucion-conflictos-lww.md)). | **Implementada en el backend** (`registrar_transaccion` en `main.py`, contador `xald_sync_duplicados_total`). Límites declarados: el estado vive en memoria y se reinicia con cada despliegue o cuando Render duerme el servicio (P-04); la cola aún no retira lo ya confirmado (P-02). | `test_mf03_reenvio_identico_es_idempotente_y_no_cuenta_como_conflicto` y `test_mf03_conflicto_real_sigue_contando_como_conflicto` en `backend/tests/test_lww.py`; 30/30 en `medir_esc05.py` |
| **MF-04** | `:aigemini` → Gemini API (conector 2) | No responde | Gemini está caído o no hay conexión; no hay categoría. | Timeout de 5 s y reintentos con espera creciente (~2 s, ~4 s) según la [Sección 6.2](06-Runtime%20view.md). Si se agotan, la transacción se guarda como «Sin Categorizar» (ESC-02). | **Pendiente declarada (P-03).** `CategorizadorGemini` es un *mock* sin llamada de red ([ADR-0012](../adr/0012-aplazamiento-integracion-gemini.md)). `ProcesarNotificacionUseCase` no captura fallos de `categorizar()` ni asigna «Sin Categorizar». | Sin prueba |
| **MF-05** | `:aigemini` → Gemini API | Responde tarde | Gemini responde después de los 5 s. | El corte de 5 s descarta la respuesta tardía y la cuenta como intento fallido. Si se agotan los intentos se aplica «Sin Categorizar», y la reclasificación posterior retoma la categoría. | **Pendiente declarada (P-03, P-05).** Mismo motivo que MF-04. Además, la reclasificación automática no existe como componente (Sección 6.2). | Sin prueba |
| **MF-06** | `:aigemini` → Gemini API | Responde dos veces | Un reintento tras un timeout puede traer dos respuestas para el mismo comercio: la tardía del primer intento y la del segundo. | La llamada solo consulta (sin efecto secundario externo) y la categorización es idempotente: se conserva la primera respuesta aceptada y se ignora la siguiente. | **Pendiente declarada (P-03).** No hay integración real todavía; la regla debe implementarse junto con el cliente real. | Sin prueba |

## 11.2 Correspondencia entre mitigaciones declaradas e implementadas

*Fecha de contraste con el código: octubre 2026 (semana 11).*

| Modo de fallo | Mitigación declarada | Dónde se declara | Implementación en el código | Estado |
|---|---|---|---|---|
| Gemini **no responde** (timeout) | Corte a 5 s y hasta 3 reintentos con espera creciente (~2 s, ~4 s) | arc42 §6 (ESC-02), §10 | `XALDAPP/aigemini/src/main/java/CategorizadorGemini.kt` es un mock; no hay cliente HTTP, timeout ni reintentos | **Pendiente declarado** ([ADR-0012](../adr/0012-aplazamiento-integracion-gemini.md)) — plan P-03 |
| Gemini **falla tras los reintentos** | Guardar la transacción como "Sin Categorizar"; no se pierde el registro | arc42 §6 (ESC-02), §10 | El mock siempre devuelve una categoría, así que esta rama no existe | **Pendiente declarado** — plan P-03 |
| Reclasificación posterior al volver el servicio | Reclasificar automáticamente las "Sin Categorizar" | arc42 §6, §11 | No existe el componente | **Pendiente declarado** — plan P-05 |
| Gemini **responde tarde** (después del corte de 5 s) | Se trata como fallo: la respuesta tardía se descarta y la transacción se atiende en la reclasificación posterior | Esta tabla | No implementado | **Pendiente declarado** — plan P-03 |
| Backend **no responde** al sincronizar | La transacción permanece en la cola y se reenvía al reconectar, en orden cronológico | arc42 §1 | `ColaSincronizacion` guarda en memoria (`mutableListOf`); no hay cliente HTTP ni reenvío | **Parcial:** encolado implementado; envío y reintento pendientes ([EVIDENCIA-S9.md](../EVIDENCIA-S9.md), H-3) — plan P-01 |
| Backend recibe la **misma transacción dos veces** | Idempotencia por `id_transaccion`; LWW solo para conflictos reales | [ADR-0011](../adr/0011-resolucion-conflictos-lww.md), §8.5 | `backend/app/main.py` (`ultima_version`, `xald_sync_duplicados_total`) con pruebas en `backend/tests/test_lww.py` | **Implementado y probado** en el backend; la cola debe retirar lo confirmado — plan P-02 |
| Backend **pierde su estado** al reiniciarse | Persistencia del estado, junto con la migración a SQLite/Room | ADR-0011, `aspectos.md` (A-02) | El estado vive solo en memoria | **Pendiente declarado** — plan P-04 |

## 11.3 Plan para las mitigaciones pendientes

Cada pendiente de las tablas anteriores tiene un paso concreto, una semana objetivo y la prueba que lo dará por cerrado. Lo que no llegue a la semana objetivo se reporta en `correcciones.md` con su nueva fecha.

| ID | Qué se implementa | Modos de fallo que cierra | Semana objetivo | Responsable | Cómo se verifica |
|---|---|---|---|---|---|
| **P-01** | Cliente HTTP en `:syncqueue` (timeout que cubra el cold start, calentamiento con `GET /health` y reintentos con espera creciente) y alineación del payload de Android con `TransaccionDTO` (`moneda`, `categoria`, `origen_datos`) | MF-01, MF-02 | Semana 13 | *Por asignar* | Prueba unitaria con servidor simulado que no responde y que responde tarde: la transacción sigue en la cola y se reintenta |
| **P-02** | Que `ColaSincronizacion` retire un elemento solo al recibir 2xx (`confirmarEnvio(id)`) | MF-03 (lado cliente) | Semana 13 | *Por asignar* | Prueba unitaria: tras un 202 la cola baja en 1; tras un error, no |
| **P-03** | Cliente real de Gemini en `CategorizadorGemini` con corte de 5 s, 3 reintentos, «Sin Categorizar» al agotarlos y descarte de respuestas tardías; captura del fallo en `ProcesarNotificacionUseCase` | MF-04, MF-05, MF-06 | Semana 14, condicionada a resolver la llave ([ADR-0012](../adr/0012-aplazamiento-integracion-gemini.md), [ADR-0013](../adr/0013-extraer-o-no-integracion-gemini.md)) | *Por asignar* | Prueba con un `ServicioCategorizacion` falso que lanza excepción o tarda más de 5 s: la transacción se guarda como «Sin Categorizar» |
| **P-04** | Persistencia de `ultima_version` en el backend con clave única por `id_transaccion` | MF-03 tras reinicio | Semana 14 | *Por asignar* | Prueba: reiniciar la app de pruebas y reenviar la misma transacción no la cuenta como nueva |
| **P-05** | Componente de reclasificación de transacciones «Sin Categorizar» | MF-05 | Semana 15 | *Por asignar* | Prueba: al volver el servicio, las transacciones «Sin Categorizar» reciben categoría |

# Glossary

| Term | Definition |
| --- | --- |
| **ADR (Architecture Decision Record)** | Documento individual que registra una decisión de arquitectura, su contexto y sus consecuencias; en XALD se guardan como archivos separados en `docs/adr/`. |
| **AES-256** | Algoritmo de cifrado simétrico usado para proteger la base de datos financiera almacenada localmente en el dispositivo. |
| **Android Keystore** | Almacén seguro del sistema operativo Android donde se guardan las llaves criptográficas que protegen el cifrado AES-256 de la base de datos local. |
| **API REST** | Estilo de interfaz de comunicación mediante peticiones HTTP (GET, POST, PUT) que usa XALD para comunicarse con el Backend XALD y con la API de Gemini. |
| **BroadcastReceiver** | Mecanismo nativo de Android que permite a la app "escuchar" eventos del sistema operativo, como la llegada de un SMS, sin intervención directa del usuario. |
| **C4 Model** | Modelo jerárquico de documentación de arquitectura de software en cuatro niveles de abstracción: Contexto (C1), Contenedores (C2), Componentes (C3) y Código (C4). |
| **Carga cognitiva** | Esfuerzo mental que le exige a una persona una tarea; en XALD se usa para explicar por qué el registro manual de gastos genera abandono de la app (ver Requirements Overview, OB-01). |
| **CSV (Comma-Separated Values)** | Formato de archivo de texto plano que el equipo consideró inicialmente como una vía alterna de captura de transacciones (exportado desde el banco). Se evaluó y se decidió **no usarlo** como método de entrada de datos del sistema (ver [ADR-0005](docs/adr/0005-reduccion-de-funcionalidades.md)). |
| **Gastos hormiga** | Expresión coloquial para los gastos pequeños y frecuentes (café, transporte, snacks) que, por su bajo monto, suelen no registrarse manualmente y terminan perdiendo integridad el historial financiero del usuario. |
| **Google Gemini API** | Servicio externo de inteligencia artificial de Google, usado por XALD para inferir la categoría de gasto a partir del nombre del comercio, cuando el Regex Engine no logra reconocerlo. |
| **Habeas Data** | Derecho de las personas a conocer, actualizar y rectificar la información que existe sobre ellas en bases de datos, desarrollado por la Ley 1581 de 2012 en Colombia. |
| **JSON (JavaScript Object Notation)** | Formato de texto usado para el intercambio de datos entre XALD y sus servicios externos (Gemini API, Backend XALD), tanto en las solicitudes como en las respuestas. |
| **Ley 1581 de 2012** | Normativa colombiana de protección de datos personales (Habeas Data), en concordancia con los artículos 15 y 20 de la Constitución Política; establece las restricciones legales que sigue XALD para el manejo de la información financiera del usuario. |
| **LWW (Last-Write-Wins)** | Estrategia de resolución de conflictos de sincronización: cuando hay un cruce entre la información del celular y la del servidor, se conserva la transacción con la marca de tiempo más reciente. |
| **Offline-First** | Patrón arquitectónico en el que la aplicación guarda y muestra la información primero de forma local, sin depender de tener conexión a internet para funcionar. |
| **Open Banking** | Modelo de APIs bancarias oficiales que permitiría a XALD leer transacciones directamente desde el banco; no está disponible para el equipo, por lo que XALD depende de leer los SMS (ver RT-04). |
| **Privacidad desde el Diseño (Privacy by Design)** | Principio de diseño que exige incorporar la protección de datos personales desde el inicio del desarrollo, y no como un añadido posterior; en XALD se aplica limitando lo que se envía a la Gemini API solo al comercio y el monto (ver Solution Strategy y RL-01). |
| **RECEIVE_SMS** | Permiso de Android requerido para que una aplicación pueda leer los mensajes de texto (SMS) entrantes en segundo plano. |
| **Regex (Expresiones Regulares)** | Patrones de texto usados por el Processing & Parser Module para reconocer e interpretar automáticamente el comercio y el monto dentro del texto plano de un SMS bancario. |
| **Sin Categorizar** | Categoría temporal que se le asigna a una transacción cuando el servicio de IA (Gemini API) no responde o falla, para que el registro del gasto nunca se bloquee. |
| **Sync Queue** | Cola de sincronización local donde se almacenan las transacciones pendientes de subir al Backend XALD, cada una con su timestamp y UUID, hasta que el dispositivo recupera la conexión. |
| **Timestamp** | Marca de tiempo asociada a cada transacción, usada para determinar el orden cronológico y resolver conflictos de sincronización (LWW). |
| **TLS 1.3** | Protocolo de seguridad que cifra las comunicaciones HTTPS entre la app XALD y sus servicios externos (Backend XALD y Gemini API). |
| **UUID (Universally Unique Identifier)** | Código único que identifica cada transacción, usado junto con el timestamp para mantener el orden cronológico exacto al sincronizar. |
| **XALD** | Nombre del proyecto y de la aplicación móvil de gestión financiera personal desarrollada por el equipo. |
