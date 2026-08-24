# AS_20262_XALD
PROYECTO XALD, APP FINANZAS PERSONALES

## Esqueleto de Ejecución
 dir:: XALDAPP/app/src/main/java/com/proyecto/xald/
# Comandos de Ejecución y Verificación

## 1. Desde la carpeta del proyecto (`XALDAPP/`)

**PowerShell (Windows):**
```powershell
$env:ANDROID_HOME="C:\Users\(usuario)\AppData\Local\Android\Sdk"; $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test
```
**Android Studio: fuera del directorio**
```as
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME="C:\Users\(us)\AppData\Local\Android\Sdk"; .\XALDAPP\gradlew.bat -p XALDAPP test
```

## 2. Desde la raíz del repositorio apuntando al subproyecto

**PowerShell (Windows):**
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; $env:ANDROID_HOME="C:\Users\(usuario)\AppData\Local\Android\Sdk"; .\XALDAPP\gradlew.bat -p XALDAPP test
```

**CMD (Windows con variables globales configuradas):**
```cmd
XALDAPP\gradlew.bat -p XALDAPP test
```

**Linux / macOS:**
```bash
./XALDAPP/gradlew -p XALDAPP test
```

---

## Salida Esperada en Consola

Al ejecutar cualquiera de los comandos anteriores, la suite de pruebas unitarias validará el entorno. El resultado exitoso debe verse así:

```plaintext
Reusing configuration cache.

BUILD SUCCESSFUL in 1s
24 actionable tasks: 24 up-to-date
Configuration cache entry reused.
```


### Descripción de la app

El objetivo es ofrecer a los usuarios una herramienta intuitiva y eficiente para el control de sus ingresos, gastos y ahorros, permitiéndoles tomar decisiones financieras más informadas a través de un seguimiento claro de su actividad económica diaria. Con un enfoque centrado en la simplicidad y la usabilidad, la aplicación busca convertirse en un aliado práctico para la organización financiera personal.

### Situación problema 

Muchas personas carecen de un control claro sobre sus ingresos, gastos y ahorros, lo que dificulta tomar decisiones financieras informadas y favorece el endeudamiento innecesario. Esto se debe, en parte, al uso de métodos poco eficientes( cuadernos, hojas de calculo genéricas o ningún registro) y a que las aplicaciones existentes suelen ser demasiado complejas o demasiado básicas para cubrir sus necesidades reales. Esta app surge para resolver esta problemática, ofreciendo una herramienta simple y accesible que permita a los usuarios comprender y organizar su actividad económica diaria.



