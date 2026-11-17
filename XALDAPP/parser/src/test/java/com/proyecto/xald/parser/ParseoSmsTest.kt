package com.proyecto.xald.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ParseoSmsTest {

    private lateinit var parser: ServicioParser

    @Before
    fun setUp() {
        parser = crearServicioParser()
    }

    @Test
    fun parsearSms_bancolombiaValido_retornaExitoso() {
        val sms = "Bancolombia le informa compra por $45,900.00 en Exito Cartagena 10/11/2026."
        val resultado = parser.parsearSms("890900", sms, 1700000000000L)

        assertTrue(resultado is ResultadoParseo.Exitoso)
        val datos = (resultado as ResultadoParseo.Exitoso).datos
        assertEquals(45900.0, datos.monto, 0.001)
        assertEquals("Exito Cartagena", datos.comercio)
        assertEquals("Bancolombia", datos.entidadBancaria)
    }

    @Test
    fun parsearSms_nequiValido_retornaExitoso() {
        val sms = "Pagaste $15.500 a TIENDA DON PEDRO."
        val resultado = parser.parsearSms("85954", sms)

        assertTrue(resultado is ResultadoParseo.Exitoso)
        val datos = (resultado as ResultadoParseo.Exitoso).datos
        assertEquals(15500.0, datos.monto, 0.001)
        assertEquals("TIENDA DON PEDRO", datos.comercio)
        assertEquals("Nequi", datos.entidadBancaria)
    }

    @Test
    fun parsearSms_remitenteNoAutorizado_retornaRemitenteNoAutorizado() {
        val sms = "Bancolombia le informa compra por $45,900.00 en Exito Cartagena"
        val resultado = parser.parsearSms("3001234567", sms)

        assertTrue(resultado is ResultadoParseo.RemitenteNoAutorizado)
    }

    @Test
    fun parsearSms_formatoMensajeInvalido_retornaFormatoNoReconocido() {
        val sms = "Su clave dinamica es 123456 para ingresar a Bancolombia"
        val resultado = parser.parsearSms("890900", sms)

        assertTrue(resultado is ResultadoParseo.FormatoNoReconocido)
    }
}