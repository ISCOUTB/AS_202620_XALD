package com.proyecto.xald.corefinanciero

/**
 * Objeto de transferencia de datos (DTO) compartido para la cola de sincronización.
 */
data class PayloadSincronizacionDTO(
    val idTransaccion: String,
    val monto: Double,
    val comercio: String,
    val fechaTimestamp: Long,
    val moneda: String = "COP",
    val categoria: String = "Sin Categorizar",
    val origenDatos: String = "SMS_REGEX"
)