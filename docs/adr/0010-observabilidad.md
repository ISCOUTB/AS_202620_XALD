# ADR-0010: Observabilidad con Logs JSON y Métricas Prometheus Embebidas

* *Estatus:* Decidido
* *Fecha:* Septiembre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Segundo corte — despliegue del Backend XALD

## Contexto y Problema

Con el Backend XALD desplegado en Render (ADR-0008), el equipo necesita saber qué está pasando en producción: qué peticiones llegan, con qué resultado, y si el mecanismo de resolución de conflictos (RT-05, ESC-05) está actuando. Esto exige registros consultables y al menos una métrica ligada a un escenario de calidad, bajo RO-02 (presupuesto $0) y RO-03 (sin tarjeta de crédito).

## Opciones Evaluadas

* *Datadog:* descartada. Su prueba gratuita es temporal y el uso continuo requiere un plan de pago, incompatible con RO-02 y RO-03.
* *Grafana Cloud (capa gratuita):* descartada. Ofrece capa gratuita, pero exige una cuenta externa adicional y configurar un agente o un recolector que envíe los datos desde Render. Para un solo servicio, ese costo de configuración no se justifica en el alcance de RO-01.
* *Logs JSON a la salida estándar + endpoint `/metrics` embebido (Adoptada):* el backend escribe cada evento como una línea JSON, que Render recoge en su panel de logs sin configuración adicional, y expone sus propias métricas en formato Prometheus con la librería de código abierto `prometheus-client`.

## Decisión Tomada

* **Logs:** cada evento se emite como un objeto JSON por línea en la salida estándar, mediante el formateador `FormatoJSON` de [`backend/app/main.py`](../../backend/app/main.py). El log de acceso de uvicorn se desactiva (`--no-access-log` en [`backend/Dockerfile`](../../backend/Dockerfile)) para que no se mezclen líneas de texto plano.
* **Métricas:** el endpoint `GET /metrics` expone, en formato Prometheus, la métrica `xald_sync_conflictos_resueltos_total`, ligada a **ESC-05 (Resolución de conflictos al sincronizar)**, y `xald_transacciones_recibidas_total` como volumen de sincronización.

## Justificación Técnica

* **Cumple RO-02 y RO-03:** el panel de logs viene incluido en el plan Free de Render, verificado al desplegar el servicio el 27 de septiembre de 2026; `prometheus-client` es software libre. No se crea ninguna cuenta adicional.
* **Ligada a un escenario de calidad:** la métrica principal no es una métrica genérica de sistema, sino que mide directamente la respuesta de ESC-05.
* **Formato estándar y portable:** los logs JSON y el formato Prometheus pueden ser recolectados más adelante por cualquier plataforma de observabilidad sin modificar el código.

## Consecuencias

### Positivas:

* Registros consultables con campos (`nivel`, `evento`, `ruta`, `codigo`, `duracion_ms`) desde el primer despliegue.
* Una métrica que responde directamente a un escenario de calidad del proyecto.

### Riesgos y Mitigación:

* **Riesgo:** los contadores viven en memoria y se reinician con cada despliegue o cuando el plan Free duerme el servicio.
* **Mitigación:** aceptado para el alcance actual; si hiciera falta historial, se agregaría un recolector Prometheus externo que consulte `/metrics` periódicamente, sin cambios en el backend.
* **Riesgo:** no hay alertas automáticas.
* **Mitigación:** fuera del alcance de este corte; el formato elegido permite agregarlas después sobre los mismos datos.

## Trazabilidad

* Escenario de calidad: ESC-05 · Restricción RT-05 (Last-Write-Wins).
* Restricciones relacionadas: RO-01, RO-02, RO-03.
* Decisión relacionada: [ADR-0008](0008-plataforma-de-despliegue.md) (plataforma de despliegue).
