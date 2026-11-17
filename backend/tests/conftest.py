"""
Configuración común de las pruebas del backend.

Las pruebas usan SQLite en un archivo temporal, salvo que se defina XALD_TEST_DATABASE_URL
para correrlas contra un Postgres real (por ejemplo, uno local en Docker):

    XALD_TEST_DATABASE_URL=postgresql://xald:xald@localhost:5432/xald_test python -m pytest tests/

Las variables se fijan aquí, antes de que cualquier prueba importe app.main.
"""
import os
import tempfile
from pathlib import Path

os.environ.setdefault("XALD_API_KEY", "clave-de-pruebas-ci")

_directorio = tempfile.mkdtemp(prefix="xald-pruebas-")
os.environ["DATABASE_URL"] = os.environ.get(
    "XALD_TEST_DATABASE_URL", f"sqlite:///{Path(_directorio) / 'xald-pruebas.db'}"
)
