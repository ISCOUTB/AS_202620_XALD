package com.proyecto.xald

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.proyecto.xald.aigemini.crearServicioCategorizacionConRespaldo
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.corefinanciero.crearInformacionFinanciera
import com.proyecto.xald.parser.crearServicioParser
import com.proyecto.xald.syncqueue.ColaSincronizacionService
import com.proyecto.xald.syncqueue.crearColaSincronizacionService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// Aísla el test usando una Application base para que no reviente Room ni WorkManager
@Config(application = android.app.Application::class, sdk = [34])
class ProcesarNotificacionUseCaseTest {

    private lateinit var useCase: ProcesarNotificacionUseCase
    private lateinit var coreFinanciero: InformacionFinanciera
    private lateinit var syncQueue: ColaSincronizacionService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val parser = crearServicioParser()
        // Sin API key: categoriza solo con las reglas locales (sin red).
        val categorizador = crearServicioCategorizacionConRespaldo(apiKey = "")
        coreFinanciero = crearInformacionFinanciera(context, usarBaseDatosEnMemoria = true)
        syncQueue = crearColaSincronizacionService(context, usarBaseDatosEnMemoria = true)

        useCase = ProcesarNotificacionUseCase(
            parser = parser,
            categorizador = categorizador,
            coreFinanciero = coreFinanciero,
            syncQueue = syncQueue
        )
    }

    @Test
    fun flujoCompleto_smsValido_persisteEnCoreYEncolaEnSyncQueue() = runTest {
        val smsText = "Bancolombia le informa compra por $85,000.00 en Exito Cartagena 10/11/2026."
        val resultado = useCase.ejecutar("890900", smsText)

        assertTrue(resultado)

        val ultimaTransaccion = coreFinanciero.obtenerUltimaTransaccion()
        assertEquals("Exito Cartagena", ultimaTransaccion?.comercio)
        assertEquals(85000.0, ultimaTransaccion?.monto ?: 0.0, 0.001)
        assertEquals("Supermercado", ultimaTransaccion?.categoria)

        assertEquals(1, syncQueue.obtenerCantidadPendientes())
        val siguientePayload = syncQueue.obtenerSiguienteParaSincronizar()
        assertEquals("Exito Cartagena", siguientePayload?.comercio)
        assertEquals("Supermercado", siguientePayload?.categoria)
    }

    @Test
    fun elIdDelCoreYElDeLaCola_sonElMismo() = runTest {
        useCase.ejecutar("890900", "Bancolombia le informa compra por $85,000.00 en Exito Cartagena 10/11/2026.")

        val idCore = coreFinanciero.obtenerUltimaTransaccion()?.id
        val idCola = syncQueue.obtenerSiguienteParaSincronizar()?.idTransaccion
        assertEquals(idCore, idCola)
    }

    @Test
    fun elMismoSmsEntregadoDosVeces_seProcesaUnaSolaVez() = runTest {
        val sms = "Bancolombia le informa compra por $85,000.00 en Exito Cartagena 10/11/2026."
        val timestamp = 1_760_000_000_000L

        assertTrue(useCase.ejecutar("890900", sms, timestamp))
        assertFalse(useCase.ejecutar("890900", sms, timestamp))

        assertEquals(1, coreFinanciero.obtenerHistorial().size)
        assertEquals(1, syncQueue.obtenerCantidadPendientes())
    }

    @Test
    fun dosComprasIgualesEnMomentosDistintos_sonDosTransacciones() = runTest {
        val sms = "Bancolombia le informa compra por $85,000.00 en Exito Cartagena 10/11/2026."

        assertTrue(useCase.ejecutar("890900", sms, 1_760_000_000_000L))
        assertTrue(useCase.ejecutar("890900", sms, 1_760_000_900_000L))

        assertEquals(2, coreFinanciero.obtenerHistorial().size)
        assertEquals(2, syncQueue.obtenerCantidadPendientes())
    }

    @Test
    fun comercioSinReglaLocal_quedaComoSinCategorizar() = runTest {
        val sms = "Bancolombia le informa compra por $20,000.00 en Comercio Raro XYZ 10/11/2026."
        assertTrue(useCase.ejecutar("890900", sms))

        assertEquals("Sin Categorizar", coreFinanciero.obtenerUltimaTransaccion()?.categoria)
    }

    @Test
    fun smsInvalido_noParseable_retornaFalseYNoModificaBasesDeDatos() = runTest {
        val smsInvalido = "Hola mama, ya voy para la casa."
        val resultado = useCase.ejecutar("3001234567", smsInvalido)

        assertFalse(resultado)
        assertEquals(0, syncQueue.obtenerCantidadPendientes())
        assertEquals(0, coreFinanciero.obtenerHistorial().size)
    }

    @Test
    fun remitenteNoAutorizado_conTextoBancario_seIgnora() = runTest {
        val sms = "Bancolombia le informa compra por $85,000.00 en Exito Cartagena 10/11/2026."
        val resultado = useCase.ejecutar("3001234567", sms)

        assertFalse(resultado)
        assertEquals(0, coreFinanciero.obtenerHistorial().size)
    }
}