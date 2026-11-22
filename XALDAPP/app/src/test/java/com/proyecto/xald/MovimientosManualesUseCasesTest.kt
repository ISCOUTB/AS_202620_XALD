package com.proyecto.xald

import com.proyecto.xald.corefinanciero.AnaliticaFinanciera
import com.proyecto.xald.corefinanciero.CategoriasEspeciales
import com.proyecto.xald.corefinanciero.Consolidado
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.corefinanciero.PayloadSincronizacionDTO
import com.proyecto.xald.corefinanciero.PresupuestoEntidad
import com.proyecto.xald.corefinanciero.SaldoBaseEntidad
import com.proyecto.xald.corefinanciero.TipoMovimiento
import com.proyecto.xald.corefinanciero.TransaccionEntidad
import com.proyecto.xald.syncqueue.ColaSincronizacionService
import com.proyecto.xald.syncqueue.MensajeSincronizacion
import com.proyecto.xald.syncqueue.ResumenProcesamiento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pruebas JVM puras con dobles en memoria (sin Room ni Robolectric). */
class MovimientosManualesUseCasesTest {

    private val core = CoreEnMemoria()
    private val cola = ColaEnMemoria()
    private val registrar = RegistrarMovimientoManualUseCase(core, cola, generarId = { "manual-1" })

    @Test
    fun gastoManual_seGuardaConOrigenManual_ySeEncola() {
        val resultado = registrar.ejecutar(25_000.0, " Almuerzo ", TipoMovimiento.GASTO, "Restaurante", 1_000L)

        assertEquals(RegistrarMovimientoManualUseCase.Resultado.Guardado("manual-1"), resultado)
        val guardado = core.transacciones.single()
        assertEquals("Almuerzo", guardado.comercio)
        assertEquals("Restaurante", guardado.categoria)
        assertEquals("MANUAL", guardado.origenDatos)
        assertEquals("GASTO", guardado.tipoMovimiento)
        assertEquals("manual-1", cola.encolados.single().idTransaccion)
        assertEquals("MANUAL", cola.encolados.single().origenDatos)
    }

    @Test
    fun ingresoSinDescripcion_usaElTipoComoNombre_yCategoriaIngresos() {
        registrar.ejecutar(1_000_000.0, "", TipoMovimiento.INGRESO, "Restaurante", 1_000L)
        val guardado = core.transacciones.single()
        assertEquals("Ingreso", guardado.comercio)
        assertEquals(CategoriasEspeciales.INGRESOS, guardado.categoria)
    }

    @Test
    fun montoInvalido_noGuardaNiEncola() {
        for (monto in listOf(null, 0.0, -5.0, Double.NaN)) {
            assertTrue(registrar.ejecutar(monto, "x", TipoMovimiento.GASTO, "", 1L) is RegistrarMovimientoManualUseCase.Resultado.Invalido)
        }
        assertTrue(core.transacciones.isEmpty())
        assertTrue(cola.encolados.isEmpty())
    }

    @Test
    fun reclasificar_actualizaElCore_yReencolaConLaCategoriaNueva() {
        registrar.ejecutar(50_000.0, "JUAN PEREZ", TipoMovimiento.GASTO, "Servicios", 1_000L)
        cola.encolados.clear()

        val ok = ReclasificarMovimientoUseCase(core, cola).ejecutar("manual-1", TipoMovimiento.TRANSFERENCIA_ENVIADA, "Servicios")

        assertTrue(ok)
        assertEquals("TRANSFERENCIA_ENVIADA", core.transacciones.single().tipoMovimiento)
        assertEquals(CategoriasEspeciales.TRANSFERENCIAS, cola.encolados.single().categoria)
    }

    @Test
    fun reclasificar_idInexistente_devuelveFalse() {
        assertFalse(ReclasificarMovimientoUseCase(core, cola).ejecutar("nada", TipoMovimiento.GASTO, "Salud"))
        assertTrue(cola.encolados.isEmpty())
    }

    // ------------------------------------------------------------------------------ Dobles

    private class CoreEnMemoria : InformacionFinanciera {
        val transacciones = mutableListOf<TransaccionEntidad>()
        private var base: SaldoBaseEntidad? = null

        override fun guardarTransaccion(monto: Double, comercio: String) = guardarTransaccionCompleta(monto, comercio)

        override fun guardarTransaccionCompleta(
            monto: Double, comercio: String, moneda: String, categoria: String, origenDatos: String,
            fechaTimestamp: Long, idTransaccion: String, tipoMovimiento: String
        ): Boolean {
            if (monto <= 0.0 || transacciones.any { it.id == idTransaccion }) return false
            transacciones += TransaccionEntidad(
                id = idTransaccion, monto = monto, moneda = moneda, comercio = comercio, categoria = categoria,
                fechaTimestamp = fechaTimestamp, origenDatos = origenDatos, tipoMovimiento = tipoMovimiento
            )
            return true
        }

        override fun obtenerUltimaTransaccion() = transacciones.maxByOrNull { it.fechaTimestamp }
        override fun obtenerTransaccion(idTransaccion: String) = transacciones.firstOrNull { it.id == idTransaccion }
        override fun obtenerPendientesSincronizacion(): List<PayloadSincronizacionDTO> = emptyList()
        override fun obtenerHistorial() = transacciones.sortedByDescending { it.fechaTimestamp }
        override fun obtenerSaldoTotal() = transacciones.sumOf { it.monto }
        override fun obtenerConsolidado(): Consolidado = AnaliticaFinanciera.consolidar(transacciones, base)
        override fun marcarComoSincronizada(idTransaccion: String) = false
        override fun actualizarCategoria(idTransaccion: String, categoria: String) = false

        override fun reclasificarMovimiento(idTransaccion: String, tipoMovimiento: String, categoria: String): Boolean {
            val i = transacciones.indexOfFirst { it.id == idTransaccion }
            if (i < 0) return false
            val tipo = TipoMovimiento.desdeCodigo(tipoMovimiento)
            transacciones[i] = transacciones[i].copy(tipoMovimiento = tipo.name, categoria = CategoriasEspeciales.paraTipo(tipo, categoria))
            return true
        }

        override fun obtenerSaldoBase() = base
        override fun establecerSaldoBase(monto: Double, fechaTimestamp: Long): Boolean {
            base = SaldoBaseEntidad(monto = monto, fechaTimestamp = fechaTimestamp)
            return true
        }
        override fun obtenerPresupuestos(): List<PresupuestoEntidad> = emptyList()
        override fun guardarPresupuesto(presupuesto: PresupuestoEntidad) = false
        override fun eliminarPresupuesto(categoria: String) = false
    }

    private class ColaEnMemoria : ColaSincronizacionService {
        val encolados = mutableListOf<PayloadSincronizacionDTO>()
        override fun encolarTransaccion(payload: PayloadSincronizacionDTO): Boolean {
            encolados += payload
            return true
        }
        override fun obtenerCantidadPendientes() = encolados.size
        override fun obtenerSiguienteParaSincronizar() = encolados.firstOrNull()
        override fun procesarPendientes() = ResumenProcesamiento()
        override fun obtenerFallidos(): List<MensajeSincronizacion> = emptyList()
        override fun reintentarFallido(idCorrelacion: String) = false
    }
}
