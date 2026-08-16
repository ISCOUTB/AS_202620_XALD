---
date: AGOSTO 2026
title: "  PROYECTO XALD  "
---

# Introduction and Goals {#section-introduction-and-goals}

## Requirements Overview {#_requirements_overview}

## Quality Goals {#_quality_goals}

## Stakeholders {#_stakeholders}

+-------------+---------------------------+---------------------------+
| Role/Name   | Contact                   | Expectations              |
+=============+===========================+===========================+
| *           | *\<Contact-1\>*           | *\<Expectation-1\>*       |
| \<Role-1\>* |                           |                           |
+-------------+---------------------------+---------------------------+
| *           | *\<Contact-2\>*           | *\<Expectation-2\>*       |
| \<Role-2\>* |                           |                           |
+-------------+---------------------------+---------------------------+

# Architecture Constraints

Estas son las condiciones que ya vienen dadas para el proyecto y que no podemos cambiar. No son decisiones de diseño que tomamos nosotros por gusto, sino cosas que limitan desde antes cómo se puede construir XALD.

## Restricciones Técnicas:

**RT-01 (Exclusividad de Sistema Operativo):** La app se va a desarrollar solo para Android. La razón es que leer los SMS automáticamente en segundo plano (usando BroadcastReceiver y el permiso RECEIVE_SMS) es algo que solo se puede hacer de esa forma en Android; otros sistemas móviles no dejan que una app lea mensajes de texto así por sus políticas de seguridad.

**RT-02 (Arquitectura Offline-First):** La información se guarda primero de forma local, en una base de datos SQLite con cifrado (Cipher). Leer y escribir datos no depende de tener internet.

**RT-03 (Seguridad de Datos Locales):** La base de datos local se cifra con AES-256, y las llaves que la protegen se manejan a través del Android Keystore.

**RT-04 (Ingesta por Inferencia / Regex):** XALD depende de leer e interceptar los mensajes de texto (SMS) que mandan los bancos, en lugar de usar una API bancaria oficial (Open Banking). Esto significa que si un banco cambia el formato de sus mensajes, XALD se puede ver afectado y toca ajustar la forma en que los lee.

**RT-05 (Consistencia Sencilla LWW):** Cuando hay un cruce entre lo que pasó en el celular y lo que hay en el servidor, gana la transacción más reciente (esto se conoce como Last-Write-Wins o LWW). Para saber cuál es la más reciente se usan marcas de tiempo y códigos únicos (UUIDs) dentro de la fila de espera (Sync Queue).

## Restricciones Organizacionales y de Proyecto:

**RO-01 (Límite Semestral y Equipo):** El desarrollo está limitado al alcance de un semestre académico y lo hace un equipo de estudiantes. Por eso el primer incremento del proyecto se enfoca solo en el módulo A-01 (recepción y procesamiento de notificaciones).

**RO-02 (Costo $0 / Presupuesto):** El proyecto tiene que usar únicamente servicios en sus capas gratuitas, como Google AI Studio / Gemini API (Free Tier), e infraestructura que no tenga costo.

# Context and Scope

## Business Context 

Aquí se muestra quién o qué interactúa con XALD desde afuera, sin entrar en detalles técnicos de cómo se comunican. Ahora el sistema también incluye la API de Gemini de Google, que ayuda a sugerir en qué categoría va cada gasto.


| Actor / Sistema externo | Descripción | Entradas hacia XALD | Salidas desde XALD |
| --- | --- | --- | --- |
| **Usuario Final** | Propietario de la información financiera | Corrección manual de categorías, registros manuales, consultas de reportes | Visualización de saldo, historial de transacciones, reportes de gasto |
| **Entidades Bancarias / SMS** | Proveedores de mensajería del sistema operativo que emiten alertas de movimientos | Mensaje de texto (SMS) con monto, comercio y fecha | Visualización de saldo, historial de transacciones, reportes de gasto |
| **Backend XALD / Servidor** | Sistema remoto para sincronización y reportes | Confirmación de sincronización, agregaciones de reportes | Cola de transacciones pendientes (Sync Queue) |
| **Google Gemini API** | API de IA externa para la inferencia de categorías de gasto | Categoría sugerida en formato JSON | Cadena de texto limpia del comercio / origen |


