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
### Usuarios
Los usuarios principales del sistema son individuos que buscan llevar un control de sus finanzas personales de manera eficiente, pero que se enfrentan a limitaciones de tiempo o a una alta carga cognitiva para registrar sus gastos manualmente. Requieren una solución que automatice la captura de datos con la menor fricción posible.

### Alcance
El alcance del sistema se limita exclusivamente a dispositivos móviles con sistema operativo **Android**, excluyendo el desarrollo para iOS debido a restricciones técnicas y de permisos del sistema operativo (según el ADR-0003). Además, para asegurar la viabilidad del proyecto, el alcance funcional ha sido acotado (según el ADR-0005) para enfocarse estrictamente en la captura automatizada de gastos (vía SMS y notificaciones) y su persistencia local, dejando fuera funcionalidades complejas como la conexión directa con APIs bancarias o la gestión avanzada de presupuestos colaborativos.

### Tensiones de Calidad Enfrentadas
Durante el diseño del sistema, se identificaron los siguientes conflictos (trade-offs) entre atributos de calidad:
* **Usabilidad vs. Privacidad y Seguridad:** La necesidad de ofrecer una alta usabilidad mediante la lectura automatizada de SMS y notificaciones (ADR-0002) choca directamente con la privacidad del usuario y las restricciones de seguridad del sistema operativo de Apple, exigiendo la exclusión del mismo y un manejo cuidadoso de los permisos, y el cifrado de la información en los dispositivos Android.
* **Disponibilidad (Offline) vs. Consistencia de Datos:** El requisito de que la aplicación esté siempre disponible y funcione sin conexión a internet (patrón Offline-First, ADR-0001) genera una tensión técnica con la consistencia de los datos, ya que requiere mecanismos complejos de sincronización en segundo plano y resolución de conflictos una vez que se recupera la conectividad.

