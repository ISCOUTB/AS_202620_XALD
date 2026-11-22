package com.proyecto.xald

import com.proyecto.xald.aigemini.CategoriaResultado
import com.proyecto.xald.aigemini.ServicioCategorizacion
import com.proyecto.xald.corefinanciero.CategoriasEspeciales
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import com.proyecto.xald.corefinanciero.TipoMovimiento
import com.proyecto.xald.parser.ResultadoParseo
import com.proyecto.xald.parser.ServicioParser
import com.proyecto.xald.syncqueue.ColaSincronizacionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Caso de uso principal de ingesta vertical.
 * Conecta los 4 módulos aislados del sistema.
 */
class ProcesarNotificacionUseCase(
    private val parser: ServicioParser,
    private val categorizador: ServicioCategorizacion,
    private val coreFinanciero: InformacionFinanciera,
    private val syncQueue: ColaSincronizacionService
) {

    /**
     * Devuelve `true` solo si el SMS era un movimiento reconocido (compra, ingreso o transferencia)
     * y NUEVO (se guardó y se encoló).
     * Devuelve `false` si el remitente no está autorizado, el formato no se reconoce, el monto
     * es inválido o el mismo SMS ya se había procesado.
     */
    suspend fun ejecutar(remitente: String, textoSms: String, timestamp: Long = System.currentTimeMillis()): Boolean {
        return withContext(Dispatchers.IO) {
            // 1. Parsing y validación por Allowlist en :parser
            val resultadoParseo = parser.parsearSms(remitente, textoSms, timestamp)
            if (resultadoParseo !is ResultadoParseo.Exitoso) {
                return@withContext false
            }

            val datos = resultadoParseo.datos

            // 2. Categorización aislada (ACL) en :aigemini. Solo los gastos pasan por el
            //    categorizador: un ingreso o una transferencia no es una compra en un comercio.
            val tipo = TipoMovimiento.desdeCodigo(datos.tipoMovimiento)
            val categoriaFinal = if (tipo == TipoMovimiento.GASTO) {
                when (val resultadoIa = categorizador.categorizarComercio(datos.comercio)) {
                    is CategoriaResultado.Exitosa -> resultadoIa.categoria
                    is CategoriaResultado.Fallo -> resultadoIa.categoriaFallback
                }
            } else {
                CategoriasEspeciales.paraTipo(tipo, CategoriasEspeciales.SIN_CATEGORIZAR)
            }

            // 3. Persistencia durable primaria en :corefinanciero (SQLite).
            // El id sale del propio SMS: si Android entrega el mismo mensaje dos veces, el
            // segundo guardado se rechaza y no se crea una transacción duplicada.
            val idTransaccion = idDeterminista(remitente, timestamp, textoSms)
            val guardado = coreFinanciero.guardarTransaccionCompleta(
                monto = datos.monto,
                comercio = datos.comercio,
                moneda = datos.moneda,
                categoria = categoriaFinal,
                origenDatos = datos.origenDatos,
                fechaTimestamp = datos.fechaTimestamp,
                idTransaccion = idTransaccion,
                tipoMovimiento = tipo.name
            )
            if (!guardado) {
                return@withContext false
            }

            // 4. Encolado de sincronización en :syncqueue, con el mismo id que se guardó.
            syncQueue.encolarTransaccion(
                PayloadSincronizacionDTO(
                    idTransaccion = idTransaccion,
                    monto = datos.monto,
                    comercio = datos.comercio,
                    fechaTimestamp = datos.fechaTimestamp,
                    moneda = datos.moneda,
                    categoria = categoriaFinal,
                    origenDatos = datos.origenDatos
                )
            )

            true
        }
    }

    private fun idDeterminista(remitente: String, timestamp: Long, texto: String): String =
        UUID.nameUUIDFromBytes("${remitente.trim()}|$timestamp|$texto".toByteArray(Charsets.UTF_8)).toString()
}
