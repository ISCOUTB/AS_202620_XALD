package com.proyecto.xald.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatoDineroTest {

    @Test
    fun formatear_pesosColombianos() {
        assertEquals("$ 1.234.567", FormatoDinero.formatear(1_234_567.0))
        assertEquals("$ 0", FormatoDinero.formatear(0.0))
        assertEquals("−$ 15.000", FormatoDinero.formatear(-15_000.0))
        assertEquals("+$ 2.500", FormatoDinero.formatear(2_500.0, conSigno = true))
        assertEquals("$ 45.901", FormatoDinero.formatear(45_900.6))
    }

    @Test
    fun formatear_modoPrivacidad_ocultaElMonto() {
        assertEquals("$ ****", FormatoDinero.formatear(1_234_567.0, privado = true))
        assertEquals("+$ ****", FormatoDinero.movimiento(50_000.0, esEntrada = true, privado = true))
        assertEquals("−$ ****", FormatoDinero.movimiento(50_000.0, esEntrada = false, privado = true))
    }

    @Test
    fun movimiento_conSignoSegunElTipo() {
        assertEquals("+$ 50.000", FormatoDinero.movimiento(50_000.0, esEntrada = true, privado = false))
        assertEquals("−$ 12.000", FormatoDinero.movimiento(12_000.0, esEntrada = false, privado = false))
    }

    @Test
    fun parsear_formatosQueEscribeLaGente() {
        assertEquals(45_900.0, MontoUsuario.parsear("45.900")!!, 0.001)
        assertEquals(45_900.0, MontoUsuario.parsear("45900")!!, 0.001)
        assertEquals(2_500_000.0, MontoUsuario.parsear("$ 2.500.000")!!, 0.001)
        assertEquals(2_500_000.0, MontoUsuario.parsear("2,500,000")!!, 0.001)
        assertEquals(1_234.5, MontoUsuario.parsear("1.234,50")!!, 0.001)
        assertEquals(1_234.5, MontoUsuario.parsear("1,234.50")!!, 0.001)
        assertEquals(45.5, MontoUsuario.parsear("45,5")!!, 0.001)
        assertEquals(-20_000.0, MontoUsuario.parsear("-20.000")!!, 0.001)
    }

    @Test
    fun parsear_textoInvalido_esNull() {
        assertNull(MontoUsuario.parsear(""))
        assertNull(MontoUsuario.parsear("abc"))
        assertNull(MontoUsuario.parsear("1.234.56"))
        assertNull(MontoUsuario.parsear("."))
        assertNull(MontoUsuario.parsear("-"))
    }

    @Test
    fun estiloCategoria_conocidaYDesconocida() {
        assertEquals("🛒", EstiloCategoria.emoji("supermercado"))
        assertEquals("🏷️", EstiloCategoria.emoji("Mascotas"))
        assertEquals(EstiloCategoria.color("Mascotas"), EstiloCategoria.color("MASCOTAS"))
    }
}
