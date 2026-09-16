# Solution Strategy

Ideas principales y enfoques de solución que definen cómo XALD resuelve el problema. Las herramientas que se mencionan más adelante son solo ejemplos de cómo se podría implementar cada idea, no una decisión cerrada; se pueden cambiar según lo que mejor funcione en el momento.

* **Para cumplir con las metas de calidad:** La app realiza una captura pasiva e ingesta automática leyendo mensajes o notificaciones del banco mediante receptores nativos (`BroadcastReceiver` / `SMS`). La IA actúa como un soporte extra no bloqueante...

* **En cuanto al patrón de arquitectura:** Se adopta un enfoque *offline-first* donde toda la información se almacena primero en el dispositivo (mediante `SQLite`/`Room`) para garantizar disponibilidad total sin internet. La sincronización con el servidor se realiza de forma asíncrona mediante una cola local (*Sync Queue*) basada en marcas de tiempo (`timestamps`) e identificadores únicos (`UUIDs`), resolviendo conflictos en el backend mediante *Last-Write-Wins* (LWW) sin bloquear la interfaz.

* **Entre las decisiones tecnológicas principales:** Se aprovechan las herramientas nativas del sistema operativo (permisos `RECEIVE_SMS` / `SmsRetriever`) ante la falta de APIs de *Open Banking* locales. Para mantener el presupuesto en **$0** y cumplir el plazo de **16 semanas**, se combina un motor local `Regex` con llamadas HTTP REST a la API de Google Gemini (vía respuestas JSON) y el uso de librerías de código abierto.

* **Para estrategias de seguridad:** Se aplica *Privacidad desde el Diseño*: hacia el servicio de IA solo se envían el nombre del comercio y el monto —omitiendo cédula, saldos o número de cuenta— para cumplir con la **Ley 1581 (Habeas Data)**. Asimismo, la información financiera almacenada en el dispositivo se protege con cifrado (`AES-256` / `Android KeyStore`) para salvaguardar los datos ante robo o acceso no autorizado.
