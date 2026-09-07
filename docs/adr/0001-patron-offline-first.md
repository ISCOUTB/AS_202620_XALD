## ADR-0001: Adopción de Patrón de Arquitectura Offline-First
* *Estatus:* Aprobado
* *Proyecto:* XALDAPP — Aplicación de gestión financiera

## Contexto y Problema
Las transacciones financieras deben registrarse inmediatamente cuando ocurre una compra o transferencia. Sin embargo, los usuarios pueden estar en zonas sin cobertura de red o con conexión intermitente. La arquitectura original debía evaluar si depender directamente de un servidor remoto o priorizar la disponibilidad local. Esta decisión responde directamente a la restricción RT-02 (arquitectura offline-first) y determina cómo se cumple el escenario de calidad ESC-01 (registro de transacción sin conexión).

## Opciones Evaluadas
* *Cliente-Servidor tradicional:* cada transacción requeriría una llamada de red exitosa al backend antes de poder confirmarse al usuario.
* *Sincronización periódica sin escritura local inmediata:* la UI solo se actualizaría después de un ciclo de sincronización programado, no al momento del registro.
* *Offline-First con persistencia local cifrada (Adoptada):* la base de datos local del dispositivo es la fuente de verdad inmediata; el backend se sincroniza de forma asíncrona cuando hay red.

## Decisión Tomada
Implementar una arquitectura Offline-First priorizando la persistencia en una base de datos local cifrada (SQLite/Room) dentro del dispositivo móvil. Los datos se envían de forma asíncrona al backend mediante una cola de sincronización cuando hay red.

## Consecuencias

### Positivas:
* Operatividad del 100% sin internet.
* Respuesta inmediata en la UI ante cada registro.
* Protección de datos financieros dentro del propio dispositivo.

### Riesgos y Mitigación:
* *Riesgo:* incremento en la complejidad de software: se requiere un motor de resolución de conflictos en el backend (p. ej. Last-Write-Wins), validación redundante de transacciones y manejo de colas de sincronización.
* *Mitigación:* la resolución de conflictos queda acotada al mecanismo Last-Write-Wins definido en RT-05, y la cola de sincronización se aísla en su propio módulo (`:syncqueue`) para no acoplar esta complejidad al resto del sistema.

## Trazabilidad
* *Aspectos relacionados:* A-02 — Persistencia local y arquitectura Offline-First; A-03 — Sincronización asíncrona y gestión de conflictos (`docs/aspectos.md`).
* *Restricciones relacionadas:* RT-02 (Arquitectura Offline-First), RT-05 (Consistencia sencilla LWW).
* *Escenarios de calidad relacionados:* ESC-01 (Registro de transacción sin conexión), ESC-05 (Resolución de conflictos al sincronizar).
* *Diagrama C4 relacionado:* C1 — Contexto (`docs/c4/c4.md`).
* *Módulos de código relacionados:* `:corefinanciero` (persistencia local), `:syncqueue` (cola de sincronización).
