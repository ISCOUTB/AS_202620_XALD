# Deployment View

[#deployment-view](#deployment-view)

Esta sección muestra dónde corre físicamente cada pieza del sistema XALD y qué costo mensual implica mantenerlas en línea, complementando la vista lógica de la Sección 5 (Building Block View) con la infraestructura real sobre la que se despliega cada contenedor.

## 7.1 Infraestructura de Despliegue

[#71-infraestructura-de-despliegue](#71-infraestructura-de-despliegue)

XALD se despliega sobre tres piezas de infraestructura independientes, una por cada actor/contenedor identificado en el C1 (`docs/c4/c4.md`):

subgraph "Render (nube, plan Free)"
    RENDER["Backend XALD<br/>Contenedor Docker<br/>FastAPI + Uvicorn"]
end

subgraph "Google Cloud (SaaS externo)"
    GEMINI["Google Gemini API<br/>Free Tier (AI Studio)"]
end

| Pieza | Dónde corre | Cómo se despliega | Notas |
| :--- | :--- | :--- | :--- |
| **Aplicación Móvil XALD** (`:app`, `:parser`, `:corefinanciero`, `:syncqueue`, `:aigemini`) | Dispositivo Android del usuario | APK instalado localmente; no hay infraestructura de servidor para este contenedor (Offline-First, RT-02) | Es el único contenedor que el equipo no "despliega" en el sentido de nube — vive en el hardware del usuario. |
| **Backend XALD** | [Render](https://render.com), servicio *Web Service* tipo Docker, plan **Free** | Definido como código en [`render.yaml`](../../render.yaml) (Render Blueprint); build a partir de [`backend/Dockerfile`](../../backend/Dockerfile); despliegue disparado por el Deploy Hook (`RENDER_DEPLOY_HOOK`) desde el job `deploy-backend` del CI | URL pública: `https://xald-backend.onrender.com`. Health check en `/health`. Variables sensibles (`XALD_API_KEY`) cargadas en Render → Environment, nunca en el repositorio. |
| **Google Gemini API** | Infraestructura de Google Cloud (SaaS, fuera de la frontera del sistema) | Consumida vía HTTPS desde `:aigemini` (`CategorizadorGemini`) con una API key de Google AI Studio, capa gratuita | Solo se envían nombre del comercio y monto (ver RL-01); si falla o no responde, aplica el flujo de reintentos de ESC-02. |

**Aspectos notables:** ninguna de las tres piezas requiere infraestructura propia administrada por el equipo — es una decisión directa de RO-01 y RO-02, que también motivó ADR-0001 y ADR-0006.

## 7.2 Costo Mensual

[#72-costo-mensual](#72-costo-mensual)

Estimación de costo bajo el supuesto de volumen de uso indicado, con las tarifas vigentes en las fuentes citadas (consultadas el 27 de septiembre de 2026).

**Volumen supuesto:** 50 usuarios activos, cada uno generando en promedio 5 transacciones/día → ~7 500 transacciones/mes y un número similar de posibles consultas a Gemini (caso ambiguo, ESC-01 Caso B).

| Pieza | Capa usada | Límite de la capa gratuita | Costo con el volumen supuesto | Fuente (fecha de consulta) |
| :--- | :--- | :--- | :--- | :--- |
| **Render** (Backend, Web Service Docker) | Free | 750 h/mes de cómputo, 512 MB RAM, 0.1 CPU; se "duerme" tras 15 min sin tráfico | **$0/mes** — 7 500 peticiones/mes son un tráfico bajo, muy por debajo del límite de horas de cómputo | [render.com/pricing](https://render.com/pricing) (27 sep 2026) |
| **Google Gemini API** (`:aigemini`, categorización) | Free Tier (Google AI Studio) | Del orden de cientos a ~1 500 solicitudes/día según el modelo Flash usado | **$0/mes** — con ~250 solicitudes/día promedio, se mantiene por debajo del límite | [ai.google.dev/gemini-api/docs/pricing](https://ai.google.dev/gemini-api/docs/pricing) y [ai.google.dev/gemini-api/docs/rate-limits](https://ai.google.dev/gemini-api/docs/rate-limits) (27 sep 2026) |
| **Aplicación Android** | N/A (APK local) | N/A | **$0/mes** — no hay distribución en Play Store en este alcance (RO-01) | — |
| **Total estimado** | | | **$0/mes** | |

**Punto de ruptura (cuándo deja de ser gratis):**

- **Render:** al superar las 750 h/mes de cómputo acumulado, habría que subir al plan **Starter** (~$7/mes).
- **Gemini API:** al superar el límite de solicitudes por día del modelo Flash gratuito, habría que habilitar facturación; se cobra por token (~USD $0.10–$1.50 por millón de tokens de entrada).
- En ambos casos, se alcanzaría con un crecimiento muy por encima del supuesto de 50 usuarios.

> **Nota de trazabilidad:** al momento de esta estimación, `CategorizadorGemini` es un *stub* local (`XALDAPP/aigemini/src/main/java/CategorizadorGemini.kt`) y todavía no realiza llamadas HTTP reales a la API de Gemini. El costo de esa pieza es una proyección, no un gasto ya incurrido.

ANDROID -->|"HTTPS/TLS 1.3<br/>POST /api/v1/transacciones<br/>Header X-API-Key"| RENDER
ANDROID -->|"HTTPS/TLS 1.3<br/>Solicitud de categorización<br/>(ver Runtime Scenario 1, ESC-01)"| GEMINI
