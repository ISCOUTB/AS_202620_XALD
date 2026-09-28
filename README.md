# AS_20262_XALD
PROYECTO XALD, APP FINANZAS PERSONALES

## 🌐 Despliegue y operación (Semana 8)

| | |
|---|---|
| **API en línea** | https://xald-backend.onrender.com |
| **Health check** | https://xald-backend.onrender.com/health |
| **Métricas** | https://xald-backend.onrender.com/metrics |
| **Documentación interactiva** | https://xald-backend.onrender.com/docs |
| **Plataforma** | Render · Web Service Docker · plan Free · región Oregon ([ADR-0008](docs/adr/0008-plataforma-de-despliegue.md)) |

### 1. URL accesible desde fuera de la red de la universidad
Comprobación hecha desde datos móviles, fuera de la red UTB, el **2026-09-27 a las 21:19:31 (hora Colombia)**:
```
$ curl -sS -o /dev/null -w "http=%{http_code} tiempo=%{time_total}s" https://xald-backend.onrender.com
http=200 tiempo=0.986400s
$ curl -sS -o /dev/null -w "health=%{http_code}" https://xald-backend.onrender.com/health
health=200
```
Además, el job `deploy-backend` del CI vuelve a consultar `/health` desde el runner de GitHub Actions después de cada despliegue y deja la hora UTC en su log.

### 2. Health check
- `GET /health` → `200 {"status":"ok"}` — función `health()` en `backend/app/main.py`.
- Render lo usa como sonda del servicio: `render.yaml` → `healthCheckPath: /health`.

### 3. Infraestructura como código
| Archivo | Qué describe |
|---|---|
| `render.yaml` | Servicio web Docker, plan Free, health check y variables de entorno (Render Blueprint) |
| `backend/Dockerfile` | Imagen Python 3.12 que arranca uvicorn en el puerto `$PORT` |
| `.env.example` | Variables requeridas, sin valores |
| `.github/workflows/ci.yml` | Pruebas y despliegue automático |

### 4. Cómo recrear el entorno desplegado
1. Crear una cuenta en Render con GitHub (plan Free, no pide tarjeta — RO-03).
2. **New → Blueprint** → repositorio `ISCOUTB/AS_202620_XALD`, rama `master`. Render lee `render.yaml` y crea el servicio `xald-backend`.
3. Cuando Render lo pida, cargar un valor propio para `XALD_API_KEY` (nunca en el repositorio).
4. En Render → **Settings → Deploy Hook**, copiar la URL y guardarla en GitHub → **Settings → Secrets and variables → Actions** como `RENDER_DEPLOY_HOOK`.
5. Hacer push a `master`: el CI corre las pruebas y, si pasan, el job `deploy-backend` despliega y verifica `/health`.

### 5. Pipeline
Workflow `.github/workflows/ci.yml` con tres jobs: `build-and-test` (pruebas de los módulos Android y lint del contrato OpenAPI), `backend-tests` (pytest del backend, `backend/tests/test_api.py`) y `deploy-backend` (solo en `master`, después de que pasen los otros dos).
- **Run verificado en `master`:** https://github.com/ISCOUTB/AS_202620_XALD/actions/runs/36369277181 — conclusión `success` en los tres jobs (2026-09-27).
- **Historial de runs de `master`:** https://github.com/ISCOUTB/AS_202620_XALD/actions?query=branch%3Amaster

### 6. Logs estructurados
Cada evento se escribe como una línea JSON en la salida estándar (clase `FormatoJSON` en `backend/app/main.py`). El log de acceso de uvicorn está apagado (`--no-access-log` en `backend/Dockerfile`) para que no aparezcan líneas de texto plano. Línea real tomada del panel de logs de Render:
```json
{"timestamp": "2026-09-27T17:12:52.080495+00:00", "nivel": "INFO", "evento": "peticion_http", "metodo": "GET", "ruta": "/health", "codigo": 200, "duracion_ms": 0.48}
```

### 7. Métrica ligada a un escenario de calidad
- `GET /metrics`, formato Prometheus ([ADR-0010](docs/adr/0010-observabilidad.md)).
- **`xald_sync_conflictos_resueltos_total` → ESC-05 · Resolución de conflictos al sincronizar.** Se incrementa cada vez que llega una transacción con un `id_transaccion` ya recibido y el backend resuelve el conflicto por Last-Write-Wins (RT-05).
- `xald_transacciones_recibidas_total{origen_datos}` mide el volumen de sincronización.

### 8. Secretos fuera del código
- Declarados sin valor en `.env.example`; el `.env` real está en `.gitignore`.
- Leídos del entorno en `backend/app/main.py`: `os.environ.get("XALD_API_KEY")`.
- Valor cargado en Render → Environment; `render.yaml` lo declara con `sync: false`.
- El CI usa `${{ secrets.RENDER_DEPLOY_HOOK }}` en el job `deploy-backend`, guardado en GitHub → Settings → Secrets → Actions.

