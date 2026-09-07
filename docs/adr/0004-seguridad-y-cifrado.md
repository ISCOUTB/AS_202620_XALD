## ADR-0004: Modelo de Seguridad Acotado y Cifrado de Datos a Nivel de Persistencia y Tránsito
* *Estatus:* Aprobado
* *Proyecto:* XALDAPP — Aplicación de gestión financiera

## Contexto y Problema
Dado que el proyecto se encuentra en etapa de prueba de concepto y está dirigido a grupos reducidos de prueba, implementar una infraestructura compleja de ciberseguridad a nivel empresarial (prevención de DDOS, firewalls de aplicación Web, auditorías avanzadas de red) resulta inviable e innecesario para el alcance actual. No obstante, al manejar información financiera sensible, es indispensable evitar lecturas malintencionadas de los datos en el dispositivo o durante la sincronización. Esta decisión responde a la restricción RT-03 (seguridad de datos locales) y a la restricción legal RL-01 (Ley 1581 de Habeas Data), y determina cómo se cumple el escenario de calidad ESC-04 (protección de la información almacenada).

## Opciones Evaluadas
* *Infraestructura de seguridad empresarial completa (DDOS, WAF, auditorías avanzadas):* descartada por ser inviable e innecesaria para el alcance de un proyecto en etapa de prueba de concepto con grupos reducidos de usuarios.
* *Sin cifrado, confiando solo en los permisos del sistema operativo:* descartada porque no protege los datos financieros ante un dispositivo perdido, robado o con acceso root/jailbreak.
* *Cifrado acotado a dos capas — reposo y tránsito (Adoptada):* cifrado local con AES-256 vía Android KeyStore, más HTTPS/TLS en la comunicación con el backend.

## Decisión Tomada
Centrar la estrategia de seguridad exclusivamente en dos capas: **cifrado local en reposo** (base de datos SQLite/Room cifrada con **AES-256** mediante `Android KeyStore`) y **cifrado en tránsito** (comunicación HTTPS/TLS entre la app y el backend).

## Consecuencias

### Positivas:
* Garantiza la confidencialidad de la información financiera ante lecturas no autorizadas dentro del almacenamiento local del teléfono.
* Protege los datos durante la sincronización remota.

### Riesgos y Mitigación:
* *Riesgo:* el sistema no está preparado para soportar ataques avanzados a gran escala o alta concurrencia masiva, limitando su despliegue a entornos controlados o grupos reducidos de usuarios.
* *Mitigación:* el alcance del proyecto (RO-01, un semestre académico) ya acota el despliegue a un grupo de prueba controlado; una eventual salida a producción real requeriría revisar este ADR y ampliar la estrategia de seguridad antes de escalar la base de usuarios.

## Trazabilidad
* *Aspectos relacionados:* A-04 — Seguridad y protección de datos en reposo y tránsito (`docs/aspectos.md`).
* *Restricciones relacionadas:* RT-03 (Seguridad de datos locales), RL-01 (Ley 1581 de Habeas Data).
* *Escenarios de calidad relacionados:* ESC-04 (Protección de la información almacenada).
* *Diagrama C4 relacionado:* C1 — Contexto (`docs/c4/c4.md`).
* *Módulos de código relacionados:* `:corefinanciero` (cifrado de la base de datos local), comunicación `:syncqueue` ↔ Backend XALD (cifrado en tránsito).
