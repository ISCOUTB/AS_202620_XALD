package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO

/**
 * Gestor interno de la cola de sincronización offline.
 * Administra el tránsito seguro de payloads antes del envío al Backend XALD (TLS 1.3).
 */
internal class ColaSincronizacion : ColaSincronizacionService {

    private val colaSincronizacion = mutableListOf<PayloadSincronizacionDTO>()

    override fun encolarTransaccion(payload: PayloadSincronizacionDTO): Boolean {
        return colaSincronizacion.add(payload)
    }

    override fun obtenerCantidadPendientes(): Int {
        return colaSincronizacion.size
    }

    override fun obtenerSiguienteParaSincronizar(): PayloadSincronizacionDTO? {
        return colaSincronizacion.firstOrNull()
    }
}