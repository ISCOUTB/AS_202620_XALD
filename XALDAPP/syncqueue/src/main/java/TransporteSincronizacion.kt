package com.proyecto.xald.syncqueue

/** Resultado de un intento de entrega, ya clasificado para decidir qué hace la cola. */
sealed interface ResultadoEnvio {
    /** El backend respondió 2xx: el mensaje se retira de la cola. */
    data object Confirmado : ResultadoEnvio

    /** Fallo transitorio (timeout, 408, 429, 5xx): se reintenta con backoff y consume un intento. */
    data class FalloRecuperable(val motivo: String) : ResultadoEnvio

    /** Fallo que no se arregla reintentando (400, 401, 422...): va directo a la cola de fallidos. */
    data class FalloPermanente(val motivo: String) : ResultadoEnvio

    /** No hay red. Se reprograma sin consumir intentos (Offline-First, ADR-0001). */
    data class SinConexion(val motivo: String) : ResultadoEnvio
}

/**
 * Puerto de salida de la cola. La implementación real es [TransporteHttpSincronizacion];
 * las pruebas usan dobles que simulan fallos.
 */
fun interface TransporteSincronizacion {
    fun enviar(mensaje: MensajeSincronizacion): ResultadoEnvio
}

/** Transporte por defecto mientras la app no configure el cliente HTTP: nunca hay "red". */
internal val TransporteNoConfigurado = TransporteSincronizacion {
    ResultadoEnvio.SinConexion("transporte no configurado")
}

/** Clasificación de códigos HTTP (docs/mensajes/README.md §4). */
fun clasificarRespuestaHttp(codigo: Int): ResultadoEnvio = when {
    codigo in 200..299 -> ResultadoEnvio.Confirmado
    codigo == 408 || codigo == 429 || codigo >= 500 -> ResultadoEnvio.FalloRecuperable("HTTP $codigo")
    else -> ResultadoEnvio.FalloPermanente("HTTP $codigo")
}