La idea central es que el usuario casi no tiene que hacer nada manualmente: el sistema capta la información sola desde los SMS bancarios, usa la IA de Gemini para sugerir la categoría del gasto, y el usuario solo interviene para revisar, corregir o consultar.

## Technical Context

Acá se muestra por dónde entra y sale la información, y cómo viaja de un lado a otro.

| Interfaz Técnica | Canal / Protocolo | Formato de Datos | Cifrado / Seguridad |
| --- | --- | --- | --- |
| Sistema Operativo → App XALD | Android BroadcastReceiver (Eventos del SO) | Texto plano (SmsMessage) | Permiso Android RECEIVE_SMS |
| App XALD → DB Local | Llamada interna SQLite / Room | Objetos Relacionales / Filas | AES-256 vía Android Keystore |
| App XALD → Gemini API | HTTPS / Rest (POST) | JSON (responseMimeType: application/json) | TLS 1.3 + API Key |
| App XALD → Backend XALD | HTTPS / REST (POST/PUT) | Lotes JSON (Sync Queue) | TLS 1.3 + Tokens de Sesión |

Este diagrama de texto es la base para armar después el C4 de contexto formal (con las cajas y flechas gráficas), pero ya deja claro quiénes son los actores y por dónde entra y sale la información.

**INPUT/OUTPUT MAP**

```
[Banco / SMS]
        |
        |  SMS (monto, comercio, fecha)
        v
[Sistema Operativo]
        |
        |  evento local (BroadcastReceiver)
        v
[App XALD] --texto del comercio--> [Google Gemini API]
        |  <---categoría sugerida (JSON)---
        |
        |  guardado local (cifrado AES-256)
        v
[Base de datos local]
        |
        |  cuando hay conexión (sync HTTPS/REST)
        v
[Backend XALD]

[Usuario final] <-- consulta saldo, reportes, alertas -- [App XALD]
```

# Solution Strategy 


Ideas principales y enfoques de solución que definen cómo XALD resuelve el problema. Las herramientas que se mencionan más adelante son solo ejemplos de cómo se podría implementar cada idea, no una decisión cerrada; se pueden cambiar según lo que mejor funcione en el momento.

* **Para cumplir con las metas de calidad:** La app realiza una captura pasiva e ingesta automática leyendo mensajes o notificaciones del banco mediante receptores nativos (`BroadcastReceiver` / `SMS`) y soporte para archivos CSV. La IA actúa como un soporte extra no bloqueante: si falla o no hay red, la transacción se guarda como *“Sin Categorizar”*. Además, las transacciones conocidas se procesan rápido localmente con expresiones regulares (`Regex`) para ahorrar batería y reducir costos, reservando la IA solo para casos ambiguos.

* **En cuanto al patrón de arquitectura:** Se adopta un enfoque *offline-first* donde toda la información se almacena primero en el dispositivo (mediante `SQLite`/`Room`) para garantizar disponibilidad total sin internet. La sincronización con el servidor se realiza de forma asíncrona mediante una cola local (*Sync Queue*) basada en marcas de tiempo (`timestamps`) e identificadores únicos (`UUIDs`), resolviendo conflictos en el backend mediante *Last-Write-Wins* (LWW) sin bloquear la interfaz.

* **Entre las decisiones tecnológicas principales:** Se aprovechan las herramientas nativas del sistema operativo (permisos `RECEIVE_SMS` / `SmsRetriever`) ante la falta de APIs de *Open Banking* locales. Para mantener el presupuesto en **$0** y cumplir el plazo de **16 semanas**, se combina un motor local `Regex` con llamadas HTTP REST a la API de Google Gemini (vía respuestas JSON) y el uso de librerías de código abierto.
  
* * **Para estrategias de seguridad:** Se aplica *Privacidad desde el Diseño*: hacia el servicio de IA solo se envían el nombre del comercio y el monto —omitiendo cédula, saldos o número de cuenta— para cumplir con la **Ley 1581 (Habeas Data)**. Asimismo, la información financiera almacenada en el dispositivo se protege con cifrado (`AES-256` / `Android KeyStore`) para salvaguardar los datos ante robo o acceso no autorizado.