### 9. Costo mensual
$0/mes con el supuesto de 50 usuarios; volumen, costo por pieza y punto de ruptura de la capa gratuita en [arc42 §7.2](docs/arc42/07-Deployment%20View.md#72-costo-mensual).



## 🚀 Corte Vertical Ejecutable

El proyecto cuenta con un corte vertical integrado que valida el flujo de datos completo a través de sus módulos Gradle (`:app`, `:parser`, `:aigemini`, `:corefinanciero` y `:syncqueue`).

### 🛠️ Prueba de Integración
La prueba de corte vertical (`Cortevertical.kt`) orquesta la recepción del mensaje bancario crudo, invoca las reglas del parser y valida la estructuración del DTO de la transacción.

### 📸 Evidencia de Ejecución Local
<img width="1365" height="718" alt="Captura de pantalla 2026-08-30 144428" src="https://github.com/user-attachments/assets/13427b6e-b78a-4f51-8bfd-5350157ac22e" />

Dede la carpeta Raiz del proyecto de Android Studio: Con la variable de entorno JAVA_HOME Configurada
```powershell
cd XALDAPP
.\gradlew.bat test
```
### Sumado al CI pasado todo en verde en actions

##### Si no tiene configuradas las variables de entorno de Java/Gradle en su sistema:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
cd XALDAPP
.\gradlew.bat test
```

# Comandos de Ejecución y Verificación

### Guía de Verificación y Compilación Local

##### Requisitos: JDK 17 (incluido en Android Studio JBR) y Android SDK configurados.

##### Ejecución: Abre una consola de PowerShell en la raíz del repositorio y ejecute el comando de arranque para validar los 5 módulos (:app, :corefinanciero, :parser, :syncqueue y :aigemini):
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME="C:\Users\<user>\AppData\Local\Android\Sdk"; .\XALDAPP\gradlew.bat -p XALDAPP test
```

## Salida Esperada en Consola

##### Al ejecutar el comando anterior, la suite de pruebas unitarias validará el entorno. El resultado exitoso debe verse así:

<img width="1104" height="254" alt="Captura de pantalla 2026-08-29 160428" src="https://github.com/user-attachments/assets/1ea8b153-e37b-4a51-a09c-b43a0b5f3c04" />

## 🐳 Backend XALD — Cómo levantar el entorno localmente

El Backend XALD (API de Sincronización Financiera) está en `backend/`, corre en **Python 3.12 + FastAPI**, y se distribuye como contenedor Docker (el mismo `Dockerfile` que usa Render en producción).

### Requisitos: Docker instalado (o Python 3.12+ si prefieres correrlo sin contenedor).

### 1. Configurar las variables de entorno
Copia el archivo de ejemplo y complétalo con tus propios valores locales:
```powershell
cp .env.example .env
```
El archivo `.env.example` ya trae las variables necesarias:
```
XALD_API_KEY=      # pon cualquier valor local para pruebas, ej: dev-key-123
LOG_LEVEL=INFO
PORT=8000
```
⚠️ Nunca subas tu `.env` real al repositorio — solo `.env.example` va versionado.

### 2. Construir y levantar el contenedor
```powershell
docker build -t xald-backend ./backend
docker run --rm -p 8000:8000 --env-file .env xald-backend
```

### 3. Verificar que quedó arriba
```powershell
curl http://localhost:8000/health
```
Salida esperada:
```json
{"status": "ok"}
```

También puedes abrir `http://localhost:8000/docs` en el navegador para ver la documentación interactiva (Swagger UI) y probar el endpoint `POST /api/v1/transacciones` directamente, usando el valor de `XALD_API_KEY` que pusiste en tu `.env` como header `X-API-Key`.

### Alternativa sin Docker
```powershell
cd backend
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### Descripción de la app

El objetivo es ofrecer a los usuarios una herramienta intuitiva y eficiente para el control de sus ingresos, gastos y ahorros, permitiéndoles tomar decisiones financieras más informadas a través de un seguimiento claro de su actividad económica diaria. Con un enfoque centrado en la simplicidad y la usabilidad, la aplicación busca convertirse en un aliado práctico para la organización financiera personal.


### Situación problema 

Muchas personas carecen de un control claro sobre sus ingresos, gastos y ahorros, lo que dificulta tomar decisiones financieras informadas y favorece el endeudamiento innecesario. Esto se debe, en parte, al uso de métodos poco eficientes( cuadernos, hojas de calculo genéricas o ningún registro) y a que las aplicaciones existentes suelen ser demasiado complejas o demasiado básicas para cubrir sus necesidades reales. Esta app surge para resolver esta problemática, ofreciendo una herramienta simple y accesible que permita a los usuarios comprender y organizar su actividad económica diaria.
