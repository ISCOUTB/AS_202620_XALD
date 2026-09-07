## ADR-0004: Modelo de Seguridad Acotado y Cifrado de Datos a Nivel de Persistencia y Tránsito

### 1. Contexto

Dado que el proyecto se encuentra en etapa de prueba de concepto y está dirigido a grupos reducidos de prueba, implementar una infraestructura compleja de ciberseguridad a nivel empresarial (prevención de DDOS, firewalls de aplicación Web, auditorías avanzadas de red) resulta inviable e innecesario para el alcance actual. No obstante, al manejar información financiera sensible, es indispensable evitar lecturas malintencionadas de los datos en el dispositivo o durante la sincronización.

Esta decisión responde directamente al escenario de calidad **ESC-04 (Protección de la información almacenada)**, cuya medida exige que el 0% de los campos financieros sea legible en texto plano sin la llave correspondiente, verificado extrayendo la base de datos con `adb pull` e inspeccionando con `strings`/`sqlite3`.

### 2. Decisión

Se centra la estrategia de seguridad exclusivamente en dos capas: **cifrado local en reposo** (base de datos SQLite/Room cifrada con **AES-256** mediante `Android KeyStore`) y **cifrado en tránsito** (comunicación HTTPS/TLS entre la app y el backend).

### 3. Alternativas evaluadas

- **Infraestructura de ciberseguridad a nivel empresarial** (prevención de DDoS, firewalls de aplicación Web, auditorías avanzadas de red) — **descartada** por ser inviable e innecesaria para el alcance actual: el proyecto es una prueba de concepto dirigida a grupos reducidos, y ese nivel de infraestructura excede el tiempo y los recursos de un equipo estudiantil.

### 4. Consecuencias

**Lo que se gana:** garantiza la confidencialidad de la información financiera ante lecturas no autorizadas dentro del almacenamiento local del teléfono, y protege los datos durante la sincronización remota.

**Deuda técnica aceptada a sabiendas:** el sistema no está preparado para soportar ataques avanzados a gran escala o alta concurrencia masiva, limitando su despliegue a entornos controlados o grupos reducidos de usuarios.

### 5. Estado

**Aprobado**

### 6. Trazabilidad

- **Objetivo de calidad:** 3 (Seguridad básica)
- **Objetivo de negocio:** OB-03
- **Restricciones relacionadas:** RT-03 (Seguridad de Datos Locales) · RL-01 (Habeas Data)
- **Escenario que lo verifica:** ESC-04
