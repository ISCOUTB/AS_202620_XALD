package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Rule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColaSincronizacionMensajesTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    private var ahora = 1_000_000L
    private val eventos = mutableListOf<Pair<String, Map<String, Any?>>>()
    private val registro = RegistroSincronizacion { evento, campos -> eventos += evento to campos }
    private var contadorIds = 0
    private val basesDeDatosActivas = mutableListOf<SyncDatabase>()

    @After
    fun tearDown() {
        basesDeDatosActivas.forEach { it.close() }
        basesDeDatosActivas.clear()
    }

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

    private fun cola(
        transporte: TransporteSincronizacion,
        politica: PoliticaReintentos = politicaCorta
    ): ColaSincronizacionService {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executor { it.run() }

        val db = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(executor)
            .setQueryExecutor(executor)
            .build()

        basesDeDatosActivas.add(db)

        return ColaSincronizacion(
            dao = db.mensajeDao(),
            transporte = transporte,
            politica = politica,
            reloj = { ahora },
            registro = registro,
            generarIdCorrelacion = { "corr-${++contadorIds}" }
        )
    }

    private fun payload(id: String = "tx-1", fecha: Long = 1_760_000_000_000L) =
        PayloadSincronizacionDTO(
            idTransaccion = id,
            monto = 45_900.0,
            comercio = "Tienda Ara",
            fechaTimestamp = fecha
        )

    @Test
    fun duplicado_mismaTransaccionEncoladaDosVeces_seEnviaUnaSolaVez() {
        val transporte = TransporteGuionado()
        val cola = cola(transporte)

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
        cola.encolarTransaccion(payload(fecha = 2_000))

        assertEquals(2, cola.obtenerCantidadPendientes())
        cola.procesarPendientes()
        assertEquals(listOf(1_000L, 2_000L), transporte.recibidos.map { it.payload.fechaTimestamp })
    }

    @Test
    fun falloRecuperable_reintentaConEsperaCreciente_yTerminaConfirmado() {
        val transporte = TransporteGuionado(
            ResultadoEnvio.FalloRecuperable("HTTP 503"),
            ResultadoEnvio.FalloRecuperable("timeout"),
            ResultadoEnvio.Confirmado
        )
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())

        assertEquals(1, cola.procesarPendientes().reprogramados)
        assertEquals(1, cola.obtenerCantidadPendientes())

        ahora += 999
        cola.procesarPendientes()
        assertEquals(1, transporte.recibidos.size)

        ahora += 1
        assertEquals(1, cola.procesarPendientes().reprogramados)
        ahora += 1_999
        cola.procesarPendientes()
        assertEquals(2, transporte.recibidos.size)

        ahora += 1
        assertEquals(1, cola.procesarPendientes().confirmados)
        assertEquals(3, transporte.recibidos.size)
        assertEquals(0, cola.obtenerCantidadPendientes())
        assertTrue(cola.obtenerFallidos().isEmpty())

        val esperas =
            eventos.filter { it.first == "envio_reprogramado" }.map { it.second["espera_ms"] }
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

        repeat(4) {
            assertTrue(cola.procesarPendientes().sinConexion)
            ahora += politicaCorta.esperaBaseMs
        }
        assertTrue(cola.obtenerFallidos().isEmpty())

        assertEquals(1, cola.procesarPendientes().confirmados)
    }

    @Test
    fun reintentarFallido_loDevuelveALaColaYSeConfirma() {
        val transporte = TransporteGuionado(ResultadoEnvio.FalloPermanente("HTTP 401"))
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())
        cola.procesarPendientes()
        val id = cola.obtenerFallidos().single().idCorrelacion

        cola.encolarTransaccion(payload())
        assertEquals(0, cola.obtenerCantidadPendientes())

        assertTrue(cola.reintentarFallido(id))
        assertFalse(cola.reintentarFallido("no-existe"))
        assertEquals(1, cola.procesarPendientes().confirmados)
        assertTrue(cola.obtenerFallidos().isEmpty())
    }

    @Test
    fun trazabilidad_elIdDeCorrelacionNoCambiaEntreReintentos_yApareceEnCadaEvento() {
        val transporte = TransporteGuionado(ResultadoEnvio.FalloRecuperable("HTTP 503"))
        val cola = cola(transporte)
        cola.encolarTransaccion(payload())

        cola.procesarPendientes()
        ahora += 1_000
        cola.procesarPendientes()

        assertEquals(listOf("corr-1", "corr-1"), transporte.recibidos.map { it.idCorrelacion })

        val ciclo = eventos.map { it.first }
        assertEquals(
            listOf(
                "mensaje_encolado",
                "envio_intento",
                "envio_reprogramado",
                "envio_intento",
                "envio_confirmado"
            ),
            ciclo
        )
        assertTrue(eventos.all { it.second["id_correlacion"] == "corr-1" && it.second["id_transaccion"] == "tx-1" })
    }

    @Test
    fun transporteNoConfigurado_dejaTodoPendiente() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executor { it.run() }
        val db = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(executor)
            .setQueryExecutor(executor)
            .build()
        basesDeDatosActivas.add(db)

        // Instanciamos pasando TODOS los parámetros requeridos
        val cola = ColaSincronizacion(
            dao = db.mensajeDao(),
            transporte = TransporteNoConfigurado,
            politica = PoliticaReintentos(),
            reloj = { ahora },
            registro = registro,
            generarIdCorrelacion = { "corr-${++contadorIds}" }
        )

        cola.encolarTransaccion(payload())
        assertTrue(cola.procesarPendientes().sinConexion)
        assertEquals(1, cola.obtenerCantidadPendientes())
    }
}