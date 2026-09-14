package com.proyecto.xald.corefinanciero

/**
 * Contrato que expone el Núcleo Financiero para que otros módulos (:app, :syncqueue)
 * consulten o soliciten operaciones sin acceder directamente a la base de datos.
 */
interface InformacionFinanciera {
    fun guardarTransaccion(monto: Double, comercio: String): Boolean
    fun obtenerUltimaTransaccion(): TransaccionEntidad?
    fun obtenerPendientesSincronizacion(): List<PayloadSincronizacionDTO>
}
fun crearInformacionFinanciera(): InformacionFinanciera = GestorCoreFinanciero()
