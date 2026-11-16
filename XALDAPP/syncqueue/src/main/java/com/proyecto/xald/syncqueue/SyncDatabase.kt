package com.proyecto.xald.syncqueue

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Instancia de la base de datos local para la cola de sincronización.
 * Nota de seguridad (Brecha 8.3): Esta base de datos deberá ser cifrada con SQLCipher
 * en la Fase 2 del plan de construcción. Por ahora, establece la durabilidad.
 *
 * Versión 2: añade `clave_deduplicacion` (índice UNIQUE) y `confirmado`.
 */
@Database(entities = [MensajeEntity::class], version = 2, exportSchema = false)
internal abstract class SyncDatabase : RoomDatabase() {
    abstract fun mensajeDao(): MensajeDao
}

/**
 * Migración 1 -> 2. Reconstruye la tabla. Las filas existentes usan su propio
 * id_correlacion como clave de deduplicación (único, así que no hay colisiones).
 */
internal val MIGRACION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE pendientes_nueva (
                id_correlacion TEXT NOT NULL,
                clave_deduplicacion TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                intentos INTEGER NOT NULL,
                proximo_intento_ms INTEGER NOT NULL,
                ultimo_error TEXT,
                es_fallido_permanente INTEGER NOT NULL,
                confirmado INTEGER NOT NULL,
                PRIMARY KEY(id_correlacion))"""
        )
        db.execSQL(
            """INSERT INTO pendientes_nueva
               SELECT id_correlacion, id_correlacion, payload_json, intentos,
                      proximo_intento_ms, ultimo_error, es_fallido_permanente, 0
               FROM pendientes"""
        )
        db.execSQL("DROP TABLE pendientes")
        db.execSQL("ALTER TABLE pendientes_nueva RENAME TO pendientes")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_pendientes_clave_deduplicacion " +
                    "ON pendientes (clave_deduplicacion)"
        )
    }
}