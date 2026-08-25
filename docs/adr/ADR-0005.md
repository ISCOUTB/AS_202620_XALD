## ADR-0005: Alcance Reducido en el Módulo de Analítica y Reportes (Focus en MVP)
**Estado:** En revisión y analísis de alcance.

### Contexto:
La visión inicial del proyecto contemplaba un motor avanzado de analítica, predicción de gastos e historial estadístico complejo. Sin embargo, procesar reportes pesados en un backend universitario desarrollado en poco tiempo y sin infraestructura dedicada representa un riesgo para la estabilidad del sistema.

**Decisión:** Reducir la complejidad de la interfaz gráfica y de los reportes en el backend para centrarse en las funcionalidades del Producto Mínimo Viable (MVP): saldos consolidados, gráficos básicos de gastos por categoría y lista de movimientos.

### Consecuencias:

**Positivas:** Entrega funcional dentro del tiempo límite, menor carga de procesamiento para el servidor remoto y simplificación de las interfaces de usuario.

**Negativas / Compensaciones:** Menor profundidad en el análisis financiero avanzado para el usuario final.
