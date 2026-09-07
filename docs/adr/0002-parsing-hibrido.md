# ADR-0002: Estrategia de Parsing Híbrido (Regex + Librerías Open Source) vs. API Bancaria / IA Completa
* *Estatus:* Aprobado
* *Fecha:* *(completar con la fecha real de aprobación del equipo)*
* *Proyecto:* XALDAPP — Aplicación de gestión financiera

## Contexto y Problema
Las entidades financieras en el entorno local no ofrecen APIs de *Open Banking* abiertas o gratuitas. Pagar licencias de lectura bancaria o implementar un modelo pesado de Inteligencia Artificial para validar cada SMS escapa del presupuesto ($0) y tiempo de un proyecto universitario. Esta decisión responde a la restricción RT-04 (ingesta por inferencia/Regex, ante la falta de Open Banking) y determina cómo se cumple el escenario de calidad ESC-03 (incorporación de una nueva entidad bancaria).

## Opciones Evaluadas
* *Integración con API bancaria oficial (Open Banking):* descartada porque las entidades del entorno local no ofrecen este tipo de acceso de forma abierta o gratuita.
* *Modelo de Inteligencia Artificial completo para validar cada SMS:* descartado por su costo de cómputo, la latencia que introduciría y el consumo de batería, además de exceder el presupuesto $0 del proyecto.
* *Motor híbrido de expresiones regulares (Regex) + patrones open source (Adoptada):* procesamiento local, rápido y gratuito, apoyado en un receptor de eventos (`RECEIVE_SMS`).

## Decisión Tomada
Utilizar un receptor de eventos local (`RECEIVE_SMS`) acoplado a un motor de expresiones regulares (Regex) y patrones precreados de código abierto.

## Consecuencias

### Positivas:
* Costo de implementación $0.
* Independencia de contratos con bancos.
* Procesamiento ultra rápido y bajo consumo de batería en el teléfono.

### Riesgos y Mitigación:
* *Riesgo:* posible pérdida de integridad de datos, vulnerabilidad ante *phishing* (SMS falsos procesados como compras reales), mensajes no formateados que Regex no logra leer, y necesidad de mantenimiento si los bancos cambian el formato de sus mensajes.
* *Mitigación:* el motor de patrones queda aislado en el módulo `:parser`, de modo que agregar o corregir un patrón de un banco no obliga a tocar el resto del sistema; los casos que Regex no reconoce quedan disponibles para un tratamiento posterior en `:aigemini` en vez de perderse.

## Adenda — Exclusión de importación por CSV
Como parte de esta misma estrategia de captura, el equipo evaluó también soportar la importación de archivos CSV exportados manualmente por el usuario desde su banco, como vía alterna de entrada de datos. Se decidió **descartar esta opción**: agregar un flujo de carga y validación de archivos habría introducido fricción adicional para el usuario y complejidad de mantenimiento, sin aportar al objetivo central de OB-01 (eliminar la fricción de entrada mediante captura pasiva). La captura de XALD queda exclusivamente por SMS/notificación bancaria en segundo plano.

## Trazabilidad
* *Aspectos relacionados:* A-01 — Recepción y procesamiento de información vía SMS/Notificaciones bancarias (`docs/aspectos.md`).
* *Restricciones relacionadas:* RT-04 (Ingesta por inferencia/Regex).
* *Escenarios de calidad relacionados:* ESC-03 (Incorporación de una nueva entidad bancaria).
* *Diagrama C4 relacionado:* C1 — Contexto (`docs/c4/c4.md`).
* *Módulos de código relacionados:* `:parser` (`Parser.kt`, motor Regex), `:aigemini` (`Geminiproc.kt`, pre-limpieza de casos ambiguos).
* *ADR relacionado:* ADR-0003 (comparte el aspecto A-01, ya que ambos sustentan la captura pasiva por SMS).
