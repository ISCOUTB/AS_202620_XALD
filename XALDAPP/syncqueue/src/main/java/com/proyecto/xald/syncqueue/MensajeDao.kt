package com.proyecto.xald.syncqueue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Operaciones transaccionales para la cola de sincronización.
 * Asegura la durabilidad de los mensajes ante reinicios de la aplicación.
 */
@Dao
internal interface MensajeDao {

    // Encolar (Idempotente: el índice UNIQUE de clave_deduplicacion hace que el IGNORE devuelva -1)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertar(mensaje: MensajeEntity): Long

    // Busca un mensaje (pendiente, fallido o confirmado aún retenido) con la misma clave de deduplicación
    @Query("SELECT id_correlacion FROM pendientes WHERE clave_deduplicacion = :clave LIMIT 1")
    fun idPorClave(clave: String): String?

    // Obtener cantidad para métricas/UI
    @Query("SELECT COUNT(*) FROM pendientes WHERE es_fallido_permanente = 0 AND confirmado = 0")
    fun obtenerCantidadPendientes(): Int

    // Procesar Pendientes: FIFO y respetando el backoff (proximo_intento_ms)
    @Query("SELECT * FROM pendientes WHERE es_fallido_permanente = 0 AND confirmado = 0 AND proximo_intento_ms <= :tiempoActual ORDER BY proximo_intento_ms ASC")
    fun obtenerListosParaEnviar(tiempoActual: Long): List<MensajeEntity>

    // Obtener cola de Dead-Letter (DLQ)
    @Query("SELECT * FROM pendientes WHERE es_fallido_permanente = 1 ORDER BY proximo_intento_ms DESC")
    fun obtenerFallidos(): List<MensajeEntity>

    // Actualizar estado tras un fallo recuperable (Backoff)
    @Query("UPDATE pendientes SET intentos = :intentos, proximo_intento_ms = :proximoMs, ultimo_error = :error WHERE id_correlacion = :idCorrelacion")
    fun actualizarIntento(idCorrelacion: String, intentos: Int, proximoMs: Long, error: String?)

    // Mover a fallidos (DLQ) tras agotar política o fallo 4xx
    @Query("UPDATE pendientes SET es_fallido_permanente = 1, ultimo_error = :motivo, intentos = :intentos WHERE id_correlacion = :idCorrelacion")
    fun moverAFallidos(idCorrelacion: String, intentos: Int, motivo: String)

    // Re-drive manual de la DLQ a la cola normal
    @Query("UPDATE pendientes SET es_fallido_permanente = 0, intentos = 0, proximo_intento_ms = :tiempoActual, ultimo_error = NULL WHERE id_correlacion = :idCorrelacion")
    fun reintentarFallido(idCorrelacion: String, tiempoActual: Long): Int

    // Tras un HTTP 2xx: se conserva la fila (con su clave) para seguir deduplicando; se purga más adelante
    @Query("UPDATE pendientes SET confirmado = 1, ultimo_error = NULL, proximo_intento_ms = :ahora WHERE id_correlacion = :idCorrelacion")
    fun confirmarEnvio(idCorrelacion: String, ahora: Long)

    // Limpieza de confirmados antiguos (proximo_intento_ms guarda el instante de confirmación)
    @Query("DELETE FROM pendientes WHERE confirmado = 1 AND proximo_intento_ms < :limite")
    fun purgarConfirmados(limite: Long)
}