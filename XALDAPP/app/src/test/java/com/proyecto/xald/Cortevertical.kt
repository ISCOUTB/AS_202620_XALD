package com.proyecto.xald

import com.proyecto.xald.parser.Parser
import com.proyecto.xald.corefinanciero.Coremanager
import com.proyecto.xald.syncqueue.SyncQueueManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorteVerticalTest {

    @Test
    fun testCorteVerticalCompleto5Modulos() {
        // 1. Mensaje crudo inicial en la capa de aplicación (:app)
        val mensajeCrudo = "BANCO: Compra aprobada por \$120000 en SUPERMERCADO"

        // 2 & 3. El parser invoca internamente a :aigemini (geminiproc) y genera el DTO estructurado
        val parserService = Parser()
        val transaccionDto = parserService.parsear(mensajeCrudo)

        assertEquals("120000", transaccionDto.monto)
        assertEquals("SUPERMERCADO", transaccionDto.comercio)

        // 4. Módulo :corefinanciero (Coremanager) recibe el DTO y lo registra localmente
        val coreManager = Coremanager()
        val guardadoExitoso = coreManager.guardar(transaccionDto)
        assertTrue(guardadoExitoso)

        // 5. Módulo :syncqueue (SyncQueueManager) toma la transacción y la encola para sincronización
        val syncManager = SyncQueueManager()
        val encoladoExitoso = syncManager.encolarTransaccion(transaccionDto)
        assertTrue(encoladoExitoso)
        assertEquals(1, syncManager.obtenerCantidadPendientes())
    }
}