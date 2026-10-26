package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import java.util.UUID

/**
 * Contrato público para administrar la cola de transacciones pendientes hacia el Backend XALD.
 *
 * Garantía de entrega: AL MENOS UNA VEZ (docs/mensajes/README.md §3). Un mensaje solo sale
 * de la cola cuando el backend responde 2xx; si no, se reintenta con backoff y, agotada la
 * política, pasa a la cola de fallidos, de donde solo sale por [reintentarFallido].
 */
interface ColaSincronizacionService {
    /**
     * Encola la transacción. Es idempotente: si ya hay un mensaje con la misma clave
     * (`idTransaccion` + `fechaTimestamp`) pendiente, fallido o confirmado, no crea otro
     * y devuelve true, porque la transacción ya está en camino.
     */
    fun encolarTransaccion(payload: PayloadSincronizacionDTO): Boolean
    fun obtenerCantidadPendientes(): Int
    fun obtenerSiguienteParaSincronizar(): PayloadSincronizacionDTO?

    /** Envía los mensajes cuyo turno ya llegó, en orden FIFO. Lo invoca el trabajo en segundo plano. */
    fun procesarPendientes(): ResumenProcesamiento

    /** Cola de mensajes fallidos (dead-letter): no se reintentan solos. */
    fun obtenerFallidos(): List<MensajeSincronizacion>

    /** Re-drive manual: devuelve un fallido a la cola con los intentos en cero. */
    fun reintentarFallido(idCorrelacion: String): Boolean
}

/** Qué pasó en una pasada de [ColaSincronizacionService.procesarPendientes]. */
data class ResumenProcesamiento(
    val confirmados: Int = 0,
    val reprogramados: Int = 0,
    val aFallidos: Int = 0,
    val sinConexion: Boolean = false
)

fun crearColaSincronizacionService(
    transporte: TransporteSincronizacion = TransporteNoConfigurado,
    politica: PoliticaReintentos = PoliticaReintentos(),
    reloj: () -> Long = { System.currentTimeMillis() },
    registro: RegistroSincronizacion = RegistroJsonConsola,
    generarIdCorrelacion: () -> String = { UUID.randomUUID().toString() }
): ColaSincronizacionService =
    ColaSincronizacion(transporte, politica, reloj, registro, generarIdCorrelacion)
