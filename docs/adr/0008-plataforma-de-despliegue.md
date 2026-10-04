# ADR-0008: Selección de Plataforma de Despliegue para el Backend XALD

* *Estatus:* Decidido
* *Fecha:* Septiembre 2026
* *Corte:* Segundo corte — despliegue del Backend XALD

## Contexto y Problema

El Backend XALD (FastAPI + Docker) debía quedar accesible desde fuera de la red de la universidad sin depender de un servidor propio del equipo. RO-02 exige costo $0 y RO-03 exige que la capa gratuita no pida tarjeta de crédito.

## Opciones Evaluadas

* *Heroku:* descartada; eliminó su capa gratuita en noviembre de 2022.
* *AWS (EC2 / Lightsail, Free Tier):* descartada; exige registrar una tarjeta al crear la cuenta (RO-03).
* *Fly.io:* descartada; su asignación gratuita requiere verificación con tarjeta (RO-03).
* *Railway:* descartada; ofrece un crédito de prueba limitado, no una capa gratuita permanente, y pide tarjeta al agotarlo.
* *Render, Web Service Docker, plan Free (Adoptada):* soporta contenedores Docker, `healthCheckPath` y variables de entorno desde su panel, sin tarjeta.

## Decisión Tomada

Desplegar el Backend XALD en **Render**, como *Web Service* Docker en el plan **Free** (región Oregon), definido como código en [`render.yaml`](../../render.yaml) y construido desde [`backend/Dockerfile`](../../backend/Dockerfile). El job `deploy-backend` de [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) dispara el despliegue con el secreto `RENDER_DEPLOY_HOOK` en cada push a `master`, después de que pasen las pruebas.

## Justificación Técnica

* **Capa gratuita verificada:** el equipo creó la cuenta y desplegó el servicio el 27 de septiembre de 2026 sin que Render solicitara tarjeta (RO-02, RO-03).
* **Infraestructura como código:** `render.yaml` versionado declara el servicio, el health check y las variables; solo el valor de `XALD_API_KEY` se carga fuera del repositorio.
* **Portabilidad:** el mismo `Dockerfile` sirve en local y en producción; no hay formato propietario en el código.

## Consecuencias

### Positivas:

* Sistema accesible en `https://xald-backend.onrender.com` sin costo ni medio de pago.
* Despliegue reproducible desde el mismo `render.yaml` en cualquier cuenta.

### Riesgos y Mitigación:

* **Riesgo:** el plan Free duerme el servicio tras 15 minutos sin tráfico; la primera petición tarda más (*cold start*).
* **Mitigación:** aceptado por el alcance académico (RO-01); en producción se evaluaría el plan Starter (ver [§7.2](../arc42/07-Deployment%20View.md)).
* **Riesgo:** dependencia parcial del formato `render.yaml`.
* **Mitigación:** el `Dockerfile` es estándar; migrar solo requiere un archivo de configuración equivalente.

## Trazabilidad

RO-02 · RO-03 · [arc42 §7](../arc42/07-Deployment%20View.md) · [ADR-0001](0001-patron-offline-first.md)
