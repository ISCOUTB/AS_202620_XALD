# Evidencia de ejecución — Taller de mensajes y consistencia (semana 12)

> **Pendiente al hacer el commit:** pegar aquí el hash del commit entregado y la URL del run de
> GitHub Actions que lo ejecutó (pestaña *Actions* → workflow *Android CI* → run del commit).
>
> * Commit: `<hash>`
> * Run de CI: `<https://github.com/ISCOUTB/AS_202620_XALD/actions/runs/...>`

## 1. Recorrido extremo a extremo con fallo recuperable inyectado

Backend local (`XALD_API_KEY=demo uvicorn app.main:app --port 8000 --no-access-log`) y cliente Kotlin real
(`ExtremoAExtremoManualTest`, `TransporteHttpSincronizacion`). El transporte deja pasar el primer envío
al backend pero "pierde" la respuesta, como un timeout. Además, la misma transacción se encola dos veces.
Ejecutado el 25 de octubre de 2026.

**Cliente (`:syncqueue`)**

```json
{"timestamp":"2026-10-25T19:56:07.706714925Z","nivel":"INFO","origen":"cliente-syncqueue","evento":"mensaje_encolado","id_correlacion":"345f6ac8-b24f-4259-b029-9ac4e33efa39","id_transaccion":"7ec94557-6462-43a0-8eae-639a0caee78b","version_esquema":1}
{"timestamp":"2026-10-25T19:56:07.709863127Z","nivel":"INFO","origen":"cliente-syncqueue","evento":"encolado_duplicado_ignorado","id_correlacion":"345f6ac8-b24f-4259-b029-9ac4e33efa39","id_transaccion":"7ec94557-6462-43a0-8eae-639a0caee78b","clave":"7ec94557-6462-43a0-8eae-639a0caee78b@1792958167697"}
{"timestamp":"2026-10-25T19:56:07.710013250Z","nivel":"INFO","origen":"cliente-syncqueue","evento":"envio_intento","id_correlacion":"345f6ac8-b24f-4259-b029-9ac4e33efa39","id_transaccion":"7ec94557-6462-43a0-8eae-639a0caee78b","intento":1}
{"timestamp":"2026-10-25T19:56:07.763220022Z","nivel":"INFO","origen":"cliente-syncqueue","evento":"envio_reprogramado","id_correlacion":"345f6ac8-b24f-4259-b029-9ac4e33efa39","id_transaccion":"7ec94557-6462-43a0-8eae-639a0caee78b","intento":1,"motivo":"timeout (inyectado: respuesta perdida)","espera_ms":200}
{"timestamp":"2026-10-25T19:56:07.914477247Z","nivel":"INFO","origen":"cliente-syncqueue","evento":"envio_intento","id_correlacion":"345f6ac8-b24f-4259-b029-9ac4e33efa39","id_transaccion":"7ec94557-6462-43a0-8eae-639a0caee78b","intento":2}
{"timestamp":"2026-10-25T19:56:07.919059447Z","nivel":"INFO","origen":"cliente-syncqueue","evento":"envio_confirmado","id_correlacion":"345f6ac8-b24f-4259-b029-9ac4e33efa39","id_transaccion":"7ec94557-6462-43a0-8eae-639a0caee78b","intento":2}
```

**Backend (`backend/app/main.py`)**

```json
{"timestamp": "2026-10-25T19:56:07.757707+00:00", "nivel": "INFO", "evento": "transaccion_recibida", "id_correlacion": "345f6ac8-b24f-4259-b029-9ac4e33efa39", "id_transaccion": "7ec94557-6462-43a0-8eae-639a0caee78b", "origen_datos": "SMS_REGEX"}
{"timestamp": "2026-10-25T19:56:07.758108+00:00", "nivel": "INFO", "evento": "peticion_http", "id_correlacion": "345f6ac8-b24f-4259-b029-9ac4e33efa39", "metodo": "POST", "ruta": "/api/v1/transacciones", "codigo": 202, "duracion_ms": 2.87}
{"timestamp": "2026-10-25T19:56:07.918062+00:00", "nivel": "INFO", "evento": "reenvio_duplicado_ignorado", "id_correlacion": "345f6ac8-b24f-4259-b029-9ac4e33efa39", "id_transaccion": "7ec94557-6462-43a0-8eae-639a0caee78b"}
{"timestamp": "2026-10-25T19:56:07.918291+00:00", "nivel": "INFO", "evento": "peticion_http", "id_correlacion": "345f6ac8-b24f-4259-b029-9ac4e33efa39", "metodo": "POST", "ruta": "/api/v1/transacciones", "codigo": 202, "duracion_ms": 1.43}
```

**Lectura, siguiendo `id_correlacion = 345f6ac8…`:**

1. 19:56:07.706 — el cliente encola. 3 ms después, el segundo encolado de la misma transacción se descarta (deduplicación en el productor).
2. 19:56:07.710 — intento 1. A las .757 el backend lo recibe y responde 202.
3. 19:56:07.763 — el cliente no ve la respuesta (fallo inyectado), lo trata como recuperable y lo reprograma con espera de 200 ms.
4. 19:56:07.914 — intento 2, **mismo** `id_correlacion`. A las .918 el backend lo identifica como reenvío y no cambia nada (deduplicación en el consumidor).
5. 19:56:07.919 — el cliente recibe 202 y retira el mensaje. Resultado: una transacción en el backend, dos llegadas, cero pérdidas.

