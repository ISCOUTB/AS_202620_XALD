package com.proyecto.xald

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.corefinanciero.TransaccionEntidad
import com.proyecto.xald.corefinanciero.crearInformacionFinanciera
import com.proyecto.xald.syncqueue.ColaSincronizacionProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var coreFinanciero: InformacionFinanciera

    private lateinit var tvSaldo: TextView
    private lateinit var tvPendientes: TextView
    private lateinit var tvHistorial: TextView
    private lateinit var btnSincronizar: Button
    private lateinit var btnActualizar: Button

    private var dialogoConsentimiento: AlertDialog? = null
    private var permisoYaSolicitado = false

    private val formatoMoneda = Locale.forLanguageTag("es-CO")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Inicializar referencias UI
        tvSaldo = findViewById(R.id.tvSaldo)
        tvPendientes = findViewById(R.id.tvPendientes)
        tvHistorial = findViewById(R.id.tvHistorial)
        btnSincronizar = findViewById(R.id.btnSincronizar)
        btnActualizar = findViewById(R.id.btnActualizar)

        // 2. Núcleo Financiero (singleton compartido con el receptor de SMS)
        coreFinanciero = crearInformacionFinanciera(this)

        // 3. Acciones de botones
        btnActualizar.setOnClickListener { actualizarDashboard() }
        btnSincronizar.setOnClickListener { forzarSincronizacion() }
    }

    override fun onStart() {
        super.onStart()
        // Refresco en vivo cuando llega un SMS o se confirma un envío con la app abierta.
        NotificadorCambios.registrar { actualizarDashboard() }
    }

    override fun onResume() {
        super.onResume()
        actualizarDashboard()
        gestionarConsentimientoYPermisos()
    }

    override fun onStop() {
        NotificadorCambios.registrar(null)
        super.onStop()
    }

    override fun onDestroy() {
        dialogoConsentimiento?.dismiss()
        dialogoConsentimiento = null
        super.onDestroy()
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CODIGO_PERMISO_SMS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "Permiso de SMS concedido. XALD está listo.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    this,
                    "Sin el permiso de SMS XALD no puede registrar tus compras automáticamente.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------------------------------------------------------------------------------- Pantalla

    private fun forzarSincronizacion() {
        Toast.makeText(this, "Intentando sincronizar con el servidor...", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            val resumen = ColaSincronizacionProvider.obtener()?.procesarPendientes()

            withContext(Dispatchers.Main) {
                val mensaje = when {
                    resumen == null -> "La cola de sincronización aún no está lista."
                    resumen.sinConexion -> "Sin conexión con el servidor: se reintentará automáticamente."
                    else -> "Enviadas: ${resumen.confirmados} · Reintentos: ${resumen.reprogramados} · Fallidas: ${resumen.aFallidos}"
                }
                Toast.makeText(this@MainActivity, mensaje, Toast.LENGTH_LONG).show()
                actualizarDashboard()
            }
        }
    }

    /** Lee en segundo plano (Room) y pinta en el hilo principal. */
    private fun actualizarDashboard() {
        CoroutineScope(Dispatchers.IO).launch {
            val saldo = coreFinanciero.obtenerSaldoTotal()
            val pendientes = ColaSincronizacionProvider.obtener()?.obtenerCantidadPendientes() ?: 0
            val historial = coreFinanciero.obtenerHistorial()

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                pintar(saldo, pendientes, historial)
            }
        }
    }

    private fun pintar(total: Double, pendientes: Int, historial: List<TransaccionEntidad>) {
        // Hoy solo se registran compras, así que la suma es lo gastado (no un saldo bancario).
        tvSaldo.text = "Total gastado: $${String.format(formatoMoneda, "%,.0f", total)}"
        tvPendientes.text = "Pendientes de envío al servidor ($pendientes)"

        if (historial.isEmpty()) {
            tvHistorial.text = "Aún no hay transacciones registradas en el dispositivo."
            return
        }

        val formatoFecha = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault())
        tvHistorial.text = historial.joinToString(separator = "\n\n------------------------\n\n") { tx ->
            val estado = if (tx.sincronizado) "✔ Sincronizada" else "⏳ Pendiente de envío"
            "📌 Comercio: ${tx.comercio}\n" +
                "💰 Monto: $${String.format(formatoMoneda, "%,.0f", tx.monto)} ${tx.moneda}\n" +
                "🏷️ Categoría: ${tx.categoria}\n" +
                "🕒 Fecha: ${formatoFecha.format(Date(tx.fechaTimestamp))}\n" +
                estado
        }
    }

    companion object {
        private const val CODIGO_PERMISO_SMS = 1001

        private const val TEXTO_CONSENTIMIENTO =
            "XALD registra tus compras a partir de los SMS de tu banco.\n\n" +
                "• Solo se leen los SMS que llegan de remitentes bancarios autorizados; los demás se ignoran.\n" +
                "• De cada SMS se extraen monto, comercio y fecha, y se guardan en este teléfono.\n" +
                "• Esos datos se envían al servidor de XALD para sincronizarlos.\n" +
                "• El nombre del comercio puede enviarse a un servicio de inteligencia artificial para sugerir una categoría.\n\n" +
                "Puedes rechazar: en ese caso la app no leerá ningún SMS. Tus datos se tratan según la Ley 1581 de 2012."
    }
}
