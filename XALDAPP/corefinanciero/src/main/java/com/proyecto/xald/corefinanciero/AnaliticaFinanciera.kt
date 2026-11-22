package com.proyecto.xald.corefinanciero

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToInt

// ------------------------------------------------------------------------------------- Modelos

/** Motor de consolidado: lo que el usuario tiene disponible ahora. */
data class Consolidado(
    val saldoDisponible: Double,
    /** Entradas consideradas (posteriores al saldo base, o todas si no hay base). */
    val entradas: Double,
    /** Salidas consideradas (gastos y transferencias enviadas). */
    val salidas: Double,
    val saldoBase: SaldoBaseEntidad?
)

/** Entradas contra salidas de un periodo. */
data class ResumenFlujo(val entradas: Double, val salidas: Double) {
    val neto: Double get() = entradas - salidas
    val total: Double get() = entradas + salidas

    /** Fracción 0..1 del dinero movido que fue entrada. 0 si no hubo movimientos. */
    val proporcionEntradas: Double get() = if (total <= 0.0) 0.0 else entradas / total
}

/** Gasto total de una categoría en el periodo. */
data class TotalCategoria(
    val categoria: String,
    val total: Double,
    /** Fracción 0..1 sobre el gasto total del periodo. */
    val proporcion: Double,
    val cantidad: Int
)

/** Una barra del gráfico temporal. */
data class PuntoSerie(
    val etiqueta: String,
    val entradas: Double,
    val salidas: Double,
    /** El tramo que contiene "ahora" (se resalta en el gráfico). */
    val esActual: Boolean
)

enum class NivelPresupuesto { NORMAL, ATENCION, CRITICO, EXCEDIDO }

/** Cuánto se ha consumido de un presupuesto en su periodo vigente. */
data class EstadoPresupuesto(
    val presupuesto: PresupuestoEntidad,
    val gastado: Double,
    val rango: RangoTiempo
) {
    val limite: Double get() = presupuesto.limite
    val proporcion: Double get() = if (limite <= 0.0) 0.0 else gastado / limite
    val restante: Double get() = limite - gastado

    /** Umbrales de [AnaliticaFinanciera.UMBRALES_ALERTA] (80, 90, 100) ya alcanzados. */
    val umbralesAlcanzados: List<Int>
        get() = AnaliticaFinanciera.UMBRALES_ALERTA.filter { gastado >= limite * it / 100.0 && limite > 0.0 }

    val nivel: NivelPresupuesto
        get() = when (umbralesAlcanzados.maxOrNull()) {
            100 -> NivelPresupuesto.EXCEDIDO
            90 -> NivelPresupuesto.CRITICO
            80 -> NivelPresupuesto.ATENCION
            else -> NivelPresupuesto.NORMAL
        }
}

/** Notificación pendiente por cruzar un umbral de presupuesto. */
data class AlertaPresupuesto(
    val nombre: String,
    val umbral: Int,
    val gastado: Double,
    val limite: Double,
    /** Claves que quedan cubiertas al enviar esta alerta (incluye umbrales menores saltados). */
    val clavesCubiertas: Set<String>
)

enum class SaludFinanciera { SALUDABLE, ATENCION, RIESGO }

/** Indicador de salud financiera del mes. */
data class Semaforo(val salud: SaludFinanciera, val titulo: String, val detalle: String)

/**
 * Resumen "flash" para la píldora superior del inicio. Nunca incluye montos (modo privacidad).
 * [filtroSugerido]: texto para buscar en Movimientos al tocar la tarjeta (null = no lleva a nada).
 */
data class TarjetaFlash(val emoji: String, val texto: String, val filtroSugerido: String? = null)

enum class FiltroTipo(val etiqueta: String) {
    TODOS("Todos"),
    GASTOS("Gastos"),
    INGRESOS("Ingresos"),
    TRANSFERENCIAS("Transferencias")
}

enum class FiltroOrigen(val etiqueta: String) {
    TODOS("Todo origen"),
    SMS("SMS"),
    MANUAL("Manual")
}

enum class OrdenHistorial(val etiqueta: String) {
    RECIENTES("Más recientes"),
    ANTIGUOS("Más antiguos"),
    MAYOR_MONTO("Mayor monto"),
    MENOR_MONTO("Menor monto")
}

