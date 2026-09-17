
# AS_20262_XALD
PROYECTO XALD, APP FINANZAS PERSONALES


## 🚀 Corte Vertical Ejecutable

El proyecto cuenta con un corte vertical integrado que valida el flujo de datos completo a través de la aplicación Flutter (`XALDAPP_FLU`) y sus paquetes locales en Dart (`aigemini`, `corefinanciero`, `parser` y `syncqueue`).
https://github.com/ISCOUTB/AS_202620_XALD/blob/puente/XALDAPP_FLU/test/corte_vertical_test.dart

### 🛠️ Prueba de Integración
La prueba de corte vertical (`corte_vertical_test.dart`) orquesta la recepción del mensaje bancario crudo, invoca las reglas del parser, categoriza mediante la IA, procesa el Core Financiero y encola en el servicio de sincronización.

### 📸 Evidencia de Ejecución Local
<img width="1276" height="217" alt="image" src="https://github.com/user-attachments/assets/b0d6c7f2-2ba2-440d-aea1-f18550c0b787" />


### Comandos de Ejecución y Verificación

##### Requisitos: Flutter SDK (v3.x o superior) instalado y configurado en el PATH.

##### Ejecución del Corte Vertical:
Abre una consola de PowerShell en la raíz del repositorio y ejecuta los siguientes comandos para validar el flujo completo de la prueba:

```powershell
cd XALDAPP_FLU
flutter pub get
flutter test test/corte_vertical_test.dart
```

### Descripción de la app

El objetivo es ofrecer a los usuarios una herramienta intuitiva y eficiente para el control de sus ingresos, gastos y ahorros, permitiéndoles tomar decisiones financieras más informadas a través de un seguimiento claro de su actividad económica diaria. Con un enfoque centrado en la simplicidad y la usabilidad, la aplicación busca convertirse en un aliado práctico para la organización financiera personal.

### Situación problema 

Muchas personas carecen de un control claro sobre sus ingresos, gastos y ahorros, lo que dificulta tomar decisiones financieras informadas y favorece el endeudamiento innecesario. Esto se debe, en parte, al uso de métodos poco eficientes( cuadernos, hojas de calculo genéricas o ningún registro) y a que las aplicaciones existentes suelen ser demasiado complejas o demasiado básicas para cubrir sus necesidades reales. Esta app surge para resolver esta problemática, ofreciendo una herramienta simple y accesible que permita a los usuarios comprender y organizar su actividad económica diaria.


