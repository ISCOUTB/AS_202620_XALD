package com.proyecto.xald.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import com.proyecto.xald.corefinanciero.SaludFinanciera
import kotlin.math.min

/**
 * Punto de color del semáforo de salud financiera con un halo que "respira" suavemente.
 * La animación solo corre mientras la vista está en pantalla.
 */
class PuntoSemaforoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var color = colorDe(SaludFinanciera.SALUDABLE)
    private var pulso = 0f
    private val pintaPunto = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pintaHalo = Paint(Paint.ANTI_ALIAS_FLAG)

    private val animador = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600L
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener {
            pulso = it.animatedValue as Float
            invalidate()
        }
    }

    fun mostrar(salud: SaludFinanciera) {
        color = colorDe(salud)
        contentDescription = when (salud) {
            SaludFinanciera.SALUDABLE -> "Salud financiera: bien"
            SaludFinanciera.ATENCION -> "Salud financiera: atención"
            SaludFinanciera.RIESGO -> "Salud financiera: riesgo"
        }
        invalidate()
    }

    // Solo anima mientras se ve: al cambiar de pestaña (vista GONE) o salir de la app se detiene.
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) {
            if (!animador.isStarted) animador.start()
        } else {
            animador.cancel()
            pulso = 0f
        }
    }

    override fun onDetachedFromWindow() {
        animador.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radioMax = min(width, height) / 2f
        val radioPunto = radioMax * 0.5f

        pintaHalo.color = (color and 0x00FFFFFF) or ((0x55 * (1f - pulso)).toInt() shl 24)
        canvas.drawCircle(cx, cy, radioPunto + (radioMax - radioPunto) * pulso, pintaHalo)

        pintaPunto.color = color
        canvas.drawCircle(cx, cy, radioPunto, pintaPunto)
    }

    companion object {
        fun colorDe(salud: SaludFinanciera): Int = when (salud) {
            SaludFinanciera.SALUDABLE -> 0xFF32D583.toInt()
            SaludFinanciera.ATENCION -> 0xFFFDB022.toInt()
            SaludFinanciera.RIESGO -> 0xFFF97066.toInt()
        }
    }
}
