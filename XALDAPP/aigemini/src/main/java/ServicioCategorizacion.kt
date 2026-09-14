package com.proyecto.xald.aigemini

/**
 * Contrato público que expone el módulo de Categorización.
 */
interface ServicioCategorizacion {
    suspend fun categorizar(descripcion: String): CategoriaResultado
}

fun crearServicioCategorizacion(): ServicioCategorizacion = CategorizadorGemini()