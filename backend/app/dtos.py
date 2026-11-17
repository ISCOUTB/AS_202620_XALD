from datetime import datetime, timedelta, timezone
from enum import Enum
from pydantic import BaseModel, Field, field_validator


class OrigenDatosEnum(str, Enum):
    SMS_REGEX = "SMS_REGEX"
    AI_GEMINI = "AI_GEMINI"
    MANUAL = "MANUAL"


class TransaccionDTO(BaseModel):
    id_transaccion: str = Field(..., description="UUID universal")
    monto: float = Field(..., gt=0, description="Monto mayor a 0")
    moneda: str = Field(..., min_length=3, max_length=3, examples=["COP"])
    comercio: str = Field(..., min_length=1, examples=["Tienda Ara"])
    categoria: str = Field(..., min_length=1, examples=["Supermercado"])
    fecha_transaccion: datetime = Field(..., description="ISO 8601 UTC")
    origen_datos: OrigenDatosEnum

    @field_validator("fecha_transaccion", mode="after")
    @classmethod
    def validar_y_normalizar_utc(cls, v: datetime) -> datetime:
        # Si la fecha no trae zona horaria, asume UTC para evitar TypeErrors al comparar
        fecha_utc = v.replace(tzinfo=timezone.utc) if v.tzinfo is None else v.astimezone(timezone.utc)
        # AM-07: rechazar fechas futuras más allá de 5 minutos de tolerancia por desfase de reloj
        if fecha_utc > datetime.now(timezone.utc) + timedelta(minutes=5):
            raise ValueError("fecha_transaccion no puede estar en el futuro (tolerancia máxima: 5 minutos)")
        return fecha_utc


class RespuestaSincronizacion(BaseModel):
    estado: str = "ACEPTADO"
    id_transaccion: str
    mensaje: str = "Transacción recibida y encolada exitosamente para persistencia."
    fecha_recepcion: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))


class TransaccionGuardada(BaseModel):
    """Lo que el backend tiene guardado de una transacción (GET /api/v1/transacciones/{id})."""
    id_transaccion: str
    monto: float
    moneda: str
    comercio: str
    categoria: str
    fecha_transaccion: datetime
    origen_datos: OrigenDatosEnum
    recibida_en: datetime
    actualizada_en: datetime