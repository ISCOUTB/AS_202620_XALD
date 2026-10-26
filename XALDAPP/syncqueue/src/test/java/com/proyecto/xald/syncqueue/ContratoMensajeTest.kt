package com.proyecto.xald.syncqueue

import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Prueba de contrato del lado productor: lo que serializa el cliente tiene exactamente los
 * campos del ejemplo v1 (docs/mensajes/ejemplos/transaccion-registrada-v1.json). El backend
 * valida ese mismo ejemplo en backend/tests/test_mensajes.py, así que si un lado cambia un
 * nombre de campo, una de las dos pruebas falla.
 */
class ContratoMensajeTest {

    private fun raizRepo(): File {
        var actual: File? = File(System.getProperty("user.dir")).absoluteFile
        while (actual != null) {
            if (File(actual, "docs/mensajes").isDirectory) return actual
            actual = actual.parentFile
        }
        error("No se encontró docs/mensajes subiendo desde ${System.getProperty("user.dir")}")
    }

    private fun claves(json: String): Set<String> =
        Regex("\"([a-z_]+)\"\\s*:").findAll(json).map { it.groupValues[1] }.toSet()

    @Test
    fun elPayloadSerializadoTieneLosCamposDelContratoV1() {
        val ejemplo = File(raizRepo(), "docs/mensajes/ejemplos/transaccion-registrada-v1.json").readText()
        val generado = payloadAJson(
            PayloadSincronizacionDTO("8f14e45f-ceea-467e-9e2a-abc123456789", 45900.0, "Tienda Ara", 1_759_227_300_000L)
        )
        assertEquals(claves(ejemplo), claves(generado))
    }

    @Test
    fun laFechaViajaEnIso8601Utc() {
        val json = payloadAJson(PayloadSincronizacionDTO("id", 1.0, "x", 0L))
        assertTrue(json, json.contains("\"fecha_transaccion\":\"1970-01-01T00:00:00Z\""))
    }

    @Test
    fun losTextosSeEscapan() {
        val json = payloadAJson(PayloadSincronizacionDTO("id", 1.0, "Café \"La 10\"\n", 0L))
        assertTrue(json, json.contains("\"comercio\":\"Café \\\"La 10\\\"\\n\""))
    }
}
