# ADR-0010: Observabilidad con Logs JSON y Métricas Prometheus Embebidas

* *Estatus:* Decidido
* *Fecha:* Septiembre 2026
* *Corte:* Segundo corte — despliegue del Backend XALD

## Contexto y Problema

Con el backend en Render ([ADR-0008](0008-plataforma-de-despliegue.md)), el equipo necesita registros consultables y al menos una métrica ligada a un escenario de calidad, bajo RO-02 ($0) y RO-03 (sin tarjeta).

## Opciones Evaluadas

* *Datadog:* descartada; su prueba es temporal y luego requiere plan de pago (RO-02, RO-03).
* *Grafana Cloud (capa gratuita):* descartada; exige una cuenta externa y configurar un agente que envíe los datos desde Render, un costo que no se justifica para un solo servicio (RO-01).
* *Logs JSON en la salida estándar + endpoint `/metrics` embebido (Adoptada):* Render recoge los logs en su panel sin configuración, y el backend expone sus métricas con la librería libre `prometheus-client`.

## Decisión Tomada

* **Logs:** una línea JSON por evento, con el formateador `FormatoJSON` de [`backend/app/main.py`](../../backend/app/main.py); el log de acceso de uvicorn se apaga con `--no-access-log` en [`backend/Dockerfile`](../../backend/Dockerfile).
* **Métricas:** `GET /metrics` expone `xald_sync_conflictos_resueltos_total`, ligada a **ESC-05**, y `xald_transacciones_recibidas_total` como volumen de sincronización.

## Justificación Técnica

* **Capa gratuita verificada:** el panel de logs viene en el plan Free de Render, comprobado al desplegar el 27 de septiembre de 2026; no se crea ninguna cuenta adicional.
* **Ligada a un escenario:** la métrica principal mide directamente la respuesta de ESC-05, no un indicador genérico del sistema.
* **Portable:** JSON y Prometheus pueden recolectarse después con cualquier plataforma, sin tocar el código.

## Consecuencias

* **Positivas:** registros con campos (`nivel`, `evento`, `ruta`, `codigo`, `duracion_ms`) y una métrica de escenario desde el primer despliegue.
* **Riesgo:** los contadores viven en memoria y se reinician con cada despliegue o cuando el servicio se duerme; no hay alertas.
* **Mitigación:** aceptado para este corte; un recolector externo podría consultar `/metrics` periódicamente sin cambios en el backend.

## Trazabilidad

ESC-05 · RT-05 · RO-01 · RO-02 · RO-03 · [ADR-0008](0008-plataforma-de-despliegue.md)
