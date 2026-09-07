## ADR-0005: Alcance Reducido en el Módulo de Analítica y Reportes (Focus en MVP)

### 1. Contexto

La visión inicial del proyecto contemplaba un motor avanzado de analítica, predicción de gastos e historial estadístico complejo. Sin embargo, procesar reportes pesados en un backend universitario desarrollado en poco tiempo y sin infraestructura dedicada representa un riesgo para la estabilidad del sistema.

*Nota de trazabilidad: a diferencia de otros ADR del proyecto, esta decisión no está atada a ninguno de los 5 escenarios de calidad medibles (ESC-01 a ESC-05) definidos en la Sección 10 — es una decisión de alcance por restricción organizativa (RO-01: equipo y tiempo limitados), no una decisión motivada por un atributo de calidad medible.*

### 2. Decisión

Se reduce la complejidad de la interfaz gráfica y de los reportes en el backend, centrando el alcance en las funcionalidades del Producto Mínimo Viable (MVP): saldos consolidados, gráficos básicos de gastos por categoría y lista de movimientos.

### 3. Alternativas evaluadas

- **Motor avanzado de analítica, predicción de gastos e historial estadístico complejo** (la visión original del proyecto) — **descartada** por el riesgo que representa procesar reportes pesados en un backend universitario, construido en poco tiempo y sin infraestructura dedicada, para la estabilidad del sistema.

> *Sugerencia (a validar con el equipo): si van a formalizar también la exclusión de CSV como entrada de datos dentro de este mismo ADR — como quedó enlazado en el Glosario — agréguenla aquí como una segunda alternativa descartada, con su propia razón (fricción y complejidad de validación de archivos). No la incluí como decisión ya tomada porque el contexto original de este ADR no la menciona.*

### 4. Consecuencias

**Lo que se gana:** entrega funcional dentro del tiempo límite, menor carga de procesamiento para el servidor remoto y simplificación de las interfaces de usuario.

**Deuda técnica aceptada a sabiendas:** menor profundidad en el análisis financiero avanzado para el usuario final.

### 5. Estado

**Propuesto** *(ajustar si el equipo ya lo dio por Aceptado — el estado original "En revisión y análisis de alcance" no es uno de los tres valores que acepta la nueva plantilla: Propuesto, Aceptado, o Superado por otro ADR)*

### 6. Trazabilidad

- **Restricción relacionada:** RO-01 (Límite Semestral y Equipo)
- **Objetivo de calidad / Escenario:** no aplica — ver nota en Contexto
