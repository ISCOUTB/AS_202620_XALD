package com.proyecto.xald.parser

import com.proyecto.xald.aigemini.Geminiproc

data class TransaccionDto(
    val monto: String,
    val comercio: String,
    val textoProcesado: String
)

class Parser {
    private val aiProcessor = Geminiproc()

    /**
     * Recibe el mensaje crudo, delega la limpieza a geminiproc
     * y extrae los datos estructurados en el DTO.
     */
    fun parsear(mensajeCrudo: String): TransaccionDto {
        val textoLimpio = aiProcessor.procesarMensajeCrudo(mensajeCrudo)

        val regexMonto = Regex("\\\$([0-9]+)")
        val regexComercio = Regex("en\\s+([A-ZÁÉÍÓÚÑa-záéíóúñ\\s]+)")

        val matchMonto = regexMonto.find(textoLimpio)
        val matchComercio = regexComercio.find(textoLimpio)

        val monto = matchMonto?.groupValues?.get(1) ?: "0"
        val comercio = matchComercio?.groupValues?.get(1)?.trim() ?: "DESCONOCIDO"

        return TransaccionDto(
            monto = monto,
            comercio = comercio,
            textoProcesado = textoLimpio
        )
    }
}