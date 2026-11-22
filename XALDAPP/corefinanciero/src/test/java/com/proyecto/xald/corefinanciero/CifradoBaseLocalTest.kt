package com.proyecto.xald.corefinanciero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Lógica del cifrado local que se puede probar en la JVM. La parte nativa (SQLCipher) y el
 * Android Keystore solo existen en un equipo real: ver la prueba manual en ADR-0018.
 */
class CifradoBaseLocalTest {

    /** Igual que el envoltorio del Keystore, pero con una llave AES-256 en memoria. */
    private class EnvoltorioEnMemoria : EnvoltorioClave {
        var llave: SecretKey? = nuevaLlave()
        var descartes = 0

        override fun envolver(datos: ByteArray): ByteArray {
            if (llave == null) llave = nuevaLlave()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, llave)
            return cipher.iv + cipher.doFinal(datos)
        }

        override fun desenvolver(envuelto: ByteArray): ByteArray? {
            val k = llave ?: return null
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, envuelto, 0, LARGO_IV_GCM))
                cipher.doFinal(envuelto, LARGO_IV_GCM, envuelto.size - LARGO_IV_GCM)
            } catch (e: Exception) {
                null
            }
        }

        override fun descartar() {
            llave = null
            descartes++
        }

        companion object {
            fun nuevaLlave(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        }
    }

    private class Almacen {
        var valor: String? = null
    }

    private fun gestor(envoltorio: EnvoltorioClave, almacen: Almacen) = GestorFraseBase(
        envoltorio = envoltorio,
        leer = { almacen.valor },
        guardar = { almacen.valor = it; true },
        codificar = { Base64.getEncoder().encodeToString(it) },
        decodificar = { Base64.getDecoder().decode(it) }
    )

    @Test
    fun primeraVez_creaUnaFraseDe256Bits_yLaGuardaEnvuelta() {
        val almacen = Almacen()
        val resultado = gestor(EnvoltorioEnMemoria(), almacen).obtenerOCrear()

        assertEquals(64, resultado.frase.length)
        assertTrue(resultado.frase.all { it in "0123456789abcdef" })
        assertFalse(resultado.seRegenero)
        // Lo guardado no es la frase en claro.
        assertFalse(almacen.valor!!.contains(resultado.frase))
    }

    @Test
    fun siguientesAperturas_devuelvenLaMismaFrase() {
        val almacen = Almacen()
        val envoltorio = EnvoltorioEnMemoria()
        val primera = gestor(envoltorio, almacen).obtenerOCrear()
        val segunda = gestor(envoltorio, almacen).obtenerOCrear()

        assertEquals(primera.frase, segunda.frase)
        assertFalse(segunda.seRegenero)
    }

    @Test
    fun llavePerdida_generaFraseNueva_yAvisaParaReiniciarLasBases() {
        val almacen = Almacen()
        val envoltorio = EnvoltorioEnMemoria()
        val primera = gestor(envoltorio, almacen).obtenerOCrear()

        envoltorio.llave = EnvoltorioEnMemoria.nuevaLlave() // como si el Keystore se hubiera borrado
        val segunda = gestor(envoltorio, almacen).obtenerOCrear()

        assertNotEquals(primera.frase, segunda.frase)
        assertTrue(segunda.seRegenero)
        assertEquals(1, envoltorio.descartes)
        // Y la nueva queda estable.
        assertEquals(segunda.frase, gestor(envoltorio, almacen).obtenerOCrear().frase)
    }

    @Test
    fun valorGuardadoCorrupto_noRevientaLaApp() {
        val almacen = Almacen().apply { valor = "esto-no-es-base64-%%%" }
        val resultado = gestor(EnvoltorioEnMemoria(), almacen).obtenerOCrear()

        assertEquals(64, resultado.frase.length)
        assertTrue(resultado.seRegenero)
    }

    @Test
    fun aHex_rellenaConCeros() {
        assertEquals("00ff0a", GestorFraseBase.aHex(byteArrayOf(0, -1, 10)))
    }

    @Test
    fun encabezado_detectaSoloBasesEnTextoPlano() {
        val dir = java.nio.file.Files.createTempDirectory("xald-cifrado").toFile()
        try {
            val plana = File(dir, "plana.db").apply {
                writeBytes("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII) + ByteArray(100))
            }
            val cifrada = File(dir, "cifrada.db").apply { writeBytes(ByteArray(4096) { (it * 31).toByte() }) }
            val corta = File(dir, "corta.db").apply { writeBytes("SQLite".toByteArray()) }

            assertTrue(EncabezadoSqlite.esTextoPlano(plana))
            assertFalse(EncabezadoSqlite.esTextoPlano(cifrada))
            assertFalse(EncabezadoSqlite.esTextoPlano(corta))
            assertFalse(EncabezadoSqlite.esTextoPlano(File(dir, "no-existe.db")))
        } finally {
            dir.deleteRecursively()
        }
    }
}
