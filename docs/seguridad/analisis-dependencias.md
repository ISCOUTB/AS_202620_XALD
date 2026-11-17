# Análisis de dependencias — resultado y decisiones

* *Semana:* 13 (tercer corte)
* *Amenaza relacionada:* AM-04 ([modelo de amenazas](modelo-amenazas.md))
* *Responsable:* Axel Ruiz

> No es el análisis estático del código, que ya corre en el pipeline desde el segundo corte (SonarQube).
> Este análisis revisa las **versiones de las librerías de terceros** contra bases de vulnerabilidades
> publicadas.

## 1. Herramientas en el pipeline

| Parte | Herramienta | Dónde corre | Qué cubre |
|---|---|---|---|
| Backend (Python) | `pip-audit` 2.10.1 (base PyPI / OSV) | Job `dependency-audit` de `.github/workflows/ci.yml`, en cada push y PR a `master` y `experimental` | Todo el entorno instalado del backend, **incluidas las transitivas** (Starlette, h11, anyio…), no solo las fijadas en `requirements.txt` |
| App Android (Gradle) | Árbol resuelto con `./gradlew :<módulo>:dependencies` y consulta a la API de OSV.dev (`scripts/seguridad/auditar_dependencias.py`) | El mismo job, para `app`, `parser`, `corefinanciero`, `syncqueue` y `aigemini` | Versiones resueltas por Gradle (después de que el BOM y las transitivas elijan versión) |
| Ambas | Dependabot (`.github/dependabot.yml`) | GitHub, semanal | Abre un PR cuando sale una versión nueva (pip, Gradle, Docker, GitHub Actions) |

**Cómo se fuerza la revisión.** El script compara cada hallazgo con
[`hallazgos-dependencias.json`](hallazgos-dependencias.json), donde el equipo registra su decisión
(`corregido` o `aceptado` con motivo). Un hallazgo sin decisión, o uno marcado `corregido` que sigue
apareciendo, hace fallar el job. Como `deploy-backend` depende de `dependency-audit`, **no se despliega
con una vulnerabilidad sin revisar**. El reporte queda en el resumen del run y como artefacto
`analisis-dependencias`.

## 2. Resultado de la primera ejecución (antes de corregir)

`pip-audit -r backend/requirement.lock` sobre el commit `1eb5544`, el 1 de noviembre de 2026:
**16 registros, 8 vulnerabilidades distintas en 2 paquetes.**

| Paquete | Versión | Vulnerabilidad | Qué permite | ¿Nos afecta? | Decisión | Corregida en |
|---|---|---|---|---|---|---|
| starlette (transitiva de FastAPI) | 0.38.6 | PYSEC-2026-1943 / CVE-2024-47874 | DoS: campos `multipart/form-data` sin límite de tamaño | No usamos formularios, pero el parser se activa con el `Content-Type` que mande el cliente | **Corregido** | 1.3.1 |
| starlette | 0.38.6 | PYSEC-2026-1941 / CVE-2025-54121 | Bloqueo del hilo principal al volcar archivos grandes a disco | Mismo motivo | **Corregido** | 1.3.1 |
| starlette | 0.38.6 | PYSEC-2026-161 / CVE-2026-48710 | Inyección en la cabecera `Host` al reconstruir la URL | Sí: el servicio es público en Render | **Corregido** | 1.3.1 |
| starlette | 0.38.6 | PYSEC-2026-248 / CVE-2026-54282 | Ruta que no empieza por `/` altera `request.url` | Sí: usamos `request.url.path` en los logs | **Corregido** | 1.3.1 |
| starlette | 0.38.6 | PYSEC-2026-249 / CVE-2026-54283 | Límites de `request.form()` ignorados en `x-www-form-urlencoded` | No usamos formularios | **Corregido** | 1.3.1 |
| starlette | 0.38.6 | PYSEC-2026-2280 / CVE-2026-48817 | Despacho de métodos HTTP arbitrarios en `HTTPEndpoint` | No usamos `HTTPEndpoint` | **Corregido** (sale gratis con la actualización) | 1.3.1 |
| starlette | 0.38.6 | PYSEC-2026-2281 / CVE-2026-48818 | Rutas UNC en `StaticFiles` sobre Windows | No servimos archivos estáticos ni corremos en Windows | **Corregido** (sale gratis con la actualización) | 1.3.1 |
| pytest | 8.3.0 | PYSEC-2026-1845 / CVE-2025-71176 | Directorio temporal predecible en UNIX | Solo en CI, no en producción | **Corregido** | 9.0.3 |

