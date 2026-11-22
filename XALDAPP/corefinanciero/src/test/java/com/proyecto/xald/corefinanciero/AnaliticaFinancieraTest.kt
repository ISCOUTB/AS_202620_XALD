package com.proyecto.xald.corefinanciero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pruebas JVM puras (sin Robolectric) de la lógica financiera: consolidado, estadísticas,
 * presupuestos, alertas, semáforo, tarjeta flash y filtros del historial.
 *
 * "Ahora" es fijo: miércoles 18 de noviembre de 2026, 2:00 p. m. en Bogotá.
 */
class AnaliticaFinancieraTest {

    private val zona = ZoneId.of("America/Bogota")
    private val calendario = Calendario(zona)
    private val ahora = ms("2026-11-18T14:00")
    private var secuencia = 0

    private fun ms(fechaHora: String): Long =
        LocalDateTime.parse(fechaHora).atZone(zona).toInstant().toEpochMilli()

    private fun mov(
        monto: Double,
        fecha: String,
        tipo: TipoMovimiento = TipoMovimiento.GASTO,
        categoria: String = "Supermercado",
        comercio: String = "ARA",
        origen: String = "SMS_REGEX"
    ) = TransaccionEntidad(
        id = "tx-${secuencia++}",
        monto = monto,
        comercio = comercio,
        categoria = categoria,
        fechaTimestamp = ms(fecha),
        origenDatos = origen,
        tipoMovimiento = tipo.name
    )

    // ------------------------------------------------------------------------- Tipo y origen

    @Test
    fun tipo_filaAntiguaSinTipo_seLeeComoGasto() {
        val antigua = TransaccionEntidad(id = "v1", monto = 10.0, comercio = "x", fechaTimestamp = 0L)
        assertEquals(TipoMovimiento.GASTO, antigua.tipo)
        assertEquals(TipoMovimiento.GASTO, TipoMovimiento.desdeCodigo("algo raro"))
        assertEquals(TipoMovimiento.INGRESO, TipoMovimiento.desdeCodigo(" ingreso "))
    }

    @Test
    fun origen_smsEIaSonSms_manualEsManual() {
        assertEquals(OrigenMovimiento.SMS, OrigenMovimiento.desdeCodigo("SMS_REGEX"))
        assertEquals(OrigenMovimiento.SMS, OrigenMovimiento.desdeCodigo("AI_GEMINI"))
        assertEquals(OrigenMovimiento.MANUAL, OrigenMovimiento.desdeCodigo("manual"))
    }

    // ------------------------------------------------------------------------------ Consolidado

    @Test
    fun consolidado_sinSaldoBase_esEntradasMenosSalidas() {
        val movs = listOf(
            mov(1_000_000.0, "2026-11-01T08:00", TipoMovimiento.INGRESO),
            mov(200_000.0, "2026-11-02T08:00"),
            mov(50_000.0, "2026-11-03T08:00", TipoMovimiento.TRANSFERENCIA_ENVIADA),
            mov(30_000.0, "2026-11-04T08:00", TipoMovimiento.TRANSFERENCIA_RECIBIDA)
        )
        val c = AnaliticaFinanciera.consolidar(movs, null)
        assertEquals(780_000.0, c.saldoDisponible, 0.001)
        assertEquals(1_030_000.0, c.entradas, 0.001)
        assertEquals(250_000.0, c.salidas, 0.001)
    }

    @Test
    fun consolidado_conSaldoBase_soloCuentaLoPosterior() {
        val base = SaldoBaseEntidad(monto = 500_000.0, fechaTimestamp = ms("2026-11-10T12:00"))
        val movs = listOf(
            mov(999_999.0, "2026-11-09T08:00"),                           // ya reflejado en la base
            mov(100_000.0, "2026-11-11T08:00"),
            mov(40_000.0, "2026-11-12T08:00", TipoMovimiento.INGRESO)
        )
        val c = AnaliticaFinanciera.consolidar(movs, base)
        assertEquals(440_000.0, c.saldoDisponible, 0.001)
        assertEquals(base, c.saldoBase)
    }

    @Test
    fun consolidado_puedeQuedarNegativo() {
        val base = SaldoBaseEntidad(monto = 10_000.0, fechaTimestamp = ms("2026-11-10T12:00"))
        val c = AnaliticaFinanciera.consolidar(listOf(mov(25_000.0, "2026-11-11T08:00")), base)
        assertEquals(-15_000.0, c.saldoDisponible, 0.001)
    }

