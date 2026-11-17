package com.proyecto.xald

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// El secreto está aquí: evita que arranque XaldApplication en los tests
@Config(application = android.app.Application::class, sdk = [34])
class ConsentimientoXaldTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ConsentimientoXald.revocar(context)
    }

    @Test
    fun porDefecto_noHayConsentimiento() {
        assertFalse(ConsentimientoXald.aceptado(context))
    }

    @Test
    fun alRegistrar_quedaAceptado() {
        ConsentimientoXald.registrar(context)
        assertTrue(ConsentimientoXald.aceptado(context))
    }

    @Test
    fun alRevocar_vuelveAEstarSinConsentimiento() {
        ConsentimientoXald.registrar(context)
        ConsentimientoXald.revocar(context)
        assertFalse(ConsentimientoXald.aceptado(context))
    }
}