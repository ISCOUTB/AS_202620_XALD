package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taller de mensajes y consistencia (semana 12).
 *
 * Operación: sincronización de transacciones del dispositivo al Backend XALD (:syncqueue).
 * Cada prueba usa un reloj falso y un transporte simulado, así que no duerme ni usa red.
 *
 * Las dos pruebas que pide la consigna:
 *  - DUPLICADO:          duplicado_mismaTransaccionEncoladaDosVeces_seEnviaUnaSolaVez
 *  - FALLO RECUPERABLE:  falloRecuperable_reintentaConEsperaCreciente_yTerminaConfirmado
 */
class ColaSincronizacionMensajesTest {

    // ----------------------------------------------------------------- utilidades de prueba

    private var ahora = 1_000_000L
    private val eventos = mutableListOf<Pair<String, Map<String, Any?>>>()
    private val registro = RegistroSincronizacion { evento, campos -> eventos += evento to campos }
    private var contadorIds = 0

    /** Transporte que responde con un guion y anota cada intento. */
    private class TransporteGuionado(vararg guion: ResultadoEnvio) : TransporteSincronizacion {
        private val respuestas = ArrayDeque(guion.toList())
        val recibidos = mutableListOf<MensajeSincronizacion>()
        override fun enviar(mensaje: MensajeSincronizacion): ResultadoEnvio {
            recibidos += mensaje
            return respuestas.removeFirstOrNull() ?: ResultadoEnvio.Confirmado
        }
    }

    private val politicaCorta = PoliticaReintentos(
        maxIntentos = 3, esperaBaseMs = 1_000, factor = 2.0, esperaMaximaMs = 60_000
    )

    private fun cola(transporte: TransporteSincronizacion, politica: PoliticaReintentos = politicaCorta) =
        crearColaSincronizacionService(
            transporte = transporte,
            politica = politica,
            reloj = { ahora },
            registro = registro,
            generarIdCorrelacion = { "corr-${++contadorIds}" }
        )

    private fun payload(id: String = "tx-1", fecha: Long = 1_760_000_000_000L) =
        PayloadSincronizacionDTO(idTransaccion = id, monto = 45_900.0, comercio = "Tienda Ara", fechaTimestamp = fecha)

    // ----------------------------------------------------------------- duplicados

    @Test
    fun duplicado_mismaTransaccionEncoladaDosVeces_seEnviaUnaSolaVez() {
        val transporte = TransporteGuionado()
        val cola = cola(transporte)

        // El mismo SMS procesado dos veces produce el mismo payload (mismo id y misma fecha).
        assertTrue(cola.encolarTransaccion(payload()))
        assertTrue(cola.encolarTransaccion(payload()))

        assertEquals(1, cola.obtenerCantidadPendientes())
        cola.procesarPendientes()
        assertEquals("el backend debe recibirla una sola vez", 1, transporte.recibidos.size)
        assertEquals(0, cola.obtenerCantidadPendientes())
        assertEquals(1, eventos.count { it.first == "encolado_duplicado_ignorado" })
    }

    @Test
    fun duplicado_queLlegaDespuesDeConfirmar_noSeReenvia() {
        val transporte = TransporteGuionado()
        val cola = cola(transporte)

        cola.encolarTransaccion(payload())
        cola.procesarPendientes()
        cola.encolarTransaccion(payload())
        cola.procesarPendientes()

        assertEquals(1, transporte.recibidos.size)
        assertEquals(0, cola.obtenerCantidadPendientes())
    }

    @Test
    fun mismaTransaccionConOtraFecha_noEsDuplicado_esUnaVersionNueva() {
        val transporte = TransporteGuionado()
        val cola = cola(transporte)

        cola.encolarTransaccion(payload(fecha = 1_000))
        cola.encolarTransaccion(payload(fecha = 2_000)) // edición: la resuelve LWW en el backend

        assertEquals(2, cola.obtenerCantidadPendientes())
        cola.procesarPendientes()
        assertEquals(listOf(1_000L, 2_000L), transporte.recibidos.map { it.payload.fechaTimestamp })
    }

    // ----------------------------------------------------------------- reintentos y backoff

    @Test
    fun falloRecuperable_reintentaConEsperaCreciente_yTerminaConfirmado() {
        val transporte = TransporteGuionado(
            ResultadoEnvio.FalloRecuperable("HTTP 503"), // intento 1
            ResultadoEnvio.FalloRecuperable("timeout"),  // intento 2
            ResultadoEnvio.Confirmado                     // intento 3
        )
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())

        // Intento 1 falla: espera 1 s.
        assertEquals(1, cola.procesarPendientes().reprogramados)
        assertEquals(1, cola.obtenerCantidadPendientes())

        // Antes de que venza la espera no se reenvía.
        ahora += 999
        cola.procesarPendientes()
        assertEquals(1, transporte.recibidos.size)

