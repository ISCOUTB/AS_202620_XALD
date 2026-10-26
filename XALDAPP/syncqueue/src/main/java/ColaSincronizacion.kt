package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO

/**
 * Gestor interno de la cola de sincronización offline.
 * Administra el tránsito de payloads antes del envío al Backend XALD (TLS 1.3).
 *
 * Diseño (docs/mensajes/README.md y ADR-0014):
 *  - Productor: [encolarTransaccion] deduplica por `idTransaccion@fechaTimestamp`.
 *  - Consumidor remoto: el backend, que vuelve a deduplicar con la misma clave.
 *  - Entrega: al menos una vez; el mensaje sale solo con [ResultadoEnvio.Confirmado].
 *  - Reintentos: [PoliticaReintentos] (backoff exponencial con tope).
 *  - Fallidos: [fallidos] hace de cola de mensajes muertos (DLQ).
 *  - Trazabilidad: cada evento del [registro] lleva `id_correlacion`.
 *
 * Estado en memoria: se pierde si el proceso muere (límite declarado en
 * docs/mensajes/limites-del-experimento.md).
 */
internal class ColaSincronizacion(
    private val transporte: TransporteSincronizacion,
    private val politica: PoliticaReintentos,
    private val reloj: () -> Long,
    private val registro: RegistroSincronizacion,
    private val generarIdCorrelacion: () -> String
) : ColaSincronizacionService {

    private val pendientes = mutableListOf<MensajeSincronizacion>()
    private val fallidos = mutableListOf<MensajeSincronizacion>()
    private val clavesConfirmadas = mutableSetOf<String>()

    @Synchronized
    override fun encolarTransaccion(payload: PayloadSincronizacionDTO): Boolean {
        val clave = claveDeduplicacion(payload)
        val existente = (pendientes + fallidos).firstOrNull { it.claveDeduplicacion == clave }
        if (existente != null || clave in clavesConfirmadas) {
            registrar("encolado_duplicado_ignorado", existente, payload, "clave" to clave)
            return true
        }
        val mensaje = MensajeSincronizacion(
            idCorrelacion = generarIdCorrelacion(),
            payload = payload,
            encoladoEnMs = reloj()
        )
        pendientes.add(mensaje)
        registrar("mensaje_encolado", mensaje, payload, "version_esquema" to mensaje.versionEsquema)
        return true
    }

    @Synchronized
    override fun obtenerCantidadPendientes(): Int = pendientes.size

    @Synchronized
    override fun obtenerSiguienteParaSincronizar(): PayloadSincronizacionDTO? =
        pendientes.firstOrNull()?.payload

    @Synchronized
    override fun obtenerFallidos(): List<MensajeSincronizacion> = fallidos.toList()

    @Synchronized
    override fun procesarPendientes(): ResumenProcesamiento {
        val ahora = reloj()
        var confirmados = 0
        var reprogramados = 0
        var aFallidos = 0

        val turno = pendientes.filter { it.proximoIntentoEnMs <= ahora }
        for (mensaje in turno) {
            val intento = mensaje.intentos + 1
            registrar("envio_intento", mensaje, mensaje.payload, "intento" to intento)

            when (val resultado = transporte.enviar(mensaje)) {
                is ResultadoEnvio.Confirmado -> {
                    pendientes.remove(mensaje)
                    clavesConfirmadas.add(mensaje.claveDeduplicacion)
                    confirmados++
                    registrar("envio_confirmado", mensaje, mensaje.payload, "intento" to intento)
                }

                is ResultadoEnvio.FalloRecuperable -> {
                    if (politica.agotada(intento)) {
                        moverAFallidos(mensaje, intento, resultado.motivo)
                        aFallidos++
                    } else {
                        val espera = politica.esperaTrasFallo(intento)
                        reemplazar(mensaje, mensaje.copy(
                            intentos = intento,
                            proximoIntentoEnMs = ahora + espera,
                            ultimoError = resultado.motivo
                        ))
                        reprogramados++
                        registrar(
                            "envio_reprogramado", mensaje, mensaje.payload,
                            "intento" to intento, "motivo" to resultado.motivo, "espera_ms" to espera
                        )
                    }
                }

                is ResultadoEnvio.FalloPermanente -> {
                    moverAFallidos(mensaje, intento, resultado.motivo)
                    aFallidos++
                }

                is ResultadoEnvio.SinConexion -> {
                    // Sin red no es culpa del mensaje: no consume intentos. Se corta la pasada,
                    // porque los siguientes mensajes tampoco saldrían.
                    for (pendiente in pendientes.filter { it.proximoIntentoEnMs <= ahora }) {
                        reemplazar(pendiente, pendiente.copy(proximoIntentoEnMs = ahora + politica.esperaBaseMs))
                    }
                    registrar("envio_sin_conexion", mensaje, mensaje.payload, "motivo" to resultado.motivo)
                    return ResumenProcesamiento(confirmados, reprogramados, aFallidos, sinConexion = true)
                }
            }
        }
        return ResumenProcesamiento(confirmados, reprogramados, aFallidos)
    }

    @Synchronized
    override fun reintentarFallido(idCorrelacion: String): Boolean {
        val mensaje = fallidos.firstOrNull { it.idCorrelacion == idCorrelacion } ?: return false
        fallidos.remove(mensaje)
        pendientes.add(mensaje.copy(intentos = 0, proximoIntentoEnMs = reloj(), ultimoError = null))
        registrar("fallido_reencolado", mensaje, mensaje.payload)
        return true
    }

    private fun moverAFallidos(mensaje: MensajeSincronizacion, intento: Int, motivo: String) {
        pendientes.remove(mensaje)
        fallidos.add(mensaje.copy(intentos = intento, ultimoError = motivo))
        registrar("mensaje_a_fallidos", mensaje, mensaje.payload, "intento" to intento, "motivo" to motivo)
    }

    private fun reemplazar(viejo: MensajeSincronizacion, nuevo: MensajeSincronizacion) {
        val posicion = pendientes.indexOf(viejo)
        if (posicion >= 0) pendientes[posicion] = nuevo
    }

    private fun registrar(
        evento: String,
        mensaje: MensajeSincronizacion?,
        payload: PayloadSincronizacionDTO,
        vararg extra: Pair<String, Any?>
    ) {
        val campos = linkedMapOf<String, Any?>(
            "id_correlacion" to mensaje?.idCorrelacion,
            "id_transaccion" to payload.idTransaccion
        )
        campos.putAll(extra)
        registro.registrar(evento, campos)
    }
}
