## ADR-0005: Alcance Reducido en el Módulo de Analítica y Reportes (Focus en MVP)
* *Estatus:* En revisión y análisis de alcance
* *Proyecto:* XALDAPP — Aplicación de gestión financiera

## Contexto y Problema
La visión inicial del proyecto contemplaba un motor avanzado de analítica, predicción de gastos e historial estadístico complejo. Sin embargo, procesar reportes pesados en un backend universitario desarrollado en poco tiempo y sin infraestructura dedicada representa un riesgo para la estabilidad del sistema. Esta decisión responde a las restricciones organizacionales RO-01 (límite semestral y equipo reducido) y RO-02 (presupuesto $0).

## Opciones Evaluadas
* *Motor avanzado de analítica y predicción de gastos con historial estadístico complejo (visión inicial):* descartado por el riesgo que representa para la estabilidad de un backend universitario sin infraestructura dedicada, construido en poco tiempo.
* *Delegar el procesamiento analítico a un servicio externo especializado:* descartado por ser incompatible con la restricción de presupuesto $0 (RO-02).
* *Alcance reducido centrado en el Producto Mínimo Viable — MVP (Adoptada):* saldos consolidados, gráficos básicos de gastos por categoría y lista de movimientos, sin motor predictivo.

## Decisión Tomada
Reducir la complejidad de la interfaz gráfica y de los reportes en el backend para centrarse en las funcionalidades del Producto Mínimo Viable (MVP): saldos consolidados, gráficos básicos de gastos por categoría y lista de movimientos.

## Consecuencias

### Positivas:
* Entrega funcional dentro del tiempo límite del semestre.
* Menor carga de procesamiento para el servidor remoto.
* Simplificación de las interfaces de usuario.

### Riesgos y Mitigación:
* *Riesgo:* menor profundidad en el análisis financiero avanzado para el usuario final, respecto a la visión inicial del proyecto.
* *Mitigación:* la analítica avanzada queda documentada como trabajo futuro; si el proyecto continúa más allá de este semestre, se abriría un nuevo ADR para retomarla una vez se cuente con más tiempo e infraestructura.

## Trazabilidad
* *Aspectos relacionados:* ninguno todavía — este ADR se encuentra en estado *En revisión* y, según `docs/aspectos.md`, aún no se ha asociado a ningún aspecto de la tabla.
* *Restricciones relacionadas:* RO-01 (Límite semestral y equipo), RO-02 (Costo $0 / Presupuesto).
* *Escenarios de calidad relacionados:* sin escenario medible asociado en este corte, al no tratarse todavía de un aspecto activo.
* *Diagrama C4 relacionado:* no aplica en este corte — el módulo de analítica/reportes vive conceptualmente en el Backend XALD, cuyo detalle de contenedores está pendiente.
* *Nota:* al completarse este ADR con una fecha y quedar fuera de revisión, debe evaluarse si corresponde crear un aspecto nuevo en `docs/aspectos.md` que lo referencie.

**Trazabilidad y Línea Base:**
* **Versión:** Corte 1 (corte-1)
* **Tag:** [corte-1](https://github.com/ISCOUTB/AS_202620_XALD/releases/tag/corte-1)
