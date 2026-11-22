package com.proyecto.xald.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.proyecto.xald.R
import com.proyecto.xald.corefinanciero.CategoriasEspeciales
import com.proyecto.xald.corefinanciero.EstadoPresupuesto
import com.proyecto.xald.corefinanciero.AnaliticaFinanciera
import com.proyecto.xald.corefinanciero.esTotal
import com.proyecto.xald.corefinanciero.nombreVisible
import com.proyecto.xald.corefinanciero.periodoPresupuesto

/** Fila de presupuesto (se usa en el inicio y en la pestaña Presupuestos). */
object VistaPresupuesto {

    fun crear(
        contenedor: ViewGroup,
        estado: EstadoPresupuesto,
        privado: Boolean,
        animar: Boolean,
        ahoraMs: Long,
        formatoFecha: FormatoFecha,
        alTocar: () -> Unit
    ): View {
        val vista = LayoutInflater.from(contenedor.context).inflate(R.layout.item_presupuesto, contenedor, false)
        val presupuesto = estado.presupuesto
        val clave = if (presupuesto.esTotal) CategoriasEspeciales.PRESUPUESTO_TOTAL else presupuesto.categoria

        vista.findViewById<TextView>(R.id.tvIconoPresupuesto).text = EstiloCategoria.emoji(clave)
        vista.findViewById<TextView>(R.id.tvNombrePresupuesto).text = presupuesto.nombreVisible
        vista.findViewById<TextView>(R.id.tvPeriodoPresupuesto).text = presupuesto.periodoPresupuesto.etiqueta
        vista.findViewById<TextView>(R.id.tvConsumoPresupuesto).text =
            "${FormatoDinero.formatear(estado.gastado, privado)} de ${FormatoDinero.formatear(estado.limite, privado)}"
        vista.findViewById<BarraProgresoView>(R.id.barraPresupuesto).mostrarPresupuesto(estado.proporcion, animar)

        val porcentaje = vista.findViewById<TextView>(R.id.tvPorcentajePresupuesto)
        porcentaje.text = "${AnaliticaFinanciera.porcentaje(estado.proporcion)}%"
        porcentaje.setTextColor(BarraProgresoView.colorSemaforo(estado.proporcion.toFloat()))

        val dias = formatoFecha.diasRestantes(estado.rango.finMs, ahoraMs)
        val textoDias = if (dias == 1L) "último día" else "$dias días"
        val restante = vista.findViewById<TextView>(R.id.tvRestantePresupuesto)
        if (estado.restante >= 0) {
            restante.text = "Quedan ${FormatoDinero.formatear(estado.restante, privado)} · $textoDias"
            restante.setTextColor(vista.context.getColor(R.color.xald_texto_secundario))
        } else {
            restante.text = "Te pasaste ${FormatoDinero.formatear(-estado.restante, privado)}"
            restante.setTextColor(vista.context.getColor(R.color.xald_salida))
        }

        vista.contentDescription = "${presupuesto.nombreVisible}: ${porcentaje.text} usado"
        vista.setOnClickListener { alTocar() }
        return vista
    }

    /** Línea fina entre filas de una tarjeta. */
    fun separador(contenedor: ViewGroup): View = View(contenedor.context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(resources.displayMetrics.density))
        setBackgroundColor(context.getColor(R.color.xald_separador))
    }
}
