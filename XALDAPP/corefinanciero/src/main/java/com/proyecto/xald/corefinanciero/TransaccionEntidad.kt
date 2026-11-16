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
    val sincronizado: Boolean = false
)