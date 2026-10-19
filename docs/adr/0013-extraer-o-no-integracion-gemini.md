# ADR-0013: Extraer o no extraer la integración con Gemini como servicio independiente

* *Estatus:* Decidido
* *Fecha:* Octubre 2026
* *Corte:* Tercer corte — Semana 11

## Contexto y Problema

Hoy la integración con Gemini vive dentro del monolito modular ([ADR-0006](0006-seleccion-de-estilo-arquitectonico.md)) como el módulo `:aigemini`, que expone el contrato `ServicioCategorizacion` ([ADR-0007](0007-contratos-por-modulo.md)) y está implementado por un *mock* ([ADR-0012](0012-aplazamiento-integracion-gemini.md)). Cuando se implemente la integración real hay que decidir dónde se ejecuta la llamada a la API de Gemini:

* directamente desde la app Android, dentro del módulo `:aigemini` (no extraer), o
* desde un servicio independiente, por ejemplo un endpoint de categorización en el Backend XALD que haga de intermediario (extraer).

La decisión afecta directamente a los modos de fallo MF-04, MF-05 y MF-06 de la [Sección 11](../arc42/11-Risks%20and%20Technical%20Debts.md): extraer añade un segundo salto de red, con sus propios casos de "no responde", "responde tarde" y "responde dos veces".

## Criterios de Decisión

| # | Criterio | Fuente |
|---|---|---|
| C1 | Resiliencia y Offline-First: registrar la transacción aunque la IA falle o no haya red | ESC-01, ESC-02 |
| C2 | Protección de la llave de Gemini: no debe quedar escrita en el código de la app | ADR-0004, ADR-0012, `EVIDENCIA-S9.md` |
| C3 | Disponibilidad del componente intermedio: el backend hoy guarda su estado en memoria y Render (plan Free) duerme el servicio | ADR-0011 |
| C4 | Costo y operación: capa gratuita de Google AI Studio, un servicio más que operar | arc42 §7 |
| C5 | Latencia: corte de 5 s con máximo 3 reintentos | ESC-02 |
| C6 | Modificabilidad: poder reemplazar la implementación sin tocar otros módulos | Objetivo de calidad 5, ADR-0007 |
| C7 | Privacidad: solo se envían el comercio y el monto | RL-01 |

## Opciones Evaluadas

### Opción A: No extraer — llamada directa desde `:aigemini` en la app (Adoptada)

La llamada a Gemini la hace el módulo `:aigemini` dentro de la app, detrás del contrato `ServicioCategorizacion`. No se crea ningún servicio nuevo.

* **A favor:** un salto de red menos (C5); sin dependencia del backend (C1, C3); los reintentos de ESC-02 quedan donde ya están diseñados; coherente con ADR-0006 y ADR-0007 (C6); sin servicio nuevo que operar (C4).
* **En contra:** la llave de Gemini tendría que protegerse en el dispositivo (C2) y la cuota se comparte entre dispositivos.

### Opción B: Extraer — endpoint de categorización en el Backend XALD

La app llama a un endpoint de categorización del backend y este llama a Gemini.

* **A favor:** la llave viviría en las variables de entorno del servidor (C2) y se podría controlar la cuota y cachear resultados.
* **En contra:** el backend aún no tiene contrato de categorización ni persistencia, y el arranque en frío de Render puede superar el corte de 5 s de ESC-02 (C3, C5); añade un segundo salto de red (app → backend → Gemini) con su propio manejo de reintentos (C1); suma un servicio más que operar (C4).

### Opción C: Diferir sin decidir (Descartada)

Deja sin criterio explícito el punto donde se conectará la integración real, que es justo lo que el ADR-0012 quiso evitar.

### Comparación de A y B contra los criterios

| Criterio | A: No extraer | B: Extraer |
|---|---|---|
| C1 Resiliencia y Offline-First | Favorable: sin dependencia del backend; los reintentos de ESC-02 quedan en `:aigemini` | Desfavorable: dos saltos de red (app → backend → Gemini) y doble manejo de reintentos |
| C2 Protección de la llave | Desfavorable: la llave tendría que protegerse en el dispositivo | Favorable: la llave vive en las variables de entorno del servidor |
| C3 Disponibilidad del intermedio | Favorable: no hay intermediario | Desfavorable: el backend guarda estado en memoria y Render Free lo duerme (ADR-0011) |
| C4 Costo y operación | Favorable: no suma un servicio que operar | Desfavorable: un servicio más que operar y mantener |
| C5 Latencia (corte de 5 s) | Favorable: un salto de red menos | Desfavorable: el arranque en frío de Render puede superar el corte de 5 s |
| C6 Modificabilidad | Favorable: el contrato `ServicioCategorizacion` ya aísla el reemplazo | Neutral: el contrato permite el cambio en ambos casos |
| C7 Privacidad | Neutral: solo se envían comercio y monto | Neutral: igual, pero pasa por un servidor propio adicional |

**Resultado:** A favorece 5 de los 7 criterios (C1, C3, C4, C5 y C6) y B solo favorece C2. C2 es el riesgo real de A y queda cubierto por los disparadores de reevaluación de la sección siguiente.

## Decisión Tomada

**No extraer** la integración con Gemini en este corte. `:aigemini` sigue siendo un módulo del monolito modular detrás del contrato `ServicioCategorizacion`. La integración real sigue aplazada según el ADR-0012 hasta que exista un mecanismo definido para proteger la llave (plan P-03 de la [Sección 11](../arc42/11-Risks%20and%20Technical%20Debts.md)).

No es una decisión por falta de tiempo: con el backend actual (estado en memoria y arranques en frío), extraer empeoraría la resiliencia y la latencia que exige ESC-02.

### Disparadores para reevaluar (pasar a "extraer")

1. La llave de Gemini no puede protegerse de forma aceptable en el cliente.
2. El backend tiene persistencia y disponibilidad sin arranques en frío que comprometan el corte de 5 s.
3. La cuota de la llave se agota al crecer más allá de los grupos reducidos de prueba.
4. Aparece un segundo consumidor de la categorización además de la app.

## Consecuencias

* **Positivas:** se mantiene el funcionamiento sin conexión y el determinismo de las pruebas; no se agrega un punto de fallo ni un servicio nuevo.
* **Riesgo:** mientras no se resuelva la gestión de la llave, la categorización real sigue sin implementarse (ver [Sección 11](../arc42/11-Risks%20and%20Technical%20Debts.md)).
* **Mitigación:** el contrato `ServicioCategorizacion` permite mover la implementación al backend, si se cumple algún disparador, sin cambios en otros módulos. Si se extrae, hay que revisar el argumento de "saga no aplica" de la [Sección 8.5](../arc42/08-Cross-cutting%20Concepts.md).

## Trazabilidad

[ADR-0006](0006-seleccion-de-estilo-arquitectonico.md) · [ADR-0007](0007-contratos-por-modulo.md) · [ADR-0011](0011-resolucion-conflictos-lww.md) · [ADR-0012](0012-aplazamiento-integracion-gemini.md) · ESC-02 · RL-01 · MF-04 a MF-06 · `aspectos.md` (A-01, A-05)
