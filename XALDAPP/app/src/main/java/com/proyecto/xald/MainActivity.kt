package com.proyecto.xald

import android.Manifest
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.proyecto.xald.aigemini.CategoriasPermitidas
import com.proyecto.xald.corefinanciero.AnaliticaFinanciera
import com.proyecto.xald.corefinanciero.Calendario
import com.proyecto.xald.corefinanciero.CategoriasEspeciales
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.corefinanciero.PanelFinanciero
import com.proyecto.xald.corefinanciero.Periodo
import com.proyecto.xald.corefinanciero.PresupuestoEntidad
import com.proyecto.xald.corefinanciero.TipoMovimiento
import com.proyecto.xald.corefinanciero.TransaccionEntidad
import com.proyecto.xald.corefinanciero.crearInformacionFinanciera
import com.proyecto.xald.syncqueue.ColaSincronizacionProvider
import com.proyecto.xald.syncqueue.ProgramadorSincronizacion
import com.proyecto.xald.ui.Dialogos
import com.proyecto.xald.ui.PantallaInicio
import com.proyecto.xald.ui.PantallaMovimientos
import com.proyecto.xald.ui.PantallaPresupuestos
import com.proyecto.xald.ui.PreferenciasXald
import java.time.LocalTime
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Pantalla principal con tres pestañas (Inicio, Movimientos, Presupuestos).
 *
 * Local-First: todo se lee y se escribe en la base del teléfono en un hilo de fondo y la
 * pantalla nunca espera a la red; la sincronización con el backend va por la cola aparte.
 */
class MainActivity : AppCompatActivity() {

    private enum class Pestana(val titulo: String) {
        INICIO("Tu dinero"),
        MOVIMIENTOS("Movimientos"),
        PRESUPUESTOS("Presupuestos")
    }

    /** Lo que se leyó de la base en la última carga. */
    private class Datos(
        val panel: PanelFinanciero,
        val movimientos: List<TransaccionEntidad>,
        val presupuestos: List<PresupuestoEntidad>,
        val pendientes: Int,
        val ultimaSincronizacionMs: Long?,
        val ahoraMs: Long
    )

    private lateinit var coreFinanciero: InformacionFinanciera

    // Un hilo para la base (las lecturas y escrituras quedan en orden) y otro para la red.
    private val hiloBase: ExecutorService = Executors.newSingleThreadExecutor()
    private val hiloRed: ExecutorService = Executors.newSingleThreadExecutor()
    private val calendario = Calendario()

    private lateinit var pantallaInicio: PantallaInicio
    private lateinit var pantallaMovimientos: PantallaMovimientos
    private lateinit var pantallaPresupuestos: PantallaPresupuestos
    private lateinit var vistasPantalla: Map<Pestana, View>
    private lateinit var tabs: Map<Pestana, View>

    private lateinit var tvSaludo: TextView
    private lateinit var tvTitulo: TextView
    private lateinit var btnPrivacidad: ImageButton
    private lateinit var btnSincronizar: ImageButton
    private lateinit var tvInsignia: TextView

    private var pestana = Pestana.INICIO
    private var periodo = Periodo.MES
    private var privado = false
    private var datos: Datos? = null
    private var primeraCarga = true
    private var giroSincronizacion: ObjectAnimator? = null

    private var dialogoConsentimiento: AlertDialog? = null
    private var permisoYaSolicitado = false
    private var permisoNotificacionesSolicitado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Núcleo Financiero (singleton compartido con el receptor de SMS)
        coreFinanciero = crearInformacionFinanciera(this)
        privado = PreferenciasXald.modoPrivado(this)
        periodo = PreferenciasXald.periodo(this)

        tvSaludo = findViewById(R.id.tvSaludo)
        tvTitulo = findViewById(R.id.tvTituloPantalla)
        btnPrivacidad = findViewById(R.id.btnPrivacidad)
        btnSincronizar = findViewById(R.id.btnSincronizar)
        tvInsignia = findViewById(R.id.tvInsigniaPendientes)

