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
