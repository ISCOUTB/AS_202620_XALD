package com.proyecto.xald.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class FormatoFechaTest {

    private val zona = ZoneId.of("America/Bogota")
    private val formato = FormatoFecha(zona)
    private val ahora = ms("2026-11-18T14:00")

    private fun ms(texto: String) = LocalDateTime.parse(texto).atZone(zona).toInstant().toEpochMilli()

    @Test
    fun encabezados() {
        assertEquals("Hoy", formato.encabezadoDia(ms("2026-11-18T08:00"), ahora))
        assertEquals("Ayer", formato.encabezadoDia(ms("2026-11-17T23:59"), ahora))
        assertEquals("Jue 12 nov", formato.encabezadoDia(ms("2026-11-12T10:00"), ahora))
        assertEquals("Mié 31 dic 2025", formato.encabezadoDia(ms("2025-12-31T10:00"), ahora))
    }

    @Test
    fun horaEnFormatoColombiano() {
        assertEquals("2:05 p. m.", formato.hora(ms("2026-11-18T14:05")))
        assertEquals("12:00 a. m.", formato.hora(ms("2026-11-18T00:00")))
        assertEquals("12:30 p. m.", formato.hora(ms("2026-11-18T12:30")))
    }

    @Test
    fun relativo() {
        assertEquals("hace un momento", formato.relativo(ahora - 20_000, ahora))
        assertEquals("hace 5 min", formato.relativo(ahora - 5 * 60_000, ahora))
        assertEquals("hace 3 h", formato.relativo(ms("2026-11-18T11:00"), ahora))
        assertEquals("ayer a las 9:00 p. m.", formato.relativo(ms("2026-11-17T21:00"), ahora))
        assertEquals("el 2 nov 2026", formato.relativo(ms("2026-11-02T21:00"), ahora))
    }

    @Test
    fun diasRestantesDelPeriodo() {
        // Noviembre termina el 30: del 18 al 30 son 13 días contando hoy.
        assertEquals(13L, formato.diasRestantes(ms("2026-12-01T00:00"), ahora))
        assertEquals(1L, formato.diasRestantes(ms("2026-11-19T00:00"), ahora))
    }

    @Test
    fun formulario() {
        assertEquals("Hoy, 18 nov 2026", formato.fechaFormulario(ahora, ahora))
        assertEquals(ms("2026-11-10T12:00"), formato.mediodia(2026, 11, 10))
    }
}
