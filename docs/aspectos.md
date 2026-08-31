# Tabla de Aspectos

Cada aspecto arquitectónico se enlaza con el objetivo de calidad que persigue, el diagrama de contexto, el escenario que lo verifica y la decisión que lo sustenta.

| ID | ASPECTO | REQUISITO | C4 | ESCENARIO | ADR | CÓDIGO | PRUEBAS | EVIDENCIA |
|---|---|---|---|---|---|---|---|---|
| **A-01** | Recepción y procesamiento de información vía SMS / Notificaciones bancarias | [RT-04](arc42/arc42-template-EN.md#architecture-constraints) | [C1](c4/c4.md) | [ESC-03](arc42/arc42-template-EN.md#esc-03--incorporación-de-una-nueva-entidad-bancaria) | [0002](adr/0002-parsing-hibrido.md) · [0003](adr/0003-restriccion-os.md) | [Parser.kt](https://github.com/ISCOUTB/AS_202620_XALD/blob/experimental/XALDAPP/parser/src/main/java/Parser.kt) | https://github.com/ISCOUTB/AS_202620_XALD/blob/experimental/README.md#-corte-vertical-ejecutable | *Pendiente* |
| **A-02** | Persistencia local y arquitectura Offline-First | [RT-02](arc42/arc42-template-EN.md#architecture-constraints) | [C1](c4/c4.md) | [ESC-01](arc42/arc42-template-EN.md#esc-01--registro-de-transacción-sin-conexión) | [0001](adr/0001-patron-offline-first.md) | *Pendiente* | *Pendiente* | *Pendiente* |
| **A-03** | Sincronización asíncrona y gestión de conflictos | [RT-05](arc42/arc42-template-EN.md#architecture-constraints) | [C1](c4/c4.md) | [ESC-05](arc42/arc42-template-EN.md#esc-05--resolución-de-conflictos-al-sincronizar) | [0001](adr/0001-patron-offline-first.md) | *Pendiente* | *Pendiente* | *Pendiente* |
| **A-04** | Seguridad y protección de datos en reposo y tránsito | [RT-03](arc42/arc42-template-EN.md#architecture-constraints) · [RL-01](arc42/arc42-template-EN.md#restricciones-legales) | [C1](c4/c4.md) | [ESC-04](arc42/arc42-template-EN.md#esc-04--protección-de-la-información-almacenada) | [0004](adr/0004-seguridad-y-cifrado.md) | *Pendiente* | *Pendiente* | *Pendiente* |
| **A-05** | Estilo arquitectónico y organización modular del sistema | [RO-01](arc42/arc42-template-EN.md#restricciones-organizacionales-y-de-proyecto) | [C1](c4/c4.md) | [ESC-02](arc42/arc42-template-EN.md#esc-02--indisponibilidad-del-servicio-de-categorización) | [0006](adr/0006-seleccion-de-estilo-arquitectonico.md) | *Pendiente* | *Pendiente* | *Pendiente* |

## Notas

- Los aspectos A-02 y A-03 comparten el [ADR-0001](adr/0001-patron-offline-first.md) porque la decisión de arquitectura Offline-First determina tanto la estrategia de persistencia local como el mecanismo de sincronización asíncrona con resolución Last-Write-Wins.
- El [ADR-0005](adr/0005-reduccion-de-funcionalidades.md) (alcance reducido del módulo de analítica) se encuentra en estado *En revisión*, por lo que no se asocia todavía a ningún aspecto de esta tabla.
- Las columnas CÓDIGO, PRUEBAS y EVIDENCIA se completarán a partir del primer incremento de implementación, conforme a lo previsto en [RO-01](arc42/arc42-template-EN.md#restricciones-organizacionales-y-de-proyecto).
