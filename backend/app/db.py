"""
Persistencia remota del Backend XALD (ADR-0015).

La base de datos se elige con la variable de entorno DATABASE_URL:
  - En Render: la cadena de conexión del Postgres del Blueprint (render.yaml → fromDatabase).
  - En local y en las pruebas, si no se define: SQLite en un archivo (xald-local.db).

Render, Neon y Supabase entregan la URL como "postgres://..." o "postgresql://...";
aquí se reescribe a "postgresql+psycopg://..." para usar el driver psycopg 3.
"""
import os
from datetime import datetime, timezone

from sqlalchemy import DateTime, Numeric, String, create_engine
from sqlalchemy.engine import Engine
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, sessionmaker

URL_POR_DEFECTO = "sqlite:///./xald-local.db"


def normalizar_url(url: str) -> str:
    url = url.strip()
    for prefijo in ("postgres://", "postgresql://"):
        if url.startswith(prefijo):
            return "postgresql+psycopg://" + url[len(prefijo):]
    return url


def crear_motor(url: str | None = None) -> Engine:
    url = normalizar_url(url or os.environ.get("DATABASE_URL") or URL_POR_DEFECTO)
    if url.startswith("sqlite"):
        # TestClient y uvicorn atienden en hilos distintos al que creó la conexión.
        return create_engine(url, connect_args={"check_same_thread": False})
    # pool_pre_ping: Render puede reiniciar el Postgres gratuito; descarta conexiones muertas.
    return create_engine(url, pool_pre_ping=True, pool_size=5, max_overflow=5, pool_recycle=300)


class Base(DeclarativeBase):
    pass


class TransaccionRegistro(Base):
    """
    Una fila por transacción. La clave primaria es el UUID que genera el dispositivo
    (ADR-0011), así que un reenvío nunca crea una segunda fila.
    """

    __tablename__ = "transacciones"

    id_transaccion: Mapped[str] = mapped_column(String(64), primary_key=True)
    monto: Mapped[float] = mapped_column(Numeric(18, 2), nullable=False)
    moneda: Mapped[str] = mapped_column(String(3), nullable=False)
    comercio: Mapped[str] = mapped_column(String(255), nullable=False)
    categoria: Mapped[str] = mapped_column(String(100), nullable=False)
    # Versión de la transacción para Last-Write-Wins (RT-05): la fecha del dispositivo, en UTC.
    fecha_transaccion: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    origen_datos: Mapped[str] = mapped_column(String(20), nullable=False)
    # Hora del servidor: primera recepción y última vez que una versión más reciente ganó.
    recibida_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    actualizada_en: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    id_correlacion: Mapped[str | None] = mapped_column(String(100), nullable=True)


def en_utc(valor: datetime) -> datetime:
    """SQLite devuelve fechas sin zona horaria; todo lo guardado está en UTC."""
    if valor.tzinfo is None:
        return valor.replace(tzinfo=timezone.utc)
    return valor.astimezone(timezone.utc)


motor = crear_motor()
FabricaSesiones = sessionmaker(bind=motor, expire_on_commit=False)


def crear_tablas() -> None:
    Base.metadata.create_all(motor)
