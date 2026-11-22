package com.proyecto.xald.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.proyecto.xald.R
import com.proyecto.xald.corefinanciero.AnaliticaFinanciera
import com.proyecto.xald.corefinanciero.FiltroHistorial
import com.proyecto.xald.corefinanciero.FiltroOrigen
import com.proyecto.xald.corefinanciero.FiltroTipo
import com.proyecto.xald.corefinanciero.OrdenHistorial
import com.proyecto.xald.corefinanciero.TransaccionEntidad
import com.proyecto.xald.corefinanciero.origen
import com.proyecto.xald.corefinanciero.tipo

/** Historial con búsqueda, filtros (tipo, origen), orden y agrupación por día. */
class PantallaMovimientos(raiz: View, private val acciones: Acciones) {

    fun interface Acciones {
        fun alTocarMovimiento(movimiento: TransaccionEntidad)
    }

    private val etBuscar: EditText = raiz.findViewById(R.id.etBuscar)
    private val btnLimpiar: View = raiz.findViewById(R.id.btnLimpiarBusqueda)
    private val chipsTipo = mapOf(
        FiltroTipo.TODOS to raiz.findViewById<TextView>(R.id.chipTodos),
        FiltroTipo.GASTOS to raiz.findViewById<TextView>(R.id.chipGastos),
        FiltroTipo.INGRESOS to raiz.findViewById<TextView>(R.id.chipIngresos),
        FiltroTipo.TRANSFERENCIAS to raiz.findViewById<TextView>(R.id.chipTransferencias)
    )
    private val chipOrigen: TextView = raiz.findViewById(R.id.chipOrigen)
    private val chipOrden: TextView = raiz.findViewById(R.id.chipOrden)
    private val tvResumen: TextView = raiz.findViewById(R.id.tvResumenFiltro)
    private val lista: LinearLayout = raiz.findViewById(R.id.listaMovimientos)
    private val vacio: View = raiz.findViewById(R.id.vacioMovimientos)
    private val tvVacio: TextView = raiz.findViewById(R.id.tvVacioMovimientos)

    private val formatoFecha = FormatoFecha()
    private val principal = Handler(Looper.getMainLooper())
    private val repintarBusqueda = Runnable { pintar() }

    private var movimientos: List<TransaccionEntidad> = emptyList()
    private var privado = false
    private var filtro = FiltroHistorial()

