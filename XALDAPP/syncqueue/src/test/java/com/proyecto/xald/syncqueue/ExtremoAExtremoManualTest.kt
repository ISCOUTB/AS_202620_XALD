package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/**
 * Prueba manual extremo a extremo contra un backend real (no corre en CI).
 *
 *   cd backend && XALD_API_KEY=demo uvicorn app.main:app --port 8000 --no-access-log > backend.log
 *   XALD_URL_E2E=http://127.0.0.1:8000 XALD_API_KEY=demo ./gradlew :syncqueue:testDebugUnitTest
 *
 * Inyecta un fallo recuperable realista: el primer envío SÍ llega al backend, pero el cliente
 * "pierde" la respuesta (como un timeout). El reintento llega con el mismo X-Correlation-Id y
 * el backend lo registra como duplicado. Evidencia en docs/mensajes/evidencia.md.
 */
class ExtremoAExtremoManualTest {

    private class PierdeLaPrimeraRespuesta(private val real: TransporteSincronizacion) : TransporteSincronizacion {
        private var perdida = false
        override fun enviar(mensaje: MensajeSincronizacion): ResultadoEnvio {
            val resultado = real.enviar(mensaje)
            if (!perdida && resultado == ResultadoEnvio.Confirmado) {
                perdida = true
                return ResultadoEnvio.FalloRecuperable("timeout (inyectado: respuesta perdida)")
            }
            return resultado
        }
    }

    @Test
    fun envioConRespuestaPerdida_reintenta_yElBackendDeduplica() {
        val url = System.getenv("XALD_URL_E2E")
        assumeTrue("Define XALD_URL_E2E para correr esta prueba", !url.isNullOrBlank())

        val cola = crearColaSincronizacionService(
            transporte = PierdeLaPrimeraRespuesta(TransporteHttpSincronizacion(url!!, System.getenv("XALD_API_KEY") ?: "")),
            politica = PoliticaReintentos(maxIntentos = 4, esperaBaseMs = 200, esperaMaximaMs = 2_000)
        )
        val payload = PayloadSincronizacionDTO(
            idTransaccion = UUID.randomUUID().toString(),
            monto = 45_900.0,
            comercio = "Tienda Ara",
            fechaTimestamp = System.currentTimeMillis(),
            categoria = "Supermercado"
        )

        cola.encolarTransaccion(payload)
        cola.encolarTransaccion(payload) // el mismo SMS procesado dos veces

        var confirmados = 0
        val limite = System.currentTimeMillis() + 10_000
        while (cola.obtenerCantidadPendientes() > 0 && System.currentTimeMillis() < limite) {
            confirmados += cola.procesarPendientes().confirmados
            Thread.sleep(50)
        }
        assertEquals(1, confirmados)
        assertTrue(cola.obtenerFallidos().isEmpty())
    }
}
