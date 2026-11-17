package com.proyecto.xald.aigemini

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orden de categorización de ADR-0012: Gemini primero y reglas locales de respaldo.
 * Gemini se reemplaza por un doble que devuelve una respuesta fija (no hay red en las pruebas).
 */
class CategorizadorConRespaldoTest {

    private class GeminiFijo(private val respuesta: CategoriaResultado) : ServicioCategorizacion {
        var llamadas = 0
        override suspend fun categorizarComercio(comercio: String): CategoriaResultado {
            llamadas++
            return respuesta
        }
    }

    private fun conRespaldo(gemini: ServicioCategorizacion) =
        CategorizadorConRespaldo(principal = gemini, respaldo = CategorizadorLocal())

    @Test
    fun geminiCategoriza_seUsaSuCategoria() = runTest {
        val gemini = GeminiFijo(CategoriaResultado.Exitosa("Restaurante"))
        val resultado = conRespaldo(gemini).categorizarComercio("ARA CARIBE")
        assertEquals("Restaurante", (resultado as CategoriaResultado.Exitosa).categoria)
    }

    @Test
    fun geminiFalla_seUsanLasReglasLocales() = runTest {
        val gemini = GeminiFijo(CategoriaResultado.Fallo("Timeout"))
        val resultado = conRespaldo(gemini).categorizarComercio("ARA CARIBE")
        assertEquals("Supermercado", (resultado as CategoriaResultado.Exitosa).categoria)
    }

    @Test
    fun geminiRespondeSinCategorizar_seUsanLasReglasLocales() = runTest {
        // Antes este caso devolvía "Sin Categorizar" sin probar las reglas locales.
        val gemini = GeminiFijo(CategoriaResultado.Exitosa("Sin Categorizar"))
        val resultado = conRespaldo(gemini).categorizarComercio("UBER *TRIP")
        assertEquals("Transporte", (resultado as CategoriaResultado.Exitosa).categoria)
        assertEquals(1, gemini.llamadas)
    }

    @Test
    fun ningunoCategoriza_quedaSinCategorizar() = runTest {
        val gemini = GeminiFijo(CategoriaResultado.Exitosa("Sin Categorizar"))
        val resultado = conRespaldo(gemini).categorizarComercio("PARA TI SAS")
        assertTrue(resultado is CategoriaResultado.Fallo)
        assertEquals("Sin Categorizar", (resultado as CategoriaResultado.Fallo).categoriaFallback)
    }
}
