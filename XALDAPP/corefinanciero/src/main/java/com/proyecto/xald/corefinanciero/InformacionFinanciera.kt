package com.proyecto.xald.corefinanciero

import android.content.Context
import androidx.room.Room
import java.util.UUID

/**
 * Contrato que expone el Núcleo Financiero a otros módulos (:app, :syncqueue).
 *
 * Las operaciones son síncronas (la base permite consultas en el hilo principal porque son
 * pequeñas), pero `:app` las llama desde un hilo de fondo cuando puede.
 */
interface InformacionFinanciera {
    fun guardarTransaccion(monto: Double, comercio: String): Boolean

    /**
     * Guarda una transacción. Devuelve `false` si el monto no es válido (<= 0 o no finito) o si
     * ya existe una transacción con el mismo [idTransaccion] (duplicado: no se sobrescribe).
     *
     * Quien ingiere un SMS debe pasar un [idTransaccion] determinista (derivado del SMS) para que
     * una doble entrega del mismo mensaje no cree dos transacciones.
     *
     * El monto siempre es positivo; [tipoMovimiento] (código de [TipoMovimiento]) decide si suma
     * o resta al saldo.
     */
    fun guardarTransaccionCompleta(
        monto: Double,
        comercio: String,
        moneda: String = "COP",
        categoria: String = "Sin Categorizar",
        origenDatos: String = "SMS_REGEX",
        fechaTimestamp: Long = System.currentTimeMillis(),
        idTransaccion: String = UUID.randomUUID().toString(),
        tipoMovimiento: String = TipoMovimiento.GASTO.name
    ): Boolean

    fun obtenerUltimaTransaccion(): TransaccionEntidad?
    fun obtenerTransaccion(idTransaccion: String): TransaccionEntidad?
    fun obtenerPendientesSincronizacion(): List<PayloadSincronizacionDTO>

    /** Todas las transacciones, de la más reciente a la más antigua. */
    fun obtenerHistorial(): List<TransaccionEntidad>

    /**
     * Suma de los montos registrados, sin importar su tipo. Se conserva por compatibilidad;
     * para el dinero disponible usa [obtenerConsolidado].
     */
    fun obtenerSaldoTotal(): Double

    /** Saldo disponible = saldo base + entradas - salidas posteriores a la base. */
    fun obtenerConsolidado(): Consolidado

    fun marcarComoSincronizada(idTransaccion: String): Boolean

    /** Corrección manual de categoría. `false` si el id no existe o la categoría viene vacía. */
    fun actualizarCategoria(idTransaccion: String, categoria: String): Boolean

    /**
     * Corrección manual del tipo y la categoría (por ejemplo, un "pago" que en realidad fue una
     * transferencia). Para tipos que no son gasto la categoría se fija sola (Ingresos /
     * Transferencias). `false` si el id no existe.
     */
    fun reclasificarMovimiento(idTransaccion: String, tipoMovimiento: String, categoria: String): Boolean

    // ------------------------------------------------------------------ Saldo base (ajuste manual)

    fun obtenerSaldoBase(): SaldoBaseEntidad?

    /**
     * El usuario dice "hoy tengo [monto]". Desde [fechaTimestamp] el saldo disponible parte de
     * ese valor. Acepta negativos (sobregiro). `false` si el monto no es finito.
     */
    fun establecerSaldoBase(monto: Double, fechaTimestamp: Long = System.currentTimeMillis()): Boolean

    // ------------------------------------------------------------------------------ Presupuestos

    fun obtenerPresupuestos(): List<PresupuestoEntidad>

    /** Crea o reemplaza el presupuesto de la categoría. `false` si el límite no es > 0 y finito. */
    fun guardarPresupuesto(presupuesto: PresupuestoEntidad): Boolean

    fun eliminarPresupuesto(categoria: String): Boolean
}

private val bloqueoInstancia = Any()

@Volatile
private var instanciaArchivo: InformacionFinanciera? = null

internal const val NOMBRE_BASE_CORE = "xald_core.db"

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
        return GestorCoreFinanciero(db.transaccionDao(), db.planificacionDao())
    }

    return instanciaArchivo ?: synchronized(bloqueoInstancia) {
        instanciaArchivo ?: abrirBaseCore(context, NOMBRE_BASE_CORE).let { db ->
            GestorCoreFinanciero(db.transaccionDao(), db.planificacionDao())
        }.also { instanciaArchivo = it }
    }
}

/**
 * Abre la base en archivo con sus migraciones. Separada para poder probar la migración.
 *
 * En la app siempre va cifrada con SQLCipher ([CifradoBaseLocal]). [cifrada] = false existe solo
 * para las pruebas con Robolectric, donde no hay librería nativa ni Android Keystore.
 */
internal fun abrirBaseCore(context: Context, nombreArchivo: String, cifrada: Boolean = true): CoreDatabase {
    val builder = Room.databaseBuilder(context.applicationContext, CoreDatabase::class.java, nombreArchivo)
        .addMigrations(MIGRACION_CORE_1_2)
        .allowMainThreadQueries()
    if (cifrada) builder.openHelperFactory(CifradoBaseLocal.factoria(context, nombreArchivo))
    return builder.build()
}
