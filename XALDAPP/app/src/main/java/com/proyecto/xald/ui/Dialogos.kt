package com.proyecto.xald.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.proyecto.xald.R
import com.proyecto.xald.corefinanciero.CategoriasEspeciales
import com.proyecto.xald.corefinanciero.PeriodoPresupuesto
import com.proyecto.xald.corefinanciero.PresupuestoEntidad
import com.proyecto.xald.corefinanciero.SaldoBaseEntidad
import com.proyecto.xald.corefinanciero.TipoMovimiento
import com.proyecto.xald.corefinanciero.TransaccionEntidad
import com.proyecto.xald.corefinanciero.nombreVisible
import com.proyecto.xald.corefinanciero.periodoPresupuesto
import com.proyecto.xald.corefinanciero.tipo
import java.util.Calendar

/** Datos que devuelve el formulario de movimiento manual. */
data class DatosMovimientoManual(
    val monto: Double,
    val descripcion: String,
    val tipo: TipoMovimiento,
    val categoria: String,
    val fechaMs: Long
)

/**
 * Diálogos de la app. Cada uno valida lo que el usuario escribe y solo entonces llama a su
 * callback; guardar en la base es responsabilidad de quien lo abre (en un hilo de fondo).
 */
object Dialogos {

    private val formatoFecha = FormatoFecha()

    // ---------------------------------------------------------------------- Movimiento manual

