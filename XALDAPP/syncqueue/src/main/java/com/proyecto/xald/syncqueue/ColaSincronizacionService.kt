package com.proyecto.xald.syncqueue

import android.content.Context
import androidx.room.Room
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import java.util.UUID

/**
 * Contrato público para administrar la cola de transacciones pendientes hacia el Backend XALD.
 */
interface ColaSincronizacionService {
    fun encolarTransaccion(payload: PayloadSincronizacionDTO): Boolean
    fun obtenerCantidadPendientes(): Int
    fun obtenerSiguienteParaSincronizar(): PayloadSincronizacionDTO?
    fun procesarPendientes(): ResumenProcesamiento
    fun obtenerFallidos(): List<MensajeSincronizacion>
    fun reintentarFallido(idCorrelacion: String): Boolean
}

data class ResumenProcesamiento(
    val confirmados: Int = 0,
    val reprogramados: Int = 0,
    val aFallidos: Int = 0,
    val sinConexion: Boolean = false
)

/**
 * Fábrica pública del servicio.
 */
fun crearColaSincronizacionService(
    context: Context,
    transporte: TransporteSincronizacion = TransporteNoConfigurado,
    politica: PoliticaReintentos = PoliticaReintentos(),
    reloj: () -> Long = { System.currentTimeMillis() },
    registro: RegistroSincronizacion = RegistroJsonConsola,
    generarIdCorrelacion: () -> String = { UUID.randomUUID().toString() },
    usarBaseDatosEnMemoria: Boolean = false, // <-- BANDERA AÑADIDA
    alConfirmar: (PayloadSincronizacionDTO) -> Unit = {}
): ColaSincronizacionService {

    // Si es un test, usa memoria RAM. Si es la app real, usa el archivo .db (con migración 1 -> 2)
    val builder = if (usarBaseDatosEnMemoria) {
        Room.inMemoryDatabaseBuilder(context.applicationContext, SyncDatabase::class.java)
    } else {
        Room.databaseBuilder(context.applicationContext, SyncDatabase::class.java, "xald_syncqueue.db")
            .addMigrations(MIGRACION_1_2)
    }

    val db = builder.allowMainThreadQueries().build()

    return ColaSincronizacion(
        dao = db.mensajeDao(),
        transporte = transporte,
        politica = politica,
        reloj = reloj,
        registro = registro,
        generarIdCorrelacion = generarIdCorrelacion,
        alConfirmar = alConfirmar
    )
}