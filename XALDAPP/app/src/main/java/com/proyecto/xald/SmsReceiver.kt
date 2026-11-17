package com.proyecto.xald

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.proyecto.xald.aigemini.crearCategorizadorLocal
import com.proyecto.xald.corefinanciero.crearInformacionFinanciera
import com.proyecto.xald.parser.crearServicioParser
import com.proyecto.xald.syncqueue.ColaSincronizacionProvider
import com.proyecto.xald.syncqueue.ProgramadorSincronizacion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Un fragmento de SMS tal como lo entrega Android. */
internal data class ParteSms(val remitente: String, val cuerpo: String, val timestampMs: Long)

/** Un SMS completo, con todos sus fragmentos unidos. */
internal data class SmsCompleto(val remitente: String, val cuerpo: String, val timestampMs: Long)

/**
 * Los SMS largos llegan partidos en varios fragmentos dentro del mismo Intent. Procesarlos por
 * separado rompe el parseo (el monto queda en un fragmento y el comercio en otro), así que se
 * unen por remitente, en orden, antes de parsear.
 */
internal fun unirPartesSms(partes: List<ParteSms>): List<SmsCompleto> {
    val porRemitente = LinkedHashMap<String, MutableList<ParteSms>>()
    for (parte in partes) {
        porRemitente.getOrPut(parte.remitente) { mutableListOf() }.add(parte)
    }
    return porRemitente.map { (remitente, lista) ->
        SmsCompleto(
            remitente = remitente,
            cuerpo = lista.joinToString(separator = "") { it.cuerpo },
            timestampMs = lista.first().timestampMs
        )
    }
}

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val appContext = context.applicationContext

        // Sin autorización informada del usuario no se procesa ningún SMS (RL-01).
        if (!ConsentimientoXald.aceptado(appContext)) return

        val fragmentos = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val mensajes = unirPartesSms(
            fragmentos.mapNotNull { sms ->
                val remitente = sms.originatingAddress ?: return@mapNotNull null
                ParteSms(remitente, sms.messageBody ?: "", sms.timestampMillis)
            }
        )
        if (mensajes.isEmpty()) return

        // XaldApplication instala la cola antes de que exista cualquier receiver del proceso.
        val cola = ColaSincronizacionProvider.obtener() ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Persistencia inmediata (< 5 ms) usando reglas locales sin bloquear por red.
                val useCase = ProcesarNotificacionUseCase(
                    parser = crearServicioParser(),
                    categorizador = crearCategorizadorLocal(),
                    coreFinanciero = crearInformacionFinanciera(appContext),
                    syncQueue = cola
                )

                var hayNuevas = false
                for (sms in mensajes) {
                    if (useCase.ejecutar(sms.remitente, sms.cuerpo, sms.timestampMs)) hayNuevas = true
                }

                if (hayNuevas) {
                    // 1. Sube la transacción de inmediato a la nube.
                    ProgramadorSincronizacion.solicitarEnvioInmediato(appContext)
                    // 2. Programa a Gemini en segundo plano para las que quedaron "Sin Categorizar".
                    CategorizacionIaWorker.programar(appContext)
                    // 3. Refresca la UI al instante.
                    NotificadorCambios.avisar()
                }
            } catch (e: Exception) {
                // Nunca se registra el contenido del SMS: es un dato personal.
                Log.e("XALD", "Error procesando un SMS (${e.javaClass.simpleName})")
            } finally {
                pendingResult.finish()
            }
        }
    }
}