    fun nuevoMovimiento(
        activity: Activity,
        categorias: List<String>,
        saldoBase: SaldoBaseEntidad?,
        alGuardar: (DatosMovimientoManual) -> Unit
    ) {
        val vista = inflar(activity, R.layout.dialogo_movimiento)
        val etMonto = vista.findViewById<EditText>(R.id.etMonto)
        val etDescripcion = vista.findViewById<EditText>(R.id.etDescripcion)
        val grupoCategoria = vista.findViewById<View>(R.id.grupoCategoria)
        val tvCategoria = vista.findViewById<TextView>(R.id.tvCategoria)
        val tvFecha = vista.findViewById<TextView>(R.id.tvFecha)
        val tvAviso = vista.findViewById<TextView>(R.id.tvAvisoFecha)
        val tvError = vista.findViewById<TextView>(R.id.tvErrorMovimiento)

        var tipo = TipoMovimiento.GASTO
        var categoria = CategoriasEspeciales.SIN_CATEGORIZAR
        var fechaMs = System.currentTimeMillis()

        fun refrescar() {
            grupoCategoria.visibility = if (tipo == TipoMovimiento.GASTO) View.VISIBLE else View.GONE
            pintarCategoria(tvCategoria, categoria)
            tvFecha.text = "📅  ${formatoFecha.fechaFormulario(fechaMs, System.currentTimeMillis())}"
            val antesDeLaBase = saldoBase != null && fechaMs <= saldoBase.fechaTimestamp
            tvAviso.visibility = if (antesDeLaBase) View.VISIBLE else View.GONE
            if (antesDeLaBase) {
                tvAviso.text = "Esta fecha es anterior a tu último ajuste de saldo (${formatoFecha.fechaCorta(saldoBase!!.fechaTimestamp)}): " +
                    "se guarda en el historial y las estadísticas, pero no cambia el saldo disponible."
            }
        }

        SelectorTipo(vista, TipoMovimiento.GASTO) { nuevo ->
            tipo = nuevo
            refrescar()
        }
        tvCategoria.setOnClickListener {
            elegirCategoria(activity, categorias, categoria) {
                categoria = it
                refrescar()
            }
        }
        tvFecha.setOnClickListener {
            elegirFecha(activity, fechaMs) {
                fechaMs = it
                refrescar()
            }
        }
        refrescar()

        val dialogo = AlertDialog.Builder(activity)
            .setView(vista)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val monto = MontoUsuario.parsear(etMonto.text.toString())
                if (monto == null || monto <= 0.0) {
                    mostrarError(tvError, "Escribe un monto mayor que cero, por ejemplo 45.900.")
                    return@setOnClickListener
                }
                alGuardar(DatosMovimientoManual(monto, etDescripcion.text.toString().trim(), tipo, categoria, fechaMs))
                dialogo.dismiss()
            }
        }
        mostrarConTeclado(dialogo, etMonto)
    }

    // ---------------------------------------------------------------------------- Ajustar saldo

    fun ajustarSaldo(activity: Activity, saldoActual: Double, privado: Boolean, alGuardar: (Double) -> Unit) {
        val vista = inflar(activity, R.layout.dialogo_saldo)
        val etSaldo = vista.findViewById<EditText>(R.id.etSaldo)
        val tvError = vista.findViewById<TextView>(R.id.tvErrorSaldo)
        vista.findViewById<TextView>(R.id.tvSaldoCalculado).text =
            "XALD calcula hoy: ${FormatoDinero.formatear(saldoActual, privado)}"

        val dialogo = AlertDialog.Builder(activity)
            .setView(vista)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val monto = MontoUsuario.parsear(etSaldo.text.toString())
                if (monto == null) {
                    mostrarError(tvError, "Escribe tu saldo, por ejemplo 1.250.000 (puede ser negativo).")
                    return@setOnClickListener
                }
                alGuardar(monto)
                dialogo.dismiss()
            }
        }
        mostrarConTeclado(dialogo, etSaldo)
    }

    // ----------------------------------------------------------------------------- Presupuesto

    /**
     * @param existente null para crear uno nuevo.
     * @param categoriasLibres categorías que aún no tienen presupuesto (incluye la total si está libre).
     */
    fun presupuesto(
        activity: Activity,
        existente: PresupuestoEntidad?,
        categoriasLibres: List<String>,
        alGuardar: (PresupuestoEntidad) -> Unit,
        alEliminar: (String) -> Unit
    ) {
        if (existente == null && categoriasLibres.isEmpty()) {
            AlertDialog.Builder(activity)
                .setTitle("Ya tienes todos los presupuestos")
                .setMessage("Cada categoría ya tiene un tope. Toca uno en la lista para cambiarlo.")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }

        val vista = inflar(activity, R.layout.dialogo_presupuesto)
        val tvCategoria = vista.findViewById<TextView>(R.id.tvCategoriaPresupuesto)
        val etLimite = vista.findViewById<EditText>(R.id.etLimite)
        val chipMensual = vista.findViewById<TextView>(R.id.chipMensual)
        val chipSemanal = vista.findViewById<TextView>(R.id.chipSemanal)
        val tvError = vista.findViewById<TextView>(R.id.tvErrorPresupuesto)

        var categoria = existente?.categoria ?: categoriasLibres.first()
        var periodo = existente?.periodoPresupuesto ?: PeriodoPresupuesto.MENSUAL

        fun refrescar() {
            pintarCategoria(tvCategoria, categoria)
            chipMensual.isSelected = periodo == PeriodoPresupuesto.MENSUAL
            chipSemanal.isSelected = periodo == PeriodoPresupuesto.SEMANAL
        }

        if (existente != null) {
            vista.findViewById<TextView>(R.id.tvTituloPresupuesto).text = "Editar presupuesto"
            etLimite.setText(FormatoDinero.formatear(existente.limite).removePrefix("$ "))
            // La categoría de un presupuesto existente no se cambia (es su identidad); se borra y se crea otro.
            tvCategoria.isEnabled = false
        } else {
            tvCategoria.setOnClickListener {
                elegirCategoria(activity, categoriasLibres, categoria) {
                    categoria = it
                    refrescar()
                }
            }
        }
        chipMensual.setOnClickListener {
            periodo = PeriodoPresupuesto.MENSUAL
            refrescar()
        }
        chipSemanal.setOnClickListener {
            periodo = PeriodoPresupuesto.SEMANAL
            refrescar()
        }
        refrescar()

        val constructor = AlertDialog.Builder(activity)
            .setView(vista)
            .setPositiveButton("Guardar", null)
            .setNegativeButton("Cancelar", null)
        if (existente != null) {
            constructor.setNeutralButton("Eliminar") { _, _ ->
                confirmar(activity, "¿Eliminar el presupuesto de ${existente.nombreVisible}?", "Eliminar") {
                    alEliminar(existente.categoria)
                }
            }
        }
        val dialogo = constructor.create()
        dialogo.setOnShowListener {
            dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val limite = MontoUsuario.parsear(etLimite.text.toString())
                if (limite == null || limite <= 0.0) {
                    mostrarError(tvError, "Escribe un límite mayor que cero, por ejemplo 300.000.")
                    return@setOnClickListener
                }
                alGuardar(PresupuestoEntidad(categoria = categoria, limite = limite, periodo = periodo.name))
                dialogo.dismiss()
            }
            dialogo.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(activity.getColor(R.color.xald_salida))
        }
        mostrarConTeclado(dialogo, etLimite)
    }

    // --------------------------------------------------------------------------------- Detalle

    fun detalle(activity: Activity, mov: TransaccionEntidad, privado: Boolean, alReclasificar: () -> Unit) {
        val vista = inflar(activity, R.layout.dialogo_detalle)
        val tipo = mov.tipo
        val icono = vista.findViewById<TextView>(R.id.tvIconoDetalle)
        icono.text = EstiloCategoria.emojiMovimiento(tipo, mov.categoria)
        icono.backgroundTintList = ColorStateList.valueOf(EstiloCategoria.suave(EstiloCategoria.color(mov.categoria)))

        val monto = vista.findViewById<TextView>(R.id.tvMontoDetalle)
        monto.text = FormatoDinero.movimiento(mov.monto, tipo.esEntrada, privado)
        monto.setTextColor(activity.getColor(if (tipo.esEntrada) R.color.xald_entrada else R.color.xald_texto))
        vista.findViewById<TextView>(R.id.tvComercioDetalle).text = mov.comercio

        val filas = vista.findViewById<LinearLayout>(R.id.filasDetalle)
        val origenTexto = when (mov.origenDatos.uppercase()) {
            "AI_GEMINI" -> "SMS (categoría por IA)"
            "MANUAL" -> "Ingresado a mano"
            else -> "SMS del banco"
        }
        listOf(
            "Tipo" to tipo.etiqueta,
            "Categoría" to "${EstiloCategoria.emoji(mov.categoria)} ${mov.categoria}",
            "Fecha" to "${formatoFecha.fechaCorta(mov.fechaTimestamp)}, ${formatoFecha.hora(mov.fechaTimestamp)}",
            "Origen" to origenTexto,
            "Servidor" to if (mov.sincronizado) "✓ Sincronizado" else "⏳ Pendiente de envío"
        ).forEach { (etiqueta, valor) ->
            val fila = LayoutInflater.from(activity).inflate(R.layout.item_fila_detalle, filas, false)
            fila.findViewById<TextView>(R.id.tvEtiquetaFila).text = etiqueta
            fila.findViewById<TextView>(R.id.tvValorFila).text = valor
            filas.addView(fila)
        }

        AlertDialog.Builder(activity)
            .setView(vista)
            .setPositiveButton("Corregir clasificación") { _, _ -> alReclasificar() }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    // ---------------------------------------------------------------------------- Reclasificar

    fun reclasificar(
        activity: Activity,
        mov: TransaccionEntidad,
        categorias: List<String>,
        alGuardar: (TipoMovimiento, String) -> Unit
    ) {
        val vista = inflar(activity, R.layout.dialogo_reclasificar)
        val grupoCategoria = vista.findViewById<View>(R.id.grupoCategoria)
        val tvCategoria = vista.findViewById<TextView>(R.id.tvCategoria)
        vista.findViewById<TextView>(R.id.tvComercioReclasificar).text = mov.comercio

        var tipo = mov.tipo
        var categoria = if (tipo == TipoMovimiento.GASTO) mov.categoria else CategoriasEspeciales.SIN_CATEGORIZAR

        fun refrescar() {
            grupoCategoria.visibility = if (tipo == TipoMovimiento.GASTO) View.VISIBLE else View.GONE
            pintarCategoria(tvCategoria, categoria)
        }
        SelectorTipo(vista, tipo) {
            tipo = it
            refrescar()
        }
        tvCategoria.setOnClickListener {
            elegirCategoria(activity, categorias, categoria) {
                categoria = it
                refrescar()
            }
        }
        refrescar()

        AlertDialog.Builder(activity)
            .setView(vista)
            .setPositiveButton("Guardar") { _, _ -> alGuardar(tipo, categoria) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ------------------------------------------------------------------------------ Selectores

    fun elegirCategoria(activity: Activity, categorias: List<String>, actual: String, alElegir: (String) -> Unit) {
        val etiquetas = categorias.map { "${EstiloCategoria.emoji(it)}   ${nombreCategoria(it)}" }.toTypedArray()
        val marcada = categorias.indexOfFirst { it.equals(actual, ignoreCase = true) }
        AlertDialog.Builder(activity)
            .setTitle("Categoría")
            .setSingleChoiceItems(etiquetas, marcada) { dialogo, i ->
                alElegir(categorias[i])
                dialogo.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun elegirFecha(activity: Activity, actualMs: Long, alElegir: (Long) -> Unit) {
        val fecha = formatoFecha.fecha(actualMs)
        val selector = DatePickerDialog(
            activity,
            { _, anio, mesBase0, dia ->
                val hoy = formatoFecha.fecha(System.currentTimeMillis())
                // Hoy conserva la hora actual (queda después del ajuste de saldo); otro día, mediodía.
                alElegir(
                    if (anio == hoy.year && mesBase0 + 1 == hoy.monthValue && dia == hoy.dayOfMonth) System.currentTimeMillis()
                    else formatoFecha.mediodia(anio, mesBase0 + 1, dia)
                )
            },
            fecha.year,
            fecha.monthValue - 1,
            fecha.dayOfMonth
        )
        selector.datePicker.maxDate = Calendar.getInstance().timeInMillis
        selector.show()
    }

    // -------------------------------------------------------------------------------- Ayudas

    private fun confirmar(activity: Activity, pregunta: String, accion: String, alConfirmar: () -> Unit) {
        AlertDialog.Builder(activity)
            .setMessage(pregunta)
            .setPositiveButton(accion) { _, _ -> alConfirmar() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun nombreCategoria(categoria: String): String =
        if (categoria == CategoriasEspeciales.PRESUPUESTO_TOTAL) "Gasto total (todas las categorías)" else categoria

    private fun pintarCategoria(vista: TextView, categoria: String) {
        vista.text = "${EstiloCategoria.emoji(categoria)}   ${nombreCategoria(categoria)}   ▾"
    }

    private fun mostrarError(vista: TextView, mensaje: String) {
        vista.text = mensaje
        vista.visibility = View.VISIBLE
    }

    private fun inflar(activity: Activity, layout: Int): View =
        LayoutInflater.from(activity).inflate(layout, activity.findViewById<ViewGroup>(android.R.id.content), false)

    private fun mostrarConTeclado(dialogo: AlertDialog, campo: EditText) {
        dialogo.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialogo.show()
        campo.requestFocus()
    }
}

/** Selector de tipo (cuatro chips) compartido por "nuevo movimiento" y "reclasificar". */
private class SelectorTipo(raiz: View, inicial: TipoMovimiento, private val alCambiar: (TipoMovimiento) -> Unit) {

    private val chips = mapOf(
        TipoMovimiento.GASTO to raiz.findViewById<TextView>(R.id.chipTipoGasto),
        TipoMovimiento.INGRESO to raiz.findViewById<TextView>(R.id.chipTipoIngreso),
        TipoMovimiento.TRANSFERENCIA_ENVIADA to raiz.findViewById<TextView>(R.id.chipTipoEnvie),
        TipoMovimiento.TRANSFERENCIA_RECIBIDA to raiz.findViewById<TextView>(R.id.chipTipoRecibi)
    )

    init {
        marcar(inicial)
        chips.forEach { (tipo, chip) ->
            chip.setOnClickListener {
                marcar(tipo)
                alCambiar(tipo)
            }
        }
    }

    private fun marcar(tipo: TipoMovimiento) {
        chips.forEach { (t, chip) -> chip.isSelected = t == tipo }
    }
}
