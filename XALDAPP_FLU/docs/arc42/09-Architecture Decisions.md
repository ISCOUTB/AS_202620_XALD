# Architecture Decisions

Las decisiones arquitectónicas del proyecto se registran como ADR (Architecture Decision Record) individuales en `docs/adr/`, siguiendo la convención de nombre `NNNN-titulo-en-kebab-case.md`. Cada decisión responde a un objetivo de negocio o de calidad de la Sección 1, y varias se verifican mediante los escenarios de calidad de la Sección 10.

| ID | Título | Decisión | Relacionado con |
| --- | --- | --- | --- |
| [ADR-0001](../adr/0001-patron-offline-first.md) | Adopción de Patrón de Arquitectura Offline-First | Persistencia primero en base de datos local cifrada (SQLite/Room); los datos se envían al backend de forma asíncrona mediante una cola de sincronización cuando hay red. | Objetivo de calidad 1 (Disponibilidad) · RT-02 · ESC-01 |
| [ADR-0002](../adr/0002-parsing-hibrido.md) | Estrategia de Parsing Híbrido (Regex + librerías open source) | Usar un receptor de eventos local (RECEIVE_SMS) con un motor de expresiones regulares, en vez de una API bancaria oficial o un modelo de IA completo. | Objetivo de calidad 5 (Modificabilidad) · RT-04 · RO-02 · ESC-03 |
| [ADR-0003](../adr/0003-restriccion-os.md) | Restricción de Plataforma a Android y Exclusión de iOS | Limitar el cliente exclusivamente al ecosistema Android, usando BroadcastReceiver con el permiso RECEIVE_SMS. | Objetivo de negocio OB-01 · RT-01 |
| [ADR-0004](../adr/0004-seguridad-y-cifrado.md) | Modelo de Seguridad Acotado y Cifrado de Datos | Enfocar la seguridad en dos capas: cifrado local en reposo (AES-256 vía Android Keystore) y cifrado en tránsito (HTTPS/TLS). | Objetivo de calidad 3 (Seguridad básica) · RT-03 · RL-01 · ESC-04 |
| [ADR-0005](../adr/0005-reduccion-de-funcionalidades.md) | Alcance Reducido en el Módulo de Analítica y Reportes (MVP) | Reducir el módulo de reportes a lo esencial (saldos consolidados, gráficos básicos, lista de movimientos), dejando fuera el motor avanzado de analítica y predicción. | RO-01 |
| [ADR-0006](../adr/0006-seleccion-de-estilo-arquitectonico.md) | Selección de Estilo Arquitectónico — Monolito Modular | Adoptar un monolito modular organizado por paquetes de dominio (`parser`, `corefinanciero`, `syncqueue`, `aigemini`), en vez de arquitectura por capas o hexagonal. | RO-01 · Objetivo de calidad 5 (Modificabilidad) |
