package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO

/** Tipo del único mensaje que viaja hoy por la cola (docs/mensajes/README.md). */
const val TIPO_TRANSACCION_REGISTRADA = "transaccion.registrada"

/**
 * Versión mayor del esquema del mensaje. Solo cambia ante un cambio INCOMPATIBLE
 * (criterio en docs/mensajes/README.md §2). Viaja en la cabecera `X-Version-Esquema`.
 */
const val VERSION_ESQUEMA_ACTUAL = 1

/**
 * Sobre (envelope) que la cola guarda y reenvía. El payload es el contrato de negocio;
 * el resto son metadatos de entrega.
 *
 * @property idCorrelacion UUID del mensaje. Se crea al encolar y NO cambia entre reintentos:
 *   viaja en la cabecera `X-Correlation-Id` y aparece en los logs del cliente y del backend.
 * @property intentos intentos de envío fallidos que ya consumió el mensaje.
 * @property proximoIntentoEnMs instante (reloj del dispositivo) a partir del cual se puede reenviar.
 */
data class MensajeSincronizacion(
    val idCorrelacion: String,
    val payload: PayloadSincronizacionDTO,
    val encoladoEnMs: Long,
    val tipo: String = TIPO_TRANSACCION_REGISTRADA,
    val versionEsquema: Int = VERSION_ESQUEMA_ACTUAL,
    val intentos: Int = 0,
    val proximoIntentoEnMs: Long = encoladoEnMs,
    val ultimoError: String? = null
) {
    /** Clave de deduplicación del lado productor (ver [claveDeduplicacion]). */
    val claveDeduplicacion: String get() = claveDeduplicacion(payload)
}

/**
 * Clave de deduplicación: identidad de la transacción + su versión.
 *
 * Es la misma regla que aplica el backend (`backend/app/main.py`): mismo `id_transaccion`
 * y misma `fecha_transaccion` es un duplicado; misma identidad con otra fecha es una
 * versión nueva que se resuelve por Last-Write-Wins (ADR-0011) y NO se descarta.
 */
fun claveDeduplicacion(payload: PayloadSincronizacionDTO): String =
    "${payload.idTransaccion}@${payload.fechaTimestamp}"
