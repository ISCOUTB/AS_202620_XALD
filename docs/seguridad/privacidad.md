# Privacidad: datos personales que trata XALD y con qué base

* *Semana:* 13 (tercer corte)
* *Restricción legal:* RL-01, Ley 1581 de 2012 (protección de datos personales) y su decreto reglamentario
  1377 de 2013, hoy compilado en el Decreto 1074 de 2015.
* *Responsables:* Axel Ruiz, Luis (Lucho)

> Este apartado es el análisis del equipo para un proyecto académico, no una opinión jurídica. Antes de
> una salida a producción lo debe revisar alguien con formación legal.

## 1. ¿XALD trata datos personales? Sí

El modelo de datos no tiene campos de correo, nombre, teléfono, cédula ni dirección. Se comprobó con la
búsqueda de la ficha sobre el código, sin `docs/`:

```
git grep -nIE '(email|correo|telefono|teléfono|cedula|cédula|documento|direccion|dirección|nombre_completo)' HEAD -- . ':!docs'
→ 1 coincidencia, en correcciones.md (la palabra «documento» en prosa). Ningún campo del modelo de datos.
```

Que no haya esos campos **no significa que no haya datos personales**. Según el artículo 3 de la Ley 1581,
dato personal es cualquier información vinculada o que pueda asociarse a una persona natural determinada
o determinable. Cada transacción de XALD describe un gasto de **una persona concreta**: la dueña del
teléfono que recibió el SMS. Monto, comercio y fecha juntos dicen dónde estuvo y en qué gastó. Por eso el
equipo trata toda transacción como dato personal y la información financiera con reserva, aunque el
artículo 5 no la liste como dato sensible.

## 2. Inventario contrastado con el modelo de datos

| Dato | Dónde está en el código | Elemento del C2 | ¿Personal? | Finalidad | Retención |
|---|---|---|---|---|---|
| Texto completo del SMS bancario | `TransaccionProcesadaDTO.textoProcesado` (`:parser`) | `:app` → `:parser` (conector 1) | **Sí.** Puede incluir los últimos dígitos de la cuenta o tarjeta y a veces el nombre del titular | Extraer monto y comercio | Solo en memoria durante el parseo. No se guarda ni sale del teléfono |
| Monto, comercio, fecha, `id` | `TransaccionEntidad` (`:corefinanciero`) | BD local de transacciones | **Sí** | Mostrar saldo y reportes al titular | Mientras la app esté instalada. Hoy en memoria; con A-02, cifrada con SQLCipher (ADR-0004) |
| `id_transaccion`, `monto`, `moneda`, `comercio`, `categoria`, `fecha_transaccion`, `origen_datos` | `PayloadSincronizacionDTO`, `TransaccionDTO` (`backend/app/dtos.py`) | Conector 4 → API de sincronización | **Sí** | Sincronizar y resolver conflictos entre dispositivos (ESC-05) | En el backend solo se conserva `id_transaccion → fecha_transaccion` (`ultima_version`), en memoria; se borra en cada reinicio |
| Logs del backend | `registrar(...)` en `backend/app/main.py` | API de sincronización | `id_transaccion` y `X-Correlation-Id`: seudónimos | Diagnóstico y trazabilidad (ADR-0010) | Lo que retenga Render Free. **No se registran** monto, comercio ni categoría |
| Nombre del comercio | `categorizar(comercio)` (`:aigemini`) | Conector 2 → Google Gemini API | Por sí solo, débil: no identifica al titular sin el resto | Categorizar el gasto (ESC-02) | La que aplique Google según sus términos. Hoy no se envía nada: es un *mock* (ADR-0012) |
| Dirección IP del cliente | No la registra la app (uvicorn corre con `--no-access-log`) | Plataforma Render | Sí | — | Fuera del control del equipo: la plataforma puede registrarla |

**Lo que el sistema no recolecta, a propósito:** correo, nombre, teléfono del usuario, contactos,
ubicación ni cuenta de Google. [ADR-0015](../adr/0015-autenticacion-del-cliente-movil.md) descartó el inicio
de sesión con Google para no agregar un correo que el sistema no necesita.

## 3. Base para tratarlos

| Aspecto | Cómo lo cumple XALD | Estado |
|---|---|---|
| **Autorización del titular** (art. 9: previa, expresa e informada) | El titular es el mismo usuario que instala la app y concede el permiso `RECEIVE_SMS`. El permiso de Android **no basta como autorización informada**: hace falta una pantalla de consentimiento en el primer arranque que diga qué se lee, para qué, qué sale del teléfono y hacia quién. | **Pendiente (P-08)**, obligatoria antes de cualquier APK con captura de SMS. Hoy no se captura ningún SMS real |
| **Finalidad** (art. 4) | Registrar y categorizar los gastos del propio titular. No se usan para publicidad, perfilamiento crediticio ni venta a terceros. | Declarada |
| **Libertad y minimización** | Solo se extrae monto, comercio y fecha. El texto del SMS no se persiste ni se envía. A Gemini solo va el comercio ([ADR-0017](../adr/0017-frontera-de-datos-del-componente-generativo.md)). | Hecha en el código actual |
| **Seguridad** (art. 4) | HTTPS en tránsito, SQLCipher en reposo (planeado), sin copias de seguridad en la nube (AM-02), llave rotable (AM-01). | Ver [modelo de amenazas](modelo-amenazas.md) |
| **Circulación restringida y encargados** | Render (alojamiento del backend, EE. UU.) y Google (Gemini) actúan como **encargados**: tratan datos por cuenta del responsable. Es una *transmisión* internacional, que el Decreto 1377 de 2013 (arts. 24 y 25) permite con un contrato de transmisión o con las cláusulas del encargado que lo cubran. | **Aceptado para el grupo de prueba.** Antes de producción hay que revisar los términos de Render y de la API de Gemini como contrato de transmisión (arc42 §11.4) |
| **Derechos del titular** (art. 8: conocer, actualizar, rectificar, suprimir) | En el teléfono el usuario ve y corrige sus transacciones, y suprime todo al desinstalar. En el backend solo queda `id → fecha`, en memoria, y se borra en cada reinicio. | Hecha para el estado actual. Cuando exista persistencia (P-04) hará falta un endpoint de supresión por dispositivo, que depende del token de ADR-0015 |

## 4. Pendientes de privacidad

| ID | Qué | Semana objetivo | Dueño |
|---|---|---|---|
| P-08 | Pantalla de consentimiento informado en el primer arranque, antes de pedir `RECEIVE_SMS` | Con el `BroadcastReceiver` (S14) | Luis |
| P-09 | Endpoint de supresión de los datos de un dispositivo en el backend | Con P-04 y el token de ADR-0015 (S15) | Axel |