data class FiltroHistorial(
    val texto: String = "",
    val tipo: FiltroTipo = FiltroTipo.TODOS,
    val origen: FiltroOrigen = FiltroOrigen.TODOS,
    val orden: OrdenHistorial = OrdenHistorial.RECIENTES
)

/** Todo lo que pinta la pantalla de inicio, calculado de una vez en segundo plano. */
data class PanelFinanciero(
    val periodo: Periodo,
    val consolidado: Consolidado,
    val flujo: ResumenFlujo,
    val serie: List<PuntoSerie>,
    val gastosPorCategoria: List<TotalCategoria>,
    val presupuestos: List<EstadoPresupuesto>,
    val semaforo: Semaforo,
    val flash: TarjetaFlash,
    val totalMovimientos: Int
)

// ------------------------------------------------------------------------------------ Cálculos

/**
 * Cálculos financieros puros (sin Android ni base de datos) sobre la lista de transacciones.
 * Los montos guardados son siempre positivos; el [TipoMovimiento] decide el signo.
 */
object AnaliticaFinanciera {

    val UMBRALES_ALERTA = listOf(80, 90, 100)

    private const val DIA_MS = 86_400_000L
    private const val SEMANA_MS = 7 * DIA_MS

    /** Desde este día del mes el ritmo de gasto ya es representativo para el semáforo. */
    private const val DIA_MINIMO_PARA_RITMO = 7

    // -------------------------------------------------------------------- Consolidado y flujo

    fun consolidar(movimientos: List<TransaccionEntidad>, base: SaldoBaseEntidad?): Consolidado {
        val considerados = if (base == null) movimientos else movimientos.filter { it.fechaTimestamp > base.fechaTimestamp }
        val entradas = considerados.filter { it.tipo.esEntrada }.sumOf { it.monto }
        val salidas = considerados.filterNot { it.tipo.esEntrada }.sumOf { it.monto }
        return Consolidado(
            saldoDisponible = (base?.monto ?: 0.0) + entradas - salidas,
            entradas = entradas,
            salidas = salidas,
            saldoBase = base
        )
    }

    fun flujo(movimientos: List<TransaccionEntidad>, rango: RangoTiempo): ResumenFlujo {
        val enRango = movimientos.filter { it.fechaTimestamp in rango }
        return ResumenFlujo(
            entradas = enRango.filter { it.tipo.esEntrada }.sumOf { it.monto },
            salidas = enRango.filterNot { it.tipo.esEntrada }.sumOf { it.monto }
        )
    }

    /** Solo gastos (no transferencias), agrupados por categoría, del mayor al menor. */
    fun gastosPorCategoria(movimientos: List<TransaccionEntidad>, rango: RangoTiempo): List<TotalCategoria> {
        val gastos = movimientos.filter { it.tipo == TipoMovimiento.GASTO && it.fechaTimestamp in rango }
        val totalGeneral = gastos.sumOf { it.monto }
        if (totalGeneral <= 0.0) return emptyList()
        return gastos
            .groupBy { it.categoria.trim().ifEmpty { CategoriasEspeciales.SIN_CATEGORIZAR } }
            .map { (categoria, lista) ->
                val total = lista.sumOf { it.monto }
                TotalCategoria(categoria, total, total / totalGeneral, lista.size)
            }
            .sortedWith(compareByDescending<TotalCategoria> { it.total }.thenBy { it.categoria })
    }

    fun serie(
        movimientos: List<TransaccionEntidad>,
        periodo: Periodo,
        ahoraMs: Long,
        calendario: Calendario
    ): List<PuntoSerie> = calendario.tramos(periodo, ahoraMs).map { tramo ->
        val flujoTramo = flujo(movimientos, tramo.rango)
        PuntoSerie(tramo.etiqueta, flujoTramo.entradas, flujoTramo.salidas, ahoraMs in tramo.rango)
    }

    fun gastoTotal(movimientos: List<TransaccionEntidad>, rango: RangoTiempo): Double =
        movimientos.filter { it.tipo == TipoMovimiento.GASTO && it.fechaTimestamp in rango }.sumOf { it.monto }

    // --------------------------------------------------------------------------- Presupuestos

