package com.proyecto.xald

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.proyecto.xald.aigemini.CategoriaResultado
import com.proyecto.xald.aigemini.crearServicioCategorizacion
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import com.proyecto.xald.corefinanciero.crearInformacionFinanciera
import com.proyecto.xald.syncqueue.ColaSincronizacionProvider
import com.proyecto.xald.syncqueue.ProgramadorSincronizacion
import java.util.concurrent.TimeUnit

/**
 * Trabajo diferido gestionado por WorkManager: procesa en segundo plano las transacciones que
 * quedaron en "Sin Categorizar" apenas haya conexión a internet, sin el límite de tiempo corto
 * de un BroadcastReceiver.
 */
class CategorizacionIaWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val coreFinanciero = crearInformacionFinanciera(applicationContext)
        val cola = ColaSincronizacionProvider.obtener() ?: return Result.retry()

        val pendientesIa = coreFinanciero.obtenerHistorial()
            .filter {
                it.categoria.equals("Sin Categorizar", ignoreCase = true) &&
                        it.comercio.isNotBlank() &&
                        !it.comercio.equals("DESCONOCIDO", ignoreCase = true)
            }

        if (pendientesIa.isEmpty()) {
            return Result.success()
        }

        val servicioGemini = crearServicioCategorizacion(timeoutMs = 60_000L)
        var huboActualizaciones = false
        var huboFalloTemporal = false

        for (tx in pendientesIa) {
            when (val resultado = servicioGemini.categorizarComercio(tx.comercio)) {
                is CategoriaResultado.Exitosa -> {
                    if (!resultado.categoria.equals("Sin Categorizar", ignoreCase = true)) {
                        val actualizada = coreFinanciero.actualizarCategoria(tx.id, resultado.categoria)
                        if (actualizada) {
                            huboActualizaciones = true
                            // Re-encola con el mismo idDeterminista para actualizar también el backend
                            cola.encolarTransaccion(
                                PayloadSincronizacionDTO(
                                    idTransaccion = tx.id,
                                    monto = tx.monto,
                                    comercio = tx.comercio,
                                    fechaTimestamp = tx.fechaTimestamp,
                                    moneda = tx.moneda,
                                    categoria = resultado.categoria,
                                    origenDatos = tx.origenDatos
                                )
                            )
                        }
                    }
                }
                is CategoriaResultado.Fallo -> {
                    Log.w("XALD_IA", "Diferido en worker para '${tx.comercio}': ${resultado.motivo}")
                    huboFalloTemporal = true
                }
            }
        }

        if (huboActualizaciones) {
            NotificadorCambios.avisar()
            ProgramadorSincronizacion.solicitarEnvioInmediato(applicationContext)
        }

        return if (huboFalloTemporal) Result.retry() else Result.success()
    }

    companion object {
        private const val NOMBRE_TRABAJO = "xald_categorizacion_ia"

        fun programar(context: Context) {
            val restricciones = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val solicitud = OneTimeWorkRequestBuilder<CategorizacionIaWorker>()
                .setConstraints(restricciones)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                NOMBRE_TRABAJO,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                solicitud
            )
        }
    }
}