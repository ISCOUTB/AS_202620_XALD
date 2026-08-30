package com.proyecto.xald.aigemini

class Geminiproc{
    /**
     * Recibe el texto crudo (notificación o SMS del banco) y realiza
     * un pre-análisis o limpieza para estructurarlo antes de pasarlo al parser.
     */
    fun procesarMensajeCrudo(mensajeCrudo: String): String {
        // Limpia saltos de línea, espacios múltiples y normaliza el formato
        val textoLimpio = mensajeCrudo
            .replace(Regex("[\r\n]+"), " ")
            .trim()

        return textoLimpio
    }
}