    init {
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filtro = filtro.copy(texto = s?.toString().orEmpty())
                btnLimpiar.visibility = if (filtro.texto.isEmpty()) View.GONE else View.VISIBLE
                // Espera a que el usuario deje de escribir para no redibujar en cada letra.
                principal.removeCallbacks(repintarBusqueda)
                principal.postDelayed(repintarBusqueda, 180L)
            }
        })
        btnLimpiar.setOnClickListener { etBuscar.setText("") }
        chipsTipo.forEach { (tipo, chip) ->
            chip.setOnClickListener {
                filtro = filtro.copy(tipo = tipo)
                pintar()
            }
        }
        chipOrigen.setOnClickListener {
            filtro = filtro.copy(origen = siguiente(filtro.origen))
            pintar()
        }
        chipOrden.setOnClickListener {
            filtro = filtro.copy(orden = siguiente(filtro.orden))
            pintar()
        }
    }

    fun mostrar(nuevos: List<TransaccionEntidad>, modoPrivado: Boolean) {
        movimientos = nuevos
        privado = modoPrivado
        pintar()
    }

    /** Desde el inicio (tocar una categoría o la tarjeta flash): busca ese texto con los demás filtros limpios. */
    fun buscar(texto: String) {
        filtro = FiltroHistorial(texto = texto, tipo = FiltroTipo.TODOS)
        etBuscar.setText(texto)
        etBuscar.setSelection(texto.length)
        principal.removeCallbacks(repintarBusqueda)
        pintar()
    }

    private fun pintar() {
        chipsTipo.forEach { (tipo, chip) -> chip.isSelected = tipo == filtro.tipo }
        chipOrigen.text = when (filtro.origen) {
            FiltroOrigen.TODOS -> "Origen: todos"
            FiltroOrigen.SMS -> "Origen: SMS"
            FiltroOrigen.MANUAL -> "Origen: manual"
        }
        chipOrigen.isSelected = filtro.origen != FiltroOrigen.TODOS
        chipOrden.text = filtro.orden.etiqueta
        chipOrden.isSelected = filtro.orden != OrdenHistorial.RECIENTES

        val filtrados = AnaliticaFinanciera.filtrar(movimientos, filtro)
        val entradas = filtrados.filter { it.tipo.esEntrada }.sumOf { it.monto }
        val salidas = filtrados.filterNot { it.tipo.esEntrada }.sumOf { it.monto }
        tvResumen.text = if (filtrados.isEmpty()) "" else
            "${filtrados.size} ${if (filtrados.size == 1) "movimiento" else "movimientos"} · " +
                "${FormatoDinero.movimiento(entradas, true, privado)} · ${FormatoDinero.movimiento(salidas, false, privado)}"

        lista.removeAllViews()
        if (filtrados.isEmpty()) {
            vacio.visibility = View.VISIBLE
            tvVacio.text = if (movimientos.isEmpty()) {
                "Aún no hay movimientos. Llegarán solos con los SMS de tu banco, o agrégalos con el botón +."
            } else {
                "No hay movimientos con estos filtros."
            }
            return
        }
        vacio.visibility = View.GONE

        val ahora = System.currentTimeMillis()
        // Los encabezados por día solo tienen sentido cuando la lista va en orden de fecha.
        val agruparPorDia = filtro.orden == OrdenHistorial.RECIENTES || filtro.orden == OrdenHistorial.ANTIGUOS
        val inflador = LayoutInflater.from(lista.context)
        val visibles = filtrados.take(MAX_FILAS)

        if (agruparPorDia) {
            visibles.groupBy { formatoFecha.fecha(it.fechaTimestamp) }.forEach { (_, delDia) ->
                val encabezado = inflador.inflate(R.layout.item_encabezado_dia, lista, false)
                encabezado.findViewById<TextView>(R.id.tvDia).text = formatoFecha.encabezadoDia(delDia.first().fechaTimestamp, ahora)
                val neto = delDia.sumOf { if (it.tipo.esEntrada) it.monto else -it.monto }
                encabezado.findViewById<TextView>(R.id.tvTotalDia).text = FormatoDinero.formatear(neto, privado, conSigno = true)
                lista.addView(encabezado)
                delDia.forEach { lista.addView(fila(inflador, it, mostrarFecha = false)) }
            }
        } else {
            visibles.forEach { lista.addView(fila(inflador, it, mostrarFecha = true)) }
        }

        if (filtrados.size > MAX_FILAS) {
            lista.addView(TextView(lista.context).apply {
                text = "Mostrando $MAX_FILAS de ${filtrados.size}. Usa la búsqueda o los filtros para encontrar el resto."
                setTextColor(context.getColor(R.color.xald_texto_terciario))
                textSize = 12f
                val relleno = (16 * resources.displayMetrics.density).toInt()
                setPadding(relleno, relleno, relleno, relleno)
            })
        }
    }

    private fun fila(inflador: LayoutInflater, mov: TransaccionEntidad, mostrarFecha: Boolean): View {
        val vista = inflador.inflate(R.layout.item_movimiento, lista, false)
        val tipo = mov.tipo
        val icono = vista.findViewById<TextView>(R.id.tvIconoMovimiento)
        icono.text = EstiloCategoria.emojiMovimiento(tipo, mov.categoria)
        icono.backgroundTintList = ColorStateList.valueOf(EstiloCategoria.suave(EstiloCategoria.color(mov.categoria)))

        vista.findViewById<TextView>(R.id.tvComercio).text = mov.comercio
        val cuando = if (mostrarFecha) formatoFecha.fechaCorta(mov.fechaTimestamp) else formatoFecha.hora(mov.fechaTimestamp)
        val categoria = if (tipo.esTransferencia || tipo.esEntrada) tipo.etiqueta else mov.categoria
        vista.findViewById<TextView>(R.id.tvDetalleMovimiento).text = "$categoria · ${mov.origen.etiqueta} · $cuando"

        val monto = vista.findViewById<TextView>(R.id.tvMontoMovimiento)
        monto.text = FormatoDinero.movimiento(mov.monto, tipo.esEntrada, privado)
        monto.setTextColor(vista.context.getColor(if (tipo.esEntrada) R.color.xald_entrada else R.color.xald_texto))

        vista.findViewById<TextView>(R.id.tvEstadoMovimiento).text = if (mov.sincronizado) "✓ Sincronizado" else "⏳ Por enviar"
        vista.setOnClickListener { acciones.alTocarMovimiento(mov) }
        return vista
    }

    private fun siguiente(actual: FiltroOrigen): FiltroOrigen = FiltroOrigen.entries[(actual.ordinal + 1) % FiltroOrigen.entries.size]
    private fun siguiente(actual: OrdenHistorial): OrdenHistorial = OrdenHistorial.entries[(actual.ordinal + 1) % OrdenHistorial.entries.size]

    private companion object {
        /** Tope de filas dibujadas a la vez (es un LinearLayout, no una lista reciclada). */
        const val MAX_FILAS = 300
    }
}
