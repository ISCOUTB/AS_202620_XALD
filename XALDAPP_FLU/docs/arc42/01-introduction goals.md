# Introduction and Goals

Esta sección presenta una visión general de XALD: qué problema resuelve, cómo funciona, qué objetivos de negocio y de calidad persigue y quiénes son las partes interesadas. Sirve como punto de entrada para el resto de la documentación de arquitectura.

## Requirements Overview

En la gestión financiera personal actual se identifican dos limitaciones estructurales que XALD busca resolver:

- **Fricción en la entrada de datos (carga cognitiva).** Anotar cada transacción a mano toma tiempo; al cabo de pocas semanas el usuario abandona la app, generando pérdida de integridad del historial financiero ("gastos hormiga" no registrados).
- **Dependencia estricta de conectividad (acoplamiento a red).** Si el usuario no tiene datos o la señal es mala, la mayoría de apps no abren o no permiten registrar nada.

**Solución propuesta:** una app móvil que registra los gastos con mínima intervención del usuario, leyendo automáticamente notificaciones/SMS bancarios, y que funciona sin internet para mostrar la información al instante (offline-first).

**Cómo funciona el sistema (flujo de datos):** XALD funciona como una tubería de datos (pipeline) de 4 pasos:

1. **Captura:** vía SMS/notificación bancaria, leída automáticamente en segundo plano.
2. **Validación y limpieza:** se extraen fecha, monto y comercio, verificando que los datos sean válidos.
3. **Categorización inteligente:** el nombre del comercio se envía a una API de IA (Gemini API, capa gratuita) que devuelve la categoría del gasto (ej. "Alimentación").
4. **Guardado local:** la transacción categorizada se persiste cifrada en el dispositivo (SQLite + SQLCipher, cifrado AES-256), visible al instante aunque no haya internet.

**Resiliencia:** si no hay internet o la IA no responde, el gasto se guarda igual bajo "Sin Categorizar" y se reclasifica automáticamente al volver la señal — nunca se pierde un dato. Las transacciones pendientes de sincronizar viven en una Sync Queue que garantiza orden cronológico exacto (timestamps/UUIDs) al reconectar, evitando duplicados o saldos sobrescritos.

## Business Goals

Los siguientes son los objetivos de negocio que justifican la existencia del sistema. Cada uno indica a qué interesado le importa y por qué. Los objetivos de calidad de la sección siguiente se derivan de estos.

| ID | Objetivo de negocio | Interesado principal | Por qué le importa |
|---|---|---|---|
| **OB-01** | Eliminar la fricción en la entrada de datos, que es la causa del abandono de la app y de la pérdida de integridad del historial financiero | Usuario final | Quiere el control de sus gastos sin dedicar tiempo diario a registrarlos a mano |
| **OB-02** | Desacoplar la aplicación de la conectividad, de modo que sea utilizable con o sin señal | Usuario final | Registra y consulta gastos en zonas sin cobertura o con datos agotados |
| **OB-03** | Tratar la información financiera conforme a la Ley 1581 de 2012 | Usuario final · Equipo de desarrollo | El usuario confía datos sensibles; el equipo responde legalmente por su tratamiento |
| **OB-04** | Sostener la cobertura de entidades bancarias sin reescribir el sistema cada vez que una cambie el formato de sus mensajes | Equipo de desarrollo | Un formato no soportado deja sin servicio a un segmento de usuarios |

## Quality Goals

Cada objetivo de calidad se deriva de un objetivo de negocio y se verifica mediante un escenario de la sección 10.

| # | Objetivo de calidad | Descripción | Objetivo de negocio | Escenario |
|---|---|---|---|---|
| 1 | Disponibilidad (offline-first) | Leer y escribir datos sin señal; el usuario nunca ve un error de red al registrar un gasto. | OB-02 | ESC-01 |
| 2 | Resiliencia | Si la IA falla o no responde, la app sigue funcionando con normalidad (categoría "Sin Categorizar" temporal). | OB-01 | ESC-02 |
| 3 | Seguridad básica | Proteger la base de datos local contra lecturas no autorizadas (cifrado SQLCipher/AES-256). | OB-03 | ESC-04 |
| 4 | Consistencia eventual | Al reconectar, la Sync Queue sube las transacciones en orden cronológico correcto sin duplicar ni sobrescribir saldos. | OB-02 | ESC-05 |
| 5 | Modificabilidad | Incorporar el formato de una nueva entidad bancaria sin modificar el código de las ya soportadas. | OB-04 | ESC-03 |

**Escenarios de calidad medibles:** los escenarios completos, con sus seis partes (fuente, estímulo, artefacto, entorno, respuesta y medida) y sus medidas verificables (umbral, carga y herramienta), se detallan en la sección 10 (Quality Requirements).

**Restricciones clave:**
- **Presupuesto:** $0 — solo bibliotecas open-source y capas gratuitas de APIs.
- **Privacidad (Ley 1581 de Colombia):** a la IA solo se le envía el nombre del comercio y el monto; nunca se envían nombres de usuarios ni números de cédula/cuenta.

## Stakeholders

| Rol | Contacto | Expectativas | Objetivo asociado |
| --- | --- | --- | --- |
| Usuario final | Interactúa con la app móvil | Registrar y consultar sus finanzas con mínima fricción, sin depender de señal | OB-01 · OB-02 |
| Usuario final | Interactúa con la app móvil | Que su información financiera no sea legible si pierde el dispositivo | OB-03 |
| Equipo de desarrollo (nosotros) | Diseña, implementa y documenta cada incremento | Entregar una arquitectura clara, documentada y sostenible en un semestre | OB-04 |
| Docente / Evaluador (UTB) | Revisa el repositorio de GitHub y los entregables incrementales | Verificar que la documentación (arc42) corresponda con el repositorio | Todos |
| Servicio externo de IA (Gemini) | Se consulta vía API; no almacena datos personales del usuario | Recibir solo datos anonimizados (comercio + monto) para categorizar | OB-03 |
