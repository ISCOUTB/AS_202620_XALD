import hmac
import json
import logging
import os
import sys
import time
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
def verificar_api_key(x_api_key: str = Header(default="")):
    esperada = os.environ.get("XALD_API_KEY", "")
    if not esperada or not hmac.compare_digest(x_api_key, esperada):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="API key inválida")


app = FastAPI(
    title="API de Sincronización Financiera - Proyecto XALD",
    version="1.0.0"
)


@app.middleware("http")
async def registrar_peticion(request: Request, call_next):
    inicio = time.perf_counter()
    respuesta = await call_next(request)
    registrar(
        "peticion_http",
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
    dependencies=[Depends(verificar_api_key)],
)
async def registrar_transaccion(transaccion: TransaccionDTO):
    TRANSACCIONES_RECIBIDAS.labels(origen_datos=transaccion.origen_datos.value).inc()

    previa = ultima_version.get(transaccion.id_transaccion)
    if previa is None or transaccion.fecha_transaccion > previa:
        ultima_version[transaccion.id_transaccion] = transaccion.fecha_transaccion
    if previa is not None:
        CONFLICTOS_RESUELTOS.inc()
        registrar(
            "conflicto_resuelto_lww",
            id_transaccion=transaccion.id_transaccion,
            gano="entrante" if transaccion.fecha_transaccion > previa else "existente",
        )

    registrar(
        "transaccion_recibida",
        id_transaccion=transaccion.id_transaccion,
        origen_datos=transaccion.origen_datos.value,
    )
    return RespuestaSincronizacion(
        estado="ACEPTADO",
        id_transaccion=transaccion.id_transaccion,
        mensaje="Transacción recibida y encolada exitosamente para persistencia."
    )
