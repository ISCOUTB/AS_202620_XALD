"""
Pruebas de la resolución de conflictos Last-Write-Wins (RT-05) del Backend XALD.
Escenario de calidad: ESC-05 · Resolución de conflictos al sincronizar.

Defecto que cubren: que una versión con fecha MÁS ANTIGUA sobrescriba a una MÁS
RECIENTE de la misma transacción. Si la comparación de fechas de
registrar_transaccion() (backend/app/main.py) se invierte o se elimina,
test_version_antigua_no_sobrescribe_a_la_reciente falla.
"""
import os
import uuid
from datetime import datetime, timedelta, timezone

os.environ.setdefault("XALD_API_KEY", "clave-de-pruebas-ci")

from fastapi.testclient import TestClient  # noqa: E402

from app import main  # noqa: E402

client = TestClient(main.app)
CABECERAS = {"X-API-Key": os.environ["XALD_API_KEY"]}
BASE = datetime(2026, 10, 4, 12, 0, tzinfo=timezone.utc)


def transaccion(id_transaccion, fecha):
    return {
        "id_transaccion": id_transaccion,
        "monto": 45900.0,
        "moneda": "COP",
        "comercio": "Tienda Ara",
        "categoria": "Supermercado",
        "fecha_transaccion": fecha.isoformat(),
        "origen_datos": "SMS_REGEX",
    }


def enviar(id_transaccion, fecha):
    return client.post("/api/v1/transacciones", json=transaccion(id_transaccion, fecha), headers=CABECERAS)


def conflictos_resueltos():
    for linea in client.get("/metrics").text.splitlines():
        if linea.startswith("xald_sync_conflictos_resueltos_total "):
            return float(linea.split()[1])
    return 0.0


def test_version_reciente_gana_si_llega_despues():
    id_tx = str(uuid.uuid4())
    vieja, nueva = BASE, BASE + timedelta(minutes=5)
    enviar(id_tx, vieja)
    enviar(id_tx, nueva)
    assert main.ultima_version[id_tx] == nueva


def test_version_antigua_no_sobrescribe_a_la_reciente():
    """Prueba que cubre el defecto: la versión vieja llega DESPUÉS y no debe ganar."""
    id_tx = str(uuid.uuid4())
    vieja, nueva = BASE, BASE + timedelta(minutes=5)
    enviar(id_tx, nueva)
    enviar(id_tx, vieja)
    assert main.ultima_version[id_tx] == nueva


def test_primera_version_no_cuenta_como_conflicto():
    antes = conflictos_resueltos()
    enviar(str(uuid.uuid4()), BASE)
    assert conflictos_resueltos() == antes


def test_medicion_esc05_30_transacciones_en_conflicto():
    """
    Medición de ESC-05 con su carga y su umbral (arc42 §10):
      carga  = 30 transacciones en conflicto simultáneo
      umbral = 100 % de conflictos resueltos y 0 transacciones perdidas
    La mitad de los conflictos llega con la versión vieja al final (peor caso).
    """
    carga = 30
    antes = conflictos_resueltos()
    aceptadas, correctas = 0, 0
    for i in range(carga):
        id_tx = str(uuid.uuid4())
        vieja, nueva = BASE + timedelta(seconds=i), BASE + timedelta(seconds=i, minutes=5)
        orden = (nueva, vieja) if i % 2 == 0 else (vieja, nueva)
        for fecha in orden:
            aceptadas += enviar(id_tx, fecha).status_code == 202
        correctas += main.ultima_version[id_tx] == nueva

    resueltos = conflictos_resueltos() - antes
    perdidas = 2 * carga - aceptadas
    porcentaje = 100 * correctas / carga
    print(f"\nESC-05 · carga={carga} · resueltos={int(resueltos)} · "
          f"ganó la versión más reciente={correctas}/{carga} ({porcentaje:.0f} %) · perdidas={perdidas}")

    assert perdidas == 0
    assert resueltos == carga
    assert porcentaje == 100