    // ----------------------------------------------------------------- Flujo y categorías

    @Test
    fun flujo_proporcionDeEntradas() {
        val rango = calendario.rango(Periodo.MES, ahora)
        val movs = listOf(
            mov(300.0, "2026-11-05T10:00", TipoMovimiento.INGRESO),
            mov(100.0, "2026-11-06T10:00"),
            mov(500.0, "2026-10-31T23:59")                                 // fuera del mes
        )
        val f = AnaliticaFinanciera.flujo(movs, rango)
        assertEquals(300.0, f.entradas, 0.001)
        assertEquals(100.0, f.salidas, 0.001)
        assertEquals(0.75, f.proporcionEntradas, 0.0001)
        assertEquals(200.0, f.neto, 0.001)
        assertEquals(0.0, ResumenFlujo(0.0, 0.0).proporcionEntradas, 0.0)
    }

    @Test
    fun gastosPorCategoria_ignoraTransferenciasEIngresos_yOrdenaDeMayorAMenor() {
        val rango = calendario.rango(Periodo.MES, ahora)
        val movs = listOf(
            mov(100.0, "2026-11-02T10:00", categoria = "Transporte"),
            mov(300.0, "2026-11-03T10:00", categoria = "Restaurante"),
            mov(100.0, "2026-11-04T10:00", categoria = "Transporte"),
            mov(9_000.0, "2026-11-05T10:00", TipoMovimiento.TRANSFERENCIA_ENVIADA, categoria = "Transferencias"),
            mov(9_000.0, "2026-11-06T10:00", TipoMovimiento.INGRESO, categoria = "Ingresos")
        )
        val totales = AnaliticaFinanciera.gastosPorCategoria(movs, rango)
        assertEquals(listOf("Restaurante", "Transporte"), totales.map { it.categoria })
        assertEquals(200.0, totales[1].total, 0.001)
        assertEquals(2, totales[1].cantidad)
        assertEquals(1.0, totales.sumOf { it.proporcion }, 0.0001)
    }

    // ------------------------------------------------------------------------------- Calendario

    @Test
    fun calendario_semanaEmpiezaElLunes() {
        val semana = calendario.rango(Periodo.SEMANA, ahora)
        assertEquals(ms("2026-11-16T00:00"), semana.inicioMs)
        assertEquals(ms("2026-11-23T00:00"), semana.finMs)
        assertTrue(ahora in semana)
        assertFalse(ms("2026-11-23T00:00") in semana)
    }

    @Test
    fun calendario_tramosDelMes_cubrenTodoElMes() {
        val tramos = calendario.tramos(Periodo.MES, ahora)
        assertEquals(listOf("1-7", "8-14", "15-21", "22-28", "29-30"), tramos.map { it.etiqueta })
        assertEquals(ms("2026-11-01T00:00"), tramos.first().rango.inicioMs)
        assertEquals(ms("2026-12-01T00:00"), tramos.last().rango.finMs)

        val febrero = calendario.tramos(Periodo.MES, ms("2026-02-10T10:00"))
        assertEquals(listOf("1-7", "8-14", "15-21", "22-28"), febrero.map { it.etiqueta })
    }

    @Test
    fun calendario_tramosDeSemanaYAnio() {
        assertEquals(Calendario.DIAS_CORTOS, calendario.tramos(Periodo.SEMANA, ahora).map { it.etiqueta })
        assertEquals(12, calendario.tramos(Periodo.ANIO, ahora).size)
    }

    @Test
    fun serie_marcaElTramoActual() {
        val serie = AnaliticaFinanciera.serie(
            listOf(mov(80.0, "2026-11-18T09:00"), mov(20.0, "2026-11-17T09:00", TipoMovimiento.INGRESO)),
            Periodo.SEMANA, ahora, calendario
        )
        assertEquals(7, serie.size)
        assertEquals("Mié", serie.single { it.esActual }.etiqueta)
        assertEquals(80.0, serie[2].salidas, 0.001)
        assertEquals(20.0, serie[1].entradas, 0.001)
    }

    // ----------------------------------------------------------------- Presupuestos y alertas

