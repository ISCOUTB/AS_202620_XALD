package com.proyecto.xald.corefinanciero

import android.content.Context
import androidx.room.Room
import java.util.UUID

/**
 * Contrato que expone el Núcleo Financiero a otros módulos (:app, :syncqueue).
 *
 * Las operaciones son síncronas (la base permite consultas en el hilo principal porque son
 * pequeñas), pero `:app` las llama desde `Dispatchers.IO` cuando puede.
 */
interface InformacionFinanciera {
    fun guardarTransaccion(monto: Double, comercio: String): Boolean

    /**
     * Guarda una transacción. Devuelve `false` si el monto no es válido (<= 0 o no finito) o si
     * ya existe una transacción con el mismo [idTransaccion] (duplicado: no se sobrescribe).
     *
     * Quien ingiere un SMS debe pasar un [idTransaccion] determinista (derivado del SMS) para que
     * una doble entrega del mismo mensaje no cree dos transacciones.
     */
    fun guardarTransaccionCompleta(
        monto: Double,
        comercio: String,
        moneda: String = "COP",
        categoria: String = "Sin Categorizar",
        origenDatos: String = "SMS_REGEX",
        fechaTimestamp: Long = System.currentTimeMillis(),
        idTransaccion: String = UUID.randomUUID().toString()
    ): Boolean

    fun obtenerUltimaTransaccion(): TransaccionEntidad?
    fun obtenerPendientesSincronizacion(): List<PayloadSincronizacionDTO>
    fun obtenerHistorial(): List<TransaccionEntidad>

    /** Suma de los montos registrados (hoy solo hay compras, así que equivale al total gastado). */
    fun obtenerSaldoTotal(): Double

    fun marcarComoSincronizada(idTransaccion: String): Boolean

    /** Corrección manual de categoría. `false` si el id no existe o la categoría viene vacía. */
    fun actualizarCategoria(idTransaccion: String, categoria: String): Boolean
}

private val bloqueoInstancia = Any()

@Volatile
private var instanciaArchivo: InformacionFinanciera? = null

/**
 * Fábrica pública del servicio del Núcleo Financiero.
 *
 * La versión con archivo es un singleton del proceso: `MainActivity`, el receptor de SMS y el
 * Worker comparten UNA sola conexión Room. Antes cada llamada abría una base nueva sobre el
 * mismo archivo y nunca la cerraba (una por cada SMS recibido).
 *
 * La versión en memoria ([usarBaseDatosEnMemoria] = true) es para pruebas y devuelve una
 * instancia nueva en cada llamada.
 */
fun crearInformacionFinanciera(
    context: Context,
    usarBaseDatosEnMemoria: Boolean = false
): InformacionFinanciera {
    if (usarBaseDatosEnMemoria) {
        val db = Room.inMemoryDatabaseBuilder(context.applicationContext, CoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return GestorCoreFinanciero(db.transaccionDao())
    }

    return instanciaArchivo ?: synchronized(bloqueoInstancia) {
        instanciaArchivo ?: GestorCoreFinanciero(
            Room.databaseBuilder(context.applicationContext, CoreDatabase::class.java, "xald_core.db")
                .allowMainThreadQueries()
                .build()
                .transaccionDao()
        ).also { instanciaArchivo = it }
    }
}
