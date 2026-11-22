package com.proyecto.xald.corefinanciero

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado en reposo de las bases locales (ESC-04 / ADR-0018).
 *
 * - Las bases `xald_core.db` y `xald_syncqueue.db` se abren con SQLCipher (AES-256 por página).
 * - La frase de la base es aleatoria (32 bytes) y se guarda en SharedPreferences **envuelta** con
 *   una llave AES-256-GCM que vive en el Android Keystore y nunca sale del hardware/TEE.
 * - Si una base todavía está en texto plano (instalaciones anteriores a este cambio), se migra
 *   a cifrada con `sqlcipher_export` la primera vez que se abre, sin perder datos.
 *
 * Las bases en memoria (pruebas) no pasan por aquí.
 */
object CifradoBaseLocal {

    private const val ETIQUETA = "XaldCifrado"
    private const val ALIAS_LLAVE = "xald_llave_base_local"
    private const val PREFS = "xald_cifrado_base"
    private const val PREF_FRASE = "frase_envuelta"

    @Volatile
    private var libreriaCargada = false

    @Volatile
    private var fraseEnCache: String? = null

    /**
     * Fábrica de Room para abrir [nombreArchivo] cifrada. Migra antes la base si estaba en texto
     * plano. Llamar desde el mismo proceso de la app (no hace falta hilo de fondo: es rápido salvo
     * la migración única).
     */
    @Synchronized
    fun factoria(context: Context, nombreArchivo: String): SupportSQLiteOpenHelper.Factory {
        cargarLibreria()
        val frase = obtenerFrase(context.applicationContext)
        val archivo = context.applicationContext.getDatabasePath(nombreArchivo)
        migrarSiEstaEnTextoPlano(archivo, frase)
        return SupportOpenHelperFactory(frase.toByteArray(Charsets.UTF_8))
    }

    private fun cargarLibreria() {
        if (!libreriaCargada) {
            System.loadLibrary("sqlcipher")
            libreriaCargada = true
        }
    }

    // ------------------------------------------------------------------ Frase de la base

    private fun obtenerFrase(context: Context): String {
        fraseEnCache?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val gestor = GestorFraseBase(
            envoltorio = EnvoltorioKeystore(ALIAS_LLAVE),
            leer = { prefs.getString(PREF_FRASE, null) },
            guardar = { valor -> prefs.edit().putString(PREF_FRASE, valor).commit() },
            codificar = { Base64.encodeToString(it, Base64.NO_WRAP) },
            decodificar = { Base64.decode(it, Base64.NO_WRAP) }
        )
        val resultado = gestor.obtenerOCrear()
        if (resultado.seRegenero) {
            // La llave del Keystore se perdió (p. ej. restauración en otro equipo o borrado de
            // credenciales). Las bases cifradas con la frase anterior ya no se pueden abrir:
            // se descartan para que la app arranque limpia en vez de fallar en cada inicio.
            Log.w(ETIQUETA, "Frase de la base irrecuperable; se reinician las bases locales cifradas")
            listOf(NOMBRE_BASE_CORE, NOMBRE_BASE_SYNC).forEach { context.deleteDatabase(it) }
        }
        fraseEnCache = resultado.frase
        return resultado.frase
    }

    // ------------------------------------------------------------------ Migración texto plano → cifrada

    private fun migrarSiEstaEnTextoPlano(archivo: File, frase: String) {
        if (!EncabezadoSqlite.esTextoPlano(archivo)) return

        Log.i(ETIQUETA, "Migrando ${archivo.name} de texto plano a SQLCipher")
        val temporal = File(archivo.parentFile, archivo.name + ".cifrando")
        borrarConAuxiliares(temporal)

        val plano = SQLiteDatabase.openDatabase(
            archivo.absolutePath,
            null as SQLiteDatabase.CursorFactory?,
            SQLiteDatabase.OPEN_READWRITE
        )
        try {
            val version = plano.version
            plano.execSQL("ATTACH DATABASE ? AS cifrada KEY ?", arrayOf<Any>(temporal.absolutePath, frase))
            plano.rawExecSQL("SELECT sqlcipher_export('cifrada')")
            // sqlcipher_export no garantiza copiar user_version y Room lo usa para las migraciones.
            plano.execSQL("PRAGMA cifrada.user_version = $version")
            plano.execSQL("DETACH DATABASE cifrada")
        } catch (e: Exception) {
            plano.close()
            borrarConAuxiliares(temporal)
            throw IllegalStateException("No se pudo cifrar ${archivo.name}; la base original queda intacta", e)
        }
        plano.close()

        borrarConAuxiliares(archivo)
        if (!temporal.renameTo(archivo)) {
            throw IllegalStateException("No se pudo reemplazar ${archivo.name} por su versión cifrada")
        }
    }

