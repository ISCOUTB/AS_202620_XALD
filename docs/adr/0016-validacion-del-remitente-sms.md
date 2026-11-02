# ADR-0016: Validación del Remitente del SMS en el Registro de Reglas de `:parser`

* *Estatus:* Decidido — implementación pendiente
* *Fecha:* Noviembre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Tercer corte — Semana 13, modelado de amenazas
* *Amenaza que mitiga:* AM-05 ([`docs/seguridad/modelo-amenazas.md`](../seguridad/modelo-amenazas.md))

## Contexto y Problema

[ADR-0002](0002-parsing-hibrido.md) dejó registrado un riesgo sin mitigación: un SMS falso con un texto
de compra se procesa como compra real (arc42 §11, «Vulnerabilidad ante *phishing*»). Cualquiera puede
mandar un SMS con `Compra por $500000 en ...` y alterar el saldo y los reportes del usuario.

Hoy `ServicioParser.parsear(mensajeCrudo: String)` solo recibe el texto. El remitente
(`SmsMessage.originatingAddress`) se pierde antes de llegar al parser. El `BroadcastReceiver` todavía no
existe: el manifiesto no pide `RECEIVE_SMS`. La decisión se toma ahora para que ese receptor nazca con la
validación y no se agregue después.

## Opciones Evaluadas

### A. Filtrar en `:app`, antes de llamar al parser
`:app` tendría su propia lista de remitentes. **Descartada:** duplica conocimiento bancario fuera de
`:parser` y rompe ESC-03 (agregar un banco tocaría dos módulos).

### B. Cada regla bancaria de `:parser` declara sus remitentes autorizados (adoptada)
El registro de reglas por banco que plantea ESC-03 (hoy `ParseoSms` tiene una sola regla genérica) gana un campo `remitentes`. El parser solo aplica la
regla de un banco si el remitente está en su lista; si ningún banco reconoce al remitente, devuelve
`Rechazado(motivo = REMITENTE_DESCONOCIDO)` y la transacción no se registra.

### C. Pedir confirmación al usuario por cada SMS
**Descartada:** elimina la captura automática, que es la razón de ser de la app (OB-01).

## Decisión Tomada

Opción B. El contrato cambia a `parsear(remitente: String, mensajeCrudo: String)` y el resultado pasa a
distinguir aceptado de rechazado. Agregar un banco sigue siendo agregar una regla, ahora con sus códigos
cortos de remitente.

## Cambio estructural

Cambia el contrato público de `:parser` ([ADR-0007](0007-contratos-por-modulo.md)) y el paso 1 de
`ProcesarNotificacionUseCase`, que debe pasar el remitente que recibe el `BroadcastReceiver`.

## Consecuencias

### Positivas
* Un SMS de un número cualquiera ya no crea transacciones.
* El conocimiento por banco sigue en un solo módulo.

### Negativas y riesgos
* **Riesgo residual aceptado:** la suplantación del remitente en la red del operador (*SMS spoofing*).
  La app no puede detectarla; se acepta en arc42 §11.4.
* Si un banco cambia su código corto, sus SMS se rechazan hasta actualizar la regla. Es el mismo costo
  de mantenimiento que ya acepta ADR-0002.

## Verificación (cuando se implemente)
* Prueba unitaria en `:parser`: el mismo texto de compra se acepta con el remitente del banco y se
  rechaza con un número cualquiera.
* Semana objetivo: con el `BroadcastReceiver` (S14). Dueño: Luis.

## Trazabilidad
* *Amenaza:* AM-05. *Escenario:* ESC-03. *Restricción:* RT-04.
* *ADR relacionados:* [ADR-0002](0002-parsing-hibrido.md), [ADR-0007](0007-contratos-por-modulo.md).
* *C4:* conector 1 del C2 (Android / SMS → `:app`) y módulo `:parser`.
