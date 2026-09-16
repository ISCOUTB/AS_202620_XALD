package com.proyecto.xald

import com.proyecto.xald.parser.ServicioParser
import com.proyecto.xald.aigemini.ServicioCategorizacion
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.syncqueue.ColaSincronizacionService

/**
 * Caso de Uso orquestador en la capa de Aplicación (:app).
 * Coordina la ejecución ordenada entre los 4 módulos de dominio usando solo sus contratos públicos.
 */
class ProcesarNotificacionUseCase(
    private val parserService: ServicioParser,
    private val categorizadorService: ServicioCategorizacion,
    private val coreFinancieroService: InformacionFinanciera,
    private val syncQueueService: ColaSincronizacionService
) {
    suspend fun ejecutar(smsCrudo: String): Boolean {
        // Step 1: Parseo de la transacción mediante el contexto de Ingesta
        val transaccionProcesada = parserService.parsear(smsCrudo)

        // Step 2: Categorización a través de la Capa Anticorrupción (ACL)
        categorizadorService.categorizar(transaccionProcesada.comercio)

        // Step 3: Persistencia primaria en el Núcleo Financiero
        val guardado = coreFinancieroService.guardarTransaccion(
            monto = transaccionProcesada.monto,
            comercio = transaccionProcesada.comercio
        )

        // Step 4: Encolado para la cola de sincronización offline
        if (guardado) {
            val pendientes = coreFinancieroService.obtenerPendientesSincronizacion()
            pendientes.lastOrNull()?.let { payload ->
                syncQueueService.encolarTransaccion(payload)
            }
        }

        return guardado
    }
}