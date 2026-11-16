package com.proyecto.xald.corefinanciero

import java.util.UUID

internal class GestorCoreFinanciero(
    private val dao: TransaccionDao
) : InformacionFinanciera {

    override fun guardarTransaccion(monto: Double, comercio: String): Boolean {
        return guardarTransaccionCompleta(monto = monto, comercio = comercio)
    }

    override fun guardarTransaccionCompleta(
        monto: Double,
        comercio: String,
        moneda: String,
        categoria: String,
        origenDatos: String,
        fechaTimestamp: Long,
        idTransaccion: String
    ): Boolean {
        // NaN <= 0.0 es false, por eso se comprueba aparte que el monto sea finito.
        if (monto <= 0.0 || !monto.isFinite()) return false
        if (idTransaccion.isBlank()) return false

        val nuevaEntidad = TransaccionEntidad(
            id = idTransaccion,
            monto = monto,
            moneda = moneda,
            comercio = comercio,
            categoria = categoria,
            fechaTimestamp = fechaTimestamp,
            origenDatos = origenDatos,
            sincronizado = false
        )
        // insertar() devuelve -1 si el id ya existe (OnConflictStrategy.IGNORE).
        return dao.insertar(nuevaEntidad) != -1L
    }

    override fun obtenerUltimaTransaccion(): TransaccionEntidad? {
        return dao.obtenerUltimaTransaccion()
    }

    override fun obtenerPendientesSincronizacion(): List<PayloadSincronizacionDTO> {
        return dao.obtenerPendientesSincronizacion().map { entidad ->
            PayloadSincronizacionDTO(
                idTransaccion = entidad.id,
                monto = entidad.monto,
                moneda = entidad.moneda,
                comercio = entidad.comercio,
                categoria = entidad.categoria,
                fechaTimestamp = entidad.fechaTimestamp,
                origenDatos = entidad.origenDatos
            )
        }
    }

    override fun obtenerHistorial(): List<TransaccionEntidad> {
        return dao.obtenerTodas()
    }

    override fun obtenerSaldoTotal(): Double {
        return dao.obtenerSaldoTotal()
    }

    override fun marcarComoSincronizada(idTransaccion: String): Boolean {
        return dao.marcarComoSincronizado(idTransaccion) > 0
    }

    override fun actualizarCategoria(idTransaccion: String, categoria: String): Boolean {
        val limpia = categoria.trim()
        if (limpia.isEmpty()) return false
        return dao.actualizarCategoria(idTransaccion, limpia) > 0
    }
}
