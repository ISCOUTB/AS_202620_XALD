# IA.md — Registro de Co-Pilotaje y Asistencia de IA

## 1. Análisis de Impacto y Alineación con Lenguaje Ubicuo (DDD)

- **Prompting & Rastreo:** uso de la IA para auditar la Sección 8 de Arc42, identificando inconsistencias entre la terminología del dominio y el código legado.
- **Refactorización Asistida:** asistencia en el rastreo de dependencias cruzadas y enlaces rotos antes de aplicar la refactorización de nombres vía IDE.

## 2. Diagnóstico Arquitectónico y Corrección de Violaciones (V-01 a V-07)

- **Detección de Acoplamiento:** utilización de IA para analizar el árbol de módulos e identificar importaciones ilegales entre subsistemas de dominio (`:parser`, `:aigemini`, `:corefinanciero`, `:syncqueue`).
- **Encapsulamiento y Visibilidad:** generación de propuestas para la separación física de interfaces públicas e implementaciones encapsuladas mediante el modificador `internal` en Kotlin.
- **Capa Anticorrupción (ACL):** diseño asistido del patrón ACL en `:aigemini` (`CategorizadorGemini` + `ServicioCategorizacion`) para aislar el SDK externo de Google Gemini.

## 3. Automatización de Pruebas de Gobernanza

- **Generación de `ValidacionModulosTest`:** co-creación de la suite de pruebas unitarias encargada de escanear el código mediante reflexión/análisis estático, garantizando de forma automatizada que ningún módulo de dominio importe a otro ni exponga clases internas.
- **Verificación de Corte Vertical:** asistencia en la construcción del test de integración `CorteVerticalTest` para validar el flujo completo de datos a través del orquestador `:app`.

## 4. Generación de Artefactos de Auditoría y Documentación

- **Elaboración de la Ownership Matrix:** asistencia en la estructuración de la Matriz de Propiedad de Datos, detallando dueños de entidad, mecanismos de cifrado (AES-256), tipos de acceso y trazabilidad de commits (`a6113e8` y `e21b1e6`).
- **Actualización de ADR-0006:** soporte en la redacción y alineación del ADR-0006 para plasmar la topología física real de los 5 módulos de Gradle y la regla de orquestación exclusiva desde `:app`.

## 5. Saneamiento de Dependencias en Gradle (`build.gradle.kts`)

- **Aislamiento de Módulos:** eliminación asistida de dependencias inter-módulo en los scripts de construcción de `:corefinanciero`, `:parser`, `:aigemini` y `:syncqueue`.
- **Configuración del Orquestador:** consolidación del módulo `:app` como el único punto central de integración con dependencias explícitas hacia todos los subsistemas.
