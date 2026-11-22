package com.proyecto.xald.corefinanciero

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransaccionEntidad::class, PresupuestoEntidad::class, SaldoBaseEntidad::class],
    version = 2,
    exportSchema = false
)
internal abstract class CoreDatabase : RoomDatabase() {
    abstract fun transaccionDao(): TransaccionDao
    abstract fun planificacionDao(): PlanificacionDao
}

/**
 * Versión 1 → 2: tipo de movimiento (ingresos y transferencias), presupuestos y saldo base.
 *
 * Las sentencias reproducen exactamente lo que Room genera para las entidades; si no coinciden,
 * Room rechaza la base al abrirla ("Migration didn't properly handle"). Lo comprueba
 * `MigracionCoreTest`. Ninguna transacción existente se pierde: la columna nueva queda en NULL
 * y se lee como GASTO, que es lo que eran todas.
 */
internal val MIGRACION_CORE_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `transacciones` ADD COLUMN `tipo_movimiento` TEXT")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `presupuestos` (`categoria` TEXT NOT NULL, " +
                "`limite` REAL NOT NULL, `periodo` TEXT NOT NULL, PRIMARY KEY(`categoria`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saldo_base` (`id` INTEGER NOT NULL, " +
                "`monto` REAL NOT NULL, `fecha_timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}
