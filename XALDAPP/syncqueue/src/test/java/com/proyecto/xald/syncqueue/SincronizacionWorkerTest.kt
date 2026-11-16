package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SincronizacionWorkerTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var context: Context
    private lateinit var db: SyncDatabase
    private var estadoTransporte: ResultadoEnvio = ResultadoEnvio.Confirmado
    private var contadorIds = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(executor)
            .setQueryExecutor(executor)
            .build()
        ColaSincronizacionProvider.reiniciarParaPruebas()
    }

    @After
    fun tearDown() {
        ColaSincronizacionProvider.reiniciarParaPruebas()
        db.close()
    }

    private fun instalarCola(): ColaSincronizacionService {
        val cola = ColaSincronizacion(
            dao = db.mensajeDao(),
            transporte = TransporteSincronizacion { estadoTransporte },
            politica = PoliticaReintentos(maxIntentos = 3, esperaBaseMs = 1_000),
            // Reloj fijo: los mensajes recién encolados quedan listos de inmediato.
            reloj = { 1_000_000L },
            registro = { _, _ -> },
            generarIdCorrelacion = { "corr-${++contadorIds}" }
        )
        ColaSincronizacionProvider.instalar(cola)
        return cola
    }

    private fun worker() = TestListenableWorkerBuilder<SincronizacionWorker>(context).build()

    private fun payload(id: String = "tx-1") = PayloadSincronizacionDTO(
        idTransaccion = id,
        monto = 45_900.0,
        comercio = "Tienda Ara",
        fechaTimestamp = 1_760_000_000_000L
    )

    @Test
    fun sinColaInstalada_reintentaMasTarde() {
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }

    @Test
    fun todoConfirmado_terminaConExito_yVaciaLaCola() {
        val cola = instalarCola()
        cola.encolarTransaccion(payload())

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(0, cola.obtenerCantidadPendientes())
    }

    @Test
    fun sinConexion_reintenta_yConservaElMensaje() {
        estadoTransporte = ResultadoEnvio.SinConexion("ConnectException")
        val cola = instalarCola()
        cola.encolarTransaccion(payload())

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(1, cola.obtenerCantidadPendientes())
    }

    @Test
    fun falloRecuperable_reintenta_yConservaElMensaje() {
        estadoTransporte = ResultadoEnvio.FalloRecuperable("HTTP 503")
        val cola = instalarCola()
        cola.encolarTransaccion(payload())

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(1, cola.obtenerCantidadPendientes())
    }

    @Test
    fun falloPermanente_noReintenta_yQuedaEnFallidos() {
        estadoTransporte = ResultadoEnvio.FalloPermanente("HTTP 422")
        val cola = instalarCola()
        cola.encolarTransaccion(payload())

        assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertEquals(0, cola.obtenerCantidadPendientes())
        assertEquals(1, cola.obtenerFallidos().size)
    }
}
