package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.time.Instant

/**
 * Cliente HTTP de la cola hacia `POST /api/v1/transacciones` (docs/api/openapi.yaml).
 * Solo usa `java.net`, sin dependencias nuevas.
 *
 * Cabeceras que envía en cada intento:
 *  - `X-API-Key`: autenticación (ADR-0004).
 *  - `X-Correlation-Id`: [MensajeSincronizacion.idCorrelacion], igual en todos los reintentos.
 *  - `X-Version-Esquema`: versión mayor del contrato del mensaje.
 */
class TransporteHttpSincronizacion(
    private val urlBase: String,
    private val apiKey: String,
    private val timeoutMs: Int = 10_000
) : TransporteSincronizacion {

    override fun enviar(mensaje: MensajeSincronizacion): ResultadoEnvio {
        val conexion = try {
            URI("${urlBase.trimEnd('/')}/api/v1/transacciones").toURL().openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return ResultadoEnvio.FalloPermanente("URL inválida: ${e.javaClass.simpleName}")
        }
        return try {
            conexion.requestMethod = "POST"
            conexion.connectTimeout = timeoutMs
            conexion.readTimeout = timeoutMs
            conexion.doOutput = true
            conexion.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conexion.setRequestProperty("X-API-Key", apiKey)
            conexion.setRequestProperty("X-Correlation-Id", mensaje.idCorrelacion)
            conexion.setRequestProperty("X-Version-Esquema", mensaje.versionEsquema.toString())
            conexion.outputStream.use { it.write(payloadAJson(mensaje.payload).toByteArray(Charsets.UTF_8)) }
            clasificarRespuestaHttp(conexion.responseCode)
        } catch (e: UnknownHostException) {
            ResultadoEnvio.SinConexion("UnknownHostException")
        } catch (e: ConnectException) {
            ResultadoEnvio.SinConexion("ConnectException")
        } catch (e: SocketTimeoutException) {
            // Pudo llegar al backend y perderse la respuesta: se reintenta y el backend deduplica.
            ResultadoEnvio.FalloRecuperable("timeout")
        } catch (e: IOException) {
            ResultadoEnvio.FalloRecuperable(e.javaClass.simpleName)
        } finally {
            conexion.disconnect()
        }
    }
}

/**
 * Serializa el payload con los nombres de campo del contrato v1
 * (docs/mensajes/transaccion-registrada.schema.json). Lo verifica ContratoMensajeTest.
 */
fun payloadAJson(p: PayloadSincronizacionDTO): String = buildString {
    append('{')
    append("\"id_transaccion\":").append(jsonTexto(p.idTransaccion)).append(',')
    append("\"monto\":").append(p.monto).append(',')
    append("\"moneda\":").append(jsonTexto(p.moneda)).append(',')
    append("\"comercio\":").append(jsonTexto(p.comercio)).append(',')
    append("\"categoria\":").append(jsonTexto(p.categoria)).append(',')
    append("\"fecha_transaccion\":").append(jsonTexto(Instant.ofEpochMilli(p.fechaTimestamp).toString())).append(',')
    append("\"origen_datos\":").append(jsonTexto(p.origenDatos))
    append('}')
}

internal fun jsonTexto(valor: String): String = buildString {
    append('"')
    for (c in valor) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c == '\n' -> append("\\n")
            c == '\r' -> append("\\r")
            c == '\t' -> append("\\t")
            c < ' ' -> append(String.format("\\u%04x", c.code))
            else -> append(c)
        }
    }
    append('"')
}
