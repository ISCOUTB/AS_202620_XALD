#!/usr/bin/env python3
"""
Análisis de dependencias del pipeline (semana 13, docs/seguridad/analisis-dependencias.md).

Une los dos análisis del repositorio y los contrasta con las decisiones del equipo:

  backend   salida JSON de `pip-audit` (Python, incluye dependencias transitivas)
  android   árboles de `./gradlew :<módulo>:dependencies`, consultados contra OSV.dev

Cada hallazgo se busca en docs/seguridad/hallazgos-dependencias.json. Si un hallazgo no
tiene decisión registrada ("corregido" o "aceptado" con motivo), el job falla: un hallazgo
nuevo no puede pasar sin que alguien del equipo lo revise.

Uso:
  python scripts/seguridad/auditar_dependencias.py backend pip-audit.json
  python scripts/seguridad/auditar_dependencias.py android arbol-app.txt arbol-parser.txt ...

Solo usa la biblioteca estándar (mismo criterio que backend/scripts/medir_esc05.py).
"""
import json
import os
import re
import sys
import urllib.request
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]
DECISIONES = RAIZ / "docs" / "seguridad" / "hallazgos-dependencias.json"
OSV_LOTE = "https://api.osv.dev/v1/querybatch"

# Línea de `gradle dependencies`:  +--- androidx.core:core-ktx:1.10.1 -> 1.13.0 (*)
LINEA_GRADLE = re.compile(
    r"^[\s|\\+-]*([A-Za-z0-9_.\-]+):([A-Za-z0-9_.\-]+):([^\s:]+)(?:\s*->\s*([^\s(]+))?"
)


def cargar_decisiones():
    if not DECISIONES.exists():
        return {}
    datos = json.loads(DECISIONES.read_text(encoding="utf-8"))
    return {d["id"]: d for d in datos.get("decisiones", [])}


def coordenadas_gradle(rutas):
    """Devuelve el conjunto de (grupo:artefacto, versión resuelta) de los árboles de Gradle."""
    encontradas = set()
    for ruta in rutas:
        for linea in Path(ruta).read_text(encoding="utf-8", errors="replace").splitlines():
            if "project :" in linea:
                continue  # dependencia entre módulos del propio repo
            m = LINEA_GRADLE.match(linea)
            if not m:
                continue
            grupo, artefacto, version, resuelta = m.groups()
            flecha = re.search(r"->\s*([^\s(]+)", linea)
            version = resuelta or (flecha.group(1) if flecha else version)
            if version in ("{strictly", "FAILED") or version.startswith("{"):
                continue
            encontradas.add((f"{grupo}:{artefacto}", version))
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


def hallazgos_pip(ruta_json):
    datos = json.loads(Path(ruta_json).read_text(encoding="utf-8"))
    hallazgos = []
    for dep in datos.get("dependencies", []):
        for vuln in dep.get("vulns", []):
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
    if len(argv) < 3 or argv[1] not in ("backend", "android"):
        print(__doc__)
        return 2
    decisiones = cargar_decisiones()
    if argv[1] == "backend":
        hallazgos = hallazgos_pip(argv[2])
        origen = "backend (pip-audit)"
    else:
        coordenadas = coordenadas_gradle(argv[2:])
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
