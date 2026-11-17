package com.proyecto.xald

import org.junit.Assert.assertEquals
import org.junit.Test

class AgrupacionSmsTest {

    @Test
    fun smsLargoPartidoEnFragmentos_seUneEnUnSoloMensaje() {
        val partes = listOf(
            ParteSms("890900", "Bancolombia le informa compra por \$45,900.00 en Exi", 1_000L),
            ParteSms("890900", "to Cartagena 10/11/2026.", 1_000L)
        )

        val mensajes = unirPartesSms(partes)

        assertEquals(1, mensajes.size)
        assertEquals(
            "Bancolombia le informa compra por \$45,900.00 en Exito Cartagena 10/11/2026.",
            mensajes.single().cuerpo
        )
        assertEquals("890900", mensajes.single().remitente)
        assertEquals(1_000L, mensajes.single().timestampMs)
    }

    @Test
    fun remitentesDistintos_noSeMezclan() {
        val partes = listOf(
            ParteSms("890900", "uno", 1L),
            ParteSms("85954", "dos", 2L)
        )

        val mensajes = unirPartesSms(partes)

        assertEquals(listOf("890900", "85954"), mensajes.map { it.remitente })
        assertEquals(listOf("uno", "dos"), mensajes.map { it.cuerpo })
    }

    @Test
    fun sinFragmentos_noHayMensajes() {
        assertEquals(emptyList<SmsCompleto>(), unirPartesSms(emptyList()))
    }
}
