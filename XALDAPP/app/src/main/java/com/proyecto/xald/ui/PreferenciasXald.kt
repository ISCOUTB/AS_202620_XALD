package com.proyecto.xald.ui

import android.content.Context
import android.content.SharedPreferences
import com.proyecto.xald.corefinanciero.Periodo

/**
 * Preferencias de la interfaz (no son datos financieros): modo privacidad, periodo elegido,
 * alertas de presupuesto ya enviadas y hora de la última sincronización confirmada.
 */
object PreferenciasXald {

    private const val ARCHIVO = "xald_preferencias"
    private const val CLAVE_PRIVADO = "modo_privado"
    private const val CLAVE_PERIODO = "periodo_inicio"
    private const val CLAVE_ALERTAS = "alertas_enviadas"
    private const val CLAVE_ULTIMA_SYNC = "ultima_sincronizacion_ms"
    private const val MAXIMO_ALERTAS_GUARDADAS = 300

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    fun modoPrivado(context: Context): Boolean = prefs(context).getBoolean(CLAVE_PRIVADO, false)

    fun guardarModoPrivado(context: Context, activo: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_PRIVADO, activo).apply()
    }

    fun periodo(context: Context): Periodo {
        val guardado = prefs(context).getString(CLAVE_PERIODO, null)
        return Periodo.entries.firstOrNull { it.name == guardado } ?: Periodo.MES
    }

    fun guardarPeriodo(context: Context, periodo: Periodo) {
        prefs(context).edit().putString(CLAVE_PERIODO, periodo.name).apply()
    }

    fun alertasEnviadas(context: Context): Set<String> =
        prefs(context).getStringSet(CLAVE_ALERTAS, emptySet())?.toSet() ?: emptySet()

    @Synchronized
    fun registrarAlertas(context: Context, claves: Collection<String>) {
        if (claves.isEmpty()) return
        // Se guarda una copia nueva (Android no permite modificar el Set que devuelve getStringSet).
        val todas = (alertasEnviadas(context) + claves).toList().takeLast(MAXIMO_ALERTAS_GUARDADAS).toSet()
        prefs(context).edit().putStringSet(CLAVE_ALERTAS, todas).apply()
    }

    /** Al editar un presupuesto se olvidan sus alertas para que vuelvan a avisar con el tope nuevo. */
    @Synchronized
    fun olvidarAlertas(context: Context, categoria: String) {
        val restantes = alertasEnviadas(context).filterNot { it.startsWith("$categoria|") }.toSet()
        prefs(context).edit().putStringSet(CLAVE_ALERTAS, restantes).apply()
    }

    fun ultimaSincronizacion(context: Context): Long? =
        prefs(context).getLong(CLAVE_ULTIMA_SYNC, 0L).takeIf { it > 0L }

    fun registrarSincronizacion(context: Context, instanteMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(CLAVE_ULTIMA_SYNC, instanteMs).apply()
    }
}
