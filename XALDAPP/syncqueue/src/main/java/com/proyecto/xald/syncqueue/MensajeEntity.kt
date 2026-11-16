package com.proyecto.xald.syncqueue

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Representación durable de un mensaje en la cola de sincronización.
 * Cierra la brecha identificada en el esquema (Sección 8.4) al reemplazar
 * el estado en memoria por persistencia SQLite.
 *
 * La deduplicación vive aquí: [claveDeduplicacion] ("id_transaccion@fecha") tiene
 * índice UNIQUE, así que sobrevive a reinicios de la app. Los mensajes confirmados
 * NO se borran de inmediato: se marcan [confirmado] y se purgan pasada la retención,
 * para que un duplicado tardío siga detectándose.
 */
@Entity(
    tableName = "pendientes",
    indices = [Index(value = ["clave_deduplicacion"], unique = true)]
)
internal data class MensajeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id_correlacion")
    val idCorrelacion: String,

    @ColumnInfo(name = "clave_deduplicacion")
    val claveDeduplicacion: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "intentos")
    val intentos: Int = 0,

    @ColumnInfo(name = "proximo_intento_ms")
    val proximoIntentoMs: Long = 0L,

    @ColumnInfo(name = "ultimo_error")
    val ultimoError: String? = null,

    @ColumnInfo(name = "es_fallido_permanente")
    val esFallidoPermanente: Boolean = false,

    @ColumnInfo(name = "confirmado")
    val confirmado: Boolean = false
)