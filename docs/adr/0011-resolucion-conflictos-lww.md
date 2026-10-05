# ADR-0011: Implementación de Resolución de Conflictos Last-Write-Wins (LWW)

* *Estatus:* Decidido
* *Fecha:* Octubre 2026
* *Corte:* Tercer corte — Semana 9

## Contexto y Problema

El [ADR-0001](0001-patron-offline-first.md) ya había decidido adoptar Last-Write-Wins como estrategia de resolución de conflictos para la arquitectura Offline-First (RT-05), pero esa decisión era de alto nivel: no existía todavía una implementación real que comparara versiones en conflicto. El equipo necesitaba definir *cómo* implementar esa comparación en el Backend XALD y cómo verificar que cumple el umbral de **ESC-05** (100 % de conflictos resueltos, 0 transacciones perdidas, carga de 30 transacciones simultáneas).

## Opciones Evaluadas

* **Resolución respaldada por base de datos** (tabla de historial de versiones en una BD persistente): descartada para este corte; el backend todavía no tiene persistencia de datos (ver deuda técnica en [Sección 11](../arc42/11-Risks%20and%20Technical%20Debts.md)), y el umbral de ESC-05 no exige historial persistente, solo resolución correcta en el momento de la petición.
* **Vector clocks / CRDTs:** descartada; resuelven conflictos entre múltiples campos editados de forma independiente, un problema más general que el que tiene el proyecto (una sola marca de tiempo por transacción). Añadiría complejidad de implementación y de pruebas desproporcionada para el alcance del MVP.
* **Last-Write-Wins con comparación de timestamp en memoria (Adoptada):** comparar `fecha_transaccion` de la petición entrante contra la última versión conocida, guardada en un diccionario en memoria (`ultima_version`), y conservar la más reciente.

## Decisión Tomada

Implementar la comparación LWW directamente en el handler `POST /api/v1/transacciones` de [`backend/app/main.py`](../../backend/app/main.py), usando el diccionario en memoria `ultima_version` indexado por `id_transaccion`. Cada vez que se detecta un conflicto (ya existía una versión previa), se incrementa el contador Prometheus `xald_sync_conflictos_resueltos_total`, ligado a ESC-05.

## Justificación Técnica

* **Cumplimiento verificado del umbral:** `test_medicion_esc05_30_transacciones_en_conflicto` en [`backend/tests/test_lww.py`](../../backend/tests/test_lww.py) reproduce la carga exacta de ESC-05 (30 transacciones en conflicto) y confirma 100 % resueltos y 0 perdidas. El mismo resultado (30/30) se verificó contra el backend real desplegado en Render con [`backend/scripts/medir_esc05.py`](../../backend/scripts/medir_esc05.py).
* **Prueba dirigida al defecto:** `test_version_antigua_no_sobrescribe_a_la_reciente` verifica puntualmente que una versión antigua que llega después de la reciente no la sobrescriba; se confirmó que la prueba falla si se invierte la comparación de fechas (mutación documentada).
* **Trazabilidad operacional:** cada conflicto resuelto queda reflejado en `GET /metrics`, dando evidencia en tiempo de ejecución además de la evidencia de prueba.

## Consecuencias

* **Positivas:** implementación simple, fácil de razonar y de testear; cumple el umbral medido de ESC-05.
* **Riesgo:** el estado (`ultima_version`) vive solo en memoria y se reinicia con cada despliegue o cuando Render "duerme" el servicio en el plan Free — ya documentado como deuda técnica relacionada con el ADR-0001 en la Sección 11.
* **Riesgo adicional:** la resolución depende de que la marca de tiempo enviada por el cliente sea confiable; no hay corrección de reloj. Este riesgo ya está registrado en el árbol de utilidad de la Sección 10 como el de mayor riesgo técnico del proyecto.
* **Mitigación:** aceptada para este corte. La persistencia del estado de conflictos queda pendiente para una iteración futura, junto con la migración general a SQLite/Room mencionada en el aspecto A-02 de `aspectos.md`.

## Trazabilidad

ESC-05 · RT-05 · OB-02 · [ADR-0001](0001-patron-offline-first.md) · [ADR-0010](0010-observabilidad.md)
