package com.proyecto.xald.syncqueue

import java.time.Instant

/**
 * Log estructurado de la cola. Cada evento lleva `id_correlacion` e `id_transaccion`,
 * los mismos campos que registra el backend (`backend/app/main.py`), así que un mensaje
 * se sigue de productor a consumidor filtrando por `id_correlacion`.
 */
fun interface RegistroSincronizacion {
    fun registrar(evento: String, campos: Map<String, Any?>)
}

/** Una línea JSON por evento en la salida estándar (mismo formato que el backend, ADR-0010). */
object RegistroJsonConsola : RegistroSincronizacion {
    override fun registrar(evento: String, campos: Map<String, Any?>) {
        println(lineaJson(evento, campos))
    }
}

internal fun lineaJson(evento: String, campos: Map<String, Any?>): String = buildString {
    append("{\"timestamp\":").append(jsonTexto(Instant.now().toString()))
    append(",\"nivel\":\"INFO\",\"origen\":\"cliente-syncqueue\"")
    append(",\"evento\":").append(jsonTexto(evento))
    for ((clave, valor) in campos) {
        append(',').append(jsonTexto(clave)).append(':')
        when (valor) {
            null -> append("null")
            is Number, is Boolean -> append(valor)
            else -> append(jsonTexto(valor.toString()))
        }
    }
    append('}')
}
