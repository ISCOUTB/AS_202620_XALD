package com.proyecto.xald.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Fechas cortas en español de Colombia ("Hoy", "Ayer", "Mié 12 nov", "2:30 p. m.").
 * Los nombres están escritos aquí para no depender del idioma configurado en el teléfono.
 */
class FormatoFecha(private val zona: ZoneId = ZoneId.systemDefault()) {

    fun encabezadoDia(instanteMs: Long, ahoraMs: Long): String {
        val dia = fecha(instanteMs)
        val hoy = fecha(ahoraMs)
        return when (ChronoUnit.DAYS.between(dia, hoy)) {
            0L -> "Hoy"
            1L -> "Ayer"
            else -> {
                val base = "${DIAS[dia.dayOfWeek.value - 1]} ${dia.dayOfMonth} ${MESES[dia.monthValue - 1]}"
                if (dia.year == hoy.year) base else "$base ${dia.year}"
            }
        }
    }

    /** "12 nov 2026" */
    fun fechaCorta(instanteMs: Long): String {
        val dia = fecha(instanteMs)
        return "${dia.dayOfMonth} ${MESES[dia.monthValue - 1]} ${dia.year}"
    }

    /** "Hoy", "Ayer" o "12 nov 2026" (para el campo de fecha de los formularios). */
    fun fechaFormulario(instanteMs: Long, ahoraMs: Long): String = when (ChronoUnit.DAYS.between(fecha(instanteMs), fecha(ahoraMs))) {
        0L -> "Hoy, ${fechaCorta(instanteMs)}"
        1L -> "Ayer, ${fechaCorta(instanteMs)}"
        else -> fechaCorta(instanteMs)
    }

    /** "2:30 p. m." */
    fun hora(instanteMs: Long): String {
        val momento = Instant.ofEpochMilli(instanteMs).atZone(zona)
        val hora12 = if (momento.hour % 12 == 0) 12 else momento.hour % 12
        val sufijo = if (momento.hour < 12) "a. m." else "p. m."
        return "$hora12:${momento.minute.toString().padStart(2, '0')} $sufijo"
    }

    /** "hace un momento", "hace 5 min", "hace 3 h", "ayer", "el 12 nov 2026". */
    fun relativo(instanteMs: Long, ahoraMs: Long): String {
        val minutos = (ahoraMs - instanteMs) / 60_000L
        return when {
            minutos < 1 -> "hace un momento"
            minutos < 60 -> "hace $minutos min"
            minutos < 24 * 60 && fecha(instanteMs) == fecha(ahoraMs) -> "hace ${minutos / 60} h"
            ChronoUnit.DAYS.between(fecha(instanteMs), fecha(ahoraMs)) == 1L -> "ayer a las ${hora(instanteMs)}"
            else -> "el ${fechaCorta(instanteMs)}"
        }
    }

    /** Días que faltan para [finMs] contando hoy (mínimo 1). */
    fun diasRestantes(finMs: Long, ahoraMs: Long): Long =
        ChronoUnit.DAYS.between(fecha(ahoraMs), fecha(finMs - 1)).coerceAtLeast(0L) + 1

    fun fecha(instanteMs: Long): LocalDate = Instant.ofEpochMilli(instanteMs).atZone(zona).toLocalDate()

    /** Milisegundos del mediodía del día indicado (para movimientos manuales con fecha pasada). */
    fun mediodia(anio: Int, mes: Int, dia: Int): Long =
        LocalDate.of(anio, mes, dia).atTime(12, 0).atZone(zona).toInstant().toEpochMilli()

    companion object {
        private val DIAS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        private val MESES = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    }
}
