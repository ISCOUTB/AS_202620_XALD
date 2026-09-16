package com.proyecto.xald.corefinanciero

/**
 * Representación oficial y permanente de una transacción dentro de la base de datos local.
 * Es la fuente primaria de verdad (Offline-First) cifrada con AES-256.
 */
data class TransaccionEntidad(
    val id: String,
    val monto: Double,
    val comercio: String,
    val fechaTimestamp: Long,
    val sincronizado: Boolean = false
)