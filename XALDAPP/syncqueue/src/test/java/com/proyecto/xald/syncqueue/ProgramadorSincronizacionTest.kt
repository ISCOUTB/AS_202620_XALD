package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProgramadorSincronizacionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val configuracion = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuracion)
    }

    private fun trabajos(nombre: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(nombre).get()

    @Test
    fun programarPeriodica_creaUnSoloTrabajoQueExigeRed() {
        ProgramadorSincronizacion.programarPeriodica(context)
        ProgramadorSincronizacion.programarPeriodica(context)

        val infos = trabajos(ProgramadorSincronizacion.NOMBRE_TRABAJO_PERIODICO)
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
    }

    @Test
    fun solicitarEnvioInmediato_noDuplicaTrabajosPendientes() {
        ProgramadorSincronizacion.solicitarEnvioInmediato(context)
        ProgramadorSincronizacion.solicitarEnvioInmediato(context)

        val infos = trabajos(ProgramadorSincronizacion.NOMBRE_TRABAJO_INMEDIATO)
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
    }
}