## 2. Pruebas automatizadas

### Cliente — `XALDAPP/syncqueue` (JUnit 4)

Compilado con Kotlin 2.0.21 y ejecutado con JUnit 4.13.2 fuera de Gradle (ver límites). En CI corre con
`./gradlew testDebugUnitTest`.

```
JUnit version 4.13.2
.................
Time: 0.102

OK (17 tests)
```

Pruebas incluidas: `ColaSincronizacionMensajesTest` (10), `PoliticaReintentosTest` (3),
`ContratoMensajeTest` (3), `ExampleUnitTest` (1). `ExtremoAExtremoManualTest` se salta si no hay `XALD_URL_E2E`.

**Las pruebas detectan el defecto que cubren.** Se rompió cada mecanismo a propósito y se volvió a correr:

| Mutación en el código | Pruebas que fallan |
|---|---|
| Quitar la deduplicación de `encolarTransaccion` | `duplicado_mismaTransaccionEncoladaDosVeces_seEnviaUnaSolaVez`, `duplicado_queLlegaDespuesDeConfirmar_noSeReenvia`, `reintentarFallido_loDevuelveALaColaYSeConfirma` |
| Espera constante en vez de exponencial | `falloRecuperable_reintentaConEsperaCreciente_yTerminaConfirmado` |
| No mandar a fallidos al agotar intentos | `falloRecuperable_queAgotaLosIntentos_vaALaColaDeFallidos` |

### Backend — `backend/tests` (pytest)

```
tests/test_mensajes.py::test_duplicado_mismo_mensaje_reenviado_no_cambia_estado_y_queda_en_el_log PASSED
tests/test_mensajes.py::test_fallo_recuperable_respuesta_perdida_el_reintento_da_el_mismo_resultado PASSED
tests/test_mensajes.py::test_contrato_ejemplo_v1_se_acepta PASSED
tests/test_mensajes.py::test_compatible_campo_opcional_nuevo_se_ignora PASSED
tests/test_mensajes.py::test_compatible_sin_cabecera_de_version_se_asume_v1 PASSED
tests/test_mensajes.py::test_incompatible_quitar_campo_obligatorio_se_rechaza_con_422 PASSED
tests/test_mensajes.py::test_incompatible_version_mayor_desconocida_se_rechaza_con_400 PASSED
tests/test_mensajes.py::test_trazabilidad_id_de_correlacion_en_respuesta_y_en_cada_linea_de_log PASSED
tests/test_mensajes.py::test_trazabilidad_sin_cabecera_se_genera_un_id PASSED
tests/test_mensajes.py::test_formato_de_linea_de_log_es_json_con_id_de_correlacion PASSED
```

Suite completa del backend: **21 passed** (10 nuevas + 11 existentes), ejecutada con Python 3.12.3 y las
mismas versiones fijadas en `.github/workflows/ci.yml`: fastapi 0.115.0, pydantic 2.9.0, httpx 0.27.0,
prometheus-client 0.20.0, pytest 8.3.0.

### Contrato

* `redocly lint docs/api/openapi.yaml` → válido (2 advertencias previas en `/health` y `/metrics` por no declarar 4xx).
* Los ejemplos validados contra `transaccion-registrada.schema.json`: `v1` y `v1-con-campo-nuevo` válidos;
  `incompatible-sin-moneda` inválido (`'moneda' is a required property`).

## 3. Matriz de cumplimiento

| Criterio | Evidencia | Estado |
|---|---|---|
| Operación real del proyecto | Sincronización `:syncqueue` → `POST /api/v1/transacciones` ([README §0](README.md#0-operación-elegida)) | Cumple |
| Contrato del mensaje con criterio de compatibilidad | `docs/mensajes/transaccion-registrada.schema.json`, [README §1–2](README.md#2-criterio-de-compatibilidad) | Cumple |
| Garantía de entrega y consecuencias | Al menos una vez, 5 consecuencias en [README §3](README.md#3-garantía-de-entrega-al-menos-una-vez) | Cumple |
| Idempotencia/deduplicación con su clave | `id_transaccion@fecha` — `ColaSincronizacion.kt` (`encolarTransaccion`), `backend/app/main.py` (`registrar_transaccion`) | Cumple |
| Reintentos con espera creciente | `PoliticaReintentos.kt` (30 s × 2^(n−1), tope 15 min, 6 intentos) | Cumple |
| Cola de fallidos | `ColaSincronizacion.fallidos`, `obtenerFallidos()`, `reintentarFallido()` | Cumple |
| Prueba de un duplicado | `ColaSincronizacionMensajesTest.duplicado_mismaTransaccionEncoladaDosVeces_seEnviaUnaSolaVez`; `test_mensajes.py::test_duplicado_…` | Cumple (falta URL del run) |
| Prueba de un fallo recuperable | `ColaSincronizacionMensajesTest.falloRecuperable_reintentaConEsperaCreciente_yTerminaConfirmado`; `test_mensajes.py::test_fallo_recuperable_…` | Cumple (falta URL del run) |
| Log de productor a consumidor | `id_correlacion` en cliente y backend, §1 de este documento | Cumple |
| ADR contra la alternativa síncrona | [ADR-0014](../adr/0014-sincronizacion-asincrona-con-reintentos.md) | Cumple |
| Nota de límites | [`limites-del-experimento.md`](limites-del-experimento.md) | Cumple |
