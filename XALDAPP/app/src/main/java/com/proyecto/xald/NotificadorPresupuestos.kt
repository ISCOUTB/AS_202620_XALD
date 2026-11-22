package com.proyecto.xald

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.proyecto.xald.corefinanciero.AlertaPresupuesto
import com.proyecto.xald.corefinanciero.AnaliticaFinanciera
import com.proyecto.xald.corefinanciero.Calendario
import com.proyecto.xald.corefinanciero.InformacionFinanciera
import com.proyecto.xald.corefinanciero.crearInformacionFinanciera
import com.proyecto.xald.ui.PreferenciasXald

/**
 * Alertas locales de proximidad a los topes de gasto (80 %, 90 % y 100 %).
 *
 * Cada umbral avisa una sola vez por periodo (las claves enviadas se guardan en
 * [PreferenciasXald]). Las notificaciones muestran porcentajes, nunca montos, porque pueden
 * verse en la pantalla bloqueada.
 */
object NotificadorPresupuestos {

    const val CANAL = "xald_presupuestos"
    const val PERMISO_NOTIFICACIONES = "android.permission.POST_NOTIFICATIONS"
    private const val ID_BASE = 4_000

    fun crearCanal(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(CANAL, "Alertas de presupuesto", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisos al llegar al 80 %, 90 % y 100 % de un presupuesto"
        }
        manager.createNotificationChannel(canal)
    }

    /** En Android 13+ las notificaciones necesitan permiso explícito del usuario. */
    fun puedeNotificar(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(PERMISO_NOTIFICACIONES) == PackageManager.PERMISSION_GRANTED

    /**
     * Revisa los presupuestos y notifica los umbrales recién cruzados. Hace consultas a la base:
     * llamarlo desde un hilo de fondo (después de guardar movimientos o presupuestos).
     */
    fun revisar(
        context: Context,
        core: InformacionFinanciera = crearInformacionFinanciera(context),
        ahoraMs: Long = System.currentTimeMillis()
    ) {
        val app = context.applicationContext
        try {
            val presupuestos = core.obtenerPresupuestos()
            if (presupuestos.isEmpty()) return
            val estados = AnaliticaFinanciera.estadoPresupuestos(core.obtenerHistorial(), presupuestos, ahoraMs, Calendario())
            val nuevas = AnaliticaFinanciera.alertasNuevas(estados, PreferenciasXald.alertasEnviadas(app))
            // Sin permiso no se marcan como enviadas: avisarán cuando el usuario lo conceda.
            if (nuevas.isEmpty() || !puedeNotificar(app)) return

            val manager = app.getSystemService(NotificationManager::class.java) ?: return
            nuevas.forEach { alerta -> manager.notify(idDe(alerta), construir(app, alerta)) }
            PreferenciasXald.registrarAlertas(app, nuevas.flatMap { it.clavesCubiertas })
        } catch (e: Exception) {
            // Una alerta que falla nunca debe tumbar el guardado de un movimiento.
            Log.e("XALD", "No se pudieron revisar los presupuestos (${e.javaClass.simpleName})")
        }
    }

    private fun construir(context: Context, alerta: AlertaPresupuesto): Notification {
        val porcentaje = if (alerta.limite > 0) AnaliticaFinanciera.porcentaje(alerta.gastado / alerta.limite) else alerta.umbral
        val (titulo, texto) = if (alerta.umbral >= 100) {
            "Te pasaste del presupuesto de ${alerta.nombre}" to
                "Vas en el $porcentaje% de tu tope. Revisa tus gastos en XALD."
        } else {
            "${alerta.nombre}: $porcentaje% del presupuesto" to
                "Llegaste al ${alerta.umbral}% de tu tope. Todavía puedes ajustar tus gastos."
        }
        val abrirApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_PESTANA, MainActivity.PESTANA_PRESUPUESTOS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(context, CANAL)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFF6C5CE7.toInt())
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(Notification.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .setContentIntent(abrirApp)
            .build()
    }

    /** Un id por presupuesto: la alerta del 90 % reemplaza a la del 80 % en la bandeja. */
    private fun idDe(alerta: AlertaPresupuesto): Int = ID_BASE + (alerta.nombre.hashCode() and 0x0FFF)
}
