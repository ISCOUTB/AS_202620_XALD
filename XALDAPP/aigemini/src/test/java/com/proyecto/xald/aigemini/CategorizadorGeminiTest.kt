package com.proyecto.xald.aigemini

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CategorizadorGeminiTest {

    @Test
    fun categorizarComercio_apiKeyVacia_retornaFalloYFallbackSinCategorizar() = runTest {
        val servicio = crearServicioCategorizacion(apiKey = "")
        val resultado = servicio.categorizarComercio("Éxito Cartagena")

        assertTrue(resultado is CategoriaResultado.Fallo)
        val fallo = resultado as CategoriaResultado.Fallo
        assertEquals("Sin Categorizar", fallo.categoriaFallback)
        assertTrue(fallo.motivo.contains("API Key"))
    }

    @Test
    fun categorizarComercio_comercioDesconocido_retornaFalloSinConsumoDeIa() = runTest {
        val servicio = crearServicioCategorizacion(apiKey = "fake_api_key_test")
        val resultado = servicio.categorizarComercio("DESCONOCIDO")

        assertTrue(resultado is CategoriaResultado.Fallo)
        val fallo = resultado as CategoriaResultado.Fallo
        assertEquals("Sin Categorizar", fallo.categoriaFallback)
    }

    @Test
    fun categorizarComercio_comercioEnBlanco_retornaFalloInmediato() = runTest {
        val servicio = crearServicioCategorizacion(apiKey = "fake_api_key_test")
        val resultado = servicio.categorizarComercio("   ")

        assertTrue(resultado is CategoriaResultado.Fallo)
    }

    @Test
    fun normalizarCategoria_sensibilidadMayusculasYMinusculas_retornaCategoriaOficial() {
        // Coincidencia exacta ignorando mayúsculas/minúsculas
        assertEquals("Supermercado", CategoriasPermitidas.normalizar("supermercado"))
        assertEquals("Restaurante", CategoriasPermitidas.normalizar("RESTAURANTE"))
        assertEquals("Transporte", CategoriasPermitidas.normalizar("  transporte "))
    }

    @Test
    fun normalizarCategoria_categoriaInexistente_retornaFallbackSinCategorizar() {
        // Si Gemini inventa una categoría fuera de la allowlist
        val resultado = CategoriasPermitidas.normalizar("Criptomonedas")
        assertEquals("Sin Categorizar", resultado)
    }
}