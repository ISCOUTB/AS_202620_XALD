# Context and Scope

## Business Context

Aquí se muestra quién o qué interactúa con XALD desde afuera, sin entrar en detalles técnicos de cómo se comunican. Esta tabla está alineada con el diagrama de Contexto (C1) del modelo C4: solo se listan los actores y sistemas que están fuera de la frontera del sistema XALD.

| Actor / Sistema externo | Descripción | Entradas hacia XALD | Salidas desde XALD |
| --- | --- | --- | --- |
| **Usuario Final** | Propietario de la información financiera | Corrección manual de categorías, registros manuales, consultas de reportes | Visualización de saldo, historial de transacciones, reportes de gasto |
| **SO Android / Entidades Bancarias (SMS)** | Sistema operativo que entrega las notificaciones/SMS emitidos por las entidades bancarias | Mensaje de texto (SMS) con monto, comercio y fecha | *Ninguna — el conector es unidireccional (ver C1): XALD solo escucha, no le responde nada al SO ni al banco* |
| **Google Gemini API** | API de IA externa para la inferencia de categorías de gasto | Categoría sugerida en formato JSON | Cadena de texto limpia del comercio / origen |

**Nota de alcance:** el Backend XALD se representa **dentro de la frontera del sistema XALD** (subgrafo "Sistema XALD · Frontera del proyecto" en el C1), no como actor externo — por eso no tiene fila propia en la tabla de arriba. Con la actualización del diagrama, la conexión entre la Aplicación XALD y el Backend XALD ya aparece explícita dentro del propio C1 como el **conector 4 (Sincronización REST)**, aunque su función interna se sigue detallando a fondo en el nivel de Contenedores (C2). Ver `docs/c4/c4.md`.

La idea central es que el usuario casi no tiene que hacer nada manualmente: el sistema capta la información sola desde los SMS bancarios, usa la IA de Gemini para sugerir la categoría del gasto, y el usuario solo interviene para revisar, corregir o consultar.

## Technical Context

Acá se muestra por dónde entra y sale la información, y cómo viaja de un lado a otro. Cada fila referencia el conector numerado correspondiente del C1 (`docs/c4/c4.md`) cuando aplica. Se mantiene la columna de **Alcance** para dejar explícito cuáles interfaces cruzan la frontera del sistema (Externo), cuáles cruzan red pero permanecen dentro de la frontera (Entre Contenedores) y cuáles son llamadas internas sin red (Interno).

| Interfaz Técnica | Alcance | Canal / Protocolo | Formato de Datos | Cifrado / Seguridad |
| --- | --- | --- | --- | --- |
| SO Android/SMS → Aplicación XALD (conector 1) | Externo | Android BroadcastReceiver (Eventos del SO) | Texto plano (SmsMessage) | Permiso Android RECEIVE_SMS |
| Aplicación XALD ↔ Google Gemini API (conector 2) | Externo | HTTPS / REST (POST) | JSON (responseMimeType: application/json) | TLS 1.3 + API Key |
| Usuario Final ↔ Aplicación XALD (conector 3) | Externo | UI nativa / Reportes en pantalla | Vistas y datos locales | N/A (interacción local en el dispositivo) |
| Aplicación XALD ↔ Backend XALD (conector 4) | Entre Contenedores | HTTPS / REST (POST/PUT) | Lotes JSON (Sync Queue) | TLS 1.3 + Tokens de Sesión |
| Aplicación XALD → DB Local | Interno | Llamada interna SQLite / Room | Objetos Relacionales / Filas | AES-256 vía Android Keystore |

El diagrama de contexto formal se encuentra en `docs/c4/c4.md`. Las interfaces marcadas como **Externo** corresponden a los conectores 1, 2 y 3, que cruzan la frontera del sistema en el C1. La marcada como **Entre Contenedores** corresponde al conector 4: cruza red, pero permanece dentro de la frontera de XALD —es el caso del Backend XALD, representado dentro del recuadro del sistema— y se documenta a fondo en el nivel de Contenedores (C2). La marcada como **Interno** es una llamada en el mismo proceso, sin cruzar red, y por eso no tiene número de conector en el C1.

**INPUT/OUTPUT MAP**

```
[Banco / SMS]
        |
        |  SMS (monto, comercio, fecha)
        v
[Sistema Operativo]
        |
        |  1 · Notificación SMS (BroadcastReceiver)
        v
[Aplicación XALD] --2 · Inferencia / JSON--> [Google Gemini API]
        |          <---categoría sugerida (JSON)---
        |
        |  guardado local (cifrado AES-256)
        v
[Base de datos local]

[Aplicación XALD] <==4 · Sincronización REST==> [Backend XALD]
        (ambos dentro de la frontera del sistema XALD, ver C1)

[Usuario final] <--3 · UI / Reportes--> [Aplicación XALD]
```

# Solution Strategy

Ideas principales y enfoques de solución que definen cómo XALD resuelve el problema. Las herramientas que se mencionan más adelante son solo ejemplos de cómo se podría implementar cada idea, no una decisión cerrada; se pueden cambiar según lo que mejor funcione en el momento.

* **Para cumplir con las metas de calidad:** La app realiza una captura pasiva e ingesta automática leyendo mensajes o notificaciones del banco mediante receptores nativos (`BroadcastReceiver` / `SMS`). La IA actúa como un soporte extra no bloqueante...

* **En cuanto al patrón de arquitectura:** Se adopta un enfoque *offline-first* donde toda la información se almacena primero en el dispositivo (mediante `SQLite`/`Room`) para garantizar disponibilidad total sin internet. La sincronización con el servidor se realiza de forma asíncrona mediante una cola local (*Sync Queue*) basada en marcas de tiempo (`timestamps`) e identificadores únicos (`UUIDs`), resolviendo conflictos en el backend mediante *Last-Write-Wins* (LWW) sin bloquear la interfaz.

* **Entre las decisiones tecnológicas principales:** Se aprovechan las herramientas nativas del sistema operativo (permisos `RECEIVE_SMS` / `SmsRetriever`) ante la falta de APIs de *Open Banking* locales. Para mantener el presupuesto en **$0** y cumplir el plazo de **16 semanas**, se combina un motor local `Regex` con llamadas HTTP REST a la API de Google Gemini (vía respuestas JSON) y el uso de librerías de código abierto.

* **Para estrategias de seguridad:** Se aplica *Privacidad desde el Diseño*: hacia el servicio de IA solo se envían el nombre del comercio y el monto —omitiendo cédula, saldos o número de cuenta— para cumplir con la **Ley 1581 (Habeas Data)**. Asimismo, la información financiera almacenada en el dispositivo se protege con cifrado (`AES-256` / `Android KeyStore`) para salvaguardar los datos ante robo o acceso no autorizado.
