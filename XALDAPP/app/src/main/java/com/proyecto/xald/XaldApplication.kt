package com.proyecto.xald

import android.app.Application
import android.util.Log
import com.proyecto.xald.BuildConfig
import com.proyecto.xald.corefinanciero.crearInformacionFinanciera
import com.proyecto.xald.syncqueue.ColaSincronizacionProvider
import com.proyecto.xald.syncqueue.ProgramadorSincronizacion
import com.proyecto.xald.syncqueue.TransporteHttpSincronizacion
import com.proyecto.xald.syncqueue.crearColaSincronizacionService
import com.proyecto.xald.ui.PreferenciasXald
import kotlin.concurrent.thread

class XaldApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Android ejecuta Application.onCreate antes que cualquier Activity, Receiver o Worker del
        // proceso, así que la cola siempre está instalada cuando llega un SMS o corre el Worker.
        val coreFinanciero = crearInformacionFinanciera(this)

        // La URL y la clave salen de local.properties (ver app/build.gradle.kts).
        val transporteHttp = TransporteHttpSincronizacion(
            urlBase = BuildConfig.XALD_API_URL,
            apiKey = BuildConfig.XALD_API_KEY
        )

        val colaSync = crearColaSincronizacionService(
            context = this,
            transporte = transporteHttp,
            // Cuando el backend confirma un mensaje, la transacción pasa a "sincronizada" en el core.
            alConfirmar = { payload ->
                coreFinanciero.marcarComoSincronizada(payload.idTransaccion)
                PreferenciasXald.registrarSincronizacion(this)
                NotificadorCambios.avisar()
            }
        )

        ColaSincronizacionProvider.instalar(colaSync)
        // Canal de las alertas de presupuesto (80 %, 90 %, 100 %).
        NotificadorPresupuestos.crearCanal(this)
        ProgramadorSincronizacion.programarPeriodica(this)

        // Red de seguridad "al menos una vez": si el proceso murió entre guardar una transacción
        // y encolarla, se vuelve a encolar aquí. Es idempotente: la cola deduplica por
        // "id_transaccion@fecha", así que lo ya encolado o enviado se ignora.
        thread(name = "xald-reencolado") {
            try {
                coreFinanciero.obtenerPendientesSincronizacion().forEach { colaSync.encolarTransaccion(it) }
                if (colaSync.obtenerCantidadPendientes() > 0) {
                    ProgramadorSincronizacion.solicitarEnvioInmediato(this)
                }
            } catch (e: Exception) {
                Log.e("XALD", "No se pudo reencolar pendientes (${e.javaClass.simpleName})")
            }
        }
    }
}
