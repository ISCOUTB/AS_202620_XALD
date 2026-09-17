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


