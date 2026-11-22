package com.proyecto.xald.parser

import java.text.Normalizer

/**
 * Clasificador de transferencias: decide, por el texto del SMS, si el movimiento es un gasto
 * (compra, pago, retiro), un ingreso (nómina, abono, consignación) o una transferencia enviada
 * o recibida.
 *
 * Devuelve los mismos códigos que `TipoMovimiento` de :corefinanciero (este módulo no depende
 * de él, por eso son texto). Las palabras se buscan sin tildes ni mayúsculas.
 */
object ClasificadorMovimientos {

    const val GASTO = "GASTO"
    const val INGRESO = "INGRESO"
    const val TRANSFERENCIA_ENVIADA = "TRANSFERENCIA_ENVIADA"
    const val TRANSFERENCIA_RECIBIDA = "TRANSFERENCIA_RECIBIDA"

    private val RECIBIDA_EXPLICITA = listOf(
        "recibiste una transferencia", "recibio una transferencia", "transferencia recibida",
        "te transfirio", "te transfirieron", "te envio", "te enviaron", "te llego"
    )
    private val ENVIADA = listOf(
        "transferiste", "enviaste", "transferencia exitosa", "transferencia enviada",
        "transferencia realizada", "envio de dinero a"
    )
    private val INGRESO_PALABRAS = listOf(
        "nomina", "abono", "consignacion", "te consignaron", "deposito", "pago de salario",
        "reembolso", "devolucion"
    )

    /**
     * Orden de prioridad: transferencia recibida explícita → transferencia enviada → ingreso →
     * "recibiste ... de" genérico (dinero que llega de otra persona) → gasto.
     */
    fun clasificar(textoSms: String): String {
        val texto = normalizar(textoSms)
        return when {
            RECIBIDA_EXPLICITA.any { texto.contains(it) } -> TRANSFERENCIA_RECIBIDA
            ENVIADA.any { texto.contains(it) } -> TRANSFERENCIA_ENVIADA
            INGRESO_PALABRAS.any { texto.contains(it) } -> INGRESO
            texto.contains("recibiste") -> TRANSFERENCIA_RECIBIDA
            else -> GASTO
        }
    }

    private fun normalizar(texto: String): String =
        Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("\\s+"), " ")
}
