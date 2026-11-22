package com.proyecto.xald.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.proyecto.xald.R
import com.proyecto.xald.corefinanciero.AnaliticaFinanciera
import com.proyecto.xald.corefinanciero.PanelFinanciero
import com.proyecto.xald.corefinanciero.Periodo
import com.proyecto.xald.corefinanciero.PresupuestoEntidad

/**
 * Pantalla de inicio: tarjeta flash, saldo disponible con semáforo, selector de periodo,
 * entradas vs. salidas, evolución, gastos por categoría, presupuestos y sincronización.
 */
class PantallaInicio(raiz: View, private val acciones: Acciones) {

    interface Acciones {
        fun alCambiarPeriodo(periodo: Periodo)
        fun alAjustarSaldo()
        fun alAlternarPrivacidad()
        fun alAbrirPresupuestos()
        fun alEditarPresupuesto(presupuesto: PresupuestoEntidad?)
        fun alBuscarEnMovimientos(texto: String)
        fun alSincronizar()
    }

    private val tarjetaFlash: View = raiz.findViewById(R.id.tarjetaFlash)
    private val tvFlashEmoji: TextView = raiz.findViewById(R.id.tvFlashEmoji)
    private val tvFlashTexto: TextView = raiz.findViewById(R.id.tvFlashTexto)

    private val puntoSemaforo: PuntoSemaforoView = raiz.findViewById(R.id.puntoSemaforo)
    private val tvSemaforoTitulo: TextView = raiz.findViewById(R.id.tvSemaforoTitulo)
    private val tvSemaforoDetalle: TextView = raiz.findViewById(R.id.tvSemaforoDetalle)
    private val tvSaldoDisponible: TextView = raiz.findViewById(R.id.tvSaldoDisponible)
    private val tvSaldoBaseInfo: TextView = raiz.findViewById(R.id.tvSaldoBaseInfo)

    private val opciones = mapOf(
        Periodo.SEMANA to raiz.findViewById<TextView>(R.id.opcionSemana),
        Periodo.MES to raiz.findViewById<TextView>(R.id.opcionMes),
        Periodo.ANIO to raiz.findViewById<TextView>(R.id.opcionAnio)
    )

    private val tvFlujoNeto: TextView = raiz.findViewById(R.id.tvFlujoNeto)
    private val barraFlujo: BarraFlujoView = raiz.findViewById(R.id.barraFlujo)
    private val tvEntradas: TextView = raiz.findViewById(R.id.tvEntradas)
    private val tvSalidas: TextView = raiz.findViewById(R.id.tvSalidas)
    private val tvEntradasPorcentaje: TextView = raiz.findViewById(R.id.tvEntradasPorcentaje)
    private val tvSalidasPorcentaje: TextView = raiz.findViewById(R.id.tvSalidasPorcentaje)

    private val tvTituloEvolucion: TextView = raiz.findViewById(R.id.tvTituloEvolucion)
    private val tvMaximoEvolucion: TextView = raiz.findViewById(R.id.tvMaximoEvolucion)
    private val grafico: GraficoBarrasView = raiz.findViewById(R.id.graficoEvolucion)

    private val tvTotalGastos: TextView = raiz.findViewById(R.id.tvTotalGastos)
    private val listaCategorias: LinearLayout = raiz.findViewById(R.id.listaCategorias)
    private val tvSinGastos: TextView = raiz.findViewById(R.id.tvSinGastos)

    private val listaPresupuestos: LinearLayout = raiz.findViewById(R.id.listaPresupuestosInicio)
    private val tvSinPresupuestos: TextView = raiz.findViewById(R.id.tvSinPresupuestos)

    private val tvEstadoSync: TextView = raiz.findViewById(R.id.tvEstadoSync)
    private val tvDetalleSync: TextView = raiz.findViewById(R.id.tvDetalleSync)

    private val formatoFecha = FormatoFecha()
    private var filtroFlash: String? = null

    init {
        opciones.forEach { (periodo, vista) -> vista.setOnClickListener { acciones.alCambiarPeriodo(periodo) } }
        raiz.findViewById<View>(R.id.btnAjustarSaldo).setOnClickListener { acciones.alAjustarSaldo() }
        // Gesto rápido del modo privacidad: mantener presionado el saldo.
        tvSaldoDisponible.setOnLongClickListener {
            acciones.alAlternarPrivacidad()
            true
        }
        raiz.findViewById<View>(R.id.btnVerPresupuestos).setOnClickListener { acciones.alAbrirPresupuestos() }
        tvSinPresupuestos.setOnClickListener { acciones.alEditarPresupuesto(null) }
        raiz.findViewById<View>(R.id.btnSincronizarAhora).setOnClickListener { acciones.alSincronizar() }
        tarjetaFlash.setOnClickListener { filtroFlash?.let { acciones.alBuscarEnMovimientos(it) } }
    }

