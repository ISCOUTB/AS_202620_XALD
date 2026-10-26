import hmac
import json
import logging
import os
import sys
import time
import uuid
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response, status
from prometheus_client import CONTENT_TYPE_LATEST, Counter, generate_latest

from app.dtos import RespuestaSincronizacion, TransaccionDTO


# ---------------------------------------------------------------------------
# Logs estructurados: cada línea es un objeto JSON con campos
# ---------------------------------------------------------------------------
class FormatoJSON(logging.Formatter):
    def format(self, record):
        entrada = {
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "nivel": record.levelname,
            "evento": record.getMessage(),
        }
        entrada.update(getattr(record, "campos", {}))
        return json.dumps(entrada, ensure_ascii=False)


manejador = logging.StreamHandler(sys.stdout)
manejador.setFormatter(FormatoJSON())
logger = logging.getLogger("xald")
logger.handlers = [manejador]
logger.setLevel(os.environ.get("LOG_LEVEL", "INFO"))
logger.propagate = False


def registrar(evento, **campos):
    logger.info(evento, extra={"campos": campos})


# ---------------------------------------------------------------------------
# Métricas (ligadas a ESC-05: resolución de conflictos al sincronizar)
# ---------------------------------------------------------------------------
TRANSACCIONES_RECIBIDAS = Counter(
    "xald_transacciones_recibidas_total",
    "Transacciones recibidas desde la cola de sincronización del dispositivo",
    ["origen_datos"],
)
DUPLICADOS = Counter(
    "xald_sync_duplicados_total",
    "Reenvíos idénticos (mismo id_transaccion y misma fecha) ignorados por idempotencia (MF-03)",
)
CONFLICTOS_RESUELTOS = Counter(
    "xald_sync_conflictos_resueltos_total",
    "Conflictos de sincronización resueltos por Last-Write-Wins (ESC-05)",
)

# Estado en memoria para aplicar Last-Write-Wins (RT-05).
# Se reinicia con cada despliegue: la persistencia remota sigue pendiente.
ultima_version = {}


# ---------------------------------------------------------------------------
# Seguridad: la llave se toma del entorno, nunca del código
# ---------------------------------------------------------------------------
def verificar_llave(llave: str = Header(default="", alias="X-API-Key")):
    esperada = os.environ.get("XALD_API_KEY", "")
    if not esperada or not hmac.compare_digest(llave, esperada):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="API key inválida")


# ---------------------------------------------------------------------------
# Contrato del mensaje (docs/mensajes/README.md): versión mayor que este consumidor entiende.
# Un cambio compatible NO sube esta versión; uno incompatible sí, y el consumidor viejo lo
# rechaza con 400 para que el cliente lo mande a su cola de fallidos en vez de reintentar.
# ---------------------------------------------------------------------------
VERSIONES_ESQUEMA_SOPORTADAS = {"1"}


def verificar_version_esquema(version: str = Header(default="1", alias="X-Version-Esquema")):
    if version.strip() not in VERSIONES_ESQUEMA_SOPORTADAS:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Versión de esquema no soportada: {version}. Soportadas: {sorted(VERSIONES_ESQUEMA_SOPORTADAS)}",
        )


app = FastAPI(
    title="API de Sincronización Financiera - Proyecto XALD",
    version="1.0.0"
)


@app.middleware("http")
async def registrar_peticion(request: Request, call_next):
    # Trazabilidad productor -> consumidor: el cliente manda X-Correlation-Id (estable entre
    # reintentos); si no llega, se genera uno para no perder la traza de esta petición.
    id_correlacion = request.headers.get("X-Correlation-Id") or str(uuid.uuid4())
    request.state.id_correlacion = id_correlacion
    inicio = time.perf_counter()
    respuesta = await call_next(request)
    respuesta.headers["X-Correlation-Id"] = id_correlacion
    registrar(
        "peticion_http",
        id_correlacion=id_correlacion,
        metodo=request.method,
        ruta=request.url.path,
        codigo=respuesta.status_code,
        duracion_ms=round((time.perf_counter() - inicio) * 1000, 2),
    )
    return respuesta


@app.get("/", summary="Información del servicio")
async def raiz():
    return {"servicio": "XALD - API de Sincronización", "health": "/health", "metricas": "/metrics", "docs": "/docs"}


@app.get("/health", summary="Health check del servicio")
async def health():
    return {"status": "ok"}


@app.get("/metrics", summary="Métricas en formato Prometheus")
async def metrics():
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post(
    "/api/v1/transacciones",
    status_code=status.HTTP_202_ACCEPTED,
    response_model=RespuestaSincronizacion,
    summary="Recibir transacción financiera desde la cola de sincronización",
    dependencies=[Depends(verificar_llave), Depends(verificar_version_esquema)],
)
async def registrar_transaccion(transaccion: TransaccionDTO, request: Request):
    id_correlacion = request.state.id_correlacion
    TRANSACCIONES_RECIBIDAS.labels(origen_datos=transaccion.origen_datos.value).inc()

    previa = ultima_version.get(transaccion.id_transaccion)

    # Idempotencia (arc42 §8, MF-03): un reenvío idéntico (mismo id y misma fecha)
    # no es un conflicto. Se responde igual que la primera vez y no cambia el estado.
    if previa is not None and transaccion.fecha_transaccion == previa:
        DUPLICADOS.inc()
        registrar(
            "reenvio_duplicado_ignorado",
            id_correlacion=id_correlacion,
            id_transaccion=transaccion.id_transaccion,
        )
        return RespuestaSincronizacion(
            estado="ACEPTADO",
            id_transaccion=transaccion.id_transaccion,
            mensaje="Transacción ya recibida previamente; reenvío idempotente sin cambios."
        )

    if previa is None or transaccion.fecha_transaccion > previa:
        ultima_version[transaccion.id_transaccion] = transaccion.fecha_transaccion
    if previa is not None:
        CONFLICTOS_RESUELTOS.inc()
        registrar(
            "conflicto_resuelto_lww",
            id_correlacion=id_correlacion,
            id_transaccion=transaccion.id_transaccion,
            gano="entrante" if transaccion.fecha_transaccion > previa else "existente",
        )

    registrar(
        "transaccion_recibida",
        id_correlacion=id_correlacion,
        id_transaccion=transaccion.id_transaccion,
        origen_datos=transaccion.origen_datos.value,
    )
    return RespuestaSincronizacion(
        estado="ACEPTADO",
        id_transaccion=transaccion.id_transaccion,
        mensaje="Transacción recibida y encolada exitosamente para persistencia."
    )
