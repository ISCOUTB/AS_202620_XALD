"""
Pruebas de la API de Sincronización Financiera - Proyecto XALD.

Cubre:
  - GET /health          -> 200 con {"status": "ok"}
  - GET /metrics         -> 200, formato Prometheus, expone el contador de ESC-05
  - POST /api/v1/transacciones sin X-API-Key -> 401
  - POST /api/v1/transacciones con X-API-Key válida -> 202
"""
import os

import pytest
from fastapi.testclient import TestClient

os.environ.setdefault("XALD_API_KEY", "clave-de-pruebas-ci")

from app.main import app  # noqa: E402  (el import depende de la env var anterior)

client = TestClient(app)

LLAVE_VALIDA = os.environ["XALD_API_KEY"]

TRANSACCION_VALIDA = {
    "id_transaccion": "8f14e45f-ceea-467e-9e2a-abc123456789",
    "monto": 45900.0,
    "moneda": "COP",
    "comercio": "Tienda Ara",
    "categoria": "Supermercado",
    "fecha_transaccion": "2026-09-27T10:15:00Z",
    "origen_datos": "SMS_REGEX",
}


def test_health_responde_200_status_ok():
    respuesta = client.get("/health")
    assert respuesta.status_code == 200
    assert respuesta.json() == {"status": "ok"}


def test_metrics_expone_formato_prometheus_y_contador_esc05():
    respuesta = client.get("/metrics")
    assert respuesta.status_code == 200
    assert "text/plain" in respuesta.headers["content-type"]
    cuerpo = respuesta.text
    assert "xald_sync_conflictos_resueltos_total" in cuerpo


def test_transacciones_sin_llave_devuelve_401():
    respuesta = client.post("/api/v1/transacciones", json=TRANSACCION_VALIDA)
    assert respuesta.status_code == 401


def test_transacciones_con_llave_valida_devuelve_202():
    respuesta = client.post(
        "/api/v1/transacciones",
        json=TRANSACCION_VALIDA,
        headers={"X-API-Key": LLAVE_VALIDA},
    )
    assert respuesta.status_code == 202
    cuerpo = respuesta.json()
    assert cuerpo["estado"] == "ACEPTADO"
    assert cuerpo["id_transaccion"] == TRANSACCION_VALIDA["id_transaccion"]


def test_transacciones_con_llave_incorrecta_devuelve_401():
    respuesta = client.post(
        "/api/v1/transacciones",
        json=TRANSACCION_VALIDA,
        headers={"X-API-Key": "llave-incorrecta"},
    )
    assert respuesta.status_code == 401
