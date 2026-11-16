plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    kotlin("kapt")
}

android {
    namespace = "com.proyecto.xald.syncqueue"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        // Fuerza a Kotlin 2.2.10 a generar metadatos 2.0.0 compatibles con Room 2.6.1
        freeCompilerArgs = freeCompilerArgs + listOf("-Xmetadata-version=2.0.0")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(project(":corefinanciero"))
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    // Pruebas en JVM local (Robolectric + Room in-memory)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    // Pruebas del Worker y del programador (WorkManager)
    testImplementation("androidx.work:work-testing:2.9.0")

    // Pruebas instrumentadas
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    // Room (Base de datos local SQLite)
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")
    // Fuerza un sqlite-jdbc con binario para Windows en el verificador de consultas de Room
    kapt("org.xerial:sqlite-jdbc:3.46.1.3")

    // WorkManager (Ejecución en segundo plano)
    val workVersion = "2.9.0"
    implementation("androidx.work:work-runtime-ktx:$workVersion")

    // Gson (Conversión a JSON)
    implementation("com.google.code.gson:gson:2.10.1")
}

