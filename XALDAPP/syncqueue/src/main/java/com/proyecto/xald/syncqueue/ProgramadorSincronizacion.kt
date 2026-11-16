package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Programa el [SincronizacionWorker] con WorkManager. Es la única API pública que `:app` necesita
 * (no expone tipos de WorkManager).
 *
 *  - [programarPeriodica]: red de seguridad. Se llama una vez al arrancar la app; cada ~15 min
 *    (mínimo de Android) revisa la cola si hay conexión.
 *  - [solicitarEnvioInmediato]: se llama justo después de encolar una transacción, para enviarla
 *    en cuanto haya red sin esperar al ciclo periódico.
 *
 * Ambas son idempotentes: llamarlas varias veces no crea trabajos duplicados.
 */
object ProgramadorSincronizacion {

    const val NOMBRE_TRABAJO_PERIODICO = "xald_sync_periodica"
    const val NOMBRE_TRABAJO_INMEDIATO = "xald_sync_inmediata"

    private const val ESPERA_REINTENTO_SEGUNDOS = 30L

    private val restricciones: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun programarPeriodica(context: Context, intervaloMinutos: Long = 15) {
        val peticion = PeriodicWorkRequestBuilder<SincronizacionWorker>(intervaloMinutos, TimeUnit.MINUTES)
            .setConstraints(restricciones)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ESPERA_REINTENTO_SEGUNDOS, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            NOMBRE_TRABAJO_PERIODICO,
            ExistingPeriodicWorkPolicy.KEEP,
            peticion
        )
    }

    fun solicitarEnvioInmediato(context: Context) {
        val peticion = OneTimeWorkRequestBuilder<SincronizacionWorker>()
            .setConstraints(restricciones)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ESPERA_REINTENTO_SEGUNDOS, TimeUnit.SECONDS)
            .build()

        // KEEP: si ya hay un envío pendiente o en curso, no se duplica; ese Worker vuelve a
        // intentarlo mientras queden mensajes pendientes.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            NOMBRE_TRABAJO_INMEDIATO,
            ExistingWorkPolicy.KEEP,
            peticion
        )
    }
}
