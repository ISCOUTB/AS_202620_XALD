# Risks and Technical Debts

Esta sección consolida los riesgos y deudas técnicas que ya quedaron documentados individualmente en cada ADR (Sección 9) y en la Sección 6.

| Riesgo / Deuda técnica | Origen | Mitigación actual |
| --- | --- | --- |
| Complejidad adicional por el motor de resolución de conflictos (LWW), validación redundante y manejo de colas de sincronización | ADR-0001 | Asumida como costo necesario del enfoque Offline-First; se verifica con ESC-05 |
| Vulnerabilidad ante *phishing* (SMS falsos procesados como compras reales) y mensajes que Regex no logra leer | ADR-0002 | Ninguna formalizada todavía — pendiente de definir cómo se detecta un SMS fraudulento |
| Necesidad de mantenimiento continuo si un banco cambia el formato de sus mensajes | ADR-0002 | Mitigado en parte por el registro de reglas modular (ver ESC-03); sigue siendo trabajo manual del equipo |
| Incompatibilidad absoluta con dispositivos iOS | ADR-0003 | Aceptada como restricción permanente del alcance (RT-01), no hay mitigación planeada |
| El sistema no soporta ataques avanzados a gran escala ni alta concurrencia masiva | ADR-0004 | Aceptada porque el alcance actual es solo grupos reducidos de prueba |
| Menor profundidad en el análisis financiero avanzado para el usuario final | ADR-0005 | Aceptada como parte del recorte a MVP; podría revisarse en una futura iteración |
| Riesgo de acoplamiento indeseado entre paquetes por importación directa de clases | ADR-0006 | Mitigado con el modificador `internal` de Kotlin para los componentes que no son parte de la interfaz pública del módulo |
| La reclasificación automática de transacciones "Sin Categorizar" (mencionada en ESC-02) depende de un componente que **todavía no está implementado** en el código | Sección 6 — Runtime View | Sin mitigación todavía; queda pendiente de diseño e implementación |
| La resolución de conflictos entre dispositivos (ESC-05) depende de que los relojes de los dispositivos sean razonablemente confiables | Árbol de utilidad — Sección 10 | Ninguna formalizada; es el escenario de mayor riesgo técnico del proyecto (Riesgo: Alta) |
