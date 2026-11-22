package com.proyecto.xald.ui

import com.proyecto.xald.corefinanciero.CategoriasEspeciales
import com.proyecto.xald.corefinanciero.TipoMovimiento

/** Ícono (emoji) y color de cada categoría, para listas, gráficos y presupuestos. */
object EstiloCategoria {

    private data class Estilo(val emoji: String, val color: Int)

    private val ESTILOS = mapOf(
        "Supermercado" to Estilo("🛒", 0xFF12B76A.toInt()),
        "Restaurante" to Estilo("🍽️", 0xFFF79009.toInt()),
        "Transporte" to Estilo("🚗", 0xFF2E90FA.toInt()),
        "Servicios" to Estilo("💡", 0xFF7A5AF8.toInt()),
        "Entretenimiento" to Estilo("🎬", 0xFFEE46BC.toInt()),
        "Salud" to Estilo("💊", 0xFF15B79E.toInt()),
        "Ropa y Calzado" to Estilo("👕", 0xFFF63D68.toInt()),
        "Educación" to Estilo("🎓", 0xFF0BA5EC.toInt()),
        "Tecnología" to Estilo("💻", 0xFF6172F3.toInt()),
        CategoriasEspeciales.SIN_CATEGORIZAR to Estilo("❔", 0xFF98A2B3.toInt()),
        CategoriasEspeciales.INGRESOS to Estilo("💰", 0xFF12B76A.toInt()),
        CategoriasEspeciales.TRANSFERENCIAS to Estilo("🔁", 0xFF6C5CE7.toInt()),
        CategoriasEspeciales.PRESUPUESTO_TOTAL to Estilo("🎯", 0xFF6C5CE7.toInt())
    )

    /** Colores para categorías nuevas que no estén en la tabla (se elige uno estable por nombre). */
    private val RESPALDO = listOf(0xFF6C5CE7, 0xFF2E90FA, 0xFFF79009, 0xFF15B79E, 0xFFEE46BC, 0xFF0BA5EC).map { it.toInt() }

    fun emoji(categoria: String): String = buscar(categoria)?.emoji ?: "🏷️"

    fun color(categoria: String): Int = buscar(categoria)?.color ?: RESPALDO[Math.floorMod(categoria.lowercase().hashCode(), RESPALDO.size)]

    /** Emoji de un movimiento: transferencias e ingresos se reconocen por el tipo. */
    fun emojiMovimiento(tipo: TipoMovimiento, categoria: String): String = when (tipo) {
        TipoMovimiento.GASTO -> emoji(categoria)
        TipoMovimiento.INGRESO -> "💰"
        TipoMovimiento.TRANSFERENCIA_ENVIADA -> "↗️"
        TipoMovimiento.TRANSFERENCIA_RECIBIDA -> "↙️"
    }

    /** Mismo color con transparencia (para el fondo suave de los íconos). */
    fun suave(color: Int, alfa: Int = 0x26): Int = (color and 0x00FFFFFF) or (alfa shl 24)

    private fun buscar(categoria: String): Estilo? =
        ESTILOS.entries.firstOrNull { it.key.equals(categoria.trim(), ignoreCase = true) }?.value
}
