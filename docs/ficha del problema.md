# Ficha del Problema: Proyecto XALD

## Planteamiento del Problema
En el desarrollo de sistemas de información para la gestión financiera personal, se identifican dos limitaciones estructurales en las soluciones actuales:

1. **Fricción en la entrada de datos (Carga Cognitiva):** La dependencia del ingreso manual de transacciones genera un alto índice de abandono por parte del usuario, derivando en la omisión recurrente de registros de bajo monto (*gastos hormiga*) y en la pérdida de integridad del historial financiero.
2. **Dependencia estricta de conectividad (Acoplamiento a Red):** La mayoría de las aplicaciones cliente-servidor condicionan el registro y la lectura de datos a la disponibilidad de red. Esto genera fallos de operatividad y degradación de la experiencia en entornos con latencia alta o nula conectividad.

---

## Propuesta Tecnológica (XALD)
El proyecto **XALD** propone el diseño e implementación de una arquitectura móvil orientada a mitigar la fricción de captura y garantizar disponibilidad continua mediante dos componentes centrales:

* **Captura Asistida por Eventos:** Ingesta y parseo automático de información financiera a partir de notificaciones del sistema operativo (SMS/Bancos), reduciendo la intervención manual del usuario.
* **Patrón Arquitectónico Offline-First:** Persistencia local inmediata de transacciones con mecanismos de sincronización diferida (consistencia eventual), asegurando la operatividad del sistema independientemente del estado de la red.

---

