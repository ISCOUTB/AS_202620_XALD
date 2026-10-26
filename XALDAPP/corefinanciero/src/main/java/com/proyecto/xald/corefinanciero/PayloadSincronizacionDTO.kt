package com.proyecto.xald.corefinanciero

/**
 * Objeto de solo lectura empaquetado por el núcleo financiero.
 * Sirve de contrato seguro para enviar datos al módulo :syncqueue sin exponer la tabla original.
 *
 * Evolución compatible (docs/mensajes/README.md §2): los campos `moneda`, `categoria` y
 * `origenDatos` se añadieron con valor por defecto, así que todo código que construía el
 * DTO con los cuatro campos originales sigue compilando sin cambios.
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
