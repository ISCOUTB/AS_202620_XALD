package com.proyecto.xald.corefinanciero

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Fuente primaria de verdad local (Offline-First) persistida en SQLite mediante Room.
 */
@Entity(tableName = "transacciones")
data class TransaccionEntidad(
    @PrimaryKey
    @ColumnInfo(name = "id_transaccion")
    val id: String,

    @ColumnInfo(name = "monto")
    val monto: Double,

    @ColumnInfo(name = "moneda")
    val moneda: String = "COP",

    @ColumnInfo(name = "comercio")
    val comercio: String,

    @ColumnInfo(name = "categoria")
    val categoria: String = "Sin Categorizar",

    @ColumnInfo(name = "fecha_timestamp")
    val fechaTimestamp: Long,

    @ColumnInfo(name = "origen_datos")
    val origenDatos: String = "SMS_REGEX",

    @ColumnInfo(name = "sincronizado")
    val sincronizado: Boolean = false,

    /**
     * Código de [TipoMovimiento] (GASTO, INGRESO, TRANSFERENCIA_ENVIADA, TRANSFERENCIA_RECIBIDA).
     * Es nullable a propósito: las filas creadas antes de la versión 2 de la base no lo tienen y
     * eran todas compras, así que `null` se lee como GASTO (ver [tipo]).
     */
    @ColumnInfo(name = "tipo_movimiento")
    val tipoMovimiento: String? = null
)

/** Tipo del movimiento ya resuelto (las filas antiguas sin tipo se leen como GASTO). */
val TransaccionEntidad.tipo: TipoMovimiento
    get() = TipoMovimiento.desdeCodigo(tipoMovimiento)

/** SMS (reglas o IA) o ingresado a mano por el usuario. */
val TransaccionEntidad.origen: OrigenMovimiento
    get() = OrigenMovimiento.desdeCodigo(origenDatos)
