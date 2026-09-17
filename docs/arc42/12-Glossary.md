# Glossary
| Term | Definition |
| --- | --- |
| **ADR (Architecture Decision Record)** | Documento individual que registra una decisión de arquitectura, su contexto y sus consecuencias; en XALD se guardan como archivos separados en `docs/adr/`. |
| **AES-256** | Algoritmo de cifrado simétrico usado para proteger la base de datos financiera almacenada localmente en el dispositivo. |
| **Android Keystore** | Almacén seguro del sistema operativo Android donde se guardan las llaves criptográficas que protegen el cifrado AES-256 de la base de datos local. |
| **API REST** | Estilo de interfaz de comunicación mediante peticiones HTTP (GET, POST, PUT) que usa XALD para comunicarse con el Backend XALD y con la API de Gemini. |
| **BroadcastReceiver** | Mecanismo nativo de Android que permite a la app "escuchar" eventos del sistema operativo, como la llegada de un SMS, sin intervención directa del usuario. |
| **C4 Model** | Modelo jerárquico de documentación de arquitectura de software en cuatro niveles de abstracción: Contexto (C1), Contenedores (C2), Componentes (C3) y Código (C4). |
| **Carga cognitiva** | Esfuerzo mental que le exige a una persona una tarea; en XALD se usa para explicar por qué el registro manual de gastos genera abandono de la app (ver Requirements Overview, OB-01). |
| **CSV (Comma-Separated Values)** | Formato de archivo de texto plano que el equipo consideró inicialmente como una vía alterna de captura de transacciones (exportado desde el banco). Se evaluó y se decidió **no usarlo** como método de entrada de datos del sistema (ver [ADR-0005](docs/adr/0005-reduccion-de-funcionalidades.md)). |
| **Gastos hormiga** | Expresión coloquial para los gastos pequeños y frecuentes (café, transporte, snacks) que, por su bajo monto, suelen no registrarse manualmente y terminan perdiendo integridad el historial financiero del usuario. |
| **Google Gemini API** | Servicio externo de inteligencia artificial de Google, usado por XALD para inferir la categoría de gasto a partir del nombre del comercio, cuando el Regex Engine no logra reconocerlo. |
| **Habeas Data** | Derecho de las personas a conocer, actualizar y rectificar la información que existe sobre ellas en bases de datos, desarrollado por la Ley 1581 de 2012 en Colombia. |
| **JSON (JavaScript Object Notation)** | Formato de texto usado para el intercambio de datos entre XALD y sus servicios externos (Gemini API, Backend XALD), tanto en las solicitudes como en las respuestas. |
| **Ley 1581 de 2012** | Normativa colombiana de protección de datos personales (Habeas Data), en concordancia con los artículos 15 y 20 de la Constitución Política; establece las restricciones legales que sigue XALD para el manejo de la información financiera del usuario. |
| **LWW (Last-Write-Wins)** | Estrategia de resolución de conflictos de sincronización: cuando hay un cruce entre la información del celular y la del servidor, se conserva la transacción con la marca de tiempo más reciente. |
| **Offline-First** | Patrón arquitectónico en el que la aplicación guarda y muestra la información primero de forma local, sin depender de tener conexión a internet para funcionar. |
| **Open Banking** | Modelo de APIs bancarias oficiales que permitiría a XALD leer transacciones directamente desde el banco; no está disponible para el equipo, por lo que XALD depende de leer los SMS (ver RT-04). |
| **Privacidad desde el Diseño (Privacy by Design)** | Principio de diseño que exige incorporar la protección de datos personales desde el inicio del desarrollo, y no como un añadido posterior; en XALD se aplica limitando lo que se envía a la Gemini API solo al comercio y el monto (ver Solution Strategy y RL-01). |
| **RECEIVE_SMS** | Permiso de Android requerido para que una aplicación pueda leer los mensajes de texto (SMS) entrantes en segundo plano. |
| **Regex (Expresiones Regulares)** | Patrones de texto usados por el Processing & Parser Module para reconocer e interpretar automáticamente el comercio y el monto dentro del texto plano de un SMS bancario. |
| **Sin Categorizar** | Categoría temporal que se le asigna a una transacción cuando el servicio de IA (Gemini API) no responde o falla, para que el registro del gasto nunca se bloquee. |
| **Sync Queue** | Cola de sincronización local donde se almacenan las transacciones pendientes de subir al Backend XALD, cada una con su timestamp y UUID, hasta que el dispositivo recupera la conexión. |
| **Timestamp** | Marca de tiempo asociada a cada transacción, usada para determinar el orden cronológico y resolver conflictos de sincronización (LWW). |
| **TLS 1.3** | Protocolo de seguridad que cifra las comunicaciones HTTPS entre la app XALD y sus servicios externos (Backend XALD y Gemini API). |
| **UUID (Universally Unique Identifier)** | Código único que identifica cada transacción, usado junto con el timestamp para mantener el orden cronológico exacto al sincronizar. |
| **XALD** | Nombre del proyecto y de la aplicación móvil de gestión financiera personal desarrollada por el equipo. |
