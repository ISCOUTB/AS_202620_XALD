import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Configuración local (no se sube al repositorio): URL del backend y API key de sincronización.
// En local.properties:
//   XALD_API_URL=https://tu-backend.onrender.com
//   XALD_API_KEY=la-clave-de-pruebas
// Sin estas entradas se usa el emulador (10.0.2.2) y la clave de pruebas de siempre.
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun propiedadLocal(nombre: String, porDefecto: String): String =
    (localProperties.getProperty(nombre) ?: System.getenv(nombre) ?: porDefecto).trim()

val xaldApiUrl = propiedadLocal("XALD_API_URL", "http://10.0.2.2:8000")
val xaldApiKey = propiedadLocal("XALD_API_KEY", "clave-de-pruebas-ci")

android {
    namespace = "com.proyecto.xald"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.proyecto.xald"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "XALD_API_URL", "\"$xaldApiUrl\"")
        buildConfigField("String", "XALD_API_KEY", "\"$xaldApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Módulos del monolito modular
    implementation(project(":parser"))
    implementation(project(":aigemini"))
    implementation(project(":corefinanciero"))
    implementation(project(":syncqueue"))

    // AndroidX & Corrutinas
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Pruebas
    testImplementation(libs.junit)
    testImplementation("androidx.test:core-ktx:1.6.1") // <- Provee ApplicationProvider
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
