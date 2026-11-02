import hmac
import json
import logging
import os
import sys
import time
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response, status
from fastapi.responses import JSONResponse
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
# Amenaza AM-01 (docs/seguridad/modelo-amenazas.md) y ADR-0015: se aceptan varias
# llaves vigentes a la vez (XALD_API_KEYS, separadas por comas) para poder rotar o
# revocar una llave filtrada sin dejar sin servicio a los dispositivos que ya
# tienen la nueva. XALD_API_KEY (una sola) se mantiene por compatibilidad.
# ---------------------------------------------------------------------------
def llaves_vigentes():
    llaves = [
        llave.strip()
        for llave in os.environ.get("XALD_API_KEYS", "").split(",") + [os.environ.get("XALD_API_KEY", "")]
        if llave.strip()
    ]
    return llaves


def verificar_llave(llave: str = Header(default="", alias="X-API-Key")):
    vigentes = llaves_vigentes()
    # compare_digest contra todas las llaves, sin cortar en la primera coincidencia,
    # para no filtrar por tiempo de respuesta cuál de ellas coincidió.
    coincide = False
    for esperada in vigentes:
        coincide |= hmac.compare_digest(llave.encode(), esperada.encode())
    if not vigentes or not llave or not coincide:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="API key inválida")


# ---------------------------------------------------------------------------
# Amenaza AM-07: un cliente con la llave podría mandar una fecha futura para ganar
# siempre el Last-Write-Wins (ADR-0011) y bloquear correcciones legítimas.
# Se rechaza toda fecha más allá de la tolerancia de reloj declarada.
# ---------------------------------------------------------------------------
TOLERANCIA_RELOJ = timedelta(minutes=5)

# Amenaza AM-08: cuerpo sin límite de tamaño. Una transacción válida ocupa < 1 KB.
TAMANO_MAXIMO_CUERPO = 16 * 1024


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
    if request.method == "POST":
        longitud = request.headers.get("content-length")
        if longitud is None or not longitud.isdigit():
            return JSONResponse(status_code=status.HTTP_411_LENGTH_REQUIRED, content={"detail": "Content-Length requerido"})
        if int(longitud) > TAMANO_MAXIMO_CUERPO:
            return JSONResponse(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                content={"detail": f"Cuerpo mayor a {TAMANO_MAXIMO_CUERPO} bytes"},
            )
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

    fecha = transaccion.fecha_transaccion
    if fecha.tzinfo is None:
        fecha = fecha.replace(tzinfo=timezone.utc)
    if fecha > datetime.now(timezone.utc) + TOLERANCIA_RELOJ:
        registrar("fecha_futura_rechazada", id_correlacion=id_correlacion, id_transaccion=transaccion.id_transaccion)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="fecha_transaccion está en el futuro más allá de la tolerancia de reloj (5 min)",
        )

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
