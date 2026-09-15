# Architecture Constraints

Estas son las condiciones que ya vienen dadas para el proyecto y que no podemos cambiar. No son decisiones de diseño que tomamos nosotros por gusto, sino cosas que limitan desde antes cómo se puede construir XALD.

## Restricciones Técnicas

- **RT-01 (Exclusividad de Sistema Operativo):** La app se va a desarrollar solo para Android. La razón es que leer los SMS automáticamente en segundo plano (usando BroadcastReceiver y el permiso RECEIVE_SMS) es algo que solo se puede hacer de esa forma en Android; otros sistemas móviles no dejan que una app lea mensajes de texto así por sus políticas de seguridad. *(Origen: limitación técnica de la plataforma)*

- **RT-02 (Arquitectura Offline-First):** La información se guarda primero de forma local, en una base de datos SQLite con cifrado (Cipher). Leer y escribir datos no depende de tener internet. *(Origen: decisión de arquitectura del equipo, a partir del problema de conectividad intermitente)*

- **RT-03 (Seguridad de Datos Locales):** La base de datos local se cifra con AES-256, y las llaves que la protegen se manejan a través del Android Keystore. *(Origen: buena práctica de seguridad para el manejo de datos financieros sensibles)*

- **RT-04 (Ingesta por Inferencia / Regex):** XALD depende de leer e interceptar los mensajes de texto (SMS) que mandan los bancos, en lugar de usar una API bancaria oficial (Open Banking). Esto significa que si un banco cambia el formato de sus mensajes, XALD se puede ver afectado y toca ajustar la forma en que los lee. *(Origen: ausencia de APIs bancarias abiertas/Open Banking disponibles para el equipo)*

- **RT-05 (Consistencia Sencilla LWW):** Cuando hay un cruce entre lo que pasó en el celular y lo que hay en el servidor, gana la transacción más reciente (esto se conoce como Last-Write-Wins o LWW). Para saber cuál es la más reciente se usan marcas de tiempo y códigos únicos (UUIDs) dentro de la fila de espera (Sync Queue). *(Origen: decisión de arquitectura del equipo para resolver conflictos de sincronización)*

## Restricciones Organizacionales y de Proyecto

- **RO-01 (Límite Semestral y Equipo):** El desarrollo está limitado al alcance de un semestre académico y lo hace un equipo de estudiantes. Por eso el primer incremento del proyecto se enfoca solo en el módulo A-01 (recepción y procesamiento de notificaciones). *(Origen: limitación de tiempo y tamaño del equipo, propia del curso académico)*

- **RO-02 (Costo $0 / Presupuesto):** El proyecto tiene que usar únicamente servicios en sus capas gratuitas, como Google AI Studio / Gemini API (Free Tier), e infraestructura que no tenga costo. *(Origen: limitación de presupuesto del equipo estudiantil)*

## Restricciones Legales

- **RL-01 (Protección de Datos Personales — Habeas Data):** Como XALD maneja información financiera personal (saldos, movimientos bancarios, categorías de gasto), el desarrollo tiene que respetar la Ley 1581 de 2012, que desarrolla el derecho de las personas a conocer, actualizar y rectificar la información que hay sobre ellas en bases de datos (lo que se conoce como Habeas Data), en concordancia con los artículos 15 y 20 de la Constitución Política de Colombia. En la práctica, esto significa que el usuario debe poder ver, corregir y eliminar sus datos personales dentro de la app, y que XALD no puede usar esa información para fines distintos a los que el usuario autorizó — esto también explica por qué en el flujo con Gemini API solo se envía el nombre del comercio y el monto, sin datos como cédula o número de cuenta. *(Origen: normativa nacional — Ley 1581 de 2012, Artículo 1º)*
