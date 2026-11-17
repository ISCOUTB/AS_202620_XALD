# ADR-0015: Persistencia Remota del Backend en PostgreSQL

* *Estatus:* Decidido
* *Fecha:* Noviembre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Tercer corte — conexión app ↔ backend

## Contexto y Problema

Hasta la semana 12 el Backend XALD no guardaba las transacciones que recibía de la cola del dispositivo (`:syncqueue`). Solo conservaba, en un diccionario en memoria (`ultima_version`), la fecha de cada `id_transaccion` para aplicar Last-Write-Wins ([ADR-0011](0011-resolucion-conflictos-lww.md)). Ese estado se perdía en cada despliegue y cada vez que Render dormía el servicio ([ADR-0008](0008-plataforma-de-despliegue.md)), así que:

* la respuesta `202` decía "encolada para persistencia" sin que existiera tal persistencia;
* después de un reinicio, una versión vieja de una transacción podía ganarle a una más reciente (ESC-05 solo se cumplía dentro de un mismo periodo de vida del proceso);
* el backend no tenía de dónde sacar datos para reportes o analítica ([ADR-0005](0005-reduccion-de-funcionalidades.md)).

## Opciones Evaluadas

* **Seguir en memoria:** descartada; es el problema descrito arriba.
* **SQLite en el disco del contenedor:** descartada; los servicios web Free de Render no tienen disco persistente, el archivo se borra en cada despliegue.
* **Firestore / base NoSQL administrada:** descartada; LWW necesita leer y escribir la misma fila de forma atómica, lo que en SQL es un `SELECT ... FOR UPDATE` estándar. Además ataría el backend a un proveedor y a su SDK (RO-01).
* **PostgreSQL administrado, accedido con SQLAlchemy (Adoptada):** Render lo ofrece en el mismo Blueprint (`render.yaml`) sin tarjeta (RO-02, RO-03), y el código queda independiente del proveedor: solo cambia la variable `DATABASE_URL`.

## Decisión Tomada

1. **Motor:** PostgreSQL 16 en producción (`xald-db` en [`render.yaml`](../../render.yaml)), conectado por la red interna de Render (`ipAllowList: []`, no expuesto a internet). En local y en las pruebas, si `DATABASE_URL` está vacía, se usa SQLite.
2. **Acceso:** SQLAlchemy 2.0 + psycopg 3 ([`backend/app/db.py`](../../backend/app/db.py)). La URL `postgres://…` que entregan Render, Neon o Supabase se reescribe a `postgresql+psycopg://…`.
3. **Esquema:** una tabla `transacciones` con `id_transaccion` como clave primaria (el UUID del dispositivo, ADR-0011), los campos del contrato v1 y dos horas del servidor: `recibida_en` y `actualizada_en`. Las tablas se crean al arrancar (`create_all`); no hay migraciones porque es la primera versión del esquema.
4. **Last-Write-Wins sobre la base** ([`backend/app/repositorio.py`](../../backend/app/repositorio.py)): se lee la fila con `SELECT ... FOR UPDATE`, se compara `fecha_transaccion` y se actualiza solo si la entrante es más reciente. Si dos peticiones crean la misma fila a la vez, una choca con la clave primaria y se reintenta una vez como conflicto.
5. **Falla de la base:** el endpoint responde `503`, que el cliente trata como recuperable (`clasificarRespuestaHttp` en `:syncqueue`) y reintenta con espera exponencial ([ADR-0014](0014-sincronizacion-asincrona-con-reintentos.md)). `/health` no consulta la base para que Render no reinicie el servicio por una caída del Postgres. La métrica `xald_bd_errores_total` cuenta estos casos.
6. **Consulta:** `GET /api/v1/transacciones/{id}` (con `X-API-Key`) devuelve la versión que el backend conserva. Sirve como evidencia de que el dato llegó y para verificar LWW desde fuera.

## Consecuencias

### Positivas:

* ESC-05 se cumple aunque el servicio se reinicie: lo verifica `test_lww_sobrevive_a_un_reinicio_del_proceso`.
* La concurrencia está cubierta por `test_concurrencia_muchas_versiones_a_la_vez_gana_la_mas_reciente`: 40 versiones en paralelo, gana la más reciente. Si se quita el `FOR UPDATE`, la prueba falla en la mayoría de las corridas.
* El CI corre las pruebas del backend contra un Postgres real (servicio `postgres:16` en el job `backend-tests`).

### Negativas y riesgos:

* **La base Free de Render vence 30 días después de crearla** y se borra tras 14 días más de gracia. Sirve para la entrega del corte; si los datos deben durar más, se cambia `DATABASE_URL` por una base gratuita sin vencimiento (Neon o Supabase) sin tocar el código.
* La base Free tiene 1 GB y una sola instancia por cuenta. Con el supuesto de 50 usuarios ([arc42 §7.2](../arc42/07-Deployment%20View.md#72-costo-mensual)) el volumen está muy por debajo de ese límite.
* Ediciones posteriores de una transacción (por ejemplo, corregir la categoría en la app) no se propagan todavía: la versión de LWW es `fecha_transaccion`, que no cambia al editar, así que el backend tomaría la edición como un reenvío duplicado. Queda como punto abierto.

## Puntos Abiertos

* Propagar ediciones: agregar al contrato un campo opcional `fecha_modificacion` (cambio compatible, v1) y usarlo como versión de LWW cuando exista.
* Migraciones de esquema (Alembic) cuando la tabla cambie por primera vez.

**Trazabilidad:**
* Implementación: [`backend/app/db.py`](../../backend/app/db.py), [`backend/app/repositorio.py`](../../backend/app/repositorio.py), [`backend/app/main.py`](../../backend/app/main.py)
* Infraestructura: [`render.yaml`](../../render.yaml), [`backend/Dockerfile`](../../backend/Dockerfile), [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
* Pruebas: [`backend/tests/test_persistencia.py`](../../backend/tests/test_persistencia.py), [`backend/tests/test_lww.py`](../../backend/tests/test_lww.py)
* Decisiones relacionadas: [ADR-0008](0008-plataforma-de-despliegue.md), [ADR-0011](0011-resolucion-conflictos-lww.md), [ADR-0014](0014-sincronizacion-asincrona-con-reintentos.md)
* Escenario: ESC-05 ([arc42 §10](../arc42/10-Quality%20Requirements.md))
