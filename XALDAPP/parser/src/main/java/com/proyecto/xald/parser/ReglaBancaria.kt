package com.proyecto.xald.parser

import java.util.regex.Pattern

/**
 * Regla de extracción para una entidad bancaria específica con lista de remitentes permitidos.
 * Un banco puede tener varias reglas (compra, retiro, transferencia...); se prueba en orden y
 * gana la primera que coincide. El tipo de movimiento lo decide [ClasificadorMovimientos].
 */
data class ReglaBancaria(
    val nombreEntidad: String,
    val remitentesPermitidos: Set<String>,
    val patronRegex: Pattern,
    val grupoMonto: Int = 1,
    val grupoComercio: Int = 2
)

/**
 * Formatos de ejemplo de cada banco. Los bancos cambian sus textos: cuando llegue un SMS real
 * que no se reconozca, se agrega aquí su regla (y un caso en ParseoSmsTest).
 */
internal object ReglasBancariasCatalogo {

    private const val MONTO = """\$\s*([\d\.,]+)"""
    private const val FIN = """(?:(?:\s+(?:el\s+)?\d{2}/\d{2}/\d{4})|(?:\s+en\s+(?:tu|su)\s+cuenta)|(?:\.\s*$)|$)"""

    private val BANCOLOMBIA = setOf("890900", "85534", "Bancolombia")
    private val DAVIVIENDA = setOf("85888", "Davivienda")
    private val NEQUI = setOf("85954", "Nequi")

    private fun regla(entidad: String, remitentes: Set<String>, patron: String, grupoMonto: Int = 1, grupoComercio: Int = 2) =
        ReglaBancaria(entidad, remitentes, Pattern.compile(patron, Pattern.CASE_INSENSITIVE), grupoMonto, grupoComercio)

    val reglas = listOf(
        // ---------------------------------------------------------------------------- Bancolombia
        // "Bancolombia le informa compra por $45,900.00 en Exito Cartagena 10/11/2026."
        regla("Bancolombia", BANCOLOMBIA, """compra\s+por\s+$MONTO\s+en\s+(.+?)(?:(?:\s+\d{2}/\d{2}/\d{4})|(?:\.\s*$))"""),
        // "Bancolombia le informa retiro por $200,000.00 en CAJERO CENTRO 10/11/2026."
        regla("Bancolombia", BANCOLOMBIA, """retiro\s+por\s+$MONTO\s+en\s+(.+?)$FIN"""),
        // "Bancolombia le informa pago por $85,000.00 a CLARO 10/11/2026."
        regla("Bancolombia", BANCOLOMBIA, """pago\s+por\s+$MONTO\s+a\s+(.+?)$FIN"""),
        // "Bancolombia: Transferiste $50,000.00 desde tu cuenta *1234 a la cuenta *5678 el 10/11/2026 10:30."
        regla(
            "Bancolombia", BANCOLOMBIA,
            """transferiste\s+$MONTO\s+(?:desde\s+tu\s+cuenta\s+\S+\s+)?a\s+(?:la\s+cuenta\s+)?(.+?)$FIN"""
        ),
        // "Bancolombia: Recibiste una transferencia por $100,000.00 de JUAN PEREZ en tu cuenta *1234 el 10/11/2026."
        regla("Bancolombia", BANCOLOMBIA, """recibiste\s+una\s+transferencia\s+por\s+$MONTO\s+de\s+(.+?)$FIN"""),
        // "Bancolombia le informa Pago de Nomina de EMPRESA SAS por $2,500,000.00 en su cuenta *1234 10/11/2026."
        regla("Bancolombia", BANCOLOMBIA, """pago\s+de\s+n[oó]mina\s+de\s+(.+?)\s+por\s+$MONTO""", grupoMonto = 2, grupoComercio = 1),
        // "Bancolombia le informa abono por $500,000.00 de EMPRESA SAS en su cuenta *1234 10/11/2026."
        regla("Bancolombia", BANCOLOMBIA, """abono\s+por\s+$MONTO\s+de\s+(.+?)$FIN"""),

        // ----------------------------------------------------------------------------- Davivienda
        // "Davivienda: Compro por $120.000,00 en ARA CARIBE el 10/11/2026"
        regla("Davivienda", DAVIVIENDA, """compro\s+por\s+$MONTO\s+en\s+(.+?)(?:(?:\s+el\s+)|(?:\.\s*$))"""),
        // "Davivienda: Transferencia exitosa por $50.000,00 a JUAN PEREZ el 10/11/2026"
        regla("Davivienda", DAVIVIENDA, """transferencia\s+exitosa\s+por\s+$MONTO\s+a\s+(.+?)$FIN"""),
        // "Davivienda: Recibio una transferencia por $80.000,00 de MARIA LOPEZ el 10/11/2026"
        regla("Davivienda", DAVIVIENDA, """recibi[oó]\s+una\s+transferencia\s+por\s+$MONTO\s+de\s+(.+?)$FIN"""),

        // ---------------------------------------------------------------------------------- Nequi
        // "Pagaste $15.500 a TIENDA DON PEDRO."
        regla("Nequi", NEQUI, """pagaste\s+$MONTO\s+a\s+(.+?)(?:(?:\.)|(?:\s*$))"""),
        // "Enviaste $20.000 a JUAN PEREZ."
        regla("Nequi", NEQUI, """enviaste\s+$MONTO\s+a\s+(.+?)(?:(?:\.)|(?:\s*$))"""),
        // "Recibiste $20.000 de JUAN PEREZ."  /  "JUAN PEREZ te envio $20.000"
        regla("Nequi", NEQUI, """recibiste\s+$MONTO\s+de\s+(.+?)(?:(?:\.)|(?:\s*$))"""),
        regla("Nequi", NEQUI, """^(.+?)\s+te\s+envi[oó]\s+$MONTO""", grupoMonto = 2, grupoComercio = 1)
    )
}
