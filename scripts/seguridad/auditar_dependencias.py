#!/usr/bin/env python3
"""
Análisis de dependencias del pipeline (semana 13, docs/seguridad/analisis-dependencias.md).

Une los dos análisis del repositorio y los contrasta con las decisiones del equipo:

  backend   salida JSON de `pip-audit` (Python, incluye dependencias transitivas)
  android   árboles de `./gradlew :<módulo>:dependencies`, consultados contra OSV.dev

Cada hallazgo se busca en docs/seguridad/hallazgos-dependencias.json. Si un hallazgo no
tiene decisión registrada ("corregido" o "aceptado" con motivo), el job falla: un hallazgo
nuevo no puede pasar sin que alguien del equipo lo revise.

Uso (desde la raíz del repositorio, donde el CI deja los reportes):
  python scripts/seguridad/auditar_dependencias.py backend    # lee pip-audit.json
  python scripts/seguridad/auditar_dependencias.py android    # lee arbol-*.txt

Los nombres de los reportes son fijos y se buscan solo dentro del repositorio: el script no
recibe rutas por argumento (evita path traversal, regla de SonarCloud).

Solo usa la biblioteca estándar (mismo criterio que backend/scripts/medir_esc05.py).
"""
import json
import os
import sys
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
DECISIONES = RAIZ / "docs" / "seguridad" / "hallazgos-dependencias.json"
OSV_LOTE = "https://api.osv.dev/v1/querybatch"
REPORTE_PIP = RAIZ / "pip-audit.json"
PATRON_ARBOLES = "arbol-*.txt"
CARACTERES_COORDENADA = set("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-")


def cargar_decisiones():
    if not DECISIONES.exists():
        return {}
    datos = json.loads(DECISIONES.read_text(encoding="utf-8"))
    return {d["id"]: d for d in datos.get("decisiones", [])}


def coordenada_de_linea(linea):
    """Extrae (grupo:artefacto, versión resuelta) de una línea de `gradle dependencies`.

    Ejemplos:  +--- androidx.core:core-ktx:1.10.1 -> 1.13.0 (*)
               |    \\--- androidx.compose.ui:ui -> 1.8.0        (versión puesta por el BOM)
    """
    if "project :" in linea:
        return None  # dependencia entre módulos del propio repo
    texto = linea.lstrip(" |\\+-")
    izquierda, _, derecha = texto.partition(" -> ")
    partes = izquierda.split(" ")[0].split(":")
    if len(partes) < 2 or not set(partes[0] + partes[1]) <= CARACTERES_COORDENADA:
        return None
    if derecha:
        version = derecha.split(" ")[0]
    else:
        version = partes[2] if len(partes) > 2 else ""
    if not version or version.startswith("{") or version == "FAILED":
        return None
    return f"{partes[0]}:{partes[1]}", version


def coordenadas_gradle():
    """Conjunto de dependencias Maven resueltas en los árboles arbol-*.txt de la raíz del repo."""
    encontradas = set()
    for ruta in sorted(RAIZ.glob(PATRON_ARBOLES)):
        for linea in ruta.read_text(encoding="utf-8", errors="replace").splitlines():
            coordenada = coordenada_de_linea(linea)
            if coordenada:
                encontradas.add(coordenada)
    return sorted(encontradas)


def consultar_osv(coordenadas):
    hallazgos = []
    for inicio in range(0, len(coordenadas), 500):
        lote = coordenadas[inicio:inicio + 500]
        cuerpo = json.dumps({"queries": [
            {"package": {"name": n, "ecosystem": "Maven"}, "version": v} for n, v in lote
        ]}).encode()
        pet = urllib.request.Request(OSV_LOTE, data=cuerpo, headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(pet, timeout=60) as r:
            resultados = json.load(r)["results"]
        for (nombre, version), res in zip(lote, resultados):
            for vuln in res.get("vulns", []):
                hallazgos.append({"paquete": nombre, "version": version, "id": vuln["id"], "corregida_en": "ver OSV"})
    return hallazgos


def hallazgos_pip():
    datos = json.loads(REPORTE_PIP.read_text(encoding="utf-8"))
    hallazgos, vistos = [], set()
    for dep in datos.get("dependencies", []):
        for vuln in dep.get("vulns", []):
            # pip-audit puede repetir un registro (una vez por cada fuente); se cuenta una sola vez.
            if (dep["name"], dep["version"], vuln["id"]) in vistos:
                continue
            vistos.add((dep["name"], dep["version"], vuln["id"]))
            hallazgos.append({
                "paquete": dep["name"],
                "version": dep["version"],
                "id": vuln["id"],
                "alias": vuln.get("aliases", []),
                "corregida_en": ", ".join(vuln.get("fix_versions", [])) or "sin corrección publicada",
            })
    return hallazgos


def decidir(hallazgos, decisiones, origen):
    sin_decision = []
    lineas = [f"### Análisis de dependencias — {origen}", ""]
    if not hallazgos:
        lineas.append("Sin vulnerabilidades conocidas en las versiones resueltas.")
    else:
        lineas += ["| Paquete | Versión | Vulnerabilidad | Corregida en | Decisión |", "|---|---|---|---|---|"]
        for h in hallazgos:
            ids = [h["id"], *h.get("alias", [])]
            decision = next((decisiones[i] for i in ids if i in decisiones), None)
            if decision and decision.get("estado") == "aceptado":
                texto = f"Aceptado: {decision.get('motivo', '')}"
            elif decision and decision.get("estado") == "corregido":
                # Si sigue apareciendo, la corrección no llegó a esta versión: se trata como nuevo.
                texto = "**Marcado corregido pero sigue presente**"
                sin_decision.append(h)
            else:
                texto = "**SIN DECISIÓN**"
                sin_decision.append(h)
            lineas.append(f"| {h['paquete']} | {h['version']} | [{h['id']}](https://osv.dev/vulnerability/{h['id']}) | {h['corregida_en']} | {texto} |")
    reporte = "\n".join(lineas) + "\n"
    print(reporte)
    resumen = os.environ.get("GITHUB_STEP_SUMMARY")
    if resumen:
        with open(resumen, "a", encoding="utf-8") as f:
            f.write(reporte)
    return sin_decision


def main(argv):
    if len(argv) != 2 or argv[1] not in ("backend", "android"):
        print(__doc__)
        return 2
    decisiones = cargar_decisiones()
    if argv[1] == "backend":
        hallazgos = hallazgos_pip()
        origen = "backend (pip-audit)"
    else:
        coordenadas = coordenadas_gradle()
        print(f"{len(coordenadas)} dependencias Maven resueltas en los módulos Android.")
        hallazgos = consultar_osv(coordenadas)
        origen = "Android (Gradle + OSV.dev)"
    pendientes = decidir(hallazgos, decisiones, origen)
    if pendientes:
        print(f"::error::{len(pendientes)} hallazgo(s) sin decisión registrada en {DECISIONES.relative_to(RAIZ)}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
