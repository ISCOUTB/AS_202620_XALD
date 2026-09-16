package com.proyecto.xald.aigemini

/**
 * Capa Anticorrupción (ACL) para la API de Gemini.
 * Implementa [ServicioCategorizacion] adaptando respuestas externas.
 */
internal class CategorizadorGemini : ServicioCategorizacion {

    override suspend fun categorizar(descripcion: String): CategoriaResultado {
        // Mock / Stub temporal para mantener la compilación en verde
        val categoriaTraducida = descripcion.trim().uppercase()

        return CategoriaResultado(
            nombreCategoria = if (categoriaTraducida.isEmpty()) "VARIOS" else categoriaTraducida,
            nivelConfianza = 1.0f
        )
    }
}