        btnPrivacidad.setOnClickListener { alternarPrivacidad() }
        btnSincronizar.setOnClickListener { forzarSincronizacion() }
        findViewById<View>(R.id.fabAgregar).setOnClickListener {
            if (pestana == Pestana.PRESUPUESTOS) abrirEditorPresupuesto(null) else abrirNuevoMovimiento()
        }

        vistasPantalla = mapOf(
            Pestana.INICIO to findViewById(R.id.pantallaInicio),
            Pestana.MOVIMIENTOS to findViewById(R.id.pantallaMovimientos),
            Pestana.PRESUPUESTOS to findViewById(R.id.pantallaPresupuestos)
        )
        tabs = mapOf(
            Pestana.INICIO to findViewById(R.id.tabInicio),
            Pestana.MOVIMIENTOS to findViewById(R.id.tabMovimientos),
            Pestana.PRESUPUESTOS to findViewById(R.id.tabPresupuestos)
        )
        tabs.forEach { (p, tab) -> tab.setOnClickListener { mostrarPestana(p) } }
        onBackPressedDispatcher.addCallback(volverAlInicio)

        pantallaInicio = PantallaInicio(vistasPantalla.getValue(Pestana.INICIO), accionesInicio)
        pantallaMovimientos = PantallaMovimientos(vistasPantalla.getValue(Pestana.MOVIMIENTOS)) { abrirDetalle(it) }
        pantallaPresupuestos = PantallaPresupuestos(vistasPantalla.getValue(Pestana.PRESUPUESTOS), accionesPresupuestos)

