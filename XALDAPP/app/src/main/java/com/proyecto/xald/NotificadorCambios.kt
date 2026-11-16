package com.proyecto.xald

import android.os.Handler
import android.os.Looper

/**
 * Aviso muy simple "los datos cambiaron" para refrescar la pantalla en vivo cuando llega un SMS
 * o se confirma un envío, sin que el usuario pulse "Actualizar". Solo hay un oyente (la
 * pantalla visible); `MainActivity` lo registra en onStart y lo quita en onStop.
 */
object NotificadorCambios {

    private val principal by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var oyente: (() -> Unit)? = null

    fun registrar(nuevoOyente: (() -> Unit)?) {
        oyente = nuevoOyente
    }

    /** Se puede llamar desde cualquier hilo; el oyente corre en el hilo principal. */
    fun avisar() {
        val actual = oyente ?: return
        principal.post { actual() }
    }
}
