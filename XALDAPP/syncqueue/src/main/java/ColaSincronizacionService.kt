package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO

/**
 * Contrato público para administrar la cola de transacciones pendientes hacia el Backend XALD.
 */
interface ColaSincronizacionService {
    fun encolarTransaccion(payload: PayloadSincronizacionDTO): Boolean
    fun obtenerCantidadPendientes(): Int
    fun obtenerSiguienteParaSincronizar(): PayloadSincronizacionDTO?
}

fun crearColaSincronizacionService(): ColaSincronizacionService = ColaSincronizacion()