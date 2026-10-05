## Auditoría de erosión arquitectónica

- **Fecha:** 2026-10-04 · **Commit auditado:** `<hash>`
- **Método:** análisis estático (imports entre módulos, `build.gradle.kts`, visibilidad de clases, contraste con ADR y matriz de propiedad). Continúa la [auditoría de la Semana 6](auditoriaviolaciones-semana6.md).

### Reglas verificadas

| Regla | Origen |
|---|---|
| **R1.** Los módulos de dominio (`:parser`, `:aigemini`, `:corefinanciero`, `:syncqueue`) no se conocen entre sí. Solo `:app` orquesta. Excepción: `:syncqueue` consume `PayloadSincronizacionDTO`. | ADR-0006, ADR-0007, auditoría S6 |
| **R2.** Las implementaciones concretas son `internal`. | ADR-0007 |
| **R3.** Solo `:corefinanciero` maneja `TransaccionEntidad`. | ADR-0007, `ownership-matrix.md` |
| **R4.** Solo `:app` depende de los cuatro módulos de dominio. | ADR-0007 |
| **R5.** En el backend, `dtos.py` no depende de `main.py`. | Regla adicional |
| **R6.** Lo que la documentación afirma que existe, existe en el código. | Consistencia doc ↔ código |

### Resultados

| Regla | Verificación | Resultado |
|---|---|---|
| R1 | Imports en `src/main`: `:parser` 0, `:aigemini` 0, `:corefinanciero` 0, `:syncqueue` 1 (el DTO permitido). Dependencias `project(...)`: solo `:app` hacia los 4 y `:syncqueue → :corefinanciero`. | ✅ Cumple |
| R2 | `ParseoSms`, `GestorCoreFinanciero`, `ColaSincronizacion` y `CategorizadorGemini` son `internal`. | ✅ Cumple |
| R3 | `TransaccionEntidad` no se usa fuera de `:corefinanciero`. | ✅ En uso, ⚠️ ver H-1 |
| R4 | Solo `:app/build.gradle.kts` declara los cuatro módulos. | ✅ Cumple |
| R5 | `main.py` importa `app.dtos`; `dtos.py` no importa nada del proyecto. | ✅ Cumple |
| R6 | Contraste con ADR-0004, KDoc de `TransaccionEntidad` y `ownership-matrix.md`. | ⚠️ Ver H-3 y H-4 |

**Violaciones activas de dependencias: 0.** Ninguna de V-01 a V-07 reapareció.

### Hallazgos (erosión latente)

| ID | Severidad | Hallazgo | Acción propuesta | Estado |
|---|---|---|---|---|
| **H-1** | Media | `TransaccionEntidad` es pública y sale por `InformacionFinanciera.obtenerUltimaTransaccion()`. Nadie la usa fuera del módulo, pero contradice la intención de R3. | Devolver un DTO de lectura y marcar la entidad `internal`. | Abierto |
| **H-2** | Media | `ValidacionModulosTest` cubre solo 3 de las 11 direcciones de dependencia prohibidas, solo mira imports (no `build.gradle.kts`) y pasa en verde si no encuentra la carpeta del módulo (`if (dir.exists())`). | Generalizar con una matriz de pares prohibidos y fallar si falta una carpeta. | Abierto |
| **H-3** | Alta | La documentación describe Room con AES-256 y TLS 1.3 en la cola, pero el código usa `mutableListOf` en memoria y no hay cliente HTTP. | Declarar como pendiente en `aspectos.md` (A-02, A-04) y en arc42 §11. | Abierto |
| **H-4** | Baja | `ProcesarNotificacionUseCase.kt` está fuera de la carpeta de su paquete y el corte vertical no lo invoca (repite los pasos a mano). `ownership-matrix.md` cita una ruta incorrecta para `TransaccionEntidad.kt`. | Mover el archivo, hacer que el test llame al use case y corregir la ruta. | Abierto |
| **H-5** | Baja | `:syncqueue` ve toda la API pública de `:corefinanciero`, no solo el DTO. | Extraer el DTO a un módulo de contratos o documentar el riesgo en el ADR-0007. | Abierto |

**Conclusión:** el aislamiento modular se mantiene en el código. El punto de mayor peso es H-3. Esta auditoría solo registra los hallazgos y no los corrige.

---

## Dependencias verificadas en su registro (PyPI)

- **Alcance:** las 6 dependencias de `backend/requirements.txt`.
- **Fecha de la verificación:** `<AAAA-MM-DD>` · **Responsable:** `<nombre>`

**Por qué se verifica:** parte del código se redactó con ayuda de IA (ver [`ia.md`](ia.md)), y estas herramientas pueden sugerir paquetes que no existen o con nombre parecido al real (*typosquatting*). Se comprobó que cada paquete existe en PyPI con su nombre exacto, que es el proyecto oficial y que se usa de verdad en el código.

