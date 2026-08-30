# AS_20262_XALD
PROYECTO XALD, APP FINANZAS PERSONALES

#Corte vertical ejecutable
## 🚀 Corte Vertical Ejecutable

El proyecto cuenta con un corte vertical integrado que valida el flujo de datos completo a través de sus módulos Gradle (`:app`, `:parser`, `:aigemini`, `:corefinanciero` y `:syncqueue`).

### 🛠️ Prueba de Integración
La prueba de corte vertical (`Cortevertical.kt`) orquesta la recepción del mensaje bancario crudo, invoca las reglas del parser y valida la estructuración del DTO de la transacción.

### 📸 Evidencia de Ejecución Local
<img width="1365" height="718" alt="Captura de pantalla 2026-08-30 144428" src="https://github.com/user-attachments/assets/13427b6e-b78a-4f51-8bfd-5350157ac22e" />

Dede la carpeta Raiz del proyecto de Android Studio: Con la variable de entorno JAVA_HOME Configurada
```powershell
cd XALDAPP
.\gradlew.bat test
```
### Sumado al CI pasado todo en verde en actions

##### Si no tiene configuradas las variables de entorno de Java/Gradle en su sistema:
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
cd XALDAPP
.\gradlew.bat test
```

# Comandos de Ejecución y Verificación

### Guía de Verificación y Compilación Local

##### Requisitos: JDK 17 (incluido en Android Studio JBR) y Android SDK configurados.

##### Ejecución: Abre una consola de PowerShell en la raíz del repositorio y ejecute el comando de arranque para validar los 5 módulos (:app, :corefinanciero, :parser, :syncqueue y :aigemini):
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME="C:\Users\<user>\AppData\Local\Android\Sdk"; .\XALDAPP\gradlew.bat -p XALDAPP test
```

## Salida Esperada en Consola

##### Al ejecutar el comando anterior, la suite de pruebas unitarias validará el entorno. El resultado exitoso debe verse así:

<img width="1104" height="254" alt="Captura de pantalla 2026-08-29 160428" src="https://github.com/user-attachments/assets/1ea8b153-e37b-4a51-a09c-b43a0b5f3c04" />



### Descripción de la app

El objetivo es ofrecer a los usuarios una herramienta intuitiva y eficiente para el control de sus ingresos, gastos y ahorros, permitiéndoles tomar decisiones financieras más informadas a través de un seguimiento claro de su actividad económica diaria. Con un enfoque centrado en la simplicidad y la usabilidad, la aplicación busca convertirse en un aliado práctico para la organización financiera personal.

### Situación problema 

Muchas personas carecen de un control claro sobre sus ingresos, gastos y ahorros, lo que dificulta tomar decisiones financieras informadas y favorece el endeudamiento innecesario. Esto se debe, en parte, al uso de métodos poco eficientes( cuadernos, hojas de calculo genéricas o ningún registro) y a que las aplicaciones existentes suelen ser demasiado complejas o demasiado básicas para cubrir sus necesidades reales. Esta app surge para resolver esta problemática, ofreciendo una herramienta simple y accesible que permita a los usuarios comprender y organizar su actividad económica diaria.



