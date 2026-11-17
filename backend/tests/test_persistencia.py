"""
Persistencia remota del Backend XALD (ADR-0015).

Cubre lo que el diccionario en memoria no garantizaba:
  - La transacción completa queda guardada en la base de datos, no solo su fecha.
  - Last-Write-Wins sobrevive a un reinicio del proceso (se simula descartando las conexiones
    y el estado de arranque, y leyendo de nuevo desde la base).
  - Una versión más reciente reemplaza los campos; una más vieja no toca nada.
  - GET /api/v1/transacciones/{id}: 200 con lo guardado, 404 si no existe, 401 sin llave.
  - Si la base de datos no responde, el backend contesta 503 (recuperable para :syncqueue).
"""
import os
import random
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.exc import OperationalError

from app import db, main, repositorio
from app.dtos import TransaccionDTO

client = TestClient(main.app)
CABECERAS = {"X-API-Key": os.environ["XALD_API_KEY"]}
BASE = datetime(2026, 11, 14, 15, 30, tzinfo=timezone.utc)


def cuerpo(id_transaccion, fecha, **cambios):
    datos = {
        "id_transaccion": id_transaccion,
        "monto": 45900.0,
        "moneda": "COP",
        "comercio": "Tienda Ara",
        "categoria": "Supermercado",
        "fecha_transaccion": fecha.isoformat(),
        "origen_datos": "SMS_REGEX",
    }
    datos.update(cambios)
    return datos


def enviar(datos):
    return client.post("/api/v1/transacciones", json=datos, headers=CABECERAS)


def consultar(id_transaccion, cabeceras=CABECERAS):
    return client.get(f"/api/v1/transacciones/{id_transaccion}", headers=cabeceras)


def test_la_transaccion_completa_queda_guardada():
    id_tx = str(uuid.uuid4())
    assert enviar(cuerpo(id_tx, BASE, monto=12500.5, comercio="Éxito Bocagrande")).status_code == 202

    guardada = consultar(id_tx)
    assert guardada.status_code == 200
    datos = guardada.json()
    assert datos["monto"] == 12500.5
    assert datos["comercio"] == "Éxito Bocagrande"
    assert datos["moneda"] == "COP"
    assert datos["origen_datos"] == "SMS_REGEX"
    assert datetime.fromisoformat(datos["fecha_transaccion"]) == BASE


def test_version_reciente_reemplaza_los_campos_y_la_vieja_no_los_toca():
    id_tx = str(uuid.uuid4())
    enviar(cuerpo(id_tx, BASE, categoria="Sin categoría"))
    enviar(cuerpo(id_tx, BASE + timedelta(minutes=5), categoria="Supermercado", origen_datos="AI_GEMINI"))
    enviar(cuerpo(id_tx, BASE + timedelta(minutes=1), categoria="Otra", monto=1.0))  # llega tarde y es vieja

    datos = consultar(id_tx).json()
    assert datos["categoria"] == "Supermercado"
    assert datos["origen_datos"] == "AI_GEMINI"
    assert datos["monto"] == 45900.0
    assert datetime.fromisoformat(datos["fecha_transaccion"]) == BASE + timedelta(minutes=5)


def test_lww_sobrevive_a_un_reinicio_del_proceso():
    """Antes el estado vivía en memoria y se perdía en cada despliegue (ADR-0011, riesgo)."""
    id_tx = str(uuid.uuid4())
    nueva = BASE + timedelta(minutes=10)
    enviar(cuerpo(id_tx, nueva))

    # "Reinicio": se cierran todas las conexiones y se olvida el estado de arranque.
    db.motor.dispose()
    main.tablas_listas = False

    # La versión vieja llega después del reinicio: no debe ganar.
    assert enviar(cuerpo(id_tx, BASE)).status_code == 202
    with db.FabricaSesiones() as sesion:
        assert repositorio.fecha_vigente(sesion, id_tx) == nueva


def test_consultar_inexistente_devuelve_404():
    assert consultar(str(uuid.uuid4())).status_code == 404


def test_consultar_sin_llave_devuelve_401():
    assert consultar(str(uuid.uuid4()), cabeceras={}).status_code == 401


def test_repositorio_clasifica_cada_caso():
    id_tx = str(uuid.uuid4())

    def dto(fecha):
        return TransaccionDTO(**cuerpo(id_tx, fecha))

    with db.FabricaSesiones() as sesion:
        assert repositorio.registrar(sesion, dto(BASE)) is repositorio.Resultado.NUEVA
        assert repositorio.registrar(sesion, dto(BASE)) is repositorio.Resultado.DUPLICADO
        assert repositorio.registrar(sesion, dto(BASE + timedelta(seconds=1))) is repositorio.Resultado.GANA_ENTRANTE
        assert repositorio.registrar(sesion, dto(BASE)) is repositorio.Resultado.GANA_EXISTENTE


def test_bd_caida_responde_503_para_que_el_cliente_reintente(monkeypatch):
    def falla(*_args, **_kwargs):
        raise OperationalError("SELECT 1", {}, Exception("conexión rechazada"))

    monkeypatch.setattr(repositorio, "registrar", falla)
    respuesta = enviar(cuerpo(str(uuid.uuid4()), BASE))
    assert respuesta.status_code == 503
    assert "xald_bd_errores_total" in client.get("/metrics").text


def test_fecha_con_otra_zona_horaria_se_compara_en_utc():
    """El mismo instante escrito en hora Colombia y en UTC es un duplicado, no un conflicto."""
    id_tx = str(uuid.uuid4())
    hora_colombia = BASE.astimezone(timezone(timedelta(hours=-5)))
    enviar(cuerpo(id_tx, BASE))
    respuesta = enviar(cuerpo(id_tx, hora_colombia))
    assert respuesta.json()["mensaje"].startswith("Transacción ya recibida")


@pytest.mark.skipif(db.motor.dialect.name != "postgresql", reason="La concurrencia real solo se prueba en Postgres")
def test_concurrencia_muchas_versiones_a_la_vez_gana_la_mas_reciente():
    """
    40 versiones de la misma transacción llegan en paralelo, en orden aleatorio, cada una por
    su propia conexión. Con SELECT ... FOR UPDATE y el reintento ante choque de clave primaria,
    ninguna falla y la que queda es la más reciente.
    """
    id_tx = str(uuid.uuid4())
    fechas = [BASE + timedelta(seconds=i) for i in range(40)]
    random.shuffle(fechas)

    def una_version(fecha):
        with db.FabricaSesiones() as sesion:
            return repositorio.registrar(sesion, TransaccionDTO(**cuerpo(id_tx, fecha)))

    with ThreadPoolExecutor(max_workers=10) as hilos:
        resultados = list(hilos.map(una_version, fechas))

    assert resultados.count(repositorio.Resultado.NUEVA) == 1
    with db.FabricaSesiones() as sesion:
        assert repositorio.fecha_vigente(sesion, id_tx) == max(fechas)