    @Test
    fun presupuesto_porCategoria_noDistingueMayusculas_yElTotalIgnoraTransferencias() {
        val movs = listOf(
            mov(400.0, "2026-11-03T10:00", categoria = "restaurante"),
            mov(500.0, "2026-11-04T10:00", categoria = "Transporte"),
            mov(9_000.0, "2026-11-05T10:00", TipoMovimiento.TRANSFERENCIA_ENVIADA),
            mov(700.0, "2026-10-28T10:00", categoria = "Restaurante")      // mes anterior
        )
        val presupuestos = listOf(
            PresupuestoEntidad("Restaurante", 1_000.0),
            PresupuestoEntidad(CategoriasEspeciales.PRESUPUESTO_TOTAL, 1_000.0)
        )
        val estados = AnaliticaFinanciera.estadoPresupuestos(movs, presupuestos, ahora, calendario)
        val total = estados.first()
        assertTrue("el global va primero", total.presupuesto.esTotal)
        assertEquals(900.0, total.gastado, 0.001)
        assertEquals(NivelPresupuesto.CRITICO, total.nivel)
        val restaurante = estados.single { it.presupuesto.categoria == "Restaurante" }
        assertEquals(400.0, restaurante.gastado, 0.001)
        assertEquals(NivelPresupuesto.NORMAL, restaurante.nivel)
    }

    @Test
    fun presupuestoSemanal_soloCuentaLaSemanaActual() {
        val movs = listOf(mov(300.0, "2026-11-16T10:00"), mov(300.0, "2026-11-15T10:00"))
        val estado = AnaliticaFinanciera.estadoPresupuestos(
            movs, listOf(PresupuestoEntidad("Supermercado", 1_000.0, PeriodoPresupuesto.SEMANAL.name)), ahora, calendario
        ).single()
        assertEquals(300.0, estado.gastado, 0.001)
        assertEquals(700.0, estado.restante, 0.001)
    }

    @Test
    fun nivelesDePresupuesto_enLosUmbrales() {
        fun nivel(gastado: Double) = EstadoPresupuesto(PresupuestoEntidad("X", 1_000.0), gastado, RangoTiempo(0, 1)).nivel
        assertEquals(NivelPresupuesto.NORMAL, nivel(799.0))
        assertEquals(NivelPresupuesto.ATENCION, nivel(800.0))
        assertEquals(NivelPresupuesto.CRITICO, nivel(900.0))
        assertEquals(NivelPresupuesto.EXCEDIDO, nivel(1_000.0))
    }

    @Test
    fun alertas_saltoDel70Al95_enviaUnaSolaAlertaDe90_yCubreEl80() {
        val estado = EstadoPresupuesto(PresupuestoEntidad("Restaurante", 1_000.0), 950.0, RangoTiempo(100, 200))
        val alertas = AnaliticaFinanciera.alertasNuevas(listOf(estado), emptySet())
        assertEquals(1, alertas.size)
        assertEquals(90, alertas[0].umbral)
        assertEquals(setOf("Restaurante|100|80", "Restaurante|100|90"), alertas[0].clavesCubiertas)
    }

    @Test
    fun alertas_yaEnviadas_noSeRepiten_peroElSiguienteUmbralSi() {
        val enviadas = setOf("Restaurante|100|80", "Restaurante|100|90")
        val al95 = EstadoPresupuesto(PresupuestoEntidad("Restaurante", 1_000.0), 950.0, RangoTiempo(100, 200))
        assertTrue(AnaliticaFinanciera.alertasNuevas(listOf(al95), enviadas).isEmpty())

        val al110 = al95.copy(gastado = 1_100.0)
        assertEquals(100, AnaliticaFinanciera.alertasNuevas(listOf(al110), enviadas).single().umbral)

        // Mes nuevo (otro inicio de rango): vuelve a avisar.
        val mesNuevo = al95.copy(rango = RangoTiempo(200, 300))
        assertEquals(90, AnaliticaFinanciera.alertasNuevas(listOf(mesNuevo), enviadas).single().umbral)
    }

    // ------------------------------------------------------------------------------- Semáforo

    @Test
    fun semaforo_sinMovimientos_esNeutral() {
        val s = AnaliticaFinanciera.semaforo(emptyList(), emptyList(), ahora, calendario)
        assertEquals(SaludFinanciera.SALUDABLE, s.salud)
        assertEquals("Sin datos todavía", s.titulo)
    }

