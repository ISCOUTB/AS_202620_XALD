package com.proyecto.xald.syncqueue

import com.proyecto.xald.parser.TransaccionDto

class SyncQueueManager {
    private val colaSincronizacion = mutableListOf<TransaccionDto>()

    /**
     * Recibe la transacción procesada por el core financiero
     * y la encola de forma local para su posterior sincronización remota.
     */
    fun encolarTransaccion(transaccion: TransaccionDto): Boolean {
        return colaSincronizacion.add(transaccion)
    }

    fun obtenerCantidadPendientes(): Int {
        return colaSincronizacion.size
    }

    fun obtenerSiguienteParaSincronizar(): TransaccionDto? {
        return colaSincronizacion.firstOrNull()
    }
}