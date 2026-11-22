package com.proyecto.xald

import com.proyecto.xald.corefinanciero.CategoriasEspeciales
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.corefinanciero.OrigenMovimiento
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import com.proyecto.xald.corefinanciero.TipoMovimiento
import com.proyecto.xald.corefinanciero.TransaccionEntidad
import com.proyecto.xald.syncqueue.ColaSincronizacionService
import java.util.UUID

/**
 * Registra un movimiento que el usuario escribe a mano (efectivo, un SMS que no llegó, etc.).
 * Igual que un SMS: primero se guarda en el teléfono (Local-First) y luego se encola para el
 * backend con origen MANUAL.
 */
class RegistrarMovimientoManualUseCase(
    private val core: InformacionFinanciera,
    private val cola: ColaSincronizacionService?,
    private val generarId: () -> String = { UUID.randomUUID().toString() }
) {

    sealed interface Resultado {
        data class Guardado(val idTransaccion: String) : Resultado
        data class Invalido(val motivo: String) : Resultado
    }

    fun ejecutar(
        monto: Double?,
        descripcion: String,
        tipo: TipoMovimiento,
        categoria: String,
        fechaMs: Long
    ): Resultado {
        if (monto == null || !monto.isFinite() || monto <= 0.0) {
            return Resultado.Invalido("Escribe un monto mayor que cero.")
        }
        val categoriaFinal = CategoriasEspeciales.paraTipo(tipo, categoria)
        // El backend exige un comercio no vacío: sin descripción se usa la categoría o el tipo.
        val nombre = descripcion.trim().ifEmpty {
            if (tipo == TipoMovimiento.GASTO && categoriaFinal != CategoriasEspeciales.SIN_CATEGORIZAR) categoriaFinal else tipo.etiqueta
        }
        val id = generarId()
        val guardado = core.guardarTransaccionCompleta(
            monto = monto,
            comercio = nombre,
            moneda = "COP",
            categoria = categoriaFinal,
            origenDatos = OrigenMovimiento.CODIGO_MANUAL,
            fechaTimestamp = fechaMs,
            idTransaccion = id,
            tipoMovimiento = tipo.name
        )
        if (!guardado) return Resultado.Invalido("No se pudo guardar el movimiento.")

        cola?.encolarTransaccion(payloadDe(id, monto, nombre, fechaMs, categoriaFinal, OrigenMovimiento.CODIGO_MANUAL))
        return Resultado.Guardado(id)
    }
}

/**
 * Corrección manual del tipo y la categoría de cualquier movimiento, para que las estadísticas
 * reflejen la realidad (por ejemplo, uno que quedó "Sin Categorizar").
 */
class ReclasificarMovimientoUseCase(
    private val core: InformacionFinanciera,
    private val cola: ColaSincronizacionService?
) {

    fun ejecutar(idTransaccion: String, tipo: TipoMovimiento, categoria: String): Boolean {
        if (!core.reclasificarMovimiento(idTransaccion, tipo.name, categoria)) return false
        val actualizada: TransaccionEntidad = core.obtenerTransaccion(idTransaccion) ?: return true
        // Se vuelve a encolar con la categoría nueva. Hoy el backend toma un reenvío con la misma
        // fecha como duplicado (ADR-0015, punto abierto "propagar ediciones"); la corrección
        // queda en el teléfono, que es la fuente de verdad de las estadísticas.
        cola?.encolarTransaccion(
            payloadDe(
                actualizada.id, actualizada.monto, actualizada.comercio, actualizada.fechaTimestamp,
                actualizada.categoria, actualizada.origenDatos
            )
        )
        return true
    }
}

private fun payloadDe(
    id: String,
    monto: Double,
    comercio: String,
    fechaMs: Long,
    categoria: String,
    origen: String
) = PayloadSincronizacionDTO(
    idTransaccion = id,
    monto = monto,
    comercio = comercio,
    fechaTimestamp = fechaMs,
    moneda = "COP",
    categoria = categoria,
    origenDatos = origen
)
