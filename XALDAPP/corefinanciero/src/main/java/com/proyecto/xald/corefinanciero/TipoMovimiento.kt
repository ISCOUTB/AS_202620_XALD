package com.proyecto.xald.corefinanciero

/**
 * Naturaleza de un movimiento de dinero. Decide si suma o resta al saldo disponible y en qué
 * estadística entra: solo los GASTO cuentan para categorías y presupuestos; las transferencias
 * mueven el saldo pero no son "gasto" (mandarle plata a alguien no es comprar algo).
 */
enum class TipoMovimiento(val etiqueta: String, val esEntrada: Boolean) {
    GASTO("Gasto", esEntrada = false),
    INGRESO("Ingreso", esEntrada = true),
    TRANSFERENCIA_ENVIADA("Transferencia enviada", esEntrada = false),
    TRANSFERENCIA_RECIBIDA("Transferencia recibida", esEntrada = true);

    val esTransferencia: Boolean
        get() = this == TRANSFERENCIA_ENVIADA || this == TRANSFERENCIA_RECIBIDA

    companion object {
        /** Desconocido o vacío (filas de la versión 1 de la base) se lee como GASTO. */
        fun desdeCodigo(codigo: String?): TipoMovimiento {
            val limpio = codigo?.trim().orEmpty()
            return entries.firstOrNull { it.name.equals(limpio, ignoreCase = true) } ?: GASTO
        }
    }
}

/** De dónde salió el movimiento. SMS_REGEX y AI_GEMINI vienen ambos de un SMS del banco. */
enum class OrigenMovimiento(val etiqueta: String) {
    SMS("SMS"),
    MANUAL("Manual");

    companion object {
        const val CODIGO_MANUAL = "MANUAL"

        fun desdeCodigo(origenDatos: String?): OrigenMovimiento =
            if (origenDatos?.trim().equals(CODIGO_MANUAL, ignoreCase = true)) MANUAL else SMS
    }
}

/** Categorías que no salen del categorizador de comercios. */
object CategoriasEspeciales {
    const val SIN_CATEGORIZAR = "Sin Categorizar"
    const val INGRESOS = "Ingresos"
    const val TRANSFERENCIAS = "Transferencias"

    /** Clave del presupuesto global (límite sobre todo el gasto del periodo). */
    const val PRESUPUESTO_TOTAL = "__TOTAL__"

    /** Categoría que corresponde a un tipo que no es gasto (los gastos usan la del categorizador). */
    fun paraTipo(tipo: TipoMovimiento, categoriaGasto: String): String = when (tipo) {
        TipoMovimiento.GASTO -> categoriaGasto.trim().ifEmpty { SIN_CATEGORIZAR }
        TipoMovimiento.INGRESO -> INGRESOS
        TipoMovimiento.TRANSFERENCIA_ENVIADA, TipoMovimiento.TRANSFERENCIA_RECIBIDA -> TRANSFERENCIAS
    }
}