        val inicial = savedInstanceState?.getString(ESTADO_PESTANA)
            ?: intent?.getStringExtra(EXTRA_PESTANA)
        mostrarPestana(Pestana.entries.firstOrNull { it.name == inicial } ?: Pestana.INICIO)
        pintarBarraSuperior()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Al tocar una alerta de presupuesto con la app abierta.
        intent.getStringExtra(EXTRA_PESTANA)?.let { nombre ->
            Pestana.entries.firstOrNull { it.name == nombre }?.let { mostrarPestana(it) }
        }
    }

    override fun onStart() {
        super.onStart()
        // Refresco en vivo cuando llega un SMS o se confirma un envío con la app abierta.
        NotificadorCambios.registrar { recargar(animar = false) }
    }

    override fun onResume() {
        super.onResume()
        recargar(animar = primeraCarga)
        primeraCarga = false
        gestionarConsentimientoYPermisos()
    }

    override fun onStop() {
        NotificadorCambios.registrar(null)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(ESTADO_PESTANA, pestana.name)
    }

    override fun onDestroy() {
        dialogoConsentimiento?.dismiss()
        dialogoConsentimiento = null
        giroSincronizacion?.cancel()
        hiloBase.shutdown()
        hiloRed.shutdown()
        super.onDestroy()
    }

    /** Atrás desde otra pestaña vuelve al inicio; en el inicio, Android hace lo normal (salir). */
    private val volverAlInicio = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = mostrarPestana(Pestana.INICIO)
    }

    // ------------------------------------------------------------------------------- Navegación

    private fun mostrarPestana(nueva: Pestana) {
        pestana = nueva
        vistasPantalla.forEach { (p, vista) -> vista.visibility = if (p == nueva) View.VISIBLE else View.GONE }
        tabs.forEach { (p, tab) -> tab.isSelected = p == nueva }
        tvTitulo.text = nueva.titulo
        volverAlInicio.isEnabled = nueva != Pestana.INICIO
        // Al entrar a Presupuestos las barras se llenan de nuevo (animadas).
        if (nueva == Pestana.PRESUPUESTOS) datos?.let { pantallaPresupuestos.mostrar(it.panel.presupuestos, privado, animar = true) }
    }

    private fun pintarBarraSuperior() {
        val hora = LocalTime.now().hour
        tvSaludo.text = when {
            hora < 12 -> "Buenos días 👋"
            hora < 19 -> "Buenas tardes 👋"
            else -> "Buenas noches 👋"
        }
        btnPrivacidad.setImageResource(if (privado) R.drawable.ic_ojo_tachado else R.drawable.ic_ojo)
        btnPrivacidad.contentDescription = if (privado) "Mostrar saldos" else "Ocultar saldos"
    }

    // -------------------------------------------------------------------------------- Datos

    /** Lee todo en el hilo de la base y pinta en el hilo principal. */
    private fun recargar(animar: Boolean) {
        val periodoActual = periodo
        hiloBase.execute {
            try {
                val ahora = System.currentTimeMillis()
                val movimientos = coreFinanciero.obtenerHistorial()
                val presupuestos = coreFinanciero.obtenerPresupuestos()
                val panel = AnaliticaFinanciera.construirPanel(
                    movimientos, coreFinanciero.obtenerSaldoBase(), presupuestos, periodoActual, ahora, calendario
                )
                val nuevos = Datos(
                    panel = panel,
                    movimientos = movimientos,
                    presupuestos = presupuestos,
                    pendientes = ColaSincronizacionProvider.obtener()?.obtenerCantidadPendientes() ?: 0,
                    ultimaSincronizacionMs = PreferenciasXald.ultimaSincronizacion(this),
                    ahoraMs = ahora
                )
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    datos = nuevos
                    pintar(nuevos, animar)
                }
            } catch (e: Exception) {
                Log.e("XALD", "No se pudo cargar el panel (${e.javaClass.simpleName})")
            }
        }
    }

    private fun pintar(d: Datos, animar: Boolean) {
        pantallaInicio.pintar(d.panel, d.pendientes, d.ultimaSincronizacionMs, privado, animar, d.ahoraMs)
        pantallaMovimientos.mostrar(d.movimientos, privado)
        pantallaPresupuestos.mostrar(d.panel.presupuestos, privado, animar && pestana == Pestana.PRESUPUESTOS)
        tvInsignia.visibility = if (d.pendientes > 0) View.VISIBLE else View.GONE
        tvInsignia.text = if (d.pendientes > 99) "99+" else d.pendientes.toString()
        pintarBarraSuperior()
    }

    /** Ejecuta [trabajo] en el hilo de la base y luego recarga la pantalla. */
    private fun guardarYRecargar(mensajeExito: String?, trabajo: () -> Boolean) {
        hiloBase.execute {
            val ok = try {
                trabajo()
            } catch (e: Exception) {
                Log.e("XALD", "Error guardando (${e.javaClass.simpleName})")
                false
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val mensaje = if (ok) mensajeExito else "No se pudo guardar. Inténtalo de nuevo."
                mensaje?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                recargar(animar = true)
            }
        }
    }

    // ---------------------------------------------------------------------------- Acciones

    private val accionesInicio = object : PantallaInicio.Acciones {
        override fun alCambiarPeriodo(periodo: Periodo) {
            if (this@MainActivity.periodo == periodo) return
            this@MainActivity.periodo = periodo
            PreferenciasXald.guardarPeriodo(this@MainActivity, periodo)
            recargar(animar = true)
        }

        override fun alAjustarSaldo() = abrirAjusteSaldo()
        override fun alAlternarPrivacidad() = alternarPrivacidad()
        override fun alAbrirPresupuestos() = mostrarPestana(Pestana.PRESUPUESTOS)
        override fun alEditarPresupuesto(presupuesto: PresupuestoEntidad?) = abrirEditorPresupuesto(presupuesto)
        override fun alSincronizar() = forzarSincronizacion()

        override fun alBuscarEnMovimientos(texto: String) {
            pantallaMovimientos.buscar(texto)
            mostrarPestana(Pestana.MOVIMIENTOS)
        }
    }

    private val accionesPresupuestos = object : PantallaPresupuestos.Acciones {
        override fun alNuevoPresupuesto() = abrirEditorPresupuesto(null)
        override fun alEditarPresupuesto(presupuesto: PresupuestoEntidad) = abrirEditorPresupuesto(presupuesto)
    }

    private fun alternarPrivacidad() {
        privado = !privado
        PreferenciasXald.guardarModoPrivado(this, privado)
        datos?.let { pintar(it, animar = false) } ?: pintarBarraSuperior()
        Toast.makeText(this, if (privado) "Montos ocultos" else "Montos visibles", Toast.LENGTH_SHORT).show()
    }

    private fun abrirNuevoMovimiento() {
        Dialogos.nuevoMovimiento(this, categoriasDeGasto(), datos?.panel?.consolidado?.saldoBase) { formulario ->
            guardarYRecargar("Movimiento guardado") {
                val resultado = RegistrarMovimientoManualUseCase(coreFinanciero, ColaSincronizacionProvider.obtener())
                    .ejecutar(formulario.monto, formulario.descripcion, formulario.tipo, formulario.categoria, formulario.fechaMs)
                val guardado = resultado is RegistrarMovimientoManualUseCase.Resultado.Guardado
                if (guardado) {
                    ProgramadorSincronizacion.solicitarEnvioInmediato(this)
                    NotificadorPresupuestos.revisar(this, coreFinanciero)
                }
                guardado
            }
        }
    }

    private fun abrirAjusteSaldo() {
        val actual = datos?.panel?.consolidado?.saldoDisponible ?: 0.0
        Dialogos.ajustarSaldo(this, actual, privado) { monto ->
            guardarYRecargar("Saldo ajustado") { coreFinanciero.establecerSaldoBase(monto) }
        }
    }

    private fun abrirEditorPresupuesto(existente: PresupuestoEntidad?) {
        val ocupadas = datos?.presupuestos?.map { it.categoria }?.toSet().orEmpty()
        val libres = (listOf(CategoriasEspeciales.PRESUPUESTO_TOTAL) + categoriasDeGasto(incluirSinCategoria = false))
            .filterNot { it in ocupadas }
        Dialogos.presupuesto(
            this, existente, libres,
            alGuardar = { presupuesto ->
                guardarYRecargar("Presupuesto guardado") {
                    val ok = coreFinanciero.guardarPresupuesto(presupuesto)
                    if (ok) {
                        PreferenciasXald.olvidarAlertas(this, presupuesto.categoria)
                        NotificadorPresupuestos.revisar(this, coreFinanciero)
                    }
                    ok
                }
                pedirPermisoNotificacionesUnaVez()
            },
            alEliminar = { categoria ->
                guardarYRecargar("Presupuesto eliminado") {
                    PreferenciasXald.olvidarAlertas(this, categoria)
                    coreFinanciero.eliminarPresupuesto(categoria)
                }
            }
        )
    }

    private fun abrirDetalle(movimiento: TransaccionEntidad) {
        Dialogos.detalle(this, movimiento, privado) {
            Dialogos.reclasificar(this, movimiento, categoriasDeGasto()) { tipo, categoria ->
                guardarYRecargar("Clasificación actualizada") {
                    val ok = ReclasificarMovimientoUseCase(coreFinanciero, ColaSincronizacionProvider.obtener())
                        .ejecutar(movimiento.id, tipo, categoria)
                    if (ok && tipo == TipoMovimiento.GASTO) NotificadorPresupuestos.revisar(this, coreFinanciero)
                    ok
                }
            }
        }
    }

    private fun categoriasDeGasto(incluirSinCategoria: Boolean = true): List<String> =
        CategoriasPermitidas.lista.filter { incluirSinCategoria || it != CategoriasEspeciales.SIN_CATEGORIZAR }

    // --------------------------------------------------------------------------- Sincronización

    private fun forzarSincronizacion() {
        val cola = ColaSincronizacionProvider.obtener()
        if (cola == null) {
            Toast.makeText(this, "La cola de sincronización aún no está lista.", Toast.LENGTH_SHORT).show()
            return
        }
        if (giroSincronizacion?.isRunning == true) return
        giroSincronizacion = ObjectAnimator.ofFloat(btnSincronizar, View.ROTATION, 0f, -360f).apply {
            duration = 900L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
        hiloRed.execute {
            val resumen = try {
                cola.procesarPendientes()
            } catch (e: Exception) {
                Log.e("XALD", "Error sincronizando (${e.javaClass.simpleName})")
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                giroSincronizacion?.cancel()
                btnSincronizar.rotation = 0f
                val mensaje = when {
                    resumen == null -> "No se pudo sincronizar: se reintentará automáticamente."
                    resumen.sinConexion -> "Sin conexión con el servidor: se reintentará automáticamente."
                    resumen.confirmados + resumen.reprogramados + resumen.aFallidos == 0 -> "No había nada pendiente."
                    else -> "Enviadas: ${resumen.confirmados} · Reintentos: ${resumen.reprogramados} · Fallidas: ${resumen.aFallidos}"
                }
                Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                recargar(animar = false)
            }
        }
    }

    // ---------------------------------------------------------------- Consentimiento y permisos

    /**
     * Orden obligatorio (esquema.txt RL-01): primero el aviso de consentimiento informado y solo
     * después, si el usuario acepta, el permiso técnico de Android para recibir SMS.
     */
    private fun gestionarConsentimientoYPermisos() {
        if (ConsentimientoXald.aceptado(this)) {
            solicitarPermisoSmsUnaVez()
            return
        }
        if (dialogoConsentimiento?.isShowing == true) return

        dialogoConsentimiento = AlertDialog.Builder(this)
            .setTitle("Tu autorización")
            .setMessage(TEXTO_CONSENTIMIENTO)
            .setCancelable(false)
            .setPositiveButton("Acepto") { _, _ ->
                ConsentimientoXald.registrar(this)
                solicitarPermisoSmsUnaVez()
            }
            .setNegativeButton("No acepto") { _, _ ->
                Toast.makeText(
                    this,
                    "Sin tu autorización XALD no leerá ningún SMS.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .show()
    }

    private fun solicitarPermisoSmsUnaVez() {
        if (permisoYaSolicitado) return
        val concedido = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (concedido) return

        permisoYaSolicitado = true
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS), CODIGO_PERMISO_SMS)
    }

    /** Las alertas de presupuesto necesitan este permiso en Android 13+; se pide al crear uno. */
    private fun pedirPermisoNotificacionesUnaVez() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (permisoNotificacionesSolicitado || NotificadorPresupuestos.puedeNotificar(this)) return
        permisoNotificacionesSolicitado = true
        ActivityCompat.requestPermissions(
            this, arrayOf(NotificadorPresupuestos.PERMISO_NOTIFICACIONES), CODIGO_PERMISO_NOTIFICACIONES
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val concedido = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            CODIGO_PERMISO_SMS -> Toast.makeText(
                this,
                if (concedido) "Permiso de SMS concedido. XALD está listo."
                else "Sin el permiso de SMS XALD no puede registrar tus compras automáticamente.",
                if (concedido) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
            ).show()
            CODIGO_PERMISO_NOTIFICACIONES -> if (concedido) {
                // Avisa ya de los umbrales que se hubieran cruzado sin permiso.
                hiloBase.execute { NotificadorPresupuestos.revisar(this, coreFinanciero) }
            }
        }
    }

    companion object {
        const val EXTRA_PESTANA = "xald.pestana"
        const val PESTANA_PRESUPUESTOS = "PRESUPUESTOS"

        private const val ESTADO_PESTANA = "pestana"
        private const val CODIGO_PERMISO_SMS = 1001
        private const val CODIGO_PERMISO_NOTIFICACIONES = 1002

        private const val TEXTO_CONSENTIMIENTO =
            "XALD registra tus compras a partir de los SMS de tu banco.\n\n" +
                "• Solo se leen los SMS que llegan de remitentes bancarios autorizados; los demás se ignoran.\n" +
                "• De cada SMS se extraen monto, comercio y fecha, y se guardan en este teléfono.\n" +
                "• Esos datos se envían al servidor de XALD para sincronizarlos.\n" +
                "• El nombre del comercio puede enviarse a un servicio de inteligencia artificial para sugerir una categoría.\n\n" +
                "Puedes rechazar: en ese caso la app no leerá ningún SMS. Tus datos se tratan según la Ley 1581 de 2012."
    }
}
