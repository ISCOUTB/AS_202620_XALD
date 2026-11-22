package com.proyecto.xald.corefinanciero

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tope de gasto autoimpuesto para una categoría, o para todo el gasto si
 * [categoria] == [CategoriasEspeciales.PRESUPUESTO_TOTAL]. Hay a lo sumo uno por categoría.
 */
@Entity(tableName = "presupuestos")
data class PresupuestoEntidad(
    @PrimaryKey
    @ColumnInfo(name = "categoria")
    val categoria: String,

    @ColumnInfo(name = "limite")
    val limite: Double,

    /** Código de [PeriodoPresupuesto]: MENSUAL o SEMANAL. */
    @ColumnInfo(name = "periodo")
    val periodo: String = PeriodoPresupuesto.MENSUAL.name
)

// Fuera de la entidad a propósito: Room solo debe ver columnas.
val PresupuestoEntidad.esTotal: Boolean
    get() = categoria == CategoriasEspeciales.PRESUPUESTO_TOTAL

val PresupuestoEntidad.periodoPresupuesto: PeriodoPresupuesto
    get() = PeriodoPresupuesto.desdeCodigo(periodo)

/** Nombre para mostrar: "Gasto total" para el global, la categoría en los demás. */
val PresupuestoEntidad.nombreVisible: String
    get() = if (esTotal) "Gasto total" else categoria

enum class PeriodoPresupuesto(val etiqueta: String) {
    SEMANAL("Semanal"),
    MENSUAL("Mensual");

    companion object {
        fun desdeCodigo(codigo: String?): PeriodoPresupuesto =
            entries.firstOrNull { it.name.equals(codigo?.trim(), ignoreCase = true) } ?: MENSUAL
    }
}

/**
 * Saldo que el usuario fijó a mano ("hoy tengo $X") y cuándo. Es una sola fila (id = 1).
 *
 * El saldo disponible se calcula como este monto más las entradas y menos las salidas
 * registradas DESPUÉS de [fechaTimestamp]: lo anterior ya está reflejado en el monto que el
 * usuario escribió. Volver a fijarlo corrige cualquier desvío (un SMS que nunca llegó, una
 * compra en efectivo, etc.).
 */
@Entity(tableName = "saldo_base")
data class SaldoBaseEntidad(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = ID_UNICO,

    @ColumnInfo(name = "monto")
    val monto: Double,

    @ColumnInfo(name = "fecha_timestamp")
    val fechaTimestamp: Long
) {
    companion object {
        const val ID_UNICO = 1
    }
}
