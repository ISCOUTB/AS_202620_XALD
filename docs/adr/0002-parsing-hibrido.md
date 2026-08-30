# ADR-0002: Estrategia de Parsing Híbrido (Regex + Librerías Open Source) vs. API Bancaria / IA Completa
**Estado:** Aprobado

### Contexto:
Las entidades financieras en el entorno local no ofrecen APIs de *Open Banking* abiertas o gratuitas. Pagar licencias de lectura bancaria o implementar un modelo pesado de Inteligencia Artificial para validar cada SMS escapa del presupuesto ($0) y tiempo de un proyecto universitario.

**Decisión:** Utilizar un receptor de eventos local (`RECEIVE_SMS`) acoplado a un motor de expresiones regulares (Regex) y patrones precreados de código abierto.

### Consecuencias:

**Positivas:** Costo de implementación $0, independencia de contratos con bancos, procesamiento ultra rápido y bajo consumo de batería en el teléfono.

**Negativas / Compensaciones:** Riesgo de pérdida de integridad de datos. Vulnerabilidad ante *phishing* (SMS falsos procesados como compras reales), mensajes no formateados que Regex no logra leer y necesidad de mantenimiento si los bancos cambian el formato de sus mensajes.

## Adenda — Exclusión de importación por CSV

Como parte de esta misma estrategia de captura, el equipo evaluó también soportar la importación de archivos CSV exportados manualmente por el usuario desde su banco, como vía alterna de entrada de datos. Se decidió **descartar esta opción**: agregar un flujo de carga y validación de archivos habría introducido fricción adicional para el usuario y complejidad de mantenimiento, sin aportar al objetivo central de OB-01 (eliminar la fricción de entrada mediante captura pasiva). La captura de XALD queda exclusivamente por SMS/notificación bancaria en segundo plano.
