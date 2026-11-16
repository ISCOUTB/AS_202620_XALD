import hmac
import json
import logging
import os
import sys
import time
import uuid
from contextlib import asynccontextmanager
from datetime import datetime, timezone

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response, status
from prometheus_client import CONTENT_TYPE_LATEST, Counter, generate_latest
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from app import db, repositorio
from app.dtos import RespuestaSincronizacion, TransaccionDTO, TransaccionGuardada
from app.repositorio import Resultado


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

ERRORES_BD = Counter(
    "xald_bd_errores_total",
    "Peticiones que fallaron porque la base de datos no respondió (el cliente recibe 503 y reintenta)",
)


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


# ---------------------------------------------------------------------------
# Persistencia remota (ADR-0015): la versión vigente de cada transacción vive en la BD,
# no en memoria, así que Last-Write-Wins sobrevive a despliegues y a que Render duerma.
# ---------------------------------------------------------------------------
tablas_listas = False


def asegurar_tablas():
    global tablas_listas
    if not tablas_listas:
        db.crear_tablas()
        tablas_listas = True


@asynccontextmanager
async def ciclo_de_vida(_app):
    # Si la BD no responde al arrancar, el servicio igual sube (el health check de Render
    # no depende de la BD) y las tablas se crean en la primera petición que la necesite.
    try:
        asegurar_tablas()
        registrar("bd_lista", motor=db.motor.dialect.name)
    except SQLAlchemyError as e:
        logger.error("bd_no_disponible_al_arrancar", extra={"campos": {"error": e.__class__.__name__}})
    yield


def obtener_sesion():
    try:
        asegurar_tablas()
        sesion = db.FabricaSesiones()
    except SQLAlchemyError as e:
        raise _bd_no_disponible(e)
    try:
        yield sesion
    finally:
        sesion.close()


def _bd_no_disponible(error):
    ERRORES_BD.inc()
    logger.error("bd_no_disponible", extra={"campos": {"error": error.__class__.__name__}})
    # 503 es recuperable para el cliente (clasificarRespuestaHttp en :syncqueue): reintenta con
    # espera exponencial y la transacción no se pierde.
    return HTTPException(
        status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
        detail="Base de datos no disponible, reintente más tarde.",
    )


app = FastAPI(
    title="API de Sincronización Financiera - Proyecto XALD",
    version="1.1.0",
    lifespan=ciclo_de_vida,
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
def registrar_transaccion(transaccion: TransaccionDTO, request: Request, sesion: Session = Depends(obtener_sesion)):
    # Función síncrona a propósito: FastAPI la corre en su pool de hilos y la consulta a la BD
    # no bloquea el event loop.
    id_correlacion = request.state.id_correlacion
    TRANSACCIONES_RECIBIDAS.labels(origen_datos=transaccion.origen_datos.value).inc()

    try:
        resultado = repositorio.registrar(sesion, transaccion, id_correlacion)
    except SQLAlchemyError as e:
        sesion.rollback()
        raise _bd_no_disponible(e)

    # Idempotencia (arc42 §8, MF-03): un reenvío idéntico (mismo id y misma fecha)
    # no es un conflicto. Se responde igual que la primera vez y no cambia el estado.
    if resultado is Resultado.DUPLICADO:
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

    if resultado in (Resultado.GANA_ENTRANTE, Resultado.GANA_EXISTENTE):
        CONFLICTOS_RESUELTOS.inc()
        registrar(
            "conflicto_resuelto_lww",
            id_correlacion=id_correlacion,
            id_transaccion=transaccion.id_transaccion,
            gano="entrante" if resultado is Resultado.GANA_ENTRANTE else "existente",
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
        mensaje=(
            "Transacción recibida; se conserva una versión más reciente (Last-Write-Wins)."
            if resultado is Resultado.GANA_EXISTENTE
            else "Transacción recibida y guardada."
        ),
    )


@app.get(
    "/api/v1/transacciones/{id_transaccion}",
    response_model=TransaccionGuardada,
    summary="Consultar la versión que el backend conserva de una transacción",
    dependencies=[Depends(verificar_llave)],
)
def consultar_transaccion(id_transaccion: str, sesion: Session = Depends(obtener_sesion)):
    try:
        fila = repositorio.obtener(sesion, id_transaccion)
    except SQLAlchemyError as e:
        raise _bd_no_disponible(e)
    if fila is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Transacción no encontrada")
    return TransaccionGuardada(
        id_transaccion=fila.id_transaccion,
        monto=float(fila.monto),
        moneda=fila.moneda,
        comercio=fila.comercio,
        categoria=fila.categoria,
        fecha_transaccion=db.en_utc(fila.fecha_transaccion),
        origen_datos=fila.origen_datos,
        recibida_en=db.en_utc(fila.recibida_en),
        actualizada_en=db.en_utc(fila.actualizada_en),
    )
