from enum import Enum
from pydantic import BaseModel, Field
from datetime import datetime

class OrigenDatosEnum(str, Enum):
    SMS_REGEX = "SMS_REGEX"
    AI_GEMINI = "AI_GEMINI"
    MANUAL = "MANUAL"

class TransaccionDTO(BaseModel):
    id_transaccion: str = Field(..., description="UUID universal")
    monto: float = Field(..., gt=0, description="Monto mayor a 0")
    moneda: str = Field(..., min_length=3, max_length=3, example="COP")
    comercio: str = Field(..., min_length=1, example="Tienda Ara")
    categoria: str = Field(..., min_length=1, example="Supermercado")
    fecha_transaccion: datetime = Field(..., description="ISO 8601 UTC")
    origen_datos: OrigenDatosEnum

class RespuestaSincronizacion(BaseModel):
    estado: str = "ACEPTADO"
    id_transaccion: str
    mensaje: str = "Transacción recibida y encolada exitosamente para persistencia."
    fecha_recepcion: datetime = Field(default_factory=datetime.utcnow)