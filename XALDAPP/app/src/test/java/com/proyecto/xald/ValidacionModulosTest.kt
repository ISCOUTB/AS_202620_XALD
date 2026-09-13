package com.proyecto.xald

import org.junit.Test
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class ValidacionModulosTest {

    @Test
    fun validarAislamientoEntreModulos() {
        // Resolución robusta de la raíz del repo (funciona en CI y local)
        val rootDir = encontrarRootRepo()

        val aigeminiDir = File(rootDir, "aigemini")
        val coreFinancieroDir = File(rootDir, "corefinanciero")
        val parserDir = File(rootDir, "parser")

        // 1. :aigemini no debe importar :parser
        if (aigeminiDir.exists()) {
            val importsAigemini = buscarImportsEnDirectorio(aigeminiDir, "com.proyecto.xald.parser")
            assertFalse("Violación: :aigemini importa código de :parser", importsAigemini)
        }

        // 2. :corefinanciero no debe importar :aigemini
        if (coreFinancieroDir.exists()) {
            val importsCore = buscarImportsEnDirectorio(coreFinancieroDir, "com.proyecto.xald.aigemini")
            assertFalse("Violación: :corefinanciero importa código de :aigemini", importsCore)
        }

        // 3. :parser no debe importar :syncqueue
        if (parserDir.exists()) {
            val importsParser = buscarImportsEnDirectorio(parserDir, "com.proyecto.xald.syncqueue")
            assertFalse("Violación: :parser importa código de :syncqueue", importsParser)
        }

        // 4. Verificar que las implementaciones internas estén declaradas como `internal`
        val clasesInternasRequeridas = mapOf(
            "parser" to listOf("ParseoSms"),
            "aigemini" to listOf("CategorizadorGemini"),
            "corefinanciero" to listOf("GestorCoreFinanciero"),
            "syncqueue" to listOf("ColaSincronizacion")
        )

        val faltantes = mutableListOf<String>()
        clasesInternasRequeridas.forEach { (modulo, clases) ->
            val dir = File(rootDir, modulo)
            if (dir.exists()) {
                clases.forEach { nombre ->
                    val foundInternal = dir.walkTopDown()
                        .filter { it.extension == "kt" }
                        .any { file -> file.readText().contains(Regex("\\binternal\\s+class\\s+$nombre\\b")) }
                    if (!foundInternal) {
                        faltantes.add("$modulo/$nombre")
                    }
                }
            }
        }
        assertTrue("Faltan declaraciones 'internal' en: $faltantes", faltantes.isEmpty())

        // 5. Verificar que no existan dependencias de proyecto no deseadas en los build.gradle.kts
        val archivosBuild = listOf(
            "parser/build.gradle.kts",
            "aigemini/build.gradle.kts",
            "corefinanciero/build.gradle.kts"
        )
        val depsViolaciones = archivosBuild.mapNotNull { path ->
            val file = File(rootDir, path)
            if (!file.exists()) return@mapNotNull null
            val text = file.readText()
            // Busca project(":otherModule") o project(":module:sub") patrones comunes
            val regex = Regex("project\\(\s*[\"']:(parser|aigemini|corefinanciero|syncqueue):?[\"']\\s*\\)")
            val matches = regex.findAll(text).map { it.value }.toList()
            if (matches.isNotEmpty()) "$path -> ${matches.joinToString("; ")}" else null
        }
        assertTrue("Dependencias de proyecto no deseadas en build files: $depsViolaciones", depsViolaciones.isEmpty())
    }

    private fun encontrarRootRepo(start: File = File(System.getProperty("user.dir"))): File {
        var cur: File? = start
        while (cur != null && cur.exists()) {
            if (File(cur, ".git").exists() || File(cur, "settings.gradle.kts").exists() || File(cur, "settings.gradle").exists()) {
                return cur
            }
            cur = cur.parentFile
        }
        return start
    }

    private fun buscarImportsEnDirectorio(dir: File, patron: String): Boolean {
        return dir.walkTopDown()
            .filter { it.extension == "kt" }
            .any { file ->
                file.useLines { lines ->
                    lines.any { line ->
                        line.trim().startsWith("import") && line.contains(patron)
                    }
                }
            }
    }
}
