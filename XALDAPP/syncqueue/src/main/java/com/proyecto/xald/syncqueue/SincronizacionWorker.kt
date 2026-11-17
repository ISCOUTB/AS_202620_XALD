package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Trabajo en segundo plano que vacía la cola de sincronización (esquema.txt Fase 6, puntos 2-3).
 *
 * Lo programa [ProgramadorSincronizacion] con la restricción "red conectada", así que WorkManager
 * solo lo ejecuta cuando hay conexión. El backoff de cada mensaje lo decide la propia cola
 * ([PoliticaReintentos]); aquí solo se decide si WorkManager debe volver a llamar al Worker:
 *
 *  - quedan mensajes pendientes (sin conexión, o esperando su próximo reintento) -> `retry()`;
 *  - la cola quedó vacía, o solo quedan fallidos (que no se reintentan solos) -> `success()`.
 */
class SincronizacionWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        // Si el proceso arrancó sin que :app instalara la cola, se vuelve a intentar más tarde.
        val cola = ColaSincronizacionProvider.obtener() ?: return Result.retry()

        return try {
            cola.procesarPendientes()
            if (cola.obtenerCantidadPendientes() > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
