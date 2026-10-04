"""
Medición de ESC-05 (Resolución de conflictos al sincronizar) contra el Backend XALD desplegado.

Carga y umbral tomados de arc42 §10:
  carga  = 30 transacciones en conflicto
  umbral = 100 % de conflictos resueltos y 0 transacciones perdidas

Uso (PowerShell, desde la raíz del repo):
  $env:XALD_API_KEY = "<la llave>"
  python backend/scripts/medir_esc05.py https://xald-backend.onrender.com

Solo usa la librería estándar de Python. La llave se lee del entorno: nunca va en el código.
"""
import json
import os
import statistics
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

URL = (sys.argv[1] if len(sys.argv) > 1 else "https://xald-backend.onrender.com").rstrip("/")
LLAVE = os.environ.get("XALD_API_KEY", "")
CARGA = 30
METRICA = "xald_sync_conflictos_resueltos_total"


def contador():
    with urllib.request.urlopen(f"{URL}/metrics", timeout=120) as r:
        for linea in r.read().decode().splitlines():
            if linea.startswith(METRICA + " "):
                return float(linea.split()[1])
    return 0.0


def enviar(id_tx, fecha):
    cuerpo = json.dumps({
        "id_transaccion": id_tx, "monto": 45900.0, "moneda": "COP", "comercio": "Tienda Ara",
        "categoria": "Supermercado", "fecha_transaccion": fecha.isoformat(), "origen_datos": "SMS_REGEX",
    }).encode()
    peticion = urllib.request.Request(f"{URL}/api/v1/transacciones", data=cuerpo, method="POST",
                                      headers={"Content-Type": "application/json", "X-API-Key": LLAVE})
    inicio = time.perf_counter()
    try:
        with urllib.request.urlopen(peticion, timeout=120) as r:
            codigo = r.status
    except urllib.error.HTTPError as e:
        codigo = e.code
    return codigo, (time.perf_counter() - inicio) * 1000


def main():
    if not LLAVE:
        sys.exit("Falta la variable de entorno XALD_API_KEY.")
    urllib.request.urlopen(f"{URL}/health", timeout=120).read()  # despierta el servicio si estaba dormido
    antes = contador()
    base = datetime.now(timezone.utc)
    aceptadas, latencias = 0, []
    for i in range(CARGA):
        id_tx = str(uuid.uuid4())
        vieja, nueva = base + timedelta(seconds=i), base + timedelta(seconds=i, minutes=5)
        for fecha in ((nueva, vieja) if i % 2 == 0 else (vieja, nueva)):
            codigo, ms = enviar(id_tx, fecha)
            aceptadas += codigo == 202
            latencias.append(ms)
    resueltos = contador() - antes
    perdidas = 2 * CARGA - aceptadas
    latencias.sort()
    p95 = latencias[int(len(latencias) * 0.95) - 1]
    cumple = perdidas == 0 and resueltos == CARGA
    print(f"Fecha (UTC):            {datetime.now(timezone.utc):%Y-%m-%d %H:%M:%S}")
    print(f"Sistema:                {URL}")
    print(f"Carga:                  {CARGA} transacciones en conflicto ({2 * CARGA} envíos)")
    print(f"Conflictos resueltos:   {int(resueltos)}/{CARGA} ({100 * resueltos / CARGA:.0f} %)   umbral: 100 %")
    print(f"Transacciones perdidas: {perdidas}   umbral: 0")
    print(f"Latencia por envío:     p50={statistics.median(latencias):.0f} ms · p95={p95:.0f} ms")
    print(f"Resultado:              {'CUMPLE' if cumple else 'NO CUMPLE'} el umbral de ESC-05")


if __name__ == "__main__":
    main()
