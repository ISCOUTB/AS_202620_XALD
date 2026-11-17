package com.proyecto.xald.parser

import java.util.regex.Pattern

/**
 * Regla de extracción para una entidad bancaria específica con lista de remitentes permitidos.
 */
data class ReglaBancaria(
    val nombreEntidad: String,
    val remitentesPermitidos: Set<String>,
    val patronRegex: Pattern,
    val grupoMonto: Int = 1,
    val grupoComercio: Int = 2
)

internal object ReglasBancariasCatalogo {
    val reglas = listOf(
        // Ejemplo Bancolombia: "Bancolombia le informa compra por $45,900.00 en Exito Cartagena 10/11/2026."
        ReglaBancaria(
            nombreEntidad = "Bancolombia",
            remitentesPermitidos = setOf("890900", "85534", "Bancolombia"),
            patronRegex = Pattern.compile(
                """compra\s+por\s+\$\s*([\d\.,]+)\s+en\s+(.+?)(?:(?:\s+\d{2}/\d{2}/\d{4})|(?:\.\s*$))""",
                Pattern.CASE_INSENSITIVE
            )
        ),
        // Ejemplo Davivienda: "Davivienda: Compro por $120.000,00 en ARA CARIBE el 10/11/2026"
        ReglaBancaria(
            nombreEntidad = "Davivienda",
            remitentesPermitidos = setOf("85888", "Davivienda"),
            patronRegex = Pattern.compile(
                """compro\s+por\s+\$\s*([\d\.,]+)\s+en\s+(.+?)(?:(?:\s+el\s+)|(?:\.\s*$))""",
                Pattern.CASE_INSENSITIVE
            )
        ),
        // Ejemplo Nequi: "Pagaste $15.500 a TIENDA DON PEDRO."
        ReglaBancaria(
            nombreEntidad = "Nequi",
            remitentesPermitidos = setOf("85954", "Nequi"),
            patronRegex = Pattern.compile(
                """pagaste\s+\$\s*([\d\.,]+)\s+a\s+(.+?)(?:(?:\.)|(?:\s*$))""",
                Pattern.CASE_INSENSITIVE
            )
        )
    )
}