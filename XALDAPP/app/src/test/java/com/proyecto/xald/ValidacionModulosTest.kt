package com.proyecto.xald

import org.junit.Test
import java.io.File
import org.junit.Assert.assertFalse

class ValidacionModulosTest {

    @Test
    fun validarAislamientoEntreModulos() {
        // Obtenemos la ruta raíz del proyecto a partir del directorio de trabajo actual
        val rootDir = File(System.getProperty("user.dir")).parentFile ?: File(".")

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