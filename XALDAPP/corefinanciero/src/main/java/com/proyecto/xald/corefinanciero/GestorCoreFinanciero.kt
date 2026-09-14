package com.proyecto.xald.corefinanciero

import java.util.UUID

/**
 * Implementación interna del Núcleo Financiero.
 * Administra la persistencia de [TransaccionEntidad] de forma encapsulada.
 */
internal class GestorCoreFinanciero : InformacionFinanciera {

    private val baseDeDatosLocal = mutableListOf<TransaccionEntidad>()

    override fun guardarTransaccion(monto: Double, comercio: String): Boolean {
        if (monto <= 0.0) return false

        val nuevaEntidad = TransaccionEntidad(
            id = UUID.randomUUID().toString(),
            monto = monto,
            comercio = comercio,
            fechaTimestamp = System.currentTimeMillis(),
            sincronizado = false
        )
        return baseDeDatosLocal.add(nuevaEntidad)
    }

    override fun obtenerUltimaTransaccion(): TransaccionEntidad? {
        return baseDeDatosLocal.lastOrNull()
    }

    override fun obtenerPendientesSincronizacion(): List<PayloadSincronizacionDTO> {
        return baseDeDatosLocal
            .filter { !it.sincronizado }
            .map { entidad ->
                PayloadSincronizacionDTO(
                    idTransaccion = entidad.id,
                    monto = entidad.monto,
                    comercio = entidad.comercio,
                    fechaTimestamp = entidad.fechaTimestamp
                )
            }
    }
}