package com.proyecto.xald.corefinanciero

/**
 * Objeto de solo lectura empaquetado por el núcleo financiero.
 * Sirve de contrato seguro para enviar datos al módulo :syncqueue sin exponer la tabla original.
 */
data class PayloadSincronizacionDTO(
    val idTransaccion: String,
    val monto: Double,
    val comercio: String,
    val fechaTimestamp: Long
)