| # | Paquete | Restricción | Uso | Dónde | PyPI | Versión | Verificado |
|---|---|---|---|---|---|---|---|
| 1 | `fastapi` | `>=0.115.0` | Framework de la API | `backend/app/main.py` | [enlace](https://pypi.org/project/fastapi/) | `<versión>` | ✅ |
| 2 | `uvicorn[standard]` | `>=0.30.0` | Servidor ASGI | `backend/Dockerfile` | [enlace](https://pypi.org/project/uvicorn/) | `<versión>` | ✅ |
| 3 | `pydantic` | `>=2.9.0` | Validación de DTO | `backend/app/dtos.py` | [enlace](https://pypi.org/project/pydantic/) | `<versión>` | ✅ |
| 4 | `prometheus-client` | `>=0.20.0` | Métricas `/metrics` (ESC-05) | `backend/app/main.py` | [enlace](https://pypi.org/project/prometheus-client/) | `<versión>` | ✅ |
| 5 | `pytest` | `>=8.3.0` | Pruebas | `backend/tests/`, CI | [enlace](https://pypi.org/project/pytest/) | `<versión>` | ✅ |
| 6 | `httpx` | `>=0.27.0` | Requerido por `TestClient` | `backend/tests/` | [enlace](https://pypi.org/project/httpx/) | `<versión>` | ✅ |

**Conclusión:** las 6 dependencias existen en PyPI con su nombre exacto, son proyectos reales y cada una tiene un uso identificado en el repositorio.

**Observaciones:**

| ID | Observación | Acción propuesta |
|---|---|---|
| D-1 | `pytest` y `httpx` se instalan también en la imagen de producción, porque el `Dockerfile` usa el mismo `requirements.txt`. | Separar `requirements-dev.txt`. |
| D-2 | Todas las restricciones son `>=` y no hay archivo de bloqueo, así que los builds no son reproducibles. | Fijar versiones (`==`) o usar archivo de bloqueo con hashes. |
| D-3 | El CI instala `@redocly/cli@1.25.0` desde npm, fuera de estas 6. Está fijado y usa `--ignore-scripts`. | Mantener. |

---

## Barrido de credenciales

- **Fecha:** 2026-10-04 · **Commit barrido:** `<hash>`
- **Resultado:** el árbol de archivos no contiene credenciales reales. Hay **6 alertas del escáner pendientes de clasificar**.
- **Alcance:** todos los archivos del repositorio, excepto binarios. **No cubre el historial de Git**, que debe revisarse con `gitleaks` o con las alertas de *secret scanning* de GitHub.

### Qué se buscó

| Categoría | Resultado |
|---|---|
| Llaves de Google/Gemini (`AIza...`), AWS (`AKIA...`), tokens de GitHub (`ghp_`, `github_pat_`), Slack (`xox...`), llaves tipo `sk-...` | 0 |
| Llaves privadas (`-----BEGIN ... PRIVATE KEY-----`) | 0 |
| Deploy hooks y IDs de Render, webhooks de Slack/Discord | 0 |
| Asignaciones `password/secret/token/api_key = "valor"` | 1 *placeholder* (ver abajo) |
| Archivos sensibles (`.env`, `*.jks`, `*.keystore`, `google-services.json`, `*.pem`, `*.p12`) | 0 presentes |

### Valores que un escáner puede marcar (no son credenciales)

| Dónde | Valor | Qué es |
|---|---|---|
| `backend/tests/test_api.py:15` y `test_lww.py:14` | `"clave-de-pruebas-ci"` | Llave solo de pruebas (`setdefault`). No existe en Render. |
| `docs/guia-ejecucion-local.md:60` | `dev-key-123` | Ejemplo para entorno local. |
| `backend/scripts/medir_esc05.py:9` | `"<la llave>"` | *Placeholder*. La llave real se lee del entorno. |
| `.env.example:2` | `XALD_API_KEY=` | Plantilla sin valor. |

### Dónde viven los secretos reales

| Secreto | Dónde se guarda | Evidencia |
|---|---|---|
| `XALD_API_KEY` (producción) | Render → Environment | `render.yaml` la declara con `sync: false`; se lee con `os.environ.get(...)` en `main.py` |
| `XALD_API_KEY` (local) | `.env`, ignorado por Git | `.gitignore` incluye `.env` |
| `RENDER_DEPLOY_HOOK` | GitHub → Settings → Secrets → Actions | `${{ secrets.RENDER_DEPLOY_HOOK }}` en `ci.yml` |
| Llave de Gemini | No existe todavía (integración aplazada) | [ADR-0012](adr/0012-aplazamiento-integracion-gemini.md) |

**Comportamiento seguro verificado:** la llave se compara con `hmac.compare_digest`; si `XALD_API_KEY` no está definida, el backend rechaza todas las peticiones con 401 (falla cerrada); la app Android no tiene llaves ni URLs escritas en el código y usa `usesCleartextTraffic="false"`.

### Las 6 alertas pendientes

| # | Herramienta | Archivo / línea | Qué detectó | ¿Real o falso positivo? | Justificación y acción | Estado |
|---|---|---|---|---|---|---|
| 1 | `<completar>` | `<completar>` | `<completar>` | `<completar>` | `<completar>` | `<completar>` |
| 2 | | | | | | |
| 3 | | | | | | |
| 4 | | | | | | |
| 5 | | | | | | |
| 6 | | | | | | |

- **Falso positivo:** se cierra en la herramienta con la razón y se anota la justificación en la tabla.
- **Credencial real:** hay que **rotarla** (generar una nueva `XALD_API_KEY`, cargarla en Render y redesplegar), no solo borrarla del archivo, porque queda en el historial.

### Hallazgos menores

| ID | Hallazgo | Acción propuesta |
|---|---|---|
| C-1 | `backend/app/__pycache__/*.pyc` está versionado aunque `.gitignore` lo excluye. | `git rm -r --cached backend/app/__pycache__` |
| C-2 | La llave de pruebas está escrita en dos archivos. | Opcional: tomarla de una variable del CI. |
| C-3 | El barrido no cubrió el historial de Git. | Ejecutar `gitleaks detect --source . --redact` y adjuntar el resultado. |