    @Test
    fun semaforo_presupuestoExcedido_esRiesgo() {
        val movs = listOf(mov(1_200.0, "2026-11-03T10:00", categoria = "Restaurante"))
        val estados = AnaliticaFinanciera.estadoPresupuestos(movs, listOf(PresupuestoEntidad("Restaurante", 1_000.0)), ahora, calendario)
        val s = AnaliticaFinanciera.semaforo(movs, estados, ahora, calendario)
        assertEquals(SaludFinanciera.RIESGO, s.salud)
        assertTrue(s.detalle.contains("Restaurante"))
    }

    @Test
    fun semaforo_presupuestoAl85_esAtencion() {
        val movs = listOf(mov(850.0, "2026-11-03T10:00", categoria = "Restaurante"))
        val estados = AnaliticaFinanciera.estadoPresupuestos(movs, listOf(PresupuestoEntidad("Restaurante", 1_000.0)), ahora, calendario)
        val s = AnaliticaFinanciera.semaforo(movs, estados, ahora, calendario)
        assertEquals(SaludFinanciera.ATENCION, s.salud)
        assertTrue(s.detalle.contains("85%"))
    }

    @Test
    fun semaforo_ritmoMuyAltoContraElHistorial_esRiesgo() {
        // Octubre (31 días): 31.000 → 1.000 por día. Noviembre al día 18: 36.000 → 2.000 por día.
        val movs = listOf(mov(31_000.0, "2026-10-10T10:00"), mov(36_000.0, "2026-11-10T10:00"))
        val ritmo = AnaliticaFinanciera.ritmoDeGasto(movs, ahora, calendario)
        assertEquals(2.0, ritmo!!, 0.0001)
        assertEquals(SaludFinanciera.RIESGO, AnaliticaFinanciera.semaforo(movs, emptyList(), ahora, calendario).salud)
    }

    @Test
    fun semaforo_alInicioDelMes_noJuzgaElRitmo() {
        val movs = listOf(mov(31_000.0, "2026-10-10T10:00"), mov(36_000.0, "2026-11-02T10:00"))
        assertNull(AnaliticaFinanciera.ritmoDeGasto(movs, ms("2026-11-03T10:00"), calendario))
    }

    @Test
    fun semaforo_saleMasDeLoQueEntra_esAtencion() {
        val movs = listOf(
            mov(100.0, "2026-11-02T10:00", TipoMovimiento.INGRESO),
            mov(300.0, "2026-11-03T10:00", TipoMovimiento.TRANSFERENCIA_ENVIADA)
        )
        val s = AnaliticaFinanciera.semaforo(movs, emptyList(), ahora, calendario)
        assertEquals(SaludFinanciera.ATENCION, s.salud)
        assertEquals("Sale más de lo que entra", s.titulo)
    }

    @Test
    fun semaforo_todoNormal_esSaludable() {
        val movs = listOf(mov(30_000.0, "2026-10-10T10:00"), mov(16_000.0, "2026-11-10T10:00"))
        assertEquals(SaludFinanciera.SALUDABLE, AnaliticaFinanciera.semaforo(movs, emptyList(), ahora, calendario).salud)
    }

    // ---------------------------------------------------------------------------- Tarjeta flash

    @Test
    fun flash_comparaConLaSemanaPasadaHastaElMismoMomento() {
        val movs = listOf(
            mov(85.0, "2026-11-17T10:00"),     // esta semana (lun 16 → mié 18 14:00)
            mov(100.0, "2026-11-10T10:00"),    // semana pasada, antes del mismo momento
            mov(5_000.0, "2026-11-13T10:00")   // semana pasada, después del mismo momento: no cuenta
        )
        val flash = AnaliticaFinanciera.tarjetaFlash(movs, ahora, calendario)
        assertEquals("Has gastado un 15% menos que la semana pasada.", flash.texto)
    }

    @Test
    fun flash_variosSinCategoria_invitaAClasificarlos() {
        val movs = (1..3).map { mov(10.0, "2026-11-0${it}T10:00", categoria = "Sin Categorizar") }
        val flash = AnaliticaFinanciera.tarjetaFlash(movs, ahora, calendario)
        assertTrue(flash.texto.startsWith("Tienes 3 gastos sin categoría"))
        assertEquals(CategoriasEspeciales.SIN_CATEGORIZAR, flash.filtroSugerido)
    }

