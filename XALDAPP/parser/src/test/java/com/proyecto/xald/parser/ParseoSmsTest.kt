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

    // ----------------------------------------------------------- Ingresos y transferencias

    private fun exitoso(remitente: String, sms: String): TransaccionProcesadaDTO {
        val resultado = parser.parsearSms(remitente, sms, 1700000000000L)
        assertTrue("No se reconoció: $sms → $resultado", resultado is ResultadoParseo.Exitoso)
        return (resultado as ResultadoParseo.Exitoso).datos
    }

    @Test
    fun parsearSms_compraSigueSiendoGasto() {
        val datos = exitoso("890900", "Bancolombia le informa compra por \$45,900.00 en Exito Cartagena 10/11/2026.")
        assertEquals("GASTO", datos.tipoMovimiento)
    }

    @Test
    fun parsearSms_bancolombiaTransferenciaEnviada() {
        val datos = exitoso("890900", "Bancolombia: Transferiste \$50,000.00 desde tu cuenta *1234 a la cuenta *5678 el 10/11/2026 10:30.")
        assertEquals(50000.0, datos.monto, 0.001)
        assertEquals("*5678", datos.comercio)
        assertEquals("TRANSFERENCIA_ENVIADA", datos.tipoMovimiento)
    }

    @Test
    fun parsearSms_bancolombiaTransferenciaRecibida() {
        val datos = exitoso("85534", "Bancolombia: Recibiste una transferencia por \$100,000.00 de JUAN PEREZ en tu cuenta *1234 el 10/11/2026.")
        assertEquals(100000.0, datos.monto, 0.001)
        assertEquals("JUAN PEREZ", datos.comercio)
        assertEquals("TRANSFERENCIA_RECIBIDA", datos.tipoMovimiento)
    }

    @Test
    fun parsearSms_bancolombiaNomina_esIngreso() {
        val datos = exitoso("890900", "Bancolombia le informa Pago de Nomina de EMPRESA SAS por \$2,500,000.00 en su cuenta *1234 10/11/2026.")
        assertEquals(2500000.0, datos.monto, 0.001)
        assertEquals("EMPRESA SAS", datos.comercio)
        assertEquals("INGRESO", datos.tipoMovimiento)
    }

    @Test
    fun parsearSms_bancolombiaRetiro_esGasto() {
        val datos = exitoso("890900", "Bancolombia le informa retiro por \$200,000.00 en CAJERO CENTRO 10/11/2026.")
        assertEquals("CAJERO CENTRO", datos.comercio)
        assertEquals("GASTO", datos.tipoMovimiento)
    }

    @Test
    fun parsearSms_daviviendaTransferencias() {
        val enviada = exitoso("85888", "Davivienda: Transferencia exitosa por \$50.000,00 a JUAN PEREZ el 10/11/2026")
        assertEquals(50000.0, enviada.monto, 0.001)
        assertEquals("JUAN PEREZ", enviada.comercio)
        assertEquals("TRANSFERENCIA_ENVIADA", enviada.tipoMovimiento)

        val recibida = exitoso("85888", "Davivienda: Recibio una transferencia por \$80.000,00 de MARIA LOPEZ el 10/11/2026")
        assertEquals("MARIA LOPEZ", recibida.comercio)
        assertEquals("TRANSFERENCIA_RECIBIDA", recibida.tipoMovimiento)
    }

    @Test
    fun parsearSms_nequiEnvioYRecepcion() {
        val enviada = exitoso("85954", "Enviaste \$20.000 a JUAN PEREZ.")
        assertEquals(20000.0, enviada.monto, 0.001)
        assertEquals("JUAN PEREZ", enviada.comercio)
        assertEquals("TRANSFERENCIA_ENVIADA", enviada.tipoMovimiento)

        val recibida = exitoso("85954", "Recibiste \$35.500 de ANA GOMEZ.")
        assertEquals(35500.0, recibida.monto, 0.001)
        assertEquals("ANA GOMEZ", recibida.comercio)
        assertEquals("TRANSFERENCIA_RECIBIDA", recibida.tipoMovimiento)

        val teEnvio = exitoso("Nequi", "ANA GOMEZ te envió \$12.000")
        assertEquals(12000.0, teEnvio.monto, 0.001)
        assertEquals("ANA GOMEZ", teEnvio.comercio)
    }
}
