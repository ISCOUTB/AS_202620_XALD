package com.proyecto.xald.corefinanciero

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface TransaccionDao {

    /**
     * IGNORE (no REPLACE): si llega dos veces la misma transacción (mismo id), la segunda se
     * descarta y devuelve -1. Con REPLACE se pisaría la fila y se perdería el estado
     * `sincronizado` y la categoría corregida por el usuario.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertar(transaccion: TransaccionEntidad): Long

    @Query("SELECT * FROM transacciones ORDER BY fecha_timestamp DESC LIMIT 1")
    fun obtenerUltimaTransaccion(): TransaccionEntidad?

    @Query("SELECT * FROM transacciones WHERE sincronizado = 0 ORDER BY fecha_timestamp ASC")
    fun obtenerPendientesSincronizacion(): List<TransaccionEntidad>

    @Query("SELECT * FROM transacciones ORDER BY fecha_timestamp DESC")
    fun obtenerTodas(): List<TransaccionEntidad>

    @Query("SELECT COALESCE(SUM(monto), 0.0) FROM transacciones")
    fun obtenerSaldoTotal(): Double

    @Query("UPDATE transacciones SET sincronizado = 1 WHERE id_transaccion = :idTransaccion")
    fun marcarComoSincronizado(idTransaccion: String): Int

    @Query("UPDATE transacciones SET categoria = :nuevaCategoria WHERE id_transaccion = :idTransaccion")
    fun actualizarCategoria(idTransaccion: String, nuevaCategoria: String): Int
}