    fun estadoPresupuestos(
        movimientos: List<TransaccionEntidad>,
        presupuestos: List<PresupuestoEntidad>,
        ahoraMs: Long,
        calendario: Calendario
    ): List<EstadoPresupuesto> = presupuestos.map { presupuesto ->
        val rango = calendario.rangoPresupuesto(presupuesto.periodoPresupuesto, ahoraMs)
        val gastos = movimientos.filter { it.tipo == TipoMovimiento.GASTO && it.fechaTimestamp in rango }
        val gastado = if (presupuesto.esTotal) {
            gastos.sumOf { it.monto }
        } else {
            gastos.filter { it.categoria.trim().equals(presupuesto.categoria, ignoreCase = true) }.sumOf { it.monto }
        }
        EstadoPresupuesto(presupuesto, gastado, rango)
    }.sortedWith(
        // El global primero; luego los más consumidos.
        compareByDescending<EstadoPresupuesto> { it.presupuesto.esTotal }.thenByDescending { it.proporcion }
    )

    fun claveAlerta(estado: EstadoPresupuesto, umbral: Int): String =
        "${estado.presupuesto.categoria}|${estado.rango.inicioMs}|$umbral"

    /**
     * Alertas de proximidad que aún no se han enviado en el periodo vigente. Si un gasto salta
     * del 70 % al 95 %, se envía una sola alerta (la del 90 %) y el 80 % queda cubierto.
     */
    fun alertasNuevas(estados: List<EstadoPresupuesto>, yaEnviadas: Set<String>): List<AlertaPresupuesto> =
        estados.mapNotNull { estado ->
            val alcanzados = estado.umbralesAlcanzados
            if (alcanzados.isEmpty()) return@mapNotNull null
            val claves = alcanzados.map { claveAlerta(estado, it) }.toSet()
            if ((claves - yaEnviadas).isEmpty()) return@mapNotNull null
            AlertaPresupuesto(
                nombre = estado.presupuesto.nombreVisible,
                umbral = alcanzados.max(),
                gastado = estado.gastado,
                limite = estado.limite,
                clavesCubiertas = claves
            )
        }

    // ------------------------------------------------------------------------------- Semáforo

    fun semaforo(
        movimientos: List<TransaccionEntidad>,
        estadosPresupuesto: List<EstadoPresupuesto>,
        ahoraMs: Long,
        calendario: Calendario
    ): Semaforo {
        if (movimientos.isEmpty()) {
            return Semaforo(
                SaludFinanciera.SALUDABLE,
                "Sin datos todavía",
                "Cuando registres movimientos verás aquí tu salud financiera."
            )
        }

        val excedidos = estadosPresupuesto.filter { it.nivel == NivelPresupuesto.EXCEDIDO }
        if (excedidos.isNotEmpty()) {
            val nombres = excedidos.joinToString(", ") { it.presupuesto.nombreVisible }
            return Semaforo(SaludFinanciera.RIESGO, "Te pasaste del presupuesto", "Superaste el tope de $nombres.")
        }

        val ritmo = ritmoDeGasto(movimientos, ahoraMs, calendario)
        if (ritmo != null && ritmo >= 1.3) {
            return Semaforo(
                SaludFinanciera.RIESGO,
                "Gastos acelerados",
                "Este mes gastas un ${porcentaje(ritmo - 1)}% más rápido que de costumbre."
            )
        }

        val cercanos = estadosPresupuesto.filter {
            it.nivel == NivelPresupuesto.ATENCION || it.nivel == NivelPresupuesto.CRITICO
        }
        if (cercanos.isNotEmpty()) {
            val masCercano = cercanos.maxBy { it.proporcion }
            return Semaforo(
                SaludFinanciera.ATENCION,
                "Cerca del tope",
                "${masCercano.presupuesto.nombreVisible} va en el ${porcentaje(masCercano.proporcion)}% de su presupuesto."
            )
        }
        if (ritmo != null && ritmo >= 1.1) {
            return Semaforo(
                SaludFinanciera.ATENCION,
                "Ritmo de gasto alto",
                "Este mes gastas un ${porcentaje(ritmo - 1)}% más rápido que de costumbre."
            )
        }
        val flujoMes = flujo(movimientos, calendario.rango(Periodo.MES, ahoraMs))
        if (flujoMes.entradas > 0.0 && flujoMes.salidas > flujoMes.entradas) {
            return Semaforo(
                SaludFinanciera.ATENCION,
                "Sale más de lo que entra",
                "Este mes ha salido más dinero del que entró."
            )
        }

        val detalle = when {
            ritmo != null && ritmo <= 0.9 -> "Este mes gastas un ${porcentaje(1 - ritmo)}% menos que de costumbre."
            ritmo != null -> "Tus gastos van al ritmo de siempre."
            else -> "Ningún presupuesto en riesgo este mes."
        }
        return Semaforo(SaludFinanciera.SALUDABLE, "Vas bien", detalle)
    }

