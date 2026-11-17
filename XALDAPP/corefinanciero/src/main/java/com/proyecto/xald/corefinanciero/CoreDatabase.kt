package com.proyecto.xald.corefinanciero

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TransaccionEntidad::class], version = 1, exportSchema = false)
internal abstract class CoreDatabase : RoomDatabase() {
    abstract fun transaccionDao(): TransaccionDao
}