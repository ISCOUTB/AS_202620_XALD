package com.proyecto.xald.syncqueue

import kotlin.math.min
import kotlin.math.pow

/**
 * Política de reintentos con espera exponencial (backoff) y tope.
 *
 * Valores por defecto (docs/mensajes/README.md §4):
 *  - 6 intentos como máximo por mensaje;
 *  - esperas de 30 s, 60 s, 120 s, 240 s y 480 s entre ellos (base 30 s, factor 2);
 *  - ninguna espera supera 15 min.
 * Tras el sexto fallo recuperable el mensaje pasa a la cola de fallidos.
 *
 * "Sin conexión" no consume intentos (ver [ColaSincronizacion]): en un sistema Offline-First
 * estar sin red es el caso normal, no un fallo del mensaje.
 */
data class PoliticaReintentos(
    val maxIntentos: Int = 6,
    val esperaBaseMs: Long = 30_000,
    val factor: Double = 2.0,
    val esperaMaximaMs: Long = 15 * 60_000
) {
    init {
        require(maxIntentos >= 1) { "maxIntentos debe ser >= 1" }
        require(esperaBaseMs > 0) { "esperaBaseMs debe ser > 0" }
        require(factor >= 1.0) { "factor debe ser >= 1 para que la espera no decrezca" }
        require(esperaMaximaMs >= esperaBaseMs) { "esperaMaximaMs debe ser >= esperaBaseMs" }
    }

    /** Espera antes del siguiente envío, tras el intento fallido número [intentoFallido] (1, 2, ...). */
    fun esperaTrasFallo(intentoFallido: Int): Long {
        require(intentoFallido >= 1)
        val exponencial = esperaBaseMs * factor.pow(intentoFallido - 1)
        return min(exponencial, esperaMaximaMs.toDouble()).toLong()
    }

    /** true si, tras [intentosFallidos] fallos, el mensaje ya no se debe reintentar. */
    fun agotada(intentosFallidos: Int): Boolean = intentosFallidos >= maxIntentos
}
