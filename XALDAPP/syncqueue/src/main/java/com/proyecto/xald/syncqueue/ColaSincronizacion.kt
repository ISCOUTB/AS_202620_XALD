package com.proyecto.xald.syncqueue

import com.google.gson.Gson
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO

/** Cuánto tiempo se recuerdan los mensajes ya confirmados para seguir detectando duplicados (30 días). */
private const val RETENCION_CONFIRMADOS_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Gestor interno de la cola de sincronización offline.
 *
 * Arquitectura (esquema.txt Fase 6):
 *  - Persistencia: SQLite mediante Room ([MensajeDao]) para sobrevivir reinicios.
 *  - Idempotencia: clave "id_transaccion@fecha" ([claveDeduplicacion]) con índice UNIQUE en Room.
 *    Los mensajes confirmados se marcan y se purgan tras [RETENCION_CONFIRMADOS_MS], de modo que
 *    la deduplicación sobrevive a reinicios de la app.
 *  - Entregas y reintentos: [PoliticaReintentos] y [TransporteHttpSincronizacion].
 */
internal class ColaSincronizacion(
    private val dao: MensajeDao,
    private val transporte: TransporteSincronizacion,
    private val politica: PoliticaReintentos,
    private val reloj: () -> Long,
    private val registro: RegistroSincronizacion,
    private val generarIdCorrelacion: () -> String,
    /**
     * Se invoca cuando el backend confirma un mensaje. `:app` lo usa para marcar la transacción
     * como sincronizada en `:corefinanciero` (la cola no conoce ese módulo). Si lanza una
     * excepción, se registra y la cola sigue: el mensaje ya quedó confirmado.
     */
    private val alConfirmar: (PayloadSincronizacionDTO) -> Unit = {}
) : ColaSincronizacionService {

    private val gson = Gson()

    @Synchronized
    override fun encolarTransaccion(payload: PayloadSincronizacionDTO): Boolean {
        val clave = claveDeduplicacion(payload)

        // 1. Duplicado: ya existe (pendiente, fallido o confirmado aún retenido) con esa clave.
        dao.idPorClave(clave)?.let { existente ->
            registrar("encolado_duplicado_ignorado", existente, payload)
            return true
        }

        val idCorrelacion = generarIdCorrelacion()
        val entity = MensajeEntity(
            idCorrelacion = idCorrelacion,
            claveDeduplicacion = clave,
            payloadJson = gson.toJson(payload),
            proximoIntentoMs = reloj()
        )

        // 2. Defensa extra en SQLite: el índice UNIQUE rechaza la carrera (insert devuelve -1).
        if (dao.insertar(entity) == -1L) {
            registrar("encolado_duplicado_ignorado", idCorrelacion, payload)
            return true
        }

        registrar("mensaje_encolado", idCorrelacion, payload)
        return true
    }

    @Synchronized
    override fun obtenerCantidadPendientes(): Int = dao.obtenerCantidadPendientes()

    @Synchronized
    override fun obtenerSiguienteParaSincronizar(): PayloadSincronizacionDTO? {
        val listos = dao.obtenerListosParaEnviar(reloj())
        val primero = listos.firstOrNull() ?: return null
        return gson.fromJson(primero.payloadJson, PayloadSincronizacionDTO::class.java)
    }

    @Synchronized
    override fun obtenerFallidos(): List<MensajeSincronizacion> {
        return dao.obtenerFallidos().map { entidad ->
            MensajeSincronizacion(
                idCorrelacion = entidad.idCorrelacion,
                payload = gson.fromJson(entidad.payloadJson, PayloadSincronizacionDTO::class.java),
                encoladoEnMs = entidad.proximoIntentoMs,
                intentos = entidad.intentos,
                proximoIntentoEnMs = entidad.proximoIntentoMs,
                ultimoError = entidad.ultimoError
            )
        }
    }

    @Synchronized
    override fun procesarPendientes(): ResumenProcesamiento {
        val ahora = reloj()
        var confirmados = 0
        var reprogramados = 0
        var aFallidos = 0

        // Limpieza de confirmados antiguos (ya no hace falta recordarlos para deduplicar)
        dao.purgarConfirmados(ahora - RETENCION_CONFIRMADOS_MS)

        // Extraer los mensajes cuyo tiempo de espera ya se cumplió desde SQLite
        val turno = dao.obtenerListosParaEnviar(ahora)

        for (entidad in turno) {
            val payloadObj = gson.fromJson(entidad.payloadJson, PayloadSincronizacionDTO::class.java)
            val intentoActual = entidad.intentos + 1

            // Creamos el objeto en memoria para pasarlo al transporte
            val mensajeEnvio = MensajeSincronizacion(
                idCorrelacion = entidad.idCorrelacion,
                payload = payloadObj,
                encoladoEnMs = 0L,
                intentos = entidad.intentos
            )

            registrar("envio_intento", entidad.idCorrelacion, payloadObj, "intento" to intentoActual)

            when (val resultado = transporte.enviar(mensajeEnvio)) {
                is ResultadoEnvio.Confirmado -> {
                    dao.confirmarEnvio(entidad.idCorrelacion, ahora)
                    confirmados++
                    try {
                        alConfirmar(payloadObj)
                    } catch (e: Exception) {
                        registrar("gancho_confirmacion_fallo", entidad.idCorrelacion, payloadObj, "error" to e.javaClass.simpleName)
                    }
                    registrar("envio_confirmado", entidad.idCorrelacion, payloadObj, "intento" to intentoActual)
                }

                is ResultadoEnvio.FalloRecuperable -> {
                    if (politica.agotada(intentoActual)) {
                        dao.moverAFallidos(entidad.idCorrelacion, intentoActual, resultado.motivo)
                        aFallidos++
                        registrar("mensaje_a_fallidos", entidad.idCorrelacion, payloadObj, "intento" to intentoActual, "motivo" to resultado.motivo)
                    } else {
                        val espera = politica.esperaTrasFallo(intentoActual)
                        val proximoMs = ahora + espera
                        dao.actualizarIntento(entidad.idCorrelacion, intentoActual, proximoMs, resultado.motivo)
                        reprogramados++
                        registrar("envio_reprogramado", entidad.idCorrelacion, payloadObj, "intento" to intentoActual, "motivo" to resultado.motivo, "espera_ms" to espera)
                    }
                }

                is ResultadoEnvio.FalloPermanente -> {
                    dao.moverAFallidos(entidad.idCorrelacion, intentoActual, resultado.motivo)
                    aFallidos++
                    registrar("mensaje_a_fallidos", entidad.idCorrelacion, payloadObj, "intento" to intentoActual, "motivo" to resultado.motivo)
                }

                is ResultadoEnvio.SinConexion -> {
                    // Ante fallo de red global, pateamos toda la cola hacia el futuro sin consumir intentos.
                    for (pendiente in dao.obtenerListosParaEnviar(ahora)) {
                        dao.actualizarIntento(pendiente.idCorrelacion, pendiente.intentos, ahora + politica.esperaBaseMs, resultado.motivo)
                    }
                    registrar("envio_sin_conexion", entidad.idCorrelacion, payloadObj, "motivo" to resultado.motivo)
                    return ResumenProcesamiento(confirmados, reprogramados, aFallidos, sinConexion = true)
                }
            }
        }
        return ResumenProcesamiento(confirmados, reprogramados, aFallidos)
    }

    @Synchronized
    override fun reintentarFallido(idCorrelacion: String): Boolean {
        val filasActualizadas = dao.reintentarFallido(idCorrelacion, reloj())
        return filasActualizadas > 0
    }

    private fun registrar(
        evento: String,
        idCorrelacion: String,
        payload: PayloadSincronizacionDTO,
        vararg extra: Pair<String, Any?>
    ) {
        val campos = linkedMapOf<String, Any?>(
            "id_correlacion" to idCorrelacion,
            "id_transaccion" to payload.idTransaccion
        )
        campos.putAll(extra)
        registro.registrar(evento, campos)
    }
}