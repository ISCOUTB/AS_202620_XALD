# ADR-0006: Selección de Estilo Arquitectónico - Monolito Modular

* *Estatus:* Decidido (Actualizado a estructura física de Gradle)
* *Fecha:* Agosto 2026
* *Proyecto:* XALDAPP — Aplicación de gestión financiera

## Contexto y Problema
El desarrollo de la aplicación XALDAPP requiere definir un estilo arquitectónico base que responda a los siguientes principios:

* Organización clara del trabajo en equipo mediante fronteras de desarrollo definidas.
* Aislamiento de servicios externos y componentes cambiantes del núcleo de la aplicación.
* Soporte para funcionamiento Offline-First.
* Capacidad de ejecutar pruebas unitarias sobre el JDK/JVM local sin dependencia del entorno de ejecución de Android.

## Opciones Evaluadas
* *Arquitectura por Capas (Layered):* Organización por niveles horizontales (presentación, dominio y datos).
* *Arquitectura Hexagonal (Ports & Adapters):* Dominio aislado rodeado de puertos y adaptadores externos.
* *Monolito Modular (Adoptado):* Módulos verticales por funcionalidad integrados en un único despliegue y comunicados por contratos.
###### * Referencia: El análisis comparativo de estas opciones se encuentra documentado en la Matriz Comparativa de Estilos Arquitectónicos.

## Decisión Tomada
Se adopta el *Monolito Modular*, implementado físicamente a través de **5 módulos independientes de Gradle**, los cuales separan las responsabilidades de dominio y presentación mediante fronteras estrictas de compilación.

### Esqueleto de Módulos Base:
```text
XALDAPP/
├── app/             # Módulo orquestador: UI (Jetpack Compose), Navegación y Login
├── parser/          # Módulo de dominio: Ingesta, lectura de SMS y expresiones regulares
├── corefinanciero/  # Módulo de dominio: Lógica financiera, entidades y base de datos local
├── syncqueue/       # Módulo de dominio: Encolamiento offline y sincronización (WorkManager)
└── aigemini/        # Módulo de dominio: Integración HTTP/SDK con servicios de IA
```

## Justificación Técnica
* **Fronteras Físicas Estrictas:** A diferencia de la simple separación por paquetes, usar módulos de Gradle obliga a declarar explícitamente qué módulo puede ver a cuál a través del bloque `dependencies`. El módulo `:app` orquesta, pero los módulos de dominio se mantienen aislados.
* **Aislamiento por Contratos:** Los módulos interactúan a través de interfaces explícitas. Los componentes externos permanecen desacoplados del núcleo del sistema, evitando que cambios de proveedores o fallos de red afecten el flujo principal.
* **Autonomía Modular:** La persistencia local y la lógica de negocio residen en módulos independientes (`:corefinanciero` y `:syncqueue`), garantizando la operación sin conexión.
* **Verificación Aislada y Caché de Compilación:** El desacoplamiento permite que el comando `gradlew test` ejecute las suites de pruebas unitarias en paralelo para cada módulo, aprovechando la caché de compilación de Gradle y evaluándose directamente sobre el JDK local para máxima velocidad.

## Consecuencias

### Positivas:
* Delimitación clara e infranqueable (a nivel de compilador) de responsabilidades entre módulos.
* Tiempos de compilación optimizados en el día a día (solo se recompila el módulo modificado).
* Facilidad para el reparto de trabajo y desarrollo en paralelo.
* Pruebas unitarias ágiles y desacopladas de la plataforma.

### Riesgos y Mitigación:
* **Riesgo:** Mayor sobrecarga inicial por la administración de múltiples archivos `build.gradle.kts` y posible exposición de clases internas.
* **Mitigación:** Centralización de dependencias (uso de Version Catalogs si aplica) y restricción de acceso mediante el uso del modificador de visibilidad `internal` en Kotlin, asegurando que solo se expongan los contratos y no la lógica de implementación de cada módulo.
