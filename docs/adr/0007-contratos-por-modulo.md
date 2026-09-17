# ADR-0007: Contratos Públicos por Módulo y Orquestación Centralizada

* *Estatus:* Decidido
* *Fecha:* Septiembre 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera
* *Corte:* Segundo corte — reajuste de límites entre contextos

## Contexto y Problema

La auditoría de arquitectura de la semana 6 detectó siete violaciones de propiedad de datos y de aislamiento entre contextos en el código del primer corte. Dos de ellas modificaban directamente las fronteras declaradas en el mapa de contextos:

* **V-02:** el módulo `:parser` declaraba una dependencia Gradle hacia `:aigemini`, de modo que el contexto de Ingesta conocía al de Categorización y quedaba acoplado a su disponibilidad.
* **V-06:** el módulo `:syncqueue` consumía el DTO producido por `:parser`, lo que hacía depender la Sincronización del formato interno de la Ingesta.

Las cinco restantes (V-01, V-03, V-04, V-05 y V-07) respondían a una misma causa: las clases de implementación eran públicas y no existía un contrato explícito que delimitara qué podía ver cada módulo desde afuera.

El problema de fondo era que las fronteras entre contextos existían solo en la documentación: nada en el código impedía que un módulo importara las clases internas de otro.

## Opciones Evaluadas

* *Mantener la comunicación directa entre módulos de dominio:* conservar las dependencias cruzadas y confiar en la disciplina del equipo para no acoplar contextos.
* *Interfaces públicas con comunicación directa entre módulos:* declarar contratos, pero permitir que los módulos de dominio se invoquen entre sí sin intermediario.
* *Contratos públicos con orquestación centralizada (Adoptado):* cada módulo expone una única interfaz pública y oculta su implementación; toda la coordinación entre contextos ocurre en el módulo `:app`.

## Decisión Tomada

Se adopta el esquema de **contratos públicos por módulo con orquestación centralizada**.

Cada módulo de dominio expone exclusivamente una interfaz pública y una función fábrica que la instancia, manteniendo su implementación declarada como `internal` de Kotlin:

| Módulo | Contrato público | Implementación interna |
| :--- | :--- | :--- |
| `:parser` | `ServicioParser` | `ParseoSms` |
| `:aigemini` | `ServicioCategorizacion` | `CategorizadorGemini` |
| `:corefinanciero` | `InformacionFinanciera` | `GestorCoreFinanciero` |
| `:syncqueue` | `ColaSincronizacionService` | `ColaSincronizacion` |

La coordinación del flujo completo se concentra en `ProcesarNotificacionUseCase`, dentro del módulo `:app`, que recibe los cuatro contratos por inyección de constructor y los invoca en secuencia. Ningún módulo de dominio depende de otro módulo de dominio.

Como consecuencia de esta decisión, se introduce `PayloadSincronizacionDTO` como contrato de transporte entre `:corefinanciero` y `:syncqueue`, reemplazando el uso del DTO de `:parser` que originaba la violación V-06.

## Justificación Técnica

* **Fronteras verificables por el compilador:** el modificador `internal` de Kotlin impide que un módulo referencie las clases de implementación de otro. La frontera deja de ser una convención y pasa a ser una restricción de compilación.
* **Eliminación de dependencias laterales:** al concentrar la coordinación en `:app`, ningún contexto de dominio necesita conocer a otro. Los archivos `build.gradle.kts` de `:parser`, `:aigemini` y `:corefinanciero` no declaran dependencias entre sí.
* **Aislamiento del servicio externo:** `CategorizadorGemini` actúa como Capa Anticorrupción y traduce la respuesta de la API de Gemini a `CategoriaResultado`, de modo que ningún tipo propio del proveedor externo cruza la frontera del módulo.
* **Verificación automatizada:** la prueba `ValidacionModulosTest` recorre el código fuente y falla si detecta una importación cruzada entre módulos, impidiendo que las violaciones corregidas reaparezcan.

## Consecuencias

### Positivas:

* Las siete violaciones detectadas en la auditoría quedan corregidas y su reaparición se detecta de forma automática.
* Cada entidad tiene un dueño único: solo `:corefinanciero` escribe `TransaccionEntidad`.
* Los módulos de dominio pueden desarrollarse y probarse de forma aislada, sin instanciar a los demás.
* El flujo completo queda descrito en un solo lugar legible, en lugar de repartido entre llamadas cruzadas.

### Riesgos y Mitigación:

* **Riesgo:** el módulo `:app` concentra el conocimiento de todo el flujo y se convierte en un punto de acoplamiento; cualquier cambio en la secuencia de procesamiento obliga a modificarlo.
* **Mitigación:** el orquestador depende únicamente de interfaces, por lo que un cambio en la implementación de cualquier módulo no lo afecta. La lógica de coordinación se mantiene deliberadamente delgada, sin reglas de negocio propias.

* **Riesgo:** la instanciación mediante funciones fábrica es una solución provisional frente a un contenedor de inyección de dependencias.
* **Mitigación:** los contratos ya están definidos, de modo que la migración a un contenedor como Koin o Hilt no requeriría modificar los módulos de dominio.

## Puntos Abiertos

* La ubicación definitiva del componente `ReceptorSmsBancario` no está resuelta. La sección 8 de arc42 asigna el contexto de Ingesta al módulo `:parser`, mientras que el diagrama C2 dirige el conector 1 hacia `:app`. El diagrama C4 nivel 3 respeta el C2 y registra la discrepancia; debe cerrarse antes de implementar la recepción de SMS.

**Trazabilidad:**
* Auditoría de violaciones: [`docs/auditoriaviolaciones-semana6.md`](../auditoriaviolaciones-semana6.md)
* Matriz de propiedad de datos: [`docs/ownership-matrix.md`](../ownership-matrix.md)
* Diagrama de componentes actualizado: [`docs/c4/c3.md`](../c4/c3.md)
* Decisión de estilo arquitectónico que antecede a esta: [ADR-0006](0006-seleccion-de-estilo-arquitectonico.md)
