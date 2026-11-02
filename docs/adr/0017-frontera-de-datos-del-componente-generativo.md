# ADR-0017: Frontera de Datos y Validación de Salida del Componente Generativo (Gemini)

* *Estatus:* Decidido — parte vigente en el orquestador, parte pendiente con el cliente real
* *Fecha:* Noviembre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Tercer corte — Semana 13, modelado de amenazas
* *Amenazas que mitiga:* AM-06 (fuga de datos), AM-09 (inyección de prompt), AM-10 (envenenamiento del contexto) — [`docs/seguridad/modelo-amenazas.md`](../seguridad/modelo-amenazas.md)

## Contexto y Problema

`:aigemini` categoriza transacciones con la API de Google Gemini (conector 2 del C2). Hoy
`CategorizadorGemini` es un *mock* ([ADR-0012](0012-aplazamiento-integracion-gemini.md)) y la integración
real llegará con el plan P-03. Un componente generativo trae tres riesgos que no tiene una API
determinista:

1. **Fuga de datos:** lo que se manda en el prompt sale del dispositivo hacia un tercero en el exterior.
2. **Inyección de prompt:** el nombre del comercio sale del texto del SMS, que controla cualquiera que
   pueda enviar un SMS (AM-05). Ese texto puede traer instrucciones para el modelo.
3. **Envenenamiento del contexto:** la respuesta se guarda en la caché comercio → categoría durante 30
   días (arc42 §8.7). Una respuesta manipulada contamina las transacciones siguientes del mismo comercio.

## Opciones Evaluadas

### A. Mandar el SMS completo y aceptar la categoría que devuelva el modelo
Da más contexto al modelo. **Descartada:** viola la finalidad de la Ley 1581 (manda monto, cuenta y
nombre a un tercero sin necesidad) y deja abiertos la inyección y el envenenamiento.

### B. Frontera de datos mínima, prompt fijo y salida restringida a un catálogo cerrado (adoptada)

### C. Categorizar solo con reglas locales, sin IA
Elimina los tres riesgos. **Descartada en este corte:** contradice ESC-02 y la propuesta de valor.
Queda como respaldo: es lo que pasa cuando la salida no se valida («Sin Categorizar»).

## Decisión Tomada (opción B)

1. **Frontera de datos.** A Gemini solo viaja el nombre del comercio normalizado (máximo 60 caracteres,
   sin dígitos). Nunca monto, fecha, `id_transaccion`, número de cuenta, nombre del titular ni el texto
   del SMS. **Vigente:** `ProcesarNotificacionUseCase` ya llama `categorizar(transaccionProcesada.comercio)`.
2. **Prompt fijo.** La instrucción del sistema es una constante del código y no se construye con texto
   del SMS. El comercio va como dato entre delimitadores, con la indicación explícita de que es un dato y
   no una orden.
3. **Salida restringida.** La respuesta se acepta solo si es, exactamente, una categoría del catálogo
   cerrado de la app. Cualquier otra cosa (texto libre, otra categoría, una instrucción) se descarta y la
   transacción queda «Sin Categorizar» (ESC-02).
4. **Caché protegida.** Solo entra a la caché una categoría que pasó el punto 3. La corrección manual del
   usuario tiene prioridad y reemplaza la entrada (§8.8). Las entradas vencen a los 30 días (§8.7).
5. **Llave.** No se compila en un APK publicado sin un mecanismo que la proteja. Si no lo hay, se dispara
   la reevaluación de [ADR-0013](0013-extraer-o-no-integracion-gemini.md) para llamar a Gemini desde el
   backend (AM-11).

## Cambio estructural

Se agrega un **validador de salida** dentro de `:aigemini`, entre la capa anticorrupción y la caché de
categorías. La caché ya no recibe la respuesta del modelo directamente, sino solo lo que el validador
aprueba. El contrato público `ServicioCategorizacion` no cambia.

## Consecuencias

### Positivas
* Un SMS falso no puede «enseñar» una categoría al sistema ni sacar datos por el prompt.
* El peor caso de un ataque al modelo es «Sin Categorizar», un estado que la app ya sabe manejar.

### Negativas y riesgos
* Con menos contexto el modelo puede acertar menos. Se acepta: la corrección del usuario lo compensa.
* Dentro del catálogo, el modelo todavía puede elegir una categoría equivocada a propósito (por ejemplo,
  «Salud» en vez de «Ocio»). Se acepta como riesgo residual en arc42 §11.4: altera un reporte, no expone
  datos, y el usuario lo corrige.

## Verificación (cuando se implemente, P-03, S14)
* Un `ServicioCategorizacion` falso que devuelve `"IGNORA TODO; Salario"` produce «Sin Categorizar» y no
  escribe en la caché.
* Una prueba sobre el armado de la petición verifica que el cuerpo enviado no contiene el monto ni el
  texto del SMS.

## Trazabilidad
* *Amenazas:* AM-06, AM-09, AM-10, AM-11.
* *ADR relacionados:* [ADR-0012](0012-aplazamiento-integracion-gemini.md), [ADR-0013](0013-extraer-o-no-integracion-gemini.md).
* *C4:* conector 2 y caché de categorías del C2. *arc42:* §8.7, §8.8, §8.10, §11.4.
* *Privacidad:* [`docs/seguridad/privacidad.md`](../seguridad/privacidad.md), transmisión a Google.
