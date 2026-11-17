package com.proyecto.xald.aigemini

/**
 * Resultado de la clasificación por Inteligencia Artificial.
 */
sealed interface CategoriaResultado {
    data class Exitosa(
        val categoria: String,
        val confianza: Double = 1.0
    ) : CategoriaResultado

    data class Fallo(
        val motivo: String,
        val categoriaFallback: String = "Sin Categorizar"
    ) : CategoriaResultado
}

/** Categorías permitidas por la aplicación para evitar salidas ambiguas de la IA. */
object CategoriasPermitidas {
    val lista = listOf(
        "Supermercado",
        "Restaurante",
        "Transporte",
        "Servicios",
        "Entretenimiento",
        "Salud",
        "Ropa y Calzado",
        "Educación",
        "Tecnología",
        "Sin Categorizar"
    )

    fun normalizar(categoriaSugerida: String): String {
        val limpia = categoriaSugerida.trim()
        return lista.firstOrNull { it.equals(limpia, ignoreCase = true) } ?: "Sin Categorizar"
    }
}