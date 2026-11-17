package com.proyecto.xald.parser

/**
 * Contrato público expuesto por el módulo :parser.
 */
interface ServicioParser {
    /** Parsing tradicional sin validación de remitente (fallback/desarrollo). */
    fun parsear(mensajeCrudo: String): TransaccionProcesadaDTO

    /** Parsing oficial con filtrado de remitente y resultado tipado. */
    fun parsearSms(
        remitente: String,
        cuerpo: String,
        timestampMensaje: Long = System.currentTimeMillis()
    ): ResultadoParseo
}

fun crearServicioParser(
    reglasPersonalizadas: List<ReglaBancaria>? = null
): ServicioParser {
    val reglas = reglasPersonalizadas ?: ReglasBancariasCatalogo.reglas
    return ParseoSms(reglas)
}