    @Test
    fun flash_categoriaPrincipalDelMes() {
        val movs = listOf(mov(700.0, "2026-11-02T10:00", categoria = "Transporte"), mov(300.0, "2026-11-03T10:00"))
        assertEquals(
            "Tu categoría principal este mes es Transporte (70% del gasto).",
            AnaliticaFinanciera.tarjetaFlash(movs, ahora, calendario).texto
        )
    }

    @Test
    fun flash_sinMovimientos_daLaBienvenida() {
        assertEquals("👋", AnaliticaFinanciera.tarjetaFlash(emptyList(), ahora, calendario).emoji)
    }

    // ------------------------------------------------------------------------------ Historial

    private val historial by lazy {
        listOf(
            mov(45_900.0, "2026-11-10T10:00", comercio = "Éxito Bocagrande"),
            mov(2_500_000.0, "2026-11-01T08:00", TipoMovimiento.INGRESO, categoria = "Ingresos", comercio = "Nómina", origen = "MANUAL"),
            mov(50_000.0, "2026-11-12T08:00", TipoMovimiento.TRANSFERENCIA_ENVIADA, categoria = "Transferencias", comercio = "JUAN PEREZ"),
            mov(12_000.0, "2026-11-15T08:00", categoria = "Transporte", comercio = "UBER", origen = "AI_GEMINI")
        )
    }

    @Test
    fun filtro_porTipoYOrigen() {
        val f = AnaliticaFinanciera
        assertEquals(listOf("UBER", "Éxito Bocagrande"), f.filtrar(historial, FiltroHistorial(tipo = FiltroTipo.GASTOS)).map { it.comercio })
        assertEquals(listOf("JUAN PEREZ"), f.filtrar(historial, FiltroHistorial(tipo = FiltroTipo.TRANSFERENCIAS)).map { it.comercio })
        assertEquals(listOf("Nómina"), f.filtrar(historial, FiltroHistorial(origen = FiltroOrigen.MANUAL)).map { it.comercio })
        assertEquals(3, f.filtrar(historial, FiltroHistorial(origen = FiltroOrigen.SMS)).size)
    }

    @Test
    fun filtro_textoSinTildesYPorMonto() {
        val f = AnaliticaFinanciera
        assertEquals(listOf("Éxito Bocagrande"), f.filtrar(historial, FiltroHistorial(texto = "exito")).map { it.comercio })
        assertEquals(listOf("Nómina"), f.filtrar(historial, FiltroHistorial(texto = "NOMINA")).map { it.comercio })
        assertEquals(listOf("UBER"), f.filtrar(historial, FiltroHistorial(texto = "transp")).map { it.comercio })
        assertEquals(listOf("Éxito Bocagrande"), f.filtrar(historial, FiltroHistorial(texto = "45.900")).map { it.comercio })
    }

    @Test
    fun filtro_ordenes() {
        val f = AnaliticaFinanciera
        assertEquals("UBER", f.filtrar(historial, FiltroHistorial()).first().comercio)
        assertEquals("Nómina", f.filtrar(historial, FiltroHistorial(orden = OrdenHistorial.ANTIGUOS)).first().comercio)
        assertEquals("Nómina", f.filtrar(historial, FiltroHistorial(orden = OrdenHistorial.MAYOR_MONTO)).first().comercio)
        assertEquals("UBER", f.filtrar(historial, FiltroHistorial(orden = OrdenHistorial.MENOR_MONTO)).first().comercio)
    }

    // ------------------------------------------------------------------------------------ Panel

    @Test
    fun panel_reuneTodo() {
        val panel = AnaliticaFinanciera.construirPanel(
            historial, null, listOf(PresupuestoEntidad("Transporte", 100_000.0)), Periodo.MES, ahora, calendario
        )
        assertEquals(Periodo.MES, panel.periodo)
        assertEquals(5, panel.serie.size)
        assertEquals(1, panel.presupuestos.size)
        assertEquals(4, panel.totalMovimientos)
        assertEquals(2_500_000.0 - 45_900.0 - 50_000.0 - 12_000.0, panel.consolidado.saldoDisponible, 0.001)
    }
}