# Building Block View
La vista de bloques de construcción muestra la descomposición del sistema XALD en sus componentes principales, desde una perspectiva de caja blanca (Nivel 1) hasta el detalle interno del bloque de la App Móvil (Nivel 2).

| Bloque | Responsabilidad |
| :--- | :--- |
| App Móvil | Es lo que ve y usa el usuario; ahí pasa todo el proceso de capturar, guardar y mostrar la información. |
| Backend | Hace de puente entre la app y el servicio de IA. |
| Servicio externo de IA | Clasifica el nombre del negocio en una categoría de gasto. |

### 5.1 Nivel 1 — Sistema XALD

### App Móvil Android
Responsabilidad: Captura pasiva de eventos (SMS/notificaciones), parsing local, llamada a la IA para inferencia, persistencia cifrada y presentación al usuario.
Interfaces clave: BroadcastReceiver/OS, cliente HTTPS hacia Gemini, acceso local a SQLite/Room, API local de UI.
Calidad crítica: privacidad (cifrado local), disponibilidad offline, latencia de IU.

### Backend XALD
Responsabilidad: Recepción/almacenamiento central, API de sincronización (Sync Queue), resolución LWW, agregación de reportes y gestión mínima de usuarios/dispositivos.
Interfaces clave: REST API /api/v1/sync, autenticación/token, almacenamiento remoto.
Calidad crítica: consistencia eventual, tolerancia a desconexiones y escalado mínimo.

### Servicio externo de IA (Google Gemini)
Responsabilidad: Recibir texto estructurado y devolver categoría sugerida + metadata (confidence, comercio limpio).
Interfaces clave: HTTPS REST con JSON, manejo de claves/API key.
Calidad crítica: precisión de clasificación, latencia y limitaciones de cuota; además minimización de datos enviados por privacidad.

### Base de datos local (DB cifrada / Sync Queue)
Responsabilidad: Almacenamiento cifrado de transacciones, estado de sincronización y cola de lotes pendientes.
Interfaces clave: API de acceso local (Room), componentes de sincronización.
Calidad crítica: seguridad (AES-256 + Keystore), integridad y rendimiento en dispositivos.

Vista de caja blanca del sistema completo: la **App Móvil Android** captura y gestiona la información financiera del usuario, mientras el **Backend XALD** la procesa y sincroniza.


| 1. App Móvil Android | 2. Backend XALD |
| :--- | :--- |
| • Ingesta de Notificaciones | • Servidor API REST |
| • Parser Local (Regex) | • Procesamiento de Reportes |
| • Base de Datos Cifrada | • Motor de Sincronización LWW |
| • UI / Gestión Financiera | • Base de Datos Remota |

1. **App Móvil Android:** Captura, procesa y presenta la información financiera del usuario de forma local, incluyendo la ingesta de notificaciones bancarias, el parseo con expresiones regulares, el almacenamiento cifrado y la interfaz de gestión financiera.
   
2. **Backend XALD:** Expone la API REST, procesa reportes y ejecuta la sincronización de datos entre dispositivos mediante el motor *Last-Write-Wins* (LWW), manteniendo la base de datos remota como respaldo consolidado.

---

### 5.2 Nivel 2 — App Móvil Android

Descomposición del bloque **“App Móvil Android”** en sus cuatro módulos internos y el flujo de datos entre ellos.

| Módulo | Función / Componente Interno |
| :--- | :--- |
| **1.1 Ingestion Module** | `BroadcastReceiver` / `SMS` |
| ⬇️ | |
| **1.2 Processing & Parser** | `Regex Engine` + `Gemini API Client` |
| ⬇️ | |
| **1.3 Data & Sync Module** | `SQLite`/`Room AES-256` + `Sync Queue` |
| ⬇️ | |
| **1.4 UI & Dashboard** | Presentación / Reportes |

* **1.1 Ingestion Module (`BroadcastReceiver` / `SMS`):** Captura de forma pasiva los mensajes y notificaciones bancarias entrantes en el dispositivo, sin intervención del usuario.
  
* **1.2 Processing & Parser Module (`Regex Engine` + `Gemini API Client`):** Interpreta el texto capturado usando expresiones regulares para los casos conocidos y, cuando el resultado es ambiguo, recurre a la API de Gemini como soporte adicional.
  
