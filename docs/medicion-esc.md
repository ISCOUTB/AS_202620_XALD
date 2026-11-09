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
