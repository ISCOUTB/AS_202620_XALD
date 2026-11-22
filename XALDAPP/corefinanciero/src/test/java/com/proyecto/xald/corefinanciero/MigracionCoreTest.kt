package com.proyecto.xald.corefinanciero

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Base de datos v1 → v2 y operaciones nuevas del Núcleo Financiero (saldo base, presupuestos,
 * tipo de movimiento). Si la migración no reproduce exactamente el esquema que espera Room,
 * Room lanza "Migration didn't properly handle" al abrir la base y esta prueba falla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigracionCoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun migracion1a2_conservaLasTransacciones_yCreaLasTablasNuevas() {
        val nombre = "xald_core_migracion_test.db"
        context.deleteDatabase(nombre)

        // Base tal como la dejó la versión 1 de la app (sentencia que Room generaba para la v1).
        val v1 = context.openOrCreateDatabase(nombre, Context.MODE_PRIVATE, null)
        v1.execSQL(
            "CREATE TABLE IF NOT EXISTS `transacciones` (`id_transaccion` TEXT NOT NULL, " +
                "`monto` REAL NOT NULL, `moneda` TEXT NOT NULL, `comercio` TEXT NOT NULL, " +
                "`categoria` TEXT NOT NULL, `fecha_timestamp` INTEGER NOT NULL, " +
                "`origen_datos` TEXT NOT NULL, `sincronizado` INTEGER NOT NULL, PRIMARY KEY(`id_transaccion`))"
        )
        v1.execSQL(
            "INSERT INTO transacciones VALUES ('tx-viejo', 45900.0, 'COP', 'ARA', 'Supermercado', " +
                "1700000000000, 'SMS_REGEX', 1)"
        )
        v1.version = 1
        v1.close()

        val db = abrirBaseCore(context, nombre)
        try {
            // La primera consulta abre la base: corre la migración y Room valida el esquema.
            val viejo = db.transaccionDao().obtenerPorId("tx-viejo")
            assertNotNull(viejo)
            assertNull(viejo!!.tipoMovimiento)
            assertEquals(TipoMovimiento.GASTO, viejo.tipo)
            assertTrue(viejo.sincronizado)

            val gestor = GestorCoreFinanciero(db.transaccionDao(), db.planificacionDao())
            assertTrue(gestor.guardarPresupuesto(PresupuestoEntidad("Restaurante", 100_000.0)))
            assertTrue(gestor.establecerSaldoBase(1_000_000.0, 1_700_000_000_001L))
            assertEquals(1, gestor.obtenerPresupuestos().size)
            assertEquals(1_000_000.0, gestor.obtenerSaldoBase()!!.monto, 0.001)
        } finally {
            db.close()
            context.deleteDatabase(nombre)
        }
    }

    @Test
    fun guardarConTipo_yConsolidadoDesdeElSaldoBase() {
        val core = crearInformacionFinanciera(context, usarBaseDatosEnMemoria = true)
        assertTrue(core.establecerSaldoBase(500_000.0, fechaTimestamp = 1_000L))
        assertTrue(core.guardarTransaccionCompleta(100_000.0, "ARA", fechaTimestamp = 2_000L, idTransaccion = "g"))
        assertTrue(
            core.guardarTransaccionCompleta(
                40_000.0, "JUAN", categoria = CategoriasEspeciales.TRANSFERENCIAS, fechaTimestamp = 3_000L,
                idTransaccion = "t", tipoMovimiento = TipoMovimiento.TRANSFERENCIA_RECIBIDA.name
            )
        )
        // Anterior al saldo base: ya estaba reflejado en los $500.000.
        assertTrue(core.guardarTransaccionCompleta(999.0, "VIEJO", fechaTimestamp = 500L, idTransaccion = "v"))

        assertEquals(TipoMovimiento.TRANSFERENCIA_RECIBIDA, core.obtenerTransaccion("t")!!.tipo)
        assertEquals(440_000.0, core.obtenerConsolidado().saldoDisponible, 0.001)
    }

    @Test
    fun tipoDesconocido_seGuardaComoGasto() {
        val core = crearInformacionFinanciera(context, usarBaseDatosEnMemoria = true)
        assertTrue(core.guardarTransaccionCompleta(10.0, "X", idTransaccion = "x", tipoMovimiento = "LO_QUE_SEA"))
        assertEquals(TipoMovimiento.GASTO.name, core.obtenerTransaccion("x")!!.tipoMovimiento)
    }

    @Test
    fun presupuestos_validacionReemplazoYEliminacion() {
        val core = crearInformacionFinanciera(context, usarBaseDatosEnMemoria = true)
        assertFalse(core.guardarPresupuesto(PresupuestoEntidad("Restaurante", 0.0)))
        assertFalse(core.guardarPresupuesto(PresupuestoEntidad("Restaurante", Double.NaN)))
        assertFalse(core.guardarPresupuesto(PresupuestoEntidad("  ", 100.0)))

        assertTrue(core.guardarPresupuesto(PresupuestoEntidad("Restaurante", 100.0)))
        assertTrue(core.guardarPresupuesto(PresupuestoEntidad("Restaurante", 250.0, "semanal")))
        val unico = core.obtenerPresupuestos().single()
        assertEquals(250.0, unico.limite, 0.001)
        assertEquals(PeriodoPresupuesto.SEMANAL.name, unico.periodo)

        assertTrue(core.eliminarPresupuesto("Restaurante"))
        assertFalse(core.eliminarPresupuesto("Restaurante"))
        assertTrue(core.obtenerPresupuestos().isEmpty())
    }

    @Test
    fun saldoBase_rechazaNoFinito_yElUltimoReemplazaAlAnterior() {
        val core = crearInformacionFinanciera(context, usarBaseDatosEnMemoria = true)
        assertNull(core.obtenerSaldoBase())
        assertFalse(core.establecerSaldoBase(Double.POSITIVE_INFINITY))
        assertTrue(core.establecerSaldoBase(-20_000.0, 10L))
        assertTrue(core.establecerSaldoBase(300_000.0, 20L))
        assertEquals(300_000.0, core.obtenerSaldoBase()!!.monto, 0.001)
        assertEquals(20L, core.obtenerSaldoBase()!!.fechaTimestamp)
    }

    @Test
    fun reclasificar_cambiaTipoYCategoria() {
        val core = crearInformacionFinanciera(context, usarBaseDatosEnMemoria = true)
        assertTrue(core.guardarTransaccionCompleta(50_000.0, "JUAN PEREZ", categoria = "Servicios", idTransaccion = "r"))

        assertTrue(core.reclasificarMovimiento("r", TipoMovimiento.TRANSFERENCIA_ENVIADA.name, "Servicios"))
        val transferencia = core.obtenerTransaccion("r")!!
        assertEquals(TipoMovimiento.TRANSFERENCIA_ENVIADA, transferencia.tipo)
        assertEquals(CategoriasEspeciales.TRANSFERENCIAS, transferencia.categoria)

        assertTrue(core.reclasificarMovimiento("r", TipoMovimiento.GASTO.name, "Restaurante"))
        assertEquals("Restaurante", core.obtenerTransaccion("r")!!.categoria)
        assertFalse(core.reclasificarMovimiento("no-existe", TipoMovimiento.GASTO.name, "Restaurante"))
    }
}