* **1.3 Data & Sync Module (`SQLite`/`Room AES-256` + `Sync Queue`):** Almacena las transacciones de forma cifrada en el dispositivo y gestiona la cola de sincronización asíncrona con el backend.
* ### Escenario 1: Captura, Parsing e Inferencia Automática de SMS (Módulo A-01)

Este escenario describe el flujo desde que el celular recibe una notificación bancaria hasta que la transacción queda guardada localmente.

1. *Recepción del Evento:* El Sistema Operativo Android recibe un SMS del banco y activa el BroadcastReceiver del *Ingestion Module*.


2. *Filtrado:* El *Ingestion Module* valida el remitente y extrae el texto plano.


3. *Parsing Local (Regex):* El *Processing & Parser Module* evalúa el texto con expresiones regulares.


* Caso A (Regex exitoso): Si reconoce el comercio y monto, genera el objeto Transaction.


* Caso B (Comercio ambiguo): Envía el texto a la *Gemini API* vía HTTPS con un prompt estructurado en JSON para extraer la categoría y comercio limpio.




4. *Persistencia Local:* El objeto Transaction se envía al *Data & Sync Module*, el cual:
* Cifra los campos con *AES-256*.


* Guarda la fila en la DB local (SQLite).


* Agrega el registro a la cola de sincronización (Sync Queue) con su UUID y timestamp.




5. *Notificación a la UI:* El *UI & Dashboard* detecta el cambio en la base de datos y actualiza el saldo y reporte en pantalla.



---

### Escenario 2: Sincronización Asíncrona Offline-First con el Backend

Este escenario describe cómo se respaldan las transacciones generadas en modo offline cuando el dispositivo recupera la conexión a internet.

1. *Detección de Red:* El *Data & Sync Module* detecta que hay conexión a internet activa.


2. *Lectura de Cola:* Lee los lotes pendientes de la tabla Sync Queue local.


3. *Envío HTTPS:* Realiza una petición POST /api/v1/sync al *Backend XALD* enviando el lote JSON.


4. *Resolución LWW:* El *Backend XALD* procesa los registros. Si hay un conflicto de edición entre el servidor y el cliente, aplica la regla Last-Write-Wins evaluando la marca de tiempo (timestamp).


5. *Confirmación y Limpieza:* El *Backend XALD* responde con un código de éxito. El *Data & Sync Module* elimina los ítems sincronizados de la Sync Queue local.


# Deployment View {#section-deployment-view}

## Infrastructure Level 1 {#_infrastructure_level_1}

***\<Overview Diagram\>***

Motivation

:   *\<explanation in text form\>*

Quality and/or Performance Features

:   *\<explanation in text form\>*

Mapping of Building Blocks to Infrastructure

:   *\<description of the mapping\>*

## Infrastructure Level 2 {#_infrastructure_level_2}

### *\<Infrastructure Element 1\>* {#_infrastructure_element_1}

*\<diagram + explanation\>*

### *\<Infrastructure Element 2\>* {#_infrastructure_element_2}

*\<diagram + explanation\>*

...​

### *\<Infrastructure Element n\>* {#_infrastructure_element_n}

*\<diagram + explanation\>*

# Cross-cutting Concepts {#section-concepts}

## *\<Concept 1\>* {#_concept_1}

*\<explanation\>*

## *\<Concept 2\>* {#_concept_2}

*\<explanation\>*

...​

## *\<Concept n\>* {#_concept_n}

*\<explanation\>*

# Architecture Decisions {#section-design-decisions}

# Quality Requirements {#section-quality-scenarios}

## Quality Requirements Overview {#_quality_requirements_overview}

## Quality Scenarios {#_quality_scenarios}

# Risks and Technical Debts {#section-technical-risks}

# Glossary {#section-glossary}

+----------------------+-----------------------------------------------+
| Term                 | Definition                                    |
+======================+===============================================+
| *\<Term-1\>*         | *\<definition-1\>*                            |
+----------------------+-----------------------------------------------+
| *\<Term-2\>*         | *\<definition-2\>*                            |
+----------------------+-----------------------------------------------+
