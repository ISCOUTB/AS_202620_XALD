package com.proyecto.xald.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class ClasificadorMovimientosTest {

    private fun tipo(texto: String) = ClasificadorMovimientos.clasificar(texto)

    @Test
    fun comprasPagosYRetiros_sonGasto() {
        assertEquals("GASTO", tipo("Bancolombia le informa compra por \$45,900.00 en Exito Cartagena"))
        assertEquals("GASTO", tipo("Pagaste \$15.500 a TIENDA DON PEDRO."))
        assertEquals("GASTO", tipo("Bancolombia le informa retiro por \$200,000.00 en CAJERO CENTRO"))
        assertEquals("GASTO", tipo("Bancolombia le informa pago por \$85,000.00 a CLARO"))
    }

    @Test
    fun envios_sonTransferenciaEnviada() {
        assertEquals("TRANSFERENCIA_ENVIADA", tipo("Bancolombia: Transferiste \$50,000.00 a la cuenta *5678"))
        assertEquals("TRANSFERENCIA_ENVIADA", tipo("Enviaste \$20.000 a JUAN PEREZ."))
        assertEquals("TRANSFERENCIA_ENVIADA", tipo("Davivienda: Transferencia exitosa por \$50.000,00 a JUAN"))
    }

    @Test
    fun recepciones_sonTransferenciaRecibida_conOSinTildes() {
        assertEquals("TRANSFERENCIA_RECIBIDA", tipo("Recibiste una transferencia por \$100,000.00 de JUAN PEREZ"))
        assertEquals("TRANSFERENCIA_RECIBIDA", tipo("Davivienda: Recibió una transferencia por \$80.000,00"))
        assertEquals("TRANSFERENCIA_RECIBIDA", tipo("Recibiste \$20.000 de JUAN PEREZ."))
        assertEquals("TRANSFERENCIA_RECIBIDA", tipo("JUAN PEREZ te envió \$20.000"))
    }

    @Test
    fun nominaYAbonos_sonIngreso() {
        assertEquals("INGRESO", tipo("Bancolombia le informa Pago de Nómina de EMPRESA SAS por \$2,500,000.00"))
        assertEquals("INGRESO", tipo("Bancolombia le informa abono por \$500,000.00 de EMPRESA SAS"))
        assertEquals("INGRESO", tipo("Te consignaron \$300.000 en tu cuenta"))
    }
}
