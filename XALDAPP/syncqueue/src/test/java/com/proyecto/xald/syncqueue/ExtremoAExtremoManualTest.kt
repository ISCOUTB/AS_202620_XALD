package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import org.junit.After
import org.junit.Assert.assertEquals
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
class ExtremoAExtremoManualTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    private lateinit var db: SyncDatabase

    @After
    fun tearDown() {
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun flujoCompleto_encolarProcesarYReintentar() {
        val contexto: Context = ApplicationProvider.getApplicationContext()
        var ahora = 1_000L

        // Magia negra para Robolectric: Fuerza a Room a no crear ningún hilo en segundo plano
        val executor = Executor { it.run() }
        db = Room.inMemoryDatabaseBuilder(contexto, SyncDatabase::class.java)
            .allowMainThreadQueries()
            .setTransactionExecutor(executor)
            .setQueryExecutor(executor)
            .build()

        val transporte = object : TransporteSincronizacion {
            var falloPrimeraVez = true
            override fun enviar(mensaje: MensajeSincronizacion): ResultadoEnvio {
                return if (falloPrimeraVez) {
                    falloPrimeraVez = false
                    ResultadoEnvio.FalloRecuperable("HTTP 500")
                } else {
                    ResultadoEnvio.Confirmado
                }
            }
        }

        // Instanciamos el servicio directamente sin la fábrica
        val servicio = ColaSincronizacion(
            dao = db.mensajeDao(),
            transporte = transporte,
            politica = PoliticaReintentos(maxIntentos = 3, esperaBaseMs = 1_000),
            reloj = { ahora },
            registro = { evento, campos -> println("E2E LOG: $evento $campos") },
            generarIdCorrelacion = { java.util.UUID.randomUUID().toString() }
        )

        val payload = PayloadSincronizacionDTO(
            idTransaccion = "tx-e2e-1",
            monto = 15000.0,
            comercio = "Exito",
            fechaTimestamp = 1760000000000L
        )

        assertTrue(servicio.encolarTransaccion(payload))
        assertEquals(1, servicio.obtenerCantidadPendientes())

        // Intento 1: Falla y reprograma
        val resumen1 = servicio.procesarPendientes()
        assertEquals(1, resumen1.reprogramados)

        // Avanzar tiempo para superar backoff
        ahora += 1_500

        // Intento 2: Confirma y limpia la cola
        val resumen2 = servicio.procesarPendientes()
        assertEquals(1, resumen2.confirmados)
        assertEquals(0, servicio.obtenerCantidadPendientes())
    }
}