    private fun borrarConAuxiliares(archivo: File) {
        listOf("", "-journal", "-wal", "-shm").forEach { File(archivo.path + it).delete() }
    }

    // ------------------------------------------------------------------ Keystore

    /** Envuelve/desenvuelve bytes con una llave AES-256-GCM del Android Keystore. */
    private class EnvoltorioKeystore(private val alias: String) : EnvoltorioClave {

        private fun llave(crearSiFalta: Boolean): SecretKey? {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(alias, null) as? SecretKey)?.let { return it }
            if (!crearSiFalta) return null
            val generador = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generador.init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            return generador.generateKey()
        }

        override fun envolver(datos: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, llave(crearSiFalta = true))
            return cipher.iv + cipher.doFinal(datos)
        }

        override fun desenvolver(envuelto: ByteArray): ByteArray? {
            val llave = llave(crearSiFalta = false) ?: return null
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, llave, GCMParameterSpec(128, envuelto, 0, LARGO_IV_GCM))
                cipher.doFinal(envuelto, LARGO_IV_GCM, envuelto.size - LARGO_IV_GCM)
            } catch (e: Exception) {
                null
            }
        }

        override fun descartar() {
            runCatching {
                KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
            }
        }
    }
}

internal const val LARGO_IV_GCM = 12
internal const val NOMBRE_BASE_SYNC = "xald_syncqueue.db"

/** Abstracción del Keystore para poder probar [GestorFraseBase] en la JVM. */
internal interface EnvoltorioClave {
    fun envolver(datos: ByteArray): ByteArray

    /** `null` si la llave ya no existe o no puede descifrar (dato alterado o llave distinta). */
    fun desenvolver(envuelto: ByteArray): ByteArray?

    fun descartar()
}

internal data class ResultadoFrase(val frase: String, val seRegenero: Boolean)

/**
 * Lógica pura de la frase de la base: la crea la primera vez, la recupera después y, si ya no se
 * puede desenvolver, genera una nueva avisando con [ResultadoFrase.seRegenero].
 */
internal class GestorFraseBase(
    private val envoltorio: EnvoltorioClave,
    private val leer: () -> String?,
    private val guardar: (String) -> Boolean,
    private val codificar: (ByteArray) -> String,
    private val decodificar: (String) -> ByteArray,
    private val aleatorio: SecureRandom = SecureRandom()
) {
    fun obtenerOCrear(): ResultadoFrase {
        val guardada = leer()
        if (guardada != null) {
            val bytes = runCatching { decodificar(guardada) }.getOrNull()?.let { envoltorio.desenvolver(it) }
            if (bytes != null && bytes.size == LARGO_FRASE) {
                return ResultadoFrase(aHex(bytes), seRegenero = false)
            }
            envoltorio.descartar()
        }
        val nueva = ByteArray(LARGO_FRASE).also { aleatorio.nextBytes(it) }
        check(guardar(codificar(envoltorio.envolver(nueva)))) { "No se pudo guardar la frase de la base" }
        return ResultadoFrase(aHex(nueva), seRegenero = guardada != null)
    }

    companion object {
        const val LARGO_FRASE = 32

        fun aHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

/** Distingue una base SQLite en texto plano de una cifrada mirando su encabezado. */
internal object EncabezadoSqlite {
    private val FIRMA = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    /** `true` solo si el archivo existe y empieza con la firma de SQLite sin cifrar. */
    fun esTextoPlano(archivo: File): Boolean {
        if (!archivo.isFile || archivo.length() < FIRMA.size) return false
        val inicio = ByteArray(FIRMA.size)
        archivo.inputStream().use { entrada ->
            var leidos = 0
            while (leidos < inicio.size) {
                val n = entrada.read(inicio, leidos, inicio.size - leidos)
                if (n < 0) return false
                leidos += n
            }
        }
        return inicio.contentEquals(FIRMA)
    }
}
