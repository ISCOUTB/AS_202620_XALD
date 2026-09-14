package com.proyecto.xald

import com.proyecto.xald.parser.ServicioParser
import com.proyecto.xald.parser.crearServicioParser
import com.proyecto.xald.aigemini.ServicioCategorizacion
import com.proyecto.xald.aigemini.crearServicioCategorizacion
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.corefinanciero.crearInformacionFinanciera
import com.proyecto.xald.syncqueue.ColaSincronizacionService
import com.proyecto.xald.syncqueue.crearColaSincronizacionService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorteVerticalTest {

    @Test
    fun testCorteVerticalCompleto5Modulos() = runBlocking {
        val mensajeCrudo = "BANCO: Compra aprobada por $120000 en SUPERMERCADO"

        // 1. Módulo :parser
        val parserService: ServicioParser = crearServicioParser()
        val transaccionProcesada = parserService.parsear(mensajeCrudo)

        assertEquals(120000.0, transaccionProcesada.monto, 0.01)
        assertEquals("SUPERMERCADO", transaccionProcesada.comercio)

        // 2. Módulo :aigemini
        val categorizadorService: ServicioCategorizacion = crearServicioCategorizacion()
        val resultadoIa = categorizadorService.categorizar(transaccionProcesada.comercio)

        assertEquals("SUPERMERCADO", resultadoIa.nombreCategoria)

        // 3. Módulo :corefinanciero
        val coreFinancieroService: InformacionFinanciera = crearInformacionFinanciera()
        val guardadoExitoso = coreFinancieroService.guardarTransaccion(
            monto = transaccionProcesada.monto,
            comercio = transaccionProcesada.comercio
        )
        assertTrue(guardadoExitoso)

        // 4. Módulo :syncqueue
        val pendientes = coreFinancieroService.obtenerPendientesSincronizacion()
        assertEquals(1, pendientes.size)

        val syncQueueService: ColaSincronizacionService = crearColaSincronizacionService()
        val encoladoExitoso = syncQueueService.encolarTransaccion(pendientes.first())

        assertTrue(encoladoExitoso)
        assertEquals(1, syncQueueService.obtenerCantidadPendientes())
    }
}