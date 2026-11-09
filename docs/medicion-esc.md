# Medición de ESC-05 · Resolución de conflictos al sincronizar

## 1. Escenario medido

Se mide **ESC-05**, declarado en [arc42 §10](arc42/10-Quality%20Requirements.md#esc-05--resolución-de-conflictos-al-sincronizar). Es el escenario de mayor riesgo técnico del árbol de utilidad (impacto Medio, riesgo Alto) y el único con métrica propia en el backend (`xald_sync_conflictos_resueltos_total`, [ADR-0010](adr/0010-observabilidad.md)).

| Elemento | Valor declarado en arc42 §10 |
|---|---|
| Estímulo | La misma transacción se modifica en dos dispositivos sin conexión y ambos sincronizan después |
| Respuesta esperada | Se aplica Last-Write-Wins ([ADR-0011](adr/0011-resolucion-conflictos-lww.md)): gana la marca de tiempo más reciente, sin duplicar ni perder |
| **Umbral** | **100 % de conflictos resueltos automáticamente y 0 transacciones distintas perdidas** |
| **Carga** | **30 transacciones en conflicto** |
| Herramienta declarada | Dos emuladores con relojes sincronizados |

**Desviación declarada:** la medición no usa dos emuladores. Un único cliente HTTP simula los dos dispositivos enviando, para el mismo `id_transaccion`, una versión vieja y una nueva, una tras otra y no de forma simultánea. Mide el motor LWW del backend, no la Sync Queue Android, que aún no tiene cliente HTTP ([ADR-0014](adr/0014-sincronizacion-asincrona-con-reintentos.md)). El umbral y la carga no se modificaron.

## 2. Procedimiento

**Herramientas**
- `backend/scripts/medir_esc05.py`: Python 3.12, solo biblioteca estándar (`urllib`). Mide contra un backend en ejecución.
- `backend/tests/test_lww.py::test_medicion_esc05_30_transacciones_en_conflicto`: pytest 9.0.3 con `TestClient`, sin red.

**Carga.** 30 transacciones con `id_transaccion` distinto (UUID), cada una enviada 2 veces: **60 `POST /api/v1/transacciones`**. En cada transacción, la versión nueva lleva una fecha 5 minutos posterior a la vieja. El orden de llegada alterna: en las pares llega primero la nueva y después la vieja (peor caso, la vieja llega al final); en las impares, al revés.

**Pasos de cada ejecución**
1. `GET /health` para despertar el servicio (en Render Free duerme tras 15 min sin tráfico).
2. Leer `xald_sync_conflictos_resueltos_total` desde `/metrics`.
3. Enviar los 60 POST.
4. Leer de nuevo el contador.
5. Calcular: `resueltos` = diferencia del contador; `perdidas` = 60 − respuestas 202.

**Criterio de cumplimiento:** `resueltos == 30` y `perdidas == 0`. La prueba pytest verifica además que la versión ganadora de cada transacción sea la más reciente (30/30).

**Repeticiones:** 3 ejecuciones contra el backend desplegado y 1 corrida de la prueba local. Cada ejecución usa UUID nuevos, por lo que son independientes.

**Límites del procedimiento:** el script remoto cuenta respuestas 202 y el contador, pero no puede leer el estado interno del backend; la comprobación de "gana la más reciente" solo la hace la prueba local. El envío es secuencial, no simultáneo.
