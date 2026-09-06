# Documento de correcciones al feedback

**Proyecto:** XALD — Arquitectura de Software (AS_202620)
**Repositorio:** `https://github.com/ISCOUTB/AS_202620_XALD`
**Fecha:** 6 de septiembre de 2026

Este documento responde a los hallazgos de las revisiones semanales. Las semanas 1 a 3 se resumen brevemente porque sus hallazgos correspondían a correcciones estándar y a completar detalles faltantes, todas ya aplicadas. El desarrollo se concentra en la semana 4, donde varios hallazgos se originan en fallos del calificador automático y no en el estado real del repositorio.

---

## Semanas 1 a 3 — Correcciones aplicadas

Todos los hallazgos de estas semanas están resueltos. No quedan puntos abiertos.

| Hallazgo | Corrección aplicada | Verificable en |
|---|---|---|
| arc42 §1 sin objetivos de negocio ni vínculo con interesados | Se agregó la sección *Business Goals* con OB-01 a OB-04, cada uno con su interesado y justificación. Los objetivos de calidad declaran de qué objetivo de negocio derivan y qué escenario los verifica. La tabla de interesados incluye la columna de objetivo asociado. | `docs/arc42/arc42-template-EN.md` §1 |
| arc42 §2 sin clase legal ni justificación de origen | Se incorporó RL-01 (Ley 1581 de 2012) como restricción legal y se declaró el origen de cada restricción. | `docs/arc42/arc42-template-EN.md` §2 |
| arc42 §3 no coincidía con el C4 | Se alineó la nota de alcance con el diagrama: el Backend XALD se representa dentro de la frontera del sistema en ambos artefactos. | `docs/arc42/arc42-template-EN.md` §3 |
| arc42 §10 vacía, sin escenarios ni árbol de utilidad | Se redactaron cinco escenarios (ESC-01 a ESC-05) con las seis partes que exige arc42 y con medidas que declaran umbral, carga y herramienta. Se agregó el árbol de utilidad priorizado por impacto y riesgo. | `docs/arc42/arc42-template-EN.md` §10 |
| C4 sin leyenda y sin el Backend declarado en §3 | Se incorporó el Backend XALD dentro del recuadro del sistema y se agregó la leyenda de tipos de elemento y de relación. | `docs/c4/c4.md` |
| Nombres de ADR fuera de convención | Los seis ADR se renombraron al formato `NNNN-titulo-en-kebab-case.md`. | `docs/adr/` |
| Celdas de `docs/aspectos.md` sin destino navegable | Cada fila enlaza a su restricción, al diagrama C4, al escenario de calidad correspondiente y al ADR que la sustenta. | `docs/aspectos.md` |
| Restos de edición en la documentación | Eliminados. | `docs/aspectos.md` |
| Prueba en verde sin constancia | Se documentó la ejecución del esqueleto con evidencia de la corrida en verde. | Documentación de S3 |

---

## Semana 4

### 4.1 Requisitos no evaluados por el calificador automático

**Hallazgo:** cuatro o cinco requisitos aparecen como no verificados en la revisión de S4.

**Situación real:** el trabajo correspondiente se realizó y se consolidó en la rama principal **antes** de la fecha de entrega. El calificador automático no evaluó esas secciones. El estado del repositorio en el momento del cierre contenía lo requerido; el fallo está en la verificación, no en la entrega.

**Acción:** ninguna. El trabajo está entregado y disponible en el repositorio para revisión manual.

### 4.2 Divergencia entre el Backend y los diagramas C1/C2

**Hallazgo:** el Backend XALD aparece en los diagramas de contexto y contenedores, pero no existe como estructura en el código.

**Aclaración:** el Backend forma parte del sistema, pero constituye un despliegue independiente de la aplicación Android. El corte vertical se implementó dentro de la aplicación móvil, que es donde reside la lógica verificada por los escenarios de calidad. La ausencia de una carpeta de backend dentro del proyecto Android es coherente con esa separación.

**Acción a realizar antes del cierre del Corte 1:** se creará la estructura de directorios del backend en la raíz del repositorio, fuera del proyecto Android, de modo que la organización del repositorio refleje la frontera declarada en los diagramas.

### 4.3 Alcance del corte vertical

**Hallazgo:** el calificador reporta que el corte vertical solo ejercita el módulo de parsing y el DTO de transacción, sin la capa de persistencia financiera.

**Situación real:** esa afirmación es incorrecta. El corte vertical ejecuta los cinco módulos del sistema, y todos participan e interactúan durante las pruebas del pipeline. El código importa todas las dependencias y emplea dobles de prueba para demostrar el flujo directo de datos sin depender de la base de datos real durante la ejecución de las pruebas unitarias.

**Acción:** ninguna. El comportamiento es verificable ejecutando las pruebas del pipeline, que constituyen la evidencia directa del alcance real del corte vertical.

### 4.4 ADR sin opciones evaluadas ni trazabilidad

**Hallazgo:** los ADR 0001 a 0005 no documentan las alternativas consideradas ni su trazabilidad hacia los escenarios de calidad.

**Acción a realizar antes del cierre del Corte 1:** se completarán con la sección de opciones evaluadas y el vínculo al objetivo de calidad y escenario que sustentan, siguiendo el formato del ADR-0006. La sección 9 del arc42 ya consolida esa trazabilidad en una tabla enlazada a cada decisión.

### 4.5 Pipeline sin análisis estático

**Hallazgo:** el pipeline de integración continua no incluye análisis estático de código.

**Causa:** la organización no tenía habilitados los permisos necesarios para integrar SonarCloud. El acceso fue otorgado el 5 de septiembre de 2026, y la integración la realizó el profesor.

**Estado:** una vez efectuados el commit final y el *pull request*, el análisis estático se ejecutará y verificará el pipeline.

---

## Cambios a realizar antes de finalizar el Corte 1

- Creación de la estructura de directorios del backend en la raíz del repositorio.
- Actualización de los ADR 0001 a 0005 con la sección de opciones evaluadas.

---

## Solicitud de revisión manual

Varios hallazgos de la semana 4 corresponden a puntos marcados como no declarados o no documentados que sí están detallados en la documentación del repositorio. El calificador automático operó de forma independiente a las pruebas reales del proyecto y no reflejó el estado efectivo de la entrega.

Se solicita revisión manual de esos puntos, dado que el resultado de una verificación automática defectuosa está afectando directamente la calificación de trabajo que sí fue realizado y entregado dentro del plazo.

Adicionalmente, y en caso de que sea posible y válido dentro de los criterios del curso, se solicita extender esa verificación manual a **las semanas 1 a 3**. Sobre esas entregas se aplicaron todas las modificaciones señaladas en el feedback, con el propósito de que la documentación quedara correctamente fundamentada: los objetivos de negocio y su vínculo con los interesados, la clasificación y el origen de las restricciones, los escenarios de calidad con sus seis partes y medidas verificables, el árbol de utilidad, el diagrama C4 con su leyenda y la trazabilidad completa desde la tabla de aspectos hasta los ADR.

El estado actual del repositorio refleja esas correcciones y permite verificar cada uno de esos puntos de forma directa.
