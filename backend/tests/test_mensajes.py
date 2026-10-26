"""
Taller de mensajes y consistencia (semana 12) — lado consumidor (Backend XALD).

Operación: sincronización de transacciones desde la cola del dispositivo (:syncqueue).
Contrato: docs/mensajes/README.md y docs/mensajes/transaccion-registrada.schema.json.

Cubre:
  - Duplicado: el mismo mensaje reenviado (mismo X-Correlation-Id, mismo id y fecha) no cambia
    el estado y se registra como duplicado, con el id de correlación en el log.
  - Fallo recuperable visto desde el consumidor: el cliente pierde la respuesta y reintenta;
    el reintento produce exactamente el mismo resultado que el primer envío.
  - Compatibilidad del contrato: un campo opcional nuevo se acepta (compatible); quitar un
    campo obligatorio se rechaza con 422 y una versión mayor desconocida con 400 (incompatibles).
  - Trazabilidad: X-Correlation-Id vuelve en la respuesta y aparece en cada línea de log.
"""
import json
import logging
import os
import uuid
from pathlib import Path

import pytest

os.environ.setdefault("XALD_API_KEY", "clave-de-pruebas-ci")

from fastapi.testclient import TestClient  # noqa: E402

from app import main  # noqa: E402

client = TestClient(main.app)
LLAVE = os.environ["XALD_API_KEY"]
EJEMPLOS = Path(__file__).resolve().parents[2] / "docs" / "mensajes" / "ejemplos"


def ejemplo(nombre, **cambios):
    datos = json.loads((EJEMPLOS / nombre).read_text(encoding="utf-8"))
    datos["id_transaccion"] = str(uuid.uuid4())  # aislar cada prueba del estado en memoria
    datos.update(cambios)
    return datos


def enviar(cuerpo, id_correlacion=None, version=None):
    cabeceras = {"X-API-Key": LLAVE}
    if id_correlacion:
        cabeceras["X-Correlation-Id"] = id_correlacion
    if version:
        cabeceras["X-Version-Esquema"] = version
    return client.post("/api/v1/transacciones", json=cuerpo, headers=cabeceras)


def contador(nombre):
    for linea in client.get("/metrics").text.splitlines():
        if linea.startswith(f"{nombre} "):
            return float(linea.split()[1])
    return 0.0


class CapturaEventos(logging.Handler):
    def __init__(self):
        super().__init__()
        self.eventos = []

    def emit(self, record):
        self.eventos.append({"evento": record.getMessage(), **getattr(record, "campos", {})})


@pytest.fixture
def eventos():
    captura = CapturaEventos()
    main.logger.addHandler(captura)
    yield captura.eventos
    main.logger.removeHandler(captura)


# --------------------------------------------------------------------------- duplicado

def test_duplicado_mismo_mensaje_reenviado_no_cambia_estado_y_queda_en_el_log(eventos):
    cuerpo = ejemplo("transaccion-registrada-v1.json")
    corr = str(uuid.uuid4())
    duplicados_antes = contador("xald_sync_duplicados_total")
    conflictos_antes = contador("xald_sync_conflictos_resueltos_total")

    primera = enviar(cuerpo, corr)
    reenvio = enviar(cuerpo, corr)

    assert primera.status_code == reenvio.status_code == 202
    assert main.ultima_version[cuerpo["id_transaccion"]].isoformat().startswith(cuerpo["fecha_transaccion"][:19])
    assert contador("xald_sync_duplicados_total") == duplicados_antes + 1
    assert contador("xald_sync_conflictos_resueltos_total") == conflictos_antes

    duplicado = [e for e in eventos if e["evento"] == "reenvio_duplicado_ignorado"]
    assert len(duplicado) == 1
    assert duplicado[0]["id_correlacion"] == corr
    assert duplicado[0]["id_transaccion"] == cuerpo["id_transaccion"]


# --------------------------------------------------------------------------- fallo recuperable

def test_fallo_recuperable_respuesta_perdida_el_reintento_da_el_mismo_resultado():
    """
    El cliente envía, el backend procesa, pero la respuesta se pierde (timeout en el cliente:
    ResultadoEnvio.FalloRecuperable en :syncqueue). El cliente reintenta con el MISMO mensaje.
    Lo que importa es que el reintento sea seguro: misma respuesta, mismo estado, sin conflicto.
    """
    cuerpo = ejemplo("transaccion-registrada-v1.json")
    corr = str(uuid.uuid4())
    recibidas_antes = contador('xald_transacciones_recibidas_total{origen_datos="SMS_REGEX"}')

    enviar(cuerpo, corr)  # respuesta "perdida"
    estado_tras_primer_envio = main.ultima_version[cuerpo["id_transaccion"]]
    reintento = enviar(cuerpo, corr)

    assert reintento.status_code == 202
    assert reintento.json()["estado"] == "ACEPTADO"
    assert reintento.headers["X-Correlation-Id"] == corr
    assert main.ultima_version[cuerpo["id_transaccion"]] == estado_tras_primer_envio
    # El contador de recepción sí cuenta las dos llegadas (límite declarado en arc42 §8).
    assert contador('xald_transacciones_recibidas_total{origen_datos="SMS_REGEX"}') == recibidas_antes + 2


# --------------------------------------------------------------------------- compatibilidad

def test_contrato_ejemplo_v1_se_acepta():
    assert enviar(ejemplo("transaccion-registrada-v1.json"), version="1").status_code == 202


def test_compatible_campo_opcional_nuevo_se_ignora():
    cuerpo = ejemplo("transaccion-registrada-v1-con-campo-nuevo.json")
    assert "nota_usuario" in cuerpo
    assert enviar(cuerpo).status_code == 202


def test_compatible_sin_cabecera_de_version_se_asume_v1():
    assert enviar(ejemplo("transaccion-registrada-v1.json")).status_code == 202


def test_incompatible_quitar_campo_obligatorio_se_rechaza_con_422():
    respuesta = enviar(ejemplo("transaccion-registrada-incompatible-sin-moneda.json"))
    assert respuesta.status_code == 422


def test_incompatible_version_mayor_desconocida_se_rechaza_con_400():
    respuesta = enviar(ejemplo("transaccion-registrada-v1.json"), version="2")
    assert respuesta.status_code == 400
    assert "no soportada" in respuesta.json()["detail"]


# --------------------------------------------------------------------------- trazabilidad

def test_trazabilidad_id_de_correlacion_en_respuesta_y_en_cada_linea_de_log(eventos):
    cuerpo = ejemplo("transaccion-registrada-v1.json")
    corr = "corr-" + str(uuid.uuid4())

    respuesta = enviar(cuerpo, corr)

    assert respuesta.headers["X-Correlation-Id"] == corr
    propios = [e for e in eventos if e.get("id_correlacion") == corr]
    assert [e["evento"] for e in propios] == ["transaccion_recibida", "peticion_http"]
    assert propios[0]["id_transaccion"] == cuerpo["id_transaccion"]


def test_trazabilidad_sin_cabecera_se_genera_un_id():
    respuesta = enviar(ejemplo("transaccion-registrada-v1.json"))
    uuid.UUID(respuesta.headers["X-Correlation-Id"])  # no lanza: es un UUID válido


def test_formato_de_linea_de_log_es_json_con_id_de_correlacion():
    registro = logging.LogRecord("xald", logging.INFO, __file__, 0, "transaccion_recibida", None, None)
    registro.campos = {"id_correlacion": "abc", "id_transaccion": "tx"}
    linea = json.loads(main.FormatoJSON().format(registro))
    assert linea["evento"] == "transaccion_recibida"
    assert linea["id_correlacion"] == "abc"
