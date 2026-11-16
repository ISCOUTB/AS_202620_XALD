"""
Regla Last-Write-Wins (ADR-0011, RT-05) aplicada sobre la base de datos (ADR-0015).

Antes vivía en un diccionario en memoria (`ultima_version`) que se perdía en cada despliegue
o cuando Render dormía el servicio. Ahora la versión vigente de cada transacción es la fila
de la tabla `transacciones`, así que la garantía de ESC-05 sobrevive a los reinicios.
"""
from datetime import datetime, timezone
from enum import Enum

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.db import TransaccionRegistro, en_utc
from app.dtos import TransaccionDTO


class Resultado(str, Enum):
    NUEVA = "nueva"                    # primera vez que llega este id_transaccion
    DUPLICADO = "duplicado"            # mismo id y misma fecha: reenvío idempotente (MF-03)
    GANA_ENTRANTE = "gana_entrante"    # conflicto: la versión que llega es más reciente
    GANA_EXISTENTE = "gana_existente"  # conflicto: la guardada es igual de reciente o más


def _copiar_campos(fila: TransaccionRegistro, dto: TransaccionDTO, id_correlacion: str | None) -> None:
    fila.monto = dto.monto
    fila.moneda = dto.moneda
    fila.comercio = dto.comercio
    fila.categoria = dto.categoria
    fila.fecha_transaccion = dto.fecha_transaccion
    fila.origen_datos = dto.origen_datos.value
    fila.id_correlacion = id_correlacion


def registrar(sesion: Session, dto: TransaccionDTO, id_correlacion: str | None = None) -> Resultado:
    """
    Guarda la transacción aplicando Last-Write-Wins y confirma la transacción de BD.

    Concurrencia: en Postgres la fila existente se bloquea con SELECT ... FOR UPDATE, así que
    dos versiones de la misma transacción que llegan a la vez se comparan una después de la otra.
    Si dos peticiones intentan crear la misma fila al mismo tiempo, una choca con la clave
    primaria (IntegrityError) y se reintenta una vez, ya como conflicto contra la que ganó.
    """
    for intento in range(2):
        try:
            resultado = _registrar_una_vez(sesion, dto, id_correlacion)
            sesion.commit()
            return resultado
        except IntegrityError:
            sesion.rollback()
            if intento == 1:
                raise
    raise RuntimeError("inalcanzable")


def _registrar_una_vez(sesion: Session, dto: TransaccionDTO, id_correlacion: str | None) -> Resultado:
    ahora = datetime.now(timezone.utc)
    fila = sesion.execute(
        select(TransaccionRegistro)
        .where(TransaccionRegistro.id_transaccion == dto.id_transaccion)
        .with_for_update()
    ).scalar_one_or_none()

    if fila is None:
        fila = TransaccionRegistro(id_transaccion=dto.id_transaccion, recibida_en=ahora, actualizada_en=ahora)
        _copiar_campos(fila, dto, id_correlacion)
        sesion.add(fila)
        sesion.flush()  # aquí salta el IntegrityError si otra petición la creó primero
        return Resultado.NUEVA

    guardada = en_utc(fila.fecha_transaccion)
    if dto.fecha_transaccion == guardada:
        return Resultado.DUPLICADO
    if dto.fecha_transaccion > guardada:
        _copiar_campos(fila, dto, id_correlacion)
        fila.actualizada_en = ahora
        return Resultado.GANA_ENTRANTE
    # Fecha igual ya se trató como duplicado; con fecha anterior se conserva la guardada.
    return Resultado.GANA_EXISTENTE


def obtener(sesion: Session, id_transaccion: str) -> TransaccionRegistro | None:
    return sesion.get(TransaccionRegistro, id_transaccion)


def fecha_vigente(sesion: Session, id_transaccion: str) -> datetime | None:
    """Versión que el backend conserva para esa transacción (la usan las pruebas de LWW)."""
    fila = obtener(sesion, id_transaccion)
    return en_utc(fila.fecha_transaccion) if fila else None
