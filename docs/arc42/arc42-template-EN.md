---
date: AGOSTO 2026
title: "  PROYECTO XALD  "
---

# Introduction and Goals {#section-introduction-and-goals}

## Requirements Overview {#_requirements_overview}

## Quality Goals {#_quality_goals}

## Stakeholders {#_stakeholders}

+-------------+---------------------------+---------------------------+
| Role/Name   | Contact                   | Expectations              |
+=============+===========================+===========================+
| *           | *\<Contact-1\>*           | *\<Expectation-1\>*       |
| \<Role-1\>* |                           |                           |
+-------------+---------------------------+---------------------------+
| *           | *\<Contact-2\>*           | *\<Expectation-2\>*       |
| \<Role-2\>* |                           |                           |
+-------------+---------------------------+---------------------------+

# Architecture Constraints

Estas son las condiciones que ya vienen dadas para el proyecto y que no podemos cambiar. No son decisiones de diseño que tomamos nosotros por gusto, sino cosas que limitan desde antes cómo se puede construir XALD.

## Restricciones Técnicas:

**RT-01** (Exclusividad de Sistema Operativo): La app se va a desarrollar solo para Android. La razón es que leer los SMS automáticamente en segundo plano (usando BroadcastReceiver y el permiso RECEIVE_SMS) es algo que solo se puede hacer de esa forma en Android; otros sistemas móviles no dejan que una app lea mensajes de texto así por sus políticas de seguridad.

**RT-02** (Arquitectura Offline-First): La información se guarda primero de forma local, en una base de datos SQLite con cifrado (Cipher). Leer y escribir datos no depende de tener internet.

**RT-03** (Seguridad de Datos Locales): La base de datos local se cifra con AES-256, y las llaves que la protegen se manejan a través del Android Keystore.

**RT-04** (Ingesta por Inferencia / Regex): XALD depende de leer e interceptar los mensajes de texto (SMS) que mandan los bancos, en lugar de usar una API bancaria oficial (Open Banking). Esto significa que si un banco cambia el formato de sus mensajes, XALD se puede ver afectado y toca ajustar la forma en que los lee.

**RT-05** (Consistencia Sencilla LWW): Cuando hay un cruce entre lo que pasó en el celular y lo que hay en el servidor, gana la transacción más reciente (esto se conoce como Last-Write-Wins o LWW). Para saber cuál es la más reciente se usan marcas de tiempo y códigos únicos (UUIDs) dentro de la fila de espera (Sync Queue).

## Restricciones Organizacionales y de Proyecto:

**RO-01** (Límite Semestral y Equipo): El desarrollo está limitado al alcance de un semestre académico y lo hace un equipo de estudiantes. Por eso el primer incremento del proyecto se enfoca solo en el módulo A-01 (recepción y procesamiento de notificaciones).

**RO-02** (Costo $0 / Presupuesto): El proyecto tiene que usar únicamente servicios en sus capas gratuitas, como Google AI Studio / Gemini API (Free Tier), e infraestructura que no tenga costo.

# Context and Scope

## Business Context 

Aquí se muestra quién o qué interactúa con XALD desde afuera, sin entrar en detalles técnicos de cómo se comunican. Ahora el sistema también incluye la API de Gemini de Google, que ayuda a sugerir en qué categoría va cada gasto.

**\<Diagram or Table\>**

Actor / Sistema externo	Descripción	Entradas hacia XALD	Salidas desde XALD
Usuario Final	Propietario de la información financiera	Corrección manual de categorías, registros manuales, consultas de reportes	Visualización de saldo, historial de transacciones, reportes de gasto
Entidades Bancarias / SMS	Proveedores de mensajería del sistema operativo que emiten alertas de movimientos	Mensaje de texto (SMS) con monto, comercio y fecha	Visualización de saldo, historial de transacciones, reportes de gasto
Backend XALD / Servidor	Sistema remoto para sincronización y reportes	Confirmación de sincronización, agregaciones de reportes	Cola de transacciones pendientes (Sync Queue)
Google Gemini API	API de IA externa para la inferencia de categorías de gasto	Categoría sugerida en formato JSON	Cadena de texto limpia del comercio / origen


**\<optionally: Explanation of external domain interfaces\>**

La idea central es que el usuario casi no tiene que hacer nada manualmente: el sistema capta la información sola desde los SMS bancarios, usa la IA de Gemini para sugerir la categoría del gasto, y el usuario solo interviene para revisar, corregir o consultar.

## Technical Context {#_technical_context}

Acá se muestra por dónde entra y sale la información, y cómo viaja de un lado a otro.

**\<Diagram or Table\>**

