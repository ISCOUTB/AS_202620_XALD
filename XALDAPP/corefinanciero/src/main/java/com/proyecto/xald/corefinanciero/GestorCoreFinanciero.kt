package com.proyecto.xald.corefinanciero

internal class GestorCoreFinanciero(
    private val dao: TransaccionDao,
    private val planificacion: PlanificacionDao
) : InformacionFinanciera {

    override fun guardarTransaccion(monto: Double, comercio: String): Boolean {
        return guardarTransaccionCompleta(monto = monto, comercio = comercio)
    }

    override fun guardarTransaccionCompleta(
        monto: Double,
        comercio: String,
        moneda: String,
        categoria: String,
        origenDatos: String,
        fechaTimestamp: Long,
        idTransaccion: String,
        tipoMovimiento: String
    ): Boolean {
        // NaN <= 0.0 es false, por eso se comprueba aparte que el monto sea finito.
        if (monto <= 0.0 || !monto.isFinite()) return false
        if (idTransaccion.isBlank()) return false

        val nuevaEntidad = TransaccionEntidad(
            id = idTransaccion,
            monto = monto,
            moneda = moneda,
            comercio = comercio,
            categoria = categoria,
            fechaTimestamp = fechaTimestamp,
            origenDatos = origenDatos,
            sincronizado = false,
            // Se guarda normalizado: un código desconocido queda como GASTO.
            tipoMovimiento = TipoMovimiento.desdeCodigo(tipoMovimiento).name
        )
        // insertar() devuelve -1 si el id ya existe (OnConflictStrategy.IGNORE).
        return dao.insertar(nuevaEntidad) != -1L
    }

    override fun obtenerUltimaTransaccion(): TransaccionEntidad? {
        return dao.obtenerUltimaTransaccion()
    }

    override fun obtenerTransaccion(idTransaccion: String): TransaccionEntidad? {
        return dao.obtenerPorId(idTransaccion)
    }

    override fun obtenerPendientesSincronizacion(): List<PayloadSincronizacionDTO> {
        return dao.obtenerPendientesSincronizacion().map { entidad ->
            PayloadSincronizacionDTO(
                idTransaccion = entidad.id,
                monto = entidad.monto,
                moneda = entidad.moneda,
                comercio = entidad.comercio,
                categoria = entidad.categoria,
                fechaTimestamp = entidad.fechaTimestamp,
                origenDatos = entidad.origenDatos
            )
        }
    }

    override fun obtenerHistorial(): List<TransaccionEntidad> {
        return dao.obtenerTodas()
    }

    override fun obtenerSaldoTotal(): Double {
        return dao.obtenerSaldoTotal()
    }

    override fun obtenerConsolidado(): Consolidado {
        return AnaliticaFinanciera.consolidar(dao.obtenerTodas(), planificacion.obtenerSaldoBase())
    }

    override fun marcarComoSincronizada(idTransaccion: String): Boolean {
        return dao.marcarComoSincronizado(idTransaccion) > 0
    }

    override fun actualizarCategoria(idTransaccion: String, categoria: String): Boolean {
        val limpia = categoria.trim()
        if (limpia.isEmpty()) return false
        return dao.actualizarCategoria(idTransaccion, limpia) > 0
    }

    override fun reclasificarMovimiento(idTransaccion: String, tipoMovimiento: String, categoria: String): Boolean {
        val tipo = TipoMovimiento.desdeCodigo(tipoMovimiento)
        val categoriaFinal = CategoriasEspeciales.paraTipo(tipo, categoria)
        return dao.reclasificar(idTransaccion, tipo.name, categoriaFinal) > 0
    }

    override fun obtenerSaldoBase(): SaldoBaseEntidad? {
        return planificacion.obtenerSaldoBase()
    }

    override fun establecerSaldoBase(monto: Double, fechaTimestamp: Long): Boolean {
        if (!monto.isFinite()) return false
        planificacion.guardarSaldoBase(SaldoBaseEntidad(monto = monto, fechaTimestamp = fechaTimestamp))
        return true
    }

    override fun obtenerPresupuestos(): List<PresupuestoEntidad> {
        return planificacion.obtenerPresupuestos()
    }

    override fun guardarPresupuesto(presupuesto: PresupuestoEntidad): Boolean {
        val categoria = presupuesto.categoria.trim()
        if (categoria.isEmpty()) return false
        if (presupuesto.limite <= 0.0 || !presupuesto.limite.isFinite()) return false
        planificacion.guardarPresupuesto(
            presupuesto.copy(
                categoria = categoria,
                periodo = PeriodoPresupuesto.desdeCodigo(presupuesto.periodo).name
            )
        )
        return true
    }

    override fun eliminarPresupuesto(categoria: String): Boolean {
        return planificacion.eliminarPresupuesto(categoria.trim()) > 0
    }
}
