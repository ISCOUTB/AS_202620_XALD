package com.proyecto.xald.ui

import android.view.View
import android.widget.LinearLayout
import com.proyecto.xald.R
import com.proyecto.xald.corefinanciero.EstadoPresupuesto
import com.proyecto.xald.corefinanciero.PresupuestoEntidad

/** Topes de gasto con barras que pasan de verde a rojo; tocar uno lo edita. */
class PantallaPresupuestos(raiz: View, private val acciones: Acciones) {

    interface Acciones {
        fun alNuevoPresupuesto()
        fun alEditarPresupuesto(presupuesto: PresupuestoEntidad)
    }

    private val lista: LinearLayout = raiz.findViewById(R.id.listaPresupuestos)
    private val tarjetaLista: View = raiz.findViewById(R.id.tarjetaListaPresupuestos)
    private val vacio: View = raiz.findViewById(R.id.vacioPresupuestos)
    private val formatoFecha = FormatoFecha()

    init {
        raiz.findViewById<View>(R.id.btnNuevoPresupuesto).setOnClickListener { acciones.alNuevoPresupuesto() }
    }

    fun mostrar(estados: List<EstadoPresupuesto>, privado: Boolean, animar: Boolean) {
        lista.removeAllViews()
        val hayPresupuestos = estados.isNotEmpty()
        tarjetaLista.visibility = if (hayPresupuestos) View.VISIBLE else View.GONE
        vacio.visibility = if (hayPresupuestos) View.GONE else View.VISIBLE
        val ahora = System.currentTimeMillis()
        estados.forEachIndexed { i, estado ->
            if (i > 0) lista.addView(VistaPresupuesto.separador(lista))
            lista.addView(
                VistaPresupuesto.crear(lista, estado, privado, animar, ahora, formatoFecha) {
                    acciones.alEditarPresupuesto(estado.presupuesto)
                }
            )
        }
    }
}