    fun pintar(
        panel: PanelFinanciero,
        pendientes: Int,
        ultimaSincronizacionMs: Long?,
        privado: Boolean,
        animar: Boolean,
        ahoraMs: Long
    ) {
        // Tarjeta flash
        tvFlashEmoji.text = panel.flash.emoji
        tvFlashTexto.text = panel.flash.texto
        filtroFlash = panel.flash.filtroSugerido
        tarjetaFlash.isClickable = filtroFlash != null

        // Saldo y semáforo
        puntoSemaforo.mostrar(panel.semaforo.salud)
        tvSemaforoTitulo.text = panel.semaforo.titulo
        tvSemaforoDetalle.text = panel.semaforo.detalle
        val consolidado = panel.consolidado
        tvSaldoDisponible.text = FormatoDinero.formatear(consolidado.saldoDisponible, privado)
        tvSaldoDisponible.contentDescription = if (privado) "Saldo oculto. Mantén presionado para mostrarlo." else null
        val base = consolidado.saldoBase
        tvSaldoBaseInfo.text = if (base == null) {
            "Entradas menos salidas registradas. Ajusta tu saldo para que cuadre con el banco."
        } else {
            "Desde tu ajuste del ${formatoFecha.fechaCorta(base.fechaTimestamp)}: " +
                "${FormatoDinero.formatear(consolidado.entradas, privado, conSigno = true)} · " +
                FormatoDinero.movimiento(consolidado.salidas, esEntrada = false, privado = privado)
        }

        // Periodo
        opciones.forEach { (periodo, vista) -> vista.isSelected = periodo == panel.periodo }

        // Entradas vs. salidas
        val flujo = panel.flujo
        tvEntradas.text = FormatoDinero.formatear(flujo.entradas, privado)
        tvSalidas.text = FormatoDinero.formatear(flujo.salidas, privado)
        if (flujo.total > 0.0) {
            val pctEntradas = AnaliticaFinanciera.porcentaje(flujo.proporcionEntradas)
            tvEntradasPorcentaje.text = "$pctEntradas%"
            tvSalidasPorcentaje.text = "${100 - pctEntradas}%"
            barraFlujo.mostrar(flujo.proporcionEntradas, animar)
        } else {
            tvEntradasPorcentaje.text = ""
            tvSalidasPorcentaje.text = ""
            barraFlujo.mostrar(null, animar)
        }
        tvFlujoNeto.text = FormatoDinero.formatear(flujo.neto, privado, conSigno = true)
        tvFlujoNeto.setTextColor(tvFlujoNeto.context.getColor(if (flujo.neto >= 0) R.color.xald_entrada else R.color.xald_salida))

        // Evolución
        tvTituloEvolucion.text = when (panel.periodo) {
            Periodo.SEMANA -> "Evolución de la semana"
            Periodo.MES -> "Evolución del mes"
            Periodo.ANIO -> "Evolución del año"
        }
        val maximo = panel.serie.maxOfOrNull { maxOf(it.entradas, it.salidas) } ?: 0.0
        tvMaximoEvolucion.text = if (maximo > 0) "Máx. ${FormatoDinero.formatear(maximo, privado)}" else ""
        grafico.mostrar(panel.serie, animar)

        pintarCategorias(panel, privado, animar)
        pintarPresupuestos(panel, privado, animar, ahoraMs)

        // Sincronización
        tvEstadoSync.text = if (pendientes == 0) "✓ Todo sincronizado" else "⏳ $pendientes ${if (pendientes == 1) "movimiento" else "movimientos"} por enviar"
        tvDetalleSync.text = if (ultimaSincronizacionMs != null) {
            "Último envío ${formatoFecha.relativo(ultimaSincronizacionMs, ahoraMs)}. Tus datos se guardan primero en el teléfono."
        } else {
            "Tus datos se guardan primero en el teléfono y se envían en segundo plano."
        }
    }

    private fun pintarCategorias(panel: PanelFinanciero, privado: Boolean, animar: Boolean) {
        listaCategorias.removeAllViews()
        val categorias = panel.gastosPorCategoria
        tvTotalGastos.text = if (categorias.isEmpty()) "" else FormatoDinero.formatear(categorias.sumOf { it.total }, privado)
        tvSinGastos.visibility = if (categorias.isEmpty()) View.VISIBLE else View.GONE

        val inflador = LayoutInflater.from(listaCategorias.context)
        categorias.take(MAX_CATEGORIAS).forEach { total ->
            val fila = inflador.inflate(R.layout.item_categoria, listaCategorias, false)
            fila.findViewById<TextView>(R.id.tvIconoCategoria).text = EstiloCategoria.emoji(total.categoria)
            fila.findViewById<TextView>(R.id.tvNombreCategoria).text = total.categoria
            fila.findViewById<TextView>(R.id.tvMontoCategoria).text = FormatoDinero.formatear(total.total, privado)
            fila.findViewById<TextView>(R.id.tvPorcentajeCategoria).text = "${AnaliticaFinanciera.porcentaje(total.proporcion)}%"
            fila.findViewById<BarraProgresoView>(R.id.barraCategoria)
                .mostrarFijo(total.proporcion, EstiloCategoria.color(total.categoria), animar)
            fila.setOnClickListener { acciones.alBuscarEnMovimientos(total.categoria) }
            listaCategorias.addView(fila)
        }
        val restantes = categorias.size - MAX_CATEGORIAS
        if (restantes > 0) {
            listaCategorias.addView(TextView(listaCategorias.context).apply {
                text = "y $restantes ${if (restantes == 1) "categoría" else "categorías"} más"
                setTextColor(context.getColor(R.color.xald_texto_terciario))
                textSize = 12f
                setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
            })
        }
    }

    private fun pintarPresupuestos(panel: PanelFinanciero, privado: Boolean, animar: Boolean, ahoraMs: Long) {
        listaPresupuestos.removeAllViews()
        val estados = panel.presupuestos
        tvSinPresupuestos.visibility = if (estados.isEmpty()) View.VISIBLE else View.GONE
        estados.take(MAX_PRESUPUESTOS).forEachIndexed { i, estado ->
            if (i > 0) listaPresupuestos.addView(VistaPresupuesto.separador(listaPresupuestos))
            listaPresupuestos.addView(
                VistaPresupuesto.crear(listaPresupuestos, estado, privado, animar, ahoraMs, formatoFecha) {
                    acciones.alEditarPresupuesto(estado.presupuesto)
                }
            )
        }
    }

    private companion object {
        const val MAX_CATEGORIAS = 6
        const val MAX_PRESUPUESTOS = 3
    }
}