    /**
     * Gasto diario promedio de este mes dividido entre el de los 3 meses anteriores con datos.
     * `null` si no hay historial o si el mes apenas empieza (el primer pago grande del mes
     * dispararía una falsa alarma).
     */
    fun ritmoDeGasto(movimientos: List<TransaccionEntidad>, ahoraMs: Long, calendario: Calendario): Double? {
        val (diaDelMes, _) = calendario.avanceDelMes(ahoraMs)
        if (diaDelMes < DIA_MINIMO_PARA_RITMO) return null

        val mesActual = calendario.rango(Periodo.MES, ahoraMs)
        val diarioActual = gastoTotal(movimientos, RangoTiempo(mesActual.inicioMs, ahoraMs + 1)) / diaDelMes

        var gastoHistorico = 0.0
        var diasHistoricos = 0
        var referencia = ahoraMs
        repeat(3) {
            val anterior = calendario.rangoAnterior(Periodo.MES, referencia)
            val gasto = gastoTotal(movimientos, anterior)
            if (gasto > 0.0) {
                gastoHistorico += gasto
                diasHistoricos += calendario.avanceDelMes(anterior.inicioMs).second
            }
            referencia = anterior.inicioMs
        }
        if (diasHistoricos == 0 || gastoHistorico <= 0.0) return null
        return diarioActual / (gastoHistorico / diasHistoricos)
    }

    // ---------------------------------------------------------------------------- Tarjeta flash

    fun tarjetaFlash(movimientos: List<TransaccionEntidad>, ahoraMs: Long, calendario: Calendario): TarjetaFlash {
        if (movimientos.isEmpty()) {
            return TarjetaFlash("👋", "Bienvenido a XALD: tus movimientos aparecerán aquí apenas llegue un SMS del banco.")
        }

        // 1. Esta semana contra la pasada, hasta el mismo momento de la semana.
        val inicioSemana = calendario.rango(Periodo.SEMANA, ahoraMs).inicioMs
        val estaSemana = gastoTotal(movimientos, RangoTiempo(inicioSemana, ahoraMs + 1))
        val semanaPasada = gastoTotal(movimientos, RangoTiempo(inicioSemana - SEMANA_MS, ahoraMs + 1 - SEMANA_MS))
        if (estaSemana > 0.0 && semanaPasada > 0.0) {
            val cambio = (estaSemana - semanaPasada) / semanaPasada
            if (abs(cambio) >= 0.10) {
                return if (cambio < 0) {
                    TarjetaFlash("📉", "Has gastado un ${porcentaje(-cambio)}% menos que la semana pasada.")
                } else {
                    TarjetaFlash("📈", "Has gastado un ${porcentaje(cambio)}% más que la semana pasada.")
                }
            }
        }

        // 2. Movimientos sin categoría que ensucian las estadísticas.
        val mes = calendario.rango(Periodo.MES, ahoraMs)
        val sinCategoria = movimientos.count {
            it.tipo == TipoMovimiento.GASTO && it.fechaTimestamp in mes &&
                it.categoria.trim().equals(CategoriasEspeciales.SIN_CATEGORIZAR, ignoreCase = true)
        }
        if (sinCategoria >= 3) {
            return TarjetaFlash(
                "✏️",
                "Tienes $sinCategoria gastos sin categoría este mes. Toca aquí para clasificarlos.",
                filtroSugerido = CategoriasEspeciales.SIN_CATEGORIZAR
            )
        }

        // 3. Categoría principal del mes.
        val principal = gastosPorCategoria(movimientos, mes)
            .firstOrNull { !it.categoria.equals(CategoriasEspeciales.SIN_CATEGORIZAR, ignoreCase = true) }
        if (principal != null) {
            return TarjetaFlash(
                "🏷️",
                "Tu categoría principal este mes es ${principal.categoria} (${porcentaje(principal.proporcion)}% del gasto).",
                filtroSugerido = principal.categoria
            )
        }

        return TarjetaFlash("✨", "Aún no hay gastos este mes. ¡Buen comienzo!")
    }

