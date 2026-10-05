# ADR-0012: Aplazamiento de la Integración Real con la API de Gemini

* *Estatus:* Decidido
* *Fecha:* Octubre 2026
* *Corte:* Tercer corte — Semana 9

## Contexto y Problema

El módulo `:aigemini` expone el contrato [`ServicioCategorizacion`](../../XALDAPP/aigemini/src/main/java/ServicioCategorizacion.kt), implementado hoy por [`CategorizadorGemini.kt`](../../XALDAPP/aigemini/src/main/java/CategorizadorGemini.kt) como un *mock* que normaliza el texto de entrada en vez de llamar a la API real de Gemini. El equipo necesita decidir si reemplaza el mock por una integración real en este corte o si documenta formalmente el aplazamiento.

## Opciones Evaluadas

* **Integrar la API real de Gemini ahora (Descartada para este corte):** requiere gestión de credenciales (API key de Gemini) y de costos/cuotas de uso, infraestructura que el equipo aún no tiene resuelta ni siquiera para el backend propio (ver [ADR-0010](0010-observabilidad.md), que ya identifica la gestión de llaves como algo a resolver por servicio). Además, una dependencia de red externa real introduciría no-determinismo en las pruebas automatizadas de CI, que hoy corren en verde de forma predecible gracias al mock.
* **Mantener el mock actual sin documentar la decisión (Descartada):** deja la brecha entre diseño (capa ACL pensada para Gemini) e implementación real sin trazabilidad, lo que ya generó un hallazgo en la auditoría de consistencia de esta semana.
* **Mantener el mock actual, pero formalizar la decisión con un ADR (Adoptada):** documenta explícitamente el porqué del aplazamiento y dentro de qué contrato (`ServicioCategorizacion`) deberá conectarse la implementación real en el futuro, sin bloquear el resto del corte.

## Decisión Tomada

Mantener `CategorizadorGemini.kt` como implementación mock de `ServicioCategorizacion` para este corte, documentando el aplazamiento de la integración real con la API de Gemini como deuda técnica explícita en la [Sección 11](../arc42/11-Risks%20and%20Technical%20Debts.md).

## Justificación Técnica

* **Determinismo en pruebas:** el mock garantiza que las pruebas automatizadas del pipeline de CI no dependan de la disponibilidad, latencia o cuota de un servicio externo.
* **Costo y gestión de credenciales no resueltos:** el proyecto no tiene todavía un mecanismo definido para gestionar secretos de servicios externos más allá de la variable `XALD_API_KEY` del propio backend; introducir una segunda credencial externa sin ese mecanismo resuelto añadiría riesgo de exposición de la llave.
* **La Capa Anticorrupción ya está lista para el reemplazo:** como `CategorizadorGemini` ya implementa el contrato `ServicioCategorizacion`, sustituir el mock por la llamada real a la API no debería requerir cambios en ningún otro módulo — es un cambio aislado y de bajo riesgo para una futura iteración.

## Consecuencias

* **Positivas:** el corte actual no se bloquea por una dependencia externa; las pruebas se mantienen estables y rápidas.
* **Riesgo:** mientras el mock esté activo, la categorización no refleja capacidades reales de IA — es una simplificación conocida y visible, no un error oculto.
* **Mitigación:** la brecha queda registrada como deuda técnica en la Sección 11 y el contrato `ServicioCategorizacion` ya aísla el punto exacto de reemplazo para cuando se implemente la integración real.

## Trazabilidad

Objetivo de calidad 5 (Modificabilidad) · [ADR-0007](0007-contratos-por-modulo.md) (contrato por módulo que permite el reemplazo aislado)
