package com.proyecto.xald.parser

/**
 * Componente interno del contexto de Ingesta.
 * Aplica expresiones regulares al SMS crudo del sistema operativo.
 */
internal class ParseoSms : ServicioParser {

    /**
     * Limpia saltos de línea y normaliza el texto crudo del SMS.
     */
    private fun procesarMensajeCrudo(mensajeCrudo: String): String {
        return mensajeCrudo
            .replace(Regex("[\\r\\n]+"), " ")
            .trim()
    }

    override fun parsear(mensajeCrudo: String): TransaccionProcesadaDTO {
        val textoLimpio = procesarMensajeCrudo(mensajeCrudo)

        val regexMonto = Regex("\\\$([0-9]+(?:\\.[0-9]{1,2})?)")
        val regexComercio = Regex("en\\s+([A-ZÁÉÍÓÚÑa-záéíóúñ\\s]+)")

        val matchMonto = regexMonto.find(textoLimpio)
        val matchComercio = regexComercio.find(textoLimpio)

        val monto = matchMonto?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val comercio = matchComercio?.groupValues?.get(1)?.trim() ?: "DESCONOCIDO"

        return TransaccionProcesadaDTO(
            monto = monto,
            comercio = comercio,
            textoProcesado = textoLimpio
        )
    }
}