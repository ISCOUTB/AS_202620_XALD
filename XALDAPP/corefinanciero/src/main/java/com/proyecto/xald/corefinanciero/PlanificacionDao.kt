package com.proyecto.xald.corefinanciero

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Presupuestos y saldo base (datos que define el usuario, no los SMS). */
@Dao
internal interface PlanificacionDao {

    @Query("SELECT * FROM presupuestos ORDER BY categoria ASC")
    fun obtenerPresupuestos(): List<PresupuestoEntidad>

    /** REPLACE: guardar un presupuesto de una categoría que ya tenía uno lo reemplaza. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun guardarPresupuesto(presupuesto: PresupuestoEntidad): Long

    @Query("DELETE FROM presupuestos WHERE categoria = :categoria")
    fun eliminarPresupuesto(categoria: String): Int

    @Query("SELECT * FROM saldo_base WHERE id = 1 LIMIT 1")
    fun obtenerSaldoBase(): SaldoBaseEntidad?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun guardarSaldoBase(saldo: SaldoBaseEntidad): Long
}
