package com.proyecto.xald.syncqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PoliticaReintentosTest {

    @Test
    fun politicaPorDefecto_esperasExponencialesDeclaradas() {
        val politica = PoliticaReintentos()
        val esperas = (1 until politica.maxIntentos).map { politica.esperaTrasFallo(it) }
        // 30 s, 60 s, 120 s, 240 s, 480 s (docs/mensajes/README.md §4)
        assertEquals(listOf(30_000L, 60_000L, 120_000L, 240_000L, 480_000L), esperas)
        assertFalse(politica.agotada(5))
        assertTrue(politica.agotada(6))
    }

    @Test
    fun laEsperaNuncaSuperaElTope() {
        val politica = PoliticaReintentos(maxIntentos = 20)
        assertEquals(15 * 60_000L, politica.esperaTrasFallo(15))
    }

    @Test
    fun clasificacionDeCodigosHttp() {
        assertEquals(ResultadoEnvio.Confirmado, clasificarRespuestaHttp(202))
        assertTrue(clasificarRespuestaHttp(503) is ResultadoEnvio.FalloRecuperable)
        assertTrue(clasificarRespuestaHttp(429) is ResultadoEnvio.FalloRecuperable)
        assertTrue(clasificarRespuestaHttp(408) is ResultadoEnvio.FalloRecuperable)
        assertTrue(clasificarRespuestaHttp(400) is ResultadoEnvio.FalloPermanente)
        assertTrue(clasificarRespuestaHttp(401) is ResultadoEnvio.FalloPermanente)
        assertTrue(clasificarRespuestaHttp(422) is ResultadoEnvio.FalloPermanente)
    }
}
