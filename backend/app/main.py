from enum import Enum
from typing import List
from datetime import datetime
from uuid import UUID
from fastapi import FastAPI, status
from pydantic import BaseModel, Field

app = FastAPI(
    title="API de Sincronización Financiera - Proyecto XALD",
    description="API REST asíncrona para la recepción e ingesta de transacciones financieras.",
    version="1.0.0",
)

class OrigenDatosEnum(str, Enum):
    SMS_REGEX = "SMS_REGEX"
    AI_GEMINI = "AI_GEMINI"
    MANUAL = "MANUAL"

class TransaccionDTO(BaseModel):
    id_transaccion: UUID = Field(..., description="Identificador único universal (UUID)")
    monto: float = Field(..., gt=0, description="Valor numérico de la transacción")
    moneda: str = Field(..., min_length=3, max_length=3, example="COP")
    comercio: str = Field(..., min_length=1, example="Tienda Ara")
    categoria: str = Field(..., min_length=1, example="Supermercado")
    fecha_transaccion: datetime = Field(..., description="Estampa de tiempo ISO 8601 UTC")
    origen_datos: OrigenDatosEnum

class RespuestaSincronizacion(BaseModel):
    estado: str = "ACEPTADO"
    id_transaccion: UUID
    mensaje: str = "Transacción recibida y encolada exitosamente para persistencia."
    fecha_recepcion: datetime

@app.post(
    "/api/v1/transacciones",
    response_model=RespuestaSincronizacion,
    status_code=status.HTTP_202_ACCEPTED,
    summary="Recibir transacción financiera desde la cola de sincronización"
)
async def registrar_transaccion(transaccion: TransaccionDTO):
    return RespuestaSincronizacion(
        id_transaccion=transaccion.id_transaccion,
        fecha_recepcion=datetime.utcnow()
    )