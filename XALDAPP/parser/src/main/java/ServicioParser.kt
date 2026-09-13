package com.proyecto.xald.parser
/**
 * Contrato público expuesto por el contexto de Ingesta para el procesamiento de SMS.
 */
interface ServicioParser {
    fun parsear(mensajeCrudo: String): TransaccionProcesadaDTO
}

fun crearServicioParser(): ServicioParser = ParseoSms()