    // -------------------------------------------------------------------------------- Historial

    fun filtrar(movimientos: List<TransaccionEntidad>, filtro: FiltroHistorial): List<TransaccionEntidad> {
        val consulta = normalizar(filtro.texto)
        val digitos = filtro.texto.filter { it.isDigit() }
        val soloNumero = consulta.isNotEmpty() && filtro.texto.all { it.isDigit() || it in " .,$" }

        val filtrados = movimientos.filter { mov ->
            val tipoOk = when (filtro.tipo) {
                FiltroTipo.TODOS -> true
                FiltroTipo.GASTOS -> mov.tipo == TipoMovimiento.GASTO
                FiltroTipo.INGRESOS -> mov.tipo == TipoMovimiento.INGRESO
                FiltroTipo.TRANSFERENCIAS -> mov.tipo.esTransferencia
            }
            val origenOk = when (filtro.origen) {
                FiltroOrigen.TODOS -> true
                FiltroOrigen.SMS -> mov.origen == OrigenMovimiento.SMS
                FiltroOrigen.MANUAL -> mov.origen == OrigenMovimiento.MANUAL
            }
            val textoOk = when {
                consulta.isEmpty() -> true
                soloNumero -> mov.monto.roundToLongSeguro().toString().contains(digitos)
                else -> normalizar(mov.comercio).contains(consulta) ||
                    normalizar(mov.categoria).contains(consulta) ||
                    normalizar(mov.tipo.etiqueta).contains(consulta)
            }
            tipoOk && origenOk && textoOk
        }

        val porFecha = compareByDescending<TransaccionEntidad> { it.fechaTimestamp }.thenBy { it.id }
        return when (filtro.orden) {
            OrdenHistorial.RECIENTES -> filtrados.sortedWith(porFecha)
            OrdenHistorial.ANTIGUOS -> filtrados.sortedWith(compareBy<TransaccionEntidad> { it.fechaTimestamp }.thenBy { it.id })
            OrdenHistorial.MAYOR_MONTO -> filtrados.sortedWith(compareByDescending<TransaccionEntidad> { it.monto }.then(porFecha))
            OrdenHistorial.MENOR_MONTO -> filtrados.sortedWith(compareBy<TransaccionEntidad> { it.monto }.then(porFecha))
        }
    }

    // ------------------------------------------------------------------------------------ Panel

    fun construirPanel(
        movimientos: List<TransaccionEntidad>,
        saldoBase: SaldoBaseEntidad?,
        presupuestos: List<PresupuestoEntidad>,
        periodo: Periodo,
        ahoraMs: Long,
        calendario: Calendario
    ): PanelFinanciero {
        val rango = calendario.rango(periodo, ahoraMs)
        val estados = estadoPresupuestos(movimientos, presupuestos, ahoraMs, calendario)
        return PanelFinanciero(
            periodo = periodo,
            consolidado = consolidar(movimientos, saldoBase),
            flujo = flujo(movimientos, rango),
            serie = serie(movimientos, periodo, ahoraMs, calendario),
            gastosPorCategoria = gastosPorCategoria(movimientos, rango),
            presupuestos = estados,
            semaforo = semaforo(movimientos, estados, ahoraMs, calendario),
            flash = tarjetaFlash(movimientos, ahoraMs, calendario),
            totalMovimientos = movimientos.size
        )
    }

    // -------------------------------------------------------------------------------- Utilidades

    /** 0.153 → 15. */
    fun porcentaje(fraccion: Double): Int = (fraccion * 100).roundToInt()

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .trim()

    private fun Double.roundToLongSeguro(): Long = if (isFinite()) Math.round(this) else 0L
}
