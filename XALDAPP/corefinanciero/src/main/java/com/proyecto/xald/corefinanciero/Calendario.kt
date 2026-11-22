package com.proyecto.xald.corefinanciero

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Periodo de los paneles estadísticos. */
enum class Periodo(val etiqueta: String) {
    SEMANA("Semana"),
    MES("Mes"),
    ANIO("Año")
}

/** Intervalo [inicioMs, finMs): incluye el inicio y excluye el fin. */
data class RangoTiempo(val inicioMs: Long, val finMs: Long) {
    operator fun contains(instanteMs: Long): Boolean = instanteMs >= inicioMs && instanteMs < finMs
}

/** Una barra del gráfico temporal: su etiqueta y el rango que agrupa. */
data class TramoTiempo(val etiqueta: String, val rango: RangoTiempo)

/**
 * Cálculo de rangos de fechas en la zona horaria del teléfono. Las semanas empiezan el lunes,
 * como en Colombia. Se inyecta la zona para que las pruebas no dependan del reloj de la máquina.
 */
class Calendario(private val zona: ZoneId = ZoneId.systemDefault()) {

    fun rango(periodo: Periodo, ahoraMs: Long): RangoTiempo {
        val inicio = inicioDe(periodo, fecha(ahoraMs))
        return RangoTiempo(ms(inicio), ms(siguiente(periodo, inicio)))
    }

    fun rangoAnterior(periodo: Periodo, ahoraMs: Long): RangoTiempo {
        val inicioActual = inicioDe(periodo, fecha(ahoraMs))
        val inicioAnterior = when (periodo) {
            Periodo.SEMANA -> inicioActual.minusWeeks(1)
            Periodo.MES -> inicioActual.minusMonths(1)
            Periodo.ANIO -> inicioActual.minusYears(1)
        }
        return RangoTiempo(ms(inicioAnterior), ms(inicioActual))
    }

    fun rangoPresupuesto(periodo: PeriodoPresupuesto, ahoraMs: Long): RangoTiempo = when (periodo) {
        PeriodoPresupuesto.SEMANAL -> rango(Periodo.SEMANA, ahoraMs)
        PeriodoPresupuesto.MENSUAL -> rango(Periodo.MES, ahoraMs)
    }

    /**
     * Barras del gráfico temporal del periodo actual:
     * semana → 7 días (Lun..Dom), mes → tramos de 7 días (1-7, 8-14, ...), año → 12 meses.
     */
    fun tramos(periodo: Periodo, ahoraMs: Long): List<TramoTiempo> {
        val inicio = inicioDe(periodo, fecha(ahoraMs))
        return when (periodo) {
            Periodo.SEMANA -> (0 until 7).map { i ->
                val dia = inicio.plusDays(i.toLong())
                TramoTiempo(DIAS_CORTOS[i], RangoTiempo(ms(dia), ms(dia.plusDays(1))))
            }
            Periodo.MES -> {
                val finMes = inicio.plusMonths(1)
                val tramos = mutableListOf<TramoTiempo>()
                var desde = inicio
                while (desde.isBefore(finMes)) {
                    val hasta = minOf(desde.plusDays(7), finMes)
                    val etiqueta = "${desde.dayOfMonth}-${hasta.minusDays(1).dayOfMonth}"
                    tramos += TramoTiempo(etiqueta, RangoTiempo(ms(desde), ms(hasta)))
                    desde = hasta
                }
                tramos
            }
            Periodo.ANIO -> (0 until 12).map { i ->
                val mes = inicio.plusMonths(i.toLong())
                TramoTiempo(MESES_CORTOS[i], RangoTiempo(ms(mes), ms(mes.plusMonths(1))))
            }
        }
    }

    /** Día del mes de [ahoraMs] (1..31) y cantidad de días de ese mes. */
    fun avanceDelMes(ahoraMs: Long): Pair<Int, Int> {
        val hoy = fecha(ahoraMs)
        return hoy.dayOfMonth to hoy.lengthOfMonth()
    }

    /** Inicio del día de [instanteMs] (para agrupar el historial por días). */
    fun inicioDelDia(instanteMs: Long): Long = ms(fecha(instanteMs))

    fun fecha(instanteMs: Long): LocalDate =
        Instant.ofEpochMilli(instanteMs).atZone(zona).toLocalDate()

    private fun inicioDe(periodo: Periodo, dia: LocalDate): LocalDate = when (periodo) {
        Periodo.SEMANA -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        Periodo.MES -> dia.withDayOfMonth(1)
        Periodo.ANIO -> dia.withDayOfYear(1)
    }

    private fun siguiente(periodo: Periodo, inicio: LocalDate): LocalDate = when (periodo) {
        Periodo.SEMANA -> inicio.plusWeeks(1)
        Periodo.MES -> inicio.plusMonths(1)
        Periodo.ANIO -> inicio.plusYears(1)
    }

    // atStartOfDay(zona) respeta los cambios de horario (Colombia no tiene, otras zonas sí).
    private fun ms(dia: LocalDate): Long = dia.atStartOfDay(zona).toInstant().toEpochMilli()

    companion object {
        val DIAS_CORTOS = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        val MESES_CORTOS = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic")
    }
}
