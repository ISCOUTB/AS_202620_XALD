package com.proyecto.xald

import android.content.Context

/**
 * Autorización previa, expresa e informada para procesar SMS (esquema.txt RL-01, Ley 1581).
 * El permiso técnico de Android no la sustituye: el receptor ignora todo SMS mientras
 * [aceptado] sea false.
 */
object ConsentimientoXald {

    /** Subir este número cuando cambie el texto del aviso: obliga a pedir la autorización de nuevo. */
    const val VERSION_TEXTO = 1

    private const val ARCHIVO = "xald_consentimiento"
    private const val CLAVE_ACEPTADO = "aceptado"
    private const val CLAVE_VERSION = "version_texto"
    private const val CLAVE_FECHA = "fecha_ms"

    fun aceptado(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        return prefs.getBoolean(CLAVE_ACEPTADO, false) && prefs.getInt(CLAVE_VERSION, 0) >= VERSION_TEXTO
    }

    fun registrar(context: Context, ahoraMs: Long = System.currentTimeMillis()) {
        context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit()
            .putBoolean(CLAVE_ACEPTADO, true)
            .putInt(CLAVE_VERSION, VERSION_TEXTO)
            .putLong(CLAVE_FECHA, ahoraMs)
            .apply()
    }

    fun revocar(context: Context) {
        context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE).edit()
            .putBoolean(CLAVE_ACEPTADO, false)
            .apply()
    }
}
