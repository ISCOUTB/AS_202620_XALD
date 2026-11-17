package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Rule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColaSincronizacionTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    private lateinit var db: SyncDatabase
    private lateinit var dao: MensajeDao
    private lateinit var servicio: ColaSincronizacionService
    private var estadoTransporte: ResultadoEnvio = ResultadoEnvio.Confirmado
    private var relojVirtual = 1000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executor { it.run() }

        // Anulamos los hilos en segundo plano
        db = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(executor)
            .setQueryExecutor(executor)
            .build()

        dao = db.mensajeDao()
        val transporteFake = TransporteSincronizacion { _ -> estadoTransporte }

        servicio = ColaSincronizacion(
            dao = dao,
            transporte = transporteFake,
            politica = PoliticaReintentos(maxIntentos = 3, esperaBaseMs = 1000),
            reloj = { relojVirtual },
            registro = { evento, campos -> println("LOG TEST -> $evento: $campos") },
            generarIdCorrelacion = { "test-uuid-123" }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `encolar transaccion debe guardar duraderamente en Room`() {
        val payload = crearPayloadEjemplo("tx-001")
        val encolado = servicio.encolarTransaccion(payload)

        assertTrue(encolado)
        assertEquals(1, servicio.obtenerCantidadPendientes())

        val siguiente = servicio.obtenerSiguienteParaSincronizar()
        assertNotNull(siguiente)
        assertEquals("tx-001", siguiente?.idTransaccion)
    }

    @Test
    fun `al procesar con respuesta HTTP 200 la cola debe eliminar el mensaje`() {
        val payload = crearPayloadEjemplo("tx-002")
        servicio.encolarTransaccion(payload)
        estadoTransporte = ResultadoEnvio.Confirmado

        val resumen = servicio.procesarPendientes()
        assertEquals(1, resumen.confirmados)
        assertEquals(0, servicio.obtenerCantidadPendientes())
    }

    @Test
    fun `al agotar reintentos por fallo debe mover el mensaje a la cola de fallidos DLQ`() {
        val payload = crearPayloadEjemplo("tx-003")
        servicio.encolarTransaccion(payload)
        estadoTransporte = ResultadoEnvio.FalloRecuperable("HTTP 500")

        servicio.procesarPendientes()
        relojVirtual += 2000
        servicio.procesarPendientes()
        relojVirtual += 4000
        val resumenFinal = servicio.procesarPendientes()

        assertEquals(1, resumenFinal.aFallidos)
        assertEquals(0, servicio.obtenerCantidadPendientes())
        assertEquals(1, servicio.obtenerFallidos().size)
        assertEquals("test-uuid-123", servicio.obtenerFallidos().first().idCorrelacion)
    }

    @Test
    fun `la deduplicacion sobrevive a un reinicio de la cola`() {
        servicio.encolarTransaccion(crearPayloadEjemplo("tx-010"))
        assertEquals(1, servicio.procesarPendientes().confirmados)

        // Simula un reinicio: otra instancia de la cola sobre la misma base de datos
        // (sin ninguna memoria de lo ya procesado).
        val reiniciada = ColaSincronizacion(
            dao = dao,
            transporte = TransporteSincronizacion { estadoTransporte },
            politica = PoliticaReintentos(maxIntentos = 3, esperaBaseMs = 1000),
            reloj = { relojVirtual },
            registro = { _, _ -> },
            generarIdCorrelacion = { "otro-uuid" }
        )

        assertTrue(reiniciada.encolarTransaccion(crearPayloadEjemplo("tx-010")))
        assertEquals(0, reiniciada.obtenerCantidadPendientes())
    }

    private fun crearPayloadEjemplo(id: String) = PayloadSincronizacionDTO(
        idTransaccion = id,
        monto = 25000.0,
        moneda = "COP",
        comercio = "Éxito Cartagena",
        categoria = "Supermercado",
        fechaTimestamp = 1700000000000L,
        origenDatos = "SMS_REGEX"
    )
}