package com.proyecto.xald.parser

internal class ParseoSms(
    private val reglas: List<ReglaBancaria>
) : ServicioParser {

    private fun normalizarTexto(mensajeCrudo: String): String {
        return mensajeCrudo
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    override fun parsear(mensajeCrudo: String): TransaccionProcesadaDTO {
        val textoLimpio = normalizarTexto(mensajeCrudo)
        val regexMonto = Regex("\\\$([0-9]+(?:[\\.,][0-9]{1,2})?)")
        val regexComercio = Regex("en\\s+([A-ZÁÉÍÓÚÑa-záéíóúñ\\s]+)")

        val matchMonto = regexMonto.find(textoLimpio)
        val matchComercio = regexComercio.find(textoLimpio)

        val montoStr = matchMonto?.groupValues?.get(1)?.replace(",", "") ?: "0.0"
        val monto = montoStr.toDoubleOrNull() ?: 0.0
        val comercio = matchComercio?.groupValues?.get(1)?.trim() ?: "DESCONOCIDO"

        return TransaccionProcesadaDTO(
            monto = monto,
            comercio = comercio,
            textoProcesado = textoLimpio
        )
    }

    override fun parsearSms(
        remitente: String,
        cuerpo: String,
        timestampMensaje: Long
    ): ResultadoParseo {
        val remitenteLimpio = remitente.trim()
        val cuerpoLimpio = normalizarTexto(cuerpo)

        // 1. Filtrado por Allowlist de Remitentes (ADR-0016)
        val reglaCoincidente = reglas.firstOrNull { regla ->
            regla.remitentesPermitidos.any { it.equals(remitenteLimpio, ignoreCase = true) }
        } ?: return ResultadoParseo.RemitenteNoAutorizado(remitenteLimpio)

        // 2. Evaluacion de Regex
        val matcher = reglaCoincidente.patronRegex.matcher(cuerpoLimpio)
        if (!matcher.find()) {
            return ResultadoParseo.FormatoNoReconocido("El mensaje de ${reglaCoincidente.nombreEntidad} no coincide con la regla de parsing")
        }

        // 3. Extracción y Limpieza de Monto
        val montoTexto = matcher.group(reglaCoincidente.grupoMonto) ?: ""
        val montoParsed = parsearMonto(montoTexto)
            ?: return ResultadoParseo.FormatoNoReconocido("No se pudo convertir el monto: '$montoTexto'")

        if (montoParsed <= 0.0) {
            return ResultadoParseo.MontoInvalido(montoParsed)
        }

        // 4. Extracción y Limpieza de Comercio
        val comercioTexto = matcher.group(reglaCoincidente.grupoComercio)?.trim() ?: ""
        if (comercioTexto.isBlank()) {
            return ResultadoParseo.FormatoNoReconocido("El nombre del comercio resultó vacío")
        }

        return ResultadoParseo.Exitoso(
            TransaccionProcesadaDTO(
                monto = montoParsed,
                comercio = comercioTexto,
                fechaTimestamp = timestampMensaje,
                moneda = "COP",
                entidadBancaria = reglaCoincidente.nombreEntidad,
                origenDatos = "SMS_REGEX",
                textoProcesado = cuerpoLimpio
            )
        )
    }

    private fun parsearMonto(montoStr: String): Double? {
        return try {
            val sinEspacios = montoStr.replace(" ", "")
            val limpio = if (sinEspacios.contains(",") && sinEspacios.contains(".")) {
                if (sinEspacios.lastIndexOf('.') > sinEspacios.lastIndexOf(',')) {
                    sinEspacios.replace(",", "")
                } else {
                    sinEspacios.replace(".", "").replace(",", ".")
                }
            } else if (sinEspacios.contains(",")) {
                val partes = sinEspacios.split(",")
                if (partes.last().length == 2) sinEspacios.replace(",", ".") else sinEspacios.replace(",", "")
            } else if (sinEspacios.contains(".")) {
                val partes = sinEspacios.split(".")
                if (partes.last().length == 2) sinEspacios else sinEspacios.replace(".", "")
            } else {
                sinEspacios
            }
            limpio.toDouble()
        } catch (e: Exception) {
            null
        }
    }
}