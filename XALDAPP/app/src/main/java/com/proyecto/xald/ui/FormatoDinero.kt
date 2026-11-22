package com.proyecto.xald.ui

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * Montos en pesos colombianos: "$ 1.234.567". Con [privado] = true se enmascaran ("$ ****")
 * para el modo privacidad.
 */
object FormatoDinero {

    const val OCULTO = "$ ****"

    // Separadores fijos (punto de miles, coma decimal) para que no cambien con el idioma del teléfono.
    private val simbolos = DecimalFormatSymbols(Locale.ROOT).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    // DecimalFormat no es seguro entre hilos: uno por hilo.
    private val formatoEntero = ThreadLocal.withInitial { DecimalFormat("#,##0", simbolos) }

    /**
     * @param conSigno antepone "+" a los positivos (para entradas y flujos netos).
     */
    fun formatear(monto: Double, privado: Boolean = false, conSigno: Boolean = false): String {
        if (privado) return if (conSigno && monto > 0) "+$OCULTO" else OCULTO
        if (!monto.isFinite()) return "$ 0"
        val cuerpo = formatoEntero.get()!!.format(Math.round(abs(monto)))
        return when {
            Math.round(monto) < 0 -> "−$ $cuerpo"
            conSigno && Math.round(monto) > 0 -> "+$ $cuerpo"
            else -> "$ $cuerpo"
        }
    }

    /** Monto de un movimiento con el signo de su tipo: "+$ 50.000" o "−$ 12.000". */
    fun movimiento(monto: Double, esEntrada: Boolean, privado: Boolean): String {
        if (privado) return (if (esEntrada) "+" else "−") + OCULTO
        val cuerpo = formatear(abs(monto))
        return (if (esEntrada) "+" else "−") + cuerpo
    }
}

/**
 * Convierte lo que el usuario escribe ("45.900", "45900", "1.234,50", "$ 2,500,000", "-20.000")
 * en un número. `null` si no es un monto.
 *
 * Regla de separadores: si aparecen punto y coma, el último es el decimal. Si aparece solo uno
 * de los dos, es separador de miles cuando se repite o deja exactamente 3 dígitos después
 * ("45.900"); si no, es decimal ("45,5").
 */
object MontoUsuario {

    fun parsear(texto: String): Double? {
        var limpio = texto.trim().replace("$", "").replace(" ", "").replace("COP", "", ignoreCase = true)
        val negativo = limpio.startsWith("-") || limpio.startsWith("−")
        limpio = limpio.trimStart('-', '−')
        if (limpio.isEmpty() || !limpio.all { it.isDigit() || it == '.' || it == ',' }) return null
        if (!limpio.any { it.isDigit() }) return null

        val normalizado = when {
            limpio.contains('.') && limpio.contains(',') -> {
                val decimal = if (limpio.lastIndexOf('.') > limpio.lastIndexOf(',')) '.' else ','
                val miles = if (decimal == '.') ',' else '.'
                limpio.replace(miles.toString(), "").replace(decimal, '.')
            }
            limpio.contains('.') -> separadorUnico(limpio, '.')
            limpio.contains(',') -> separadorUnico(limpio, ',')
            else -> limpio
        } ?: return null

        val valor = normalizado.toDoubleOrNull() ?: return null
        if (!valor.isFinite()) return null
        return if (negativo) -valor else valor
    }

    private fun separadorUnico(texto: String, separador: Char): String? {
        val partes = texto.split(separador)
        val esMiles = partes.size > 2 || partes.last().length == 3
        return if (esMiles) {
            // "1.234.56" no es un número válido.
            if (partes.drop(1).any { it.length != 3 } || partes.first().isEmpty()) null else partes.joinToString("")
        } else {
            partes.joinToString(".")
        }
    }
}