        // Intento 2 falla: la espera crece a 2 s.
        ahora += 1
        assertEquals(1, cola.procesarPendientes().reprogramados)
        ahora += 1_999
        cola.procesarPendientes()
        assertEquals(2, transporte.recibidos.size)

        // Intento 3 se confirma: el mensaje sale de la cola, sin pasar por fallidos.
        ahora += 1
        assertEquals(1, cola.procesarPendientes().confirmados)
        assertEquals(3, transporte.recibidos.size)
        assertEquals(0, cola.obtenerCantidadPendientes())
        assertTrue(cola.obtenerFallidos().isEmpty())

        val esperas = eventos.filter { it.first == "envio_reprogramado" }.map { it.second["espera_ms"] }
        assertEquals(listOf<Any?>(1_000L, 2_000L), esperas)
    }

    @Test
    fun falloRecuperable_queAgotaLosIntentos_vaALaColaDeFallidos() {
        val transporte = TransporteGuionado(
            ResultadoEnvio.FalloRecuperable("HTTP 503"),
            ResultadoEnvio.FalloRecuperable("HTTP 503"),
            ResultadoEnvio.FalloRecuperable("HTTP 503")
        )
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())

        repeat(3) {
            cola.procesarPendientes()
            ahora += 60_000
        }

        assertEquals(3, transporte.recibidos.size)
        assertEquals(0, cola.obtenerCantidadPendientes())
        val fallido = cola.obtenerFallidos().single()
        assertEquals(3, fallido.intentos)
        assertEquals("HTTP 503", fallido.ultimoError)

        // Ya no se reintenta solo.
        cola.procesarPendientes()
        assertEquals(3, transporte.recibidos.size)
    }

    @Test
    fun falloPermanente_vaDirectoAFallidos_sinReintentar() {
        val transporte = TransporteGuionado(ResultadoEnvio.FalloPermanente("HTTP 422"))
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())

        assertEquals(1, cola.procesarPendientes().aFallidos)
        assertEquals(1, transporte.recibidos.size)
        assertEquals("HTTP 422", cola.obtenerFallidos().single().ultimoError)
    }

    @Test
    fun sinConexion_noConsumeIntentos() {
        val transporte = TransporteGuionado(
            ResultadoEnvio.SinConexion("ConnectException"),
            ResultadoEnvio.SinConexion("ConnectException"),
            ResultadoEnvio.SinConexion("ConnectException"),
            ResultadoEnvio.SinConexion("ConnectException")
        )
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())

        // Más pasadas sin red que intentos permitidos (3): el mensaje no debe ir a fallidos.
        repeat(4) {
            assertTrue(cola.procesarPendientes().sinConexion)
            ahora += politicaCorta.esperaBaseMs
        }
        assertTrue(cola.obtenerFallidos().isEmpty())

        // Vuelve la red: se confirma.
        assertEquals(1, cola.procesarPendientes().confirmados)
    }

    @Test
    fun reintentarFallido_loDevuelveALaColaYSeConfirma() {
        val transporte = TransporteGuionado(ResultadoEnvio.FalloPermanente("HTTP 401"))
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())
        cola.procesarPendientes()
        val id = cola.obtenerFallidos().single().idCorrelacion

        // Mientras está en fallidos, encolarla de nuevo sigue siendo un duplicado.
        cola.encolarTransaccion(payload())
        assertEquals(0, cola.obtenerCantidadPendientes())

        assertTrue(cola.reintentarFallido(id))
        assertFalse(cola.reintentarFallido("no-existe"))
        assertEquals(1, cola.procesarPendientes().confirmados)
        assertTrue(cola.obtenerFallidos().isEmpty())
    }

    // ----------------------------------------------------------------- trazabilidad

    @Test
    fun trazabilidad_elIdDeCorrelacionNoCambiaEntreReintentos_yApareceEnCadaEvento() {
        val transporte = TransporteGuionado(ResultadoEnvio.FalloRecuperable("HTTP 503"))
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())

        cola.procesarPendientes()
        ahora += 1_000
        cola.procesarPendientes()

        // El backend recibe el mismo X-Correlation-Id en los dos intentos.
        assertEquals(listOf("corr-1", "corr-1"), transporte.recibidos.map { it.idCorrelacion })

        val ciclo = eventos.map { it.first }
        assertEquals(
            listOf("mensaje_encolado", "envio_intento", "envio_reprogramado", "envio_intento", "envio_confirmado"),
            ciclo
        )
        assertTrue(eventos.all { it.second["id_correlacion"] == "corr-1" && it.second["id_transaccion"] == "tx-1" })
    }

    @Test
    fun transporteNoConfigurado_dejaTodoPendiente() {
        val cola = crearColaSincronizacionService(registro = registro)
        cola.encolarTransaccion(payload())
        assertTrue(cola.procesarPendientes().sinConexion)
        assertEquals(1, cola.obtenerCantidadPendientes())
    }
}
