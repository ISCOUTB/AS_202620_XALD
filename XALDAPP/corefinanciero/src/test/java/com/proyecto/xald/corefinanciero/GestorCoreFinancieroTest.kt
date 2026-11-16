package com.proyecto.xald.corefinanciero

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GestorCoreFinancieroTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var servicio: InformacionFinanciera

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        servicio = crearInformacionFinanciera(context, usarBaseDatosEnMemoria = true)
    }

    @Test
    fun guardarTransaccion_montoValido_persisteEnRoom() {
        val resultado = servicio.guardarTransaccion(45000.0, "Éxito")
        assertTrue(resultado)

        val ultima = servicio.obtenerUltimaTransaccion()
        assertNotNull(ultima)
        assertEquals("Éxito", ultima?.comercio)
        assertEquals(45000.0, ultima?.monto ?: 0.0, 0.001)
        assertFalse(ultima?.sincronizado ?: true)
    }

    @Test
    fun guardarTransaccion_montoInvalido_retornaFalseYNoPersiste() {
        val resultado = servicio.guardarTransaccion(0.0, "Ara")
        assertFalse(resultado)
        assertEquals(0, servicio.obtenerHistorial().size)
    }

    @Test
    fun guardarTransaccion_montoNoFinito_retornaFalse() {
        assertFalse(servicio.guardarTransaccion(Double.NaN, "Ara"))
        assertFalse(servicio.guardarTransaccion(Double.POSITIVE_INFINITY, "Ara"))
        assertFalse(servicio.guardarTransaccion(-5.0, "Ara"))
        assertEquals(0, servicio.obtenerHistorial().size)
    }

    @Test
    fun obtenerPendientesSincronizacion_retornaSoloNoSincronizadasYTransformaADTO() {
        servicio.guardarTransaccionCompleta(10000.0, "Tienda 1")
        servicio.guardarTransaccionCompleta(20000.0, "Tienda 2")

        val pendientesIniciales = servicio.obtenerPendientesSincronizacion()
        assertEquals(2, pendientesIniciales.size)

        val idPrimera = pendientesIniciales.first().idTransaccion
        servicio.marcarComoSincronizada(idPrimera)

        val pendientesFinales = servicio.obtenerPendientesSincronizacion()
        assertEquals(1, pendientesFinales.size)
        assertEquals("Tienda 2", pendientesFinales.first().comercio)
    }

    @Test
    fun guardarConIdExistente_noDuplicaNiSobrescribe() {
        assertTrue(servicio.guardarTransaccionCompleta(100.0, "Tienda A", idTransaccion = "tx-1"))
        // Misma transacción entregada otra vez, con datos distintos: se descarta.
        assertFalse(servicio.guardarTransaccionCompleta(999.0, "Tienda B", idTransaccion = "tx-1"))

        val historial = servicio.obtenerHistorial()
        assertEquals(1, historial.size)
        assertEquals("Tienda A", historial.single().comercio)
        assertEquals(100.0, historial.single().monto, 0.001)
    }

    @Test
    fun guardarConIdExistente_noReiniciaElEstadoSincronizado() {
        servicio.guardarTransaccionCompleta(100.0, "Tienda A", idTransaccion = "tx-1")
        servicio.marcarComoSincronizada("tx-1")

        servicio.guardarTransaccionCompleta(100.0, "Tienda A", idTransaccion = "tx-1")

        assertTrue(servicio.obtenerHistorial().single().sincronizado)
        assertEquals(0, servicio.obtenerPendientesSincronizacion().size)
    }

    @Test
    fun idPersonalizado_seConservaEnElDTODeSincronizacion() {
        servicio.guardarTransaccionCompleta(
            monto = 45_900.0,
            comercio = "Exito Cartagena",
            categoria = "Supermercado",
            fechaTimestamp = 1_760_000_000_000L,
            idTransaccion = "tx-sms-1"
        )

        val dto = servicio.obtenerPendientesSincronizacion().single()
        assertEquals("tx-sms-1", dto.idTransaccion)
        assertEquals("Supermercado", dto.categoria)
        assertEquals(1_760_000_000_000L, dto.fechaTimestamp)
        assertEquals("COP", dto.moneda)
    }

    @Test
    fun marcarComoSincronizada_idInexistente_retornaFalse() {
        assertFalse(servicio.marcarComoSincronizada("no-existe"))
    }

    @Test
    fun actualizarCategoria_cambiaLaCategoriaDeLaTransaccion() {
        servicio.guardarTransaccionCompleta(100.0, "Tienda A", idTransaccion = "tx-1")

        assertTrue(servicio.actualizarCategoria("tx-1", "Salud"))
        assertEquals("Salud", servicio.obtenerHistorial().single().categoria)
    }

    @Test
    fun actualizarCategoria_vaciaOIdInexistente_retornaFalse() {
        servicio.guardarTransaccionCompleta(100.0, "Tienda A", idTransaccion = "tx-1")

        assertFalse(servicio.actualizarCategoria("tx-1", "   "))
        assertFalse(servicio.actualizarCategoria("no-existe", "Salud"))
        assertEquals("Sin Categorizar", servicio.obtenerHistorial().single().categoria)
    }

    @Test
    fun obtenerUltimaTransaccion_devuelveLaDeMayorFecha() {
        servicio.guardarTransaccionCompleta(1.0, "Vieja", fechaTimestamp = 1_000L)
        servicio.guardarTransaccionCompleta(2.0, "Nueva", fechaTimestamp = 3_000L)
        servicio.guardarTransaccionCompleta(3.0, "Media", fechaTimestamp = 2_000L)

        assertEquals("Nueva", servicio.obtenerUltimaTransaccion()?.comercio)
    }

    @Test
    fun obtenerSaldoTotal_sumaLosMontos() {
        assertEquals(0.0, servicio.obtenerSaldoTotal(), 0.001)
        servicio.guardarTransaccionCompleta(10_000.0, "A")
        servicio.guardarTransaccionCompleta(2_500.5, "B")
        assertEquals(12_500.5, servicio.obtenerSaldoTotal(), 0.001)
    }
}