Interfaz Técnica	Canal / Protocolo	Formato de Datos	Cifrado / Seguridad
Sistema Operativo → App XALD	Android BroadcastReceiver (Eventos del SO)	Texto plano (SmsMessage)	Permiso Android RECEIVE_SMS
App XALD → DB Local	Llamada interna SQLite / Room	Objetos Relacionales / Filas	AES-256 vía Android Keystore
App XALD → Gemini API	HTTPS / Rest (POST)	JSON (responseMimeType: application/json)	TLS 1.3 + API Key
App XALD → Backend XALD	HTTPS / REST (POST/PUT)	Lotes JSON (Sync Qu

**\<optionally: Explanation of technical interfaces\>**

Este diagrama de texto es la base para armar después el C4 de contexto formal (con las cajas y flechas gráficas), pero ya deja claro quiénes son los actores y por dónde entra y sale la información.

**\<Mapping Input/Output to Channels\>**

[Banco / SMS]
        |
        |  SMS (monto, comercio, fecha)
        v
[Sistema Operativo]
        |
        |  evento local (BroadcastReceiver)
        v
[App XALD] --texto del comercio--> [Google Gemini API]
        |  <---categoría sugerida (JSON)---
        |
        |  guardado local (cifrado AES-256)
        v
[Base de datos local]
        |
        |  cuando hay conexión (sync HTTPS/REST)
        v
[Backend XALD]

[Usuario final] <-- consulta saldo, reportes, alertas -- [App XALD]

# Solution Strategy {#section-solution-strategy}

# Building Block View {#section-building-block-view}

## Whitebox Overall System {#_whitebox_overall_system}

***\<Overview Diagram\>***

Motivation

:   *\<text explanation\>*

Contained Building Blocks

:   *\<Description of contained building block (black boxes)\>*

Important Interfaces

:   *\<Description of important interfaces\>*

### \<Name black box 1\> {#_name_black_box_1}

*\<Purpose/Responsibility\>*

*\<Interface(s)\>*

*\<(Optional) Quality/Performance Characteristics\>*

*\<(Optional) Directory/File Location\>*

*\<(Optional) Fulfilled Requirements\>*

*\<(optional) Open Issues/Problems/Risks\>*

### \<Name black box 2\> {#_name_black_box_2}

*\<black box template\>*

### \<Name black box n\> {#_name_black_box_n}

*\<black box template\>*

### \<Name interface 1\> {#_name_interface_1}

...​

### \<Name interface m\> {#_name_interface_m}

## Level 2 {#_level_2}

### White Box *\<building block 1\>* {#_white_box_building_block_1}

*\<white box template\>*

### White Box *\<building block 2\>* {#_white_box_building_block_2}

*\<white box template\>*

...​

### White Box *\<building block m\>* {#_white_box_building_block_m}

*\<white box template\>*

## Level 3 {#_level_3}

### White Box \<\_building block x.1\_\> {#_white_box_building_block_x_1}

*\<white box template\>*

### White Box \<\_building block x.2\_\> {#_white_box_building_block_x_2}

*\<white box template\>*

### White Box \<\_building block y.1\_\> {#_white_box_building_block_y_1}

*\<white box template\>*

# Runtime View {#section-runtime-view}

## \<Runtime Scenario 1\> {#_runtime_scenario_1}

-   *\<insert runtime diagram or textual description of the scenario\>*

-   *\<insert description of the notable aspects of the interactions
    between the building block instances depicted in this diagram.\>*

## \<Runtime Scenario 2\> {#_runtime_scenario_2}

## ...​

## \<Runtime Scenario n\> {#_runtime_scenario_n}

# Deployment View {#section-deployment-view}

## Infrastructure Level 1 {#_infrastructure_level_1}

***\<Overview Diagram\>***

Motivation

:   *\<explanation in text form\>*

Quality and/or Performance Features

:   *\<explanation in text form\>*

Mapping of Building Blocks to Infrastructure

:   *\<description of the mapping\>*

## Infrastructure Level 2 {#_infrastructure_level_2}

### *\<Infrastructure Element 1\>* {#_infrastructure_element_1}

*\<diagram + explanation\>*

### *\<Infrastructure Element 2\>* {#_infrastructure_element_2}

*\<diagram + explanation\>*

...​

### *\<Infrastructure Element n\>* {#_infrastructure_element_n}

*\<diagram + explanation\>*

# Cross-cutting Concepts {#section-concepts}

## *\<Concept 1\>* {#_concept_1}

*\<explanation\>*

## *\<Concept 2\>* {#_concept_2}

*\<explanation\>*

...​

## *\<Concept n\>* {#_concept_n}

*\<explanation\>*

# Architecture Decisions {#section-design-decisions}

# Quality Requirements {#section-quality-scenarios}

## Quality Requirements Overview {#_quality_requirements_overview}

## Quality Scenarios {#_quality_scenarios}

# Risks and Technical Debts {#section-technical-risks}

# Glossary {#section-glossary}

+----------------------+-----------------------------------------------+
| Term                 | Definition                                    |
+======================+===============================================+
| *\<Term-1\>*         | *\<definition-1\>*                            |
+----------------------+-----------------------------------------------+
| *\<Term-2\>*         | *\<definition-2\>*                            |
+----------------------+-----------------------------------------------+
