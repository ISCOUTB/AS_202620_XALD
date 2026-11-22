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
        val reglasDelRemitente = reglas.filter { regla ->
            regla.remitentesPermitidos.any { it.equals(remitenteLimpio, ignoreCase = true) }
        }
        if (reglasDelRemitente.isEmpty()) {
            return ResultadoParseo.RemitenteNoAutorizado(remitenteLimpio)
        }

        // 2. Evaluacion de Regex: cada banco tiene varias reglas (compra, retiro, transferencia...);
        //    gana la primera que coincide.
        for (regla in reglasDelRemitente) {
            val matcher = regla.patronRegex.matcher(cuerpoLimpio)
            if (!matcher.find()) continue

            // 3. Extracción y Limpieza de Monto
            val montoTexto = matcher.group(regla.grupoMonto) ?: ""
            val montoParsed = parsearMonto(montoTexto)
                ?: return ResultadoParseo.FormatoNoReconocido("No se pudo convertir el monto: '$montoTexto'")

            if (montoParsed <= 0.0) {
                return ResultadoParseo.MontoInvalido(montoParsed)
            }

            // 4. Extracción y Limpieza de Comercio (o de la contraparte en una transferencia)
            val comercioTexto = matcher.group(regla.grupoComercio)?.trim()?.trimEnd('.', ',') ?: ""
            if (comercioTexto.isBlank()) {
                return ResultadoParseo.FormatoNoReconocido("El nombre del comercio resultó vacío")
            }

            return ResultadoParseo.Exitoso(
                TransaccionProcesadaDTO(
                    monto = montoParsed,
                    comercio = comercioTexto,
                    fechaTimestamp = timestampMensaje,
                    moneda = "COP",
                    entidadBancaria = regla.nombreEntidad,
                    origenDatos = "SMS_REGEX",
                    textoProcesado = cuerpoLimpio,
                    // 5. Gasto, ingreso o transferencia (enviada/recibida) según el texto.
                    tipoMovimiento = ClasificadorMovimientos.clasificar(cuerpoLimpio)
                )
            )
        }

        return ResultadoParseo.FormatoNoReconocido(
            "El mensaje de ${reglasDelRemitente.first().nombreEntidad} no coincide con ninguna regla de parsing"
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