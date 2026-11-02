"""
Pruebas de las mitigaciones de seguridad de la semana 13 en el Backend XALD.
Modelo de amenazas: docs/seguridad/modelo-amenazas.md

  - AM-01: rotación de llaves (ADR-0015). Varias llaves vigentes a la vez en
    XALD_API_KEYS; una llave retirada de la lista deja de servir.
  - AM-07: una fecha_transaccion futura (más allá de 5 min) se rechaza con 422,
    para que nadie gane el Last-Write-Wins (ADR-0011) con una fecha inventada.
  - AM-08: un cuerpo mayor a 16 KB se rechaza con 413 antes de parsearlo.

Cada prueba falla si se quita la mitigación correspondiente de backend/app/main.py.
"""
import os
import uuid
from datetime import datetime, timedelta, timezone

os.environ.setdefault("XALD_API_KEY", "clave-de-pruebas-ci")

from fastapi.testclient import TestClient  # noqa: E402

from app import main  # noqa: E402

client = TestClient(main.app)
URL = "/api/v1/transacciones"


def transaccion(fecha=None, comercio="Tienda Ara"):
    return {
        "id_transaccion": str(uuid.uuid4()),
        "monto": 45900.0,
        "moneda": "COP",
        "comercio": comercio,
        "categoria": "Supermercado",
        "fecha_transaccion": (fecha or datetime(2026, 10, 4, 12, 0, tzinfo=timezone.utc)).isoformat(),
        "origen_datos": "SMS_REGEX",
    }


# --- AM-01: rotación de llaves -------------------------------------------------

def test_am01_acepta_cualquiera_de_las_llaves_vigentes(monkeypatch):
    monkeypatch.setenv("XALD_API_KEYS", "llave-vieja,llave-nueva")
    for llave in ("llave-vieja", "llave-nueva"):
        r = client.post(URL, json=transaccion(), headers={"X-API-Key": llave})
        assert r.status_code == 202, llave


def test_am01_llave_revocada_deja_de_servir(monkeypatch):
    monkeypatch.setenv("XALD_API_KEYS", "llave-nueva")
    monkeypatch.setenv("XALD_API_KEY", "")
    r = client.post(URL, json=transaccion(), headers={"X-API-Key": "llave-vieja"})
    assert r.status_code == 401


def test_am01_sin_llaves_configuradas_rechaza_todo(monkeypatch):
    monkeypatch.setenv("XALD_API_KEYS", "")
    monkeypatch.setenv("XALD_API_KEY", "")
    r = client.post(URL, json=transaccion(), headers={"X-API-Key": ""})
    assert r.status_code == 401


# --- AM-07: fecha futura para ganar el LWW -------------------------------------

def test_am07_fecha_futura_se_rechaza():
    futura = datetime.now(timezone.utc) + timedelta(days=365)
    r = client.post(URL, json=transaccion(futura), headers={"X-API-Key": os.environ["XALD_API_KEY"]})
    assert r.status_code == 422


def test_am07_fecha_dentro_de_la_tolerancia_se_acepta():
    casi_ahora = datetime.now(timezone.utc) + timedelta(minutes=2)
    r = client.post(URL, json=transaccion(casi_ahora), headers={"X-API-Key": os.environ["XALD_API_KEY"]})
    assert r.status_code == 202


def test_am07_fecha_futura_no_entra_al_estado_lww():
    t = transaccion(datetime.now(timezone.utc) + timedelta(days=30))
    client.post(URL, json=t, headers={"X-API-Key": os.environ["XALD_API_KEY"]})
    assert t["id_transaccion"] not in main.ultima_version


# --- AM-08: cuerpo sin límite --------------------------------------------------

def test_am08_cuerpo_demasiado_grande_devuelve_413():
    enorme = transaccion(comercio="A" * (20 * 1024))
    r = client.post(URL, json=enorme, headers={"X-API-Key": os.environ["XALD_API_KEY"]})
    assert r.status_code == 413
