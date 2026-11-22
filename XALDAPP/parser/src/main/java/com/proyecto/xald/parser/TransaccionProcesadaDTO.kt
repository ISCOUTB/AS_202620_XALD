package com.proyecto.xald.parser

/**
 * Datos extraídos de un mensaje SMS bancario válido.
 */
data class TransaccionProcesadaDTO(
    val monto: Double,
    val comercio: String,
    val fechaTimestamp: Long = System.currentTimeMillis(),
    val moneda: String = "COP",
    val entidadBancaria: String = "DESCONOCIDA",
    val origenDatos: String = "SMS_REGEX",
    val textoProcesado: String = "",
    /** Código de ClasificadorMovimientos: GASTO, INGRESO, TRANSFERENCIA_ENVIADA o TRANSFERENCIA_RECIBIDA. */
    val tipoMovimiento: String = ClasificadorMovimientos.GASTO
)

/**
 * Resultado sellado del parsing para evitar transacciones con datos vacíos o monto 0.0.
 */
sealed interface ResultadoParseo {
    data class Exitoso(val datos: TransaccionProcesadaDTO) : ResultadoParseo
    data class RemitenteNoAutorizado(val remitente: String) : ResultadoParseo
    data class FormatoNoReconocido(val motivo: String) : ResultadoParseo
    data class MontoInvalido(val valorEncontrado: Double) : ResultadoParseo
}