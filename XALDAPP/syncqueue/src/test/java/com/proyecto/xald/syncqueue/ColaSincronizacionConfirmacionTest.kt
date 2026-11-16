package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

/**
 * El gancho `alConfirmar` es lo que permite a `:app` marcar la transacción como sincronizada en
 * `:corefinanciero` cuando el backend confirma el mensaje.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ColaSincronizacionConfirmacionTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var db: SyncDatabase
    private var contadorIds = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(context, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(executor)
            .setQueryExecutor(executor)
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun cola(
        resultado: ResultadoEnvio,
        alConfirmar: (PayloadSincronizacionDTO) -> Unit
    ) = ColaSincronizacion(
        dao = db.mensajeDao(),
        transporte = TransporteSincronizacion { resultado },
        politica = PoliticaReintentos(maxIntentos = 3, esperaBaseMs = 1_000),
        reloj = { 1_000_000L },
        registro = { _, _ -> },
        generarIdCorrelacion = { "corr-${++contadorIds}" },
        alConfirmar = alConfirmar
    )

    private fun payload(id: String) = PayloadSincronizacionDTO(
        idTransaccion = id,
        monto = 45_900.0,
        comercio = "Tienda Ara",
        fechaTimestamp = 1_760_000_000_000L
    )

    @Test
    fun envioConfirmado_invocaElGanchoConElIdDeLaTransaccion() {
        val confirmadas = mutableListOf<String>()
        val cola = cola(ResultadoEnvio.Confirmado) { confirmadas += it.idTransaccion }
        cola.encolarTransaccion(payload("tx-1"))
        cola.encolarTransaccion(payload("tx-2"))

        val resumen = cola.procesarPendientes()

        assertEquals(2, resumen.confirmados)
        assertEquals(listOf("tx-1", "tx-2"), confirmadas.sorted())
    }

    @Test
    fun envioFallido_noInvocaElGancho() {
        val confirmadas = mutableListOf<String>()
        val cola = cola(ResultadoEnvio.FalloRecuperable("HTTP 503")) { confirmadas += it.idTransaccion }
        cola.encolarTransaccion(payload("tx-1"))

        cola.procesarPendientes()

        assertTrue(confirmadas.isEmpty())
        assertEquals(1, cola.obtenerCantidadPendientes())
    }

    @Test
    fun ganchoQueLanzaExcepcion_noRompeLaCola() {
        val cola = cola(ResultadoEnvio.Confirmado) { throw IllegalStateException("fallo del gancho") }
        cola.encolarTransaccion(payload("tx-1"))

        val resumen = cola.procesarPendientes()

        assertEquals(1, resumen.confirmados)
        assertEquals(0, cola.obtenerCantidadPendientes())
    }
}
