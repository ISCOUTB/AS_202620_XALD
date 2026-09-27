# ADR-0008: Selección de Plataforma de Despliegue para el Backend XALD

* *Estatus:* Decidido
* *Fecha:* Septiembre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Segundo corte — despliegue del Backend XALD

## Contexto y Problema

El Backend XALD (API de Sincronización Financiera, FastAPI + Docker) necesitaba un lugar donde correr accesible desde fuera de la red de la universidad, para que el corte pudiera evaluarse sin depender de que el equipo tuviera un servidor propio encendido. La restricción RO-02 (presupuesto $0) obliga a que esa infraestructura sea gratuita, y la nueva restricción RO-03 (sin tarjeta de crédito) exige además que la capa gratuita elegida no pida registrar un medio de pago, para no comprometer datos financieros propios del equipo ni depender de que alguien adelantara un cobro.

## Opciones Evaluadas

* *Heroku (Free Dynos):* descartada porque Heroku eliminó por completo su capa gratuita desde noviembre de 2022; hoy todos sus planes son de pago.
* *AWS (EC2 / Lightsail, Free Tier):* descartada porque, aunque técnicamente ofrece un nivel gratuito, exige registrar una tarjeta de crédito válida desde el momento de la creación de la cuenta — incompatible con RO-03.
* *Fly.io (plan gratuito):* descartada por el mismo motivo que AWS: sus asignaciones gratuitas actuales requieren verificación con tarjeta de crédito para activarse, y además reducen recursos gratuitos de forma frecuente.
* *Railway (plan gratuito / trial):* descartada porque su modelo actual entrega solo un crédito inicial limitado de prueba, no una capa gratuita permanente, y también solicita tarjeta para continuar tras agotarlo.
* *Render (Web Service Docker, plan Free) (Adoptada):* ofrece un Web Service gratuito con soporte nativo para contenedores Docker, `healthCheckPath` configurable, variables de entorno gestionadas desde su panel, y no exige tarjeta de crédito para el plan Free.

## Decisión Tomada

Desplegar el Backend XALD en **Render**, como *Web Service* de tipo Docker en el plan **Free**, definido como infraestructura como código en [`render.yaml`](../../render.yaml) (Render Blueprint) y construido a partir de [`backend/Dockerfile`](../../backend/Dockerfile). El despliegue se dispara automáticamente desde el job `deploy-backend` del CI (`.github/workflows/ci.yml`) mediante un Deploy Hook, cada vez que hay un `push` a `master`.

## Justificación Técnica

* **Cumple RO-02 y RO-03 simultáneamente:** es gratuito y no requiere tarjeta de crédito, a diferencia de todas las alternativas evaluadas.
* **Infraestructura como código real:** Render soporta un archivo `render.yaml` versionado en el propio repositorio, que declara el servicio, el `healthCheckPath` y las variables de entorno — sin necesidad de configurar nada manualmente fuera del repo salvo el valor secreto de `XALD_API_KEY`.
* **Compatible con el Dockerfile ya existente:** no se tuvo que adaptar el contenedor del backend a un formato propietario de la plataforma; el mismo `Dockerfile` sirve para desarrollo local y para producción.
* **Deploy Hook simple de integrar al CI:** el disparo del despliegue es una sola petición HTTP POST, fácil de invocar desde GitHub Actions sin instalar un CLI adicional.

## Consecuencias

### Positivas:

* El sistema queda accesible públicamente (`https://xald-backend.onrender.com`) sin costo ni compromiso de un medio de pago del equipo.
* El despliegue es reproducible: cualquier integrante puede recrear el servicio en su propia cuenta de Render a partir del mismo `render.yaml`.
* El `healthCheckPath: /health` permite que la propia plataforma reinicie el servicio si detecta que dejó de responder.

### Riesgos y Mitigación:

* **Riesgo:** el plan Free de Render "duerme" el servicio tras 15 minutos sin tráfico, y la primera petición tras dormir tarda varios segundos en responder (cold start).
* **Mitigación:** aceptado conscientemente para este corte, dado el alcance académico (RO-01); si el proyecto continuara en producción real, este ADR debería revisarse para evaluar el plan Starter de pago (~$7/mes, ver Sección 7.2 del arc42).
* **Riesgo:** dependencia de un proveedor externo (vendor lock-in parcial) por el formato de `render.yaml`.
* **Mitigación:** el `Dockerfile` es estándar y portable; migrar a otra plataforma con soporte Docker no requeriría cambios en el código del backend, solo un nuevo archivo de configuración equivalente.

## Trazabilidad

* Restricciones relacionadas: RO-02 (Costo $0 / Presupuesto), RO-03 (Sin tarjeta de crédito).
* Vista de despliegue: [`docs/arc42/07-Deployment View.md`](../arc42/07-Deployment%20View.md).
* Infraestructura como código: [`render.yaml`](../../render.yaml), [`backend/Dockerfile`](../../backend/Dockerfile).
* CI/CD: job `deploy-backend` en [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml).
* Decisión que antecede a esta: [ADR-0001](0001-patron-offline-first.md) (Offline-First, origen de por qué el backend es solo de sincronización, no la fuente de verdad).