**Qué se hizo.** FastAPI 0.115.0 → 0.162.0 (exige `starlette>=0.46.0`), Starlette fijado
explícitamente en 1.3.1 y pytest 8.3.0 → 9.0.3. Se cambió en los cuatro lugares que fijan versiones:
`backend/requirements.txt`, `backend/requirement.lock`, `backend/Dockerfile` y `.github/workflows/ci.yml`.

**Verificación de la corrección.**

* `pip-audit` sobre el entorno completo (32 paquetes, con transitivas): `No known vulnerabilities found`.
* Las 28 pruebas del backend pasan con las versiones nuevas (21 anteriores y 7 nuevas de `test_seguridad.py`).
* La lógica de decisiones se probó contra la salida anterior: con las versiones viejas el script marca
  los 16 registros como «Marcado corregido pero sigue presente» y falla.

## 3. Ejecución en el pipeline

| Run | Commit | Resultado | Qué pasó |
|---|---|---|---|
| [41373988671](https://github.com/ISCOUTB/AS_202620_XALD/actions/runs/41373988671) | `c0aa731` (PR #27) | **Falló, y era lo esperado** | `pip-audit` encontró **6 vulnerabilidades en `pip` 25.0.1**, el pip que trae el entorno virtual y también la imagen `python:3.12-slim` del backend. No tenían decisión registrada, así que el job falló y `deploy-backend` no corrió. La revisión local no las vio porque el Python 3.13 de la máquina de desarrollo trae un pip más nuevo. |
| [41374429361](https://github.com/ISCOUTB/AS_202620_XALD/actions/runs/41374429361) | `fda2f16` (PR #27) | **Verde** | Con `pip` 26.2.2 en el CI y en el `Dockerfile`: backend sin vulnerabilidades conocidas (32 paquetes, con transitivas) y **151 dependencias Maven** resueltas en los cinco módulos Android, todas sin vulnerabilidades en OSV.dev. Las 6 de pip quedan como `corregido`. |

**Hallazgos encontrados por el pipeline y decisión:**

| Paquete | Versión | Vulnerabilidad | Qué permite | ¿Nos afecta? | Decisión | Corregida en |
|---|---|---|---|---|---|---|
| pip | 25.0.1 | PYSEC-2026-1795 / CVE-2025-8869 | Enlaces simbólicos fuera del directorio al extraer un tar | En la imagen, solo si alguien instala paquetes dentro del contenedor | **Corregido** | 26.2.2 |
| pip | 25.0.1 | PYSEC-2026-1796 / CVE-2026-1703 | *Path traversal* al extraer un wheel malicioso | Igual | **Corregido** | 26.2.2 |
| pip | 25.0.1 | PYSEC-2026-2875 / CVE-2026-3219 | Confusión entre archivos tar y ZIP concatenados | Igual | **Corregido** | 26.2.2 |
| pip | 25.0.1 | PYSEC-2026-2876 / CVE-2026-6357 | La comprobación de actualización importa módulos después de instalar | Igual | **Corregido** | 26.2.2 |
| pip | 25.0.1 | PYSEC-2026-196 / CVE-2026-8643 | `console_scripts` resueltos fuera del directorio de instalación | Igual | **Corregido** | 26.2.2 |
| pip | 25.0.1 | PYSEC-2026-3721 / CVE-2026-13346 | URLs doblemente codificadas instalan archivos en rutas arbitrarias | Sí, durante el `docker build`, que instala desde PyPI | **Corregido** | 26.2.2 |

El script también se ajustó para contar una sola vez cada hallazgo: `pip-audit` repetía algunos
registros, una vez por cada fuente.

Si el job reporta hallazgos en la app Android, cada uno se agrega a `hallazgos-dependencias.json` con su
decisión y a esta tabla.
