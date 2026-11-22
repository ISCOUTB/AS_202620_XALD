package com.proyecto.xald.ui

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Barra de progreso redondeada.
 *
 * - Modo presupuesto ([mostrarPresupuesto]): el color pasa de verde a ámbar y a rojo de forma
 *   continua según lo consumido (verde hasta 70 %, ámbar hacia 90 %, rojo desde 100 %). Anima
 *   el relleno y el color a la vez.
 * - Modo color fijo ([mostrarFijo]): para las categorías del inicio.
 */
class BarraProgresoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var proporcionDibujada = 0f
    private var colorFijo: Int? = null
    private var animador: ValueAnimator? = null

    private val pintaPista = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEEF0F5.toInt() }
    private val pintaRelleno = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    fun mostrarPresupuesto(proporcion: Double, animar: Boolean = true) {
        colorFijo = null
        animarHasta(proporcion.toFloat(), animar)
    }

    fun mostrarFijo(proporcion: Double, color: Int, animar: Boolean = true) {
        colorFijo = color
        animarHasta(proporcion.toFloat(), animar)
    }

    private fun animarHasta(destino: Float, animar: Boolean) {
        val limpio = if (destino.isFinite()) destino.coerceAtLeast(0f) else 0f
        animador?.cancel()
        if (!animar) {
            proporcionDibujada = limpio
            invalidate()
            return
        }
        // Las filas se crean nuevas en cada repintado: se espera a que la vista esté en pantalla
        // (post) para que la animación se vea en lugar de saltar directo al valor final.
        proporcionDibujada = 0f
        invalidate()
        post {
            animador?.cancel()
            animador = ValueAnimator.ofFloat(0f, limpio).apply {
                duration = 650L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    proporcionDibujada = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    override fun onDetachedFromWindow() {
        animador?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val alto = height.toFloat()
        val ancho = width.toFloat()
        if (ancho <= 0f || alto <= 0f) return
        val radio = alto / 2f
        rect.set(0f, 0f, ancho, alto)
        canvas.drawRoundRect(rect, radio, radio, pintaPista)

        val llenado = proporcionDibujada.coerceAtMost(1f)
        if (llenado <= 0f) return
        // Siempre al menos un círculo visible para que un 2 % no desaparezca.
        val anchoRelleno = (ancho * llenado).coerceAtLeast(alto)
        rect.set(0f, 0f, anchoRelleno, alto)
        pintaRelleno.color = colorFijo ?: colorSemaforo(proporcionDibujada)
        canvas.drawRoundRect(rect, radio, radio, pintaRelleno)
    }

    companion object {
        private const val VERDE = 0xFF12B76A.toInt()
        private const val AMBAR = 0xFFF79009.toInt()
        private const val ROJO = 0xFFF04438.toInt()
        private val evaluador = ArgbEvaluator()

        /** Verde hasta 70 %, se vuelve ámbar hacia el 90 % y rojo al 100 % o más. */
        fun colorSemaforo(proporcion: Float): Int = when {
            proporcion <= 0.7f -> VERDE
            proporcion <= 0.9f -> evaluador.evaluate((proporcion - 0.7f) / 0.2f, VERDE, AMBAR) as Int
            proporcion < 1f -> evaluador.evaluate((proporcion - 0.9f) / 0.1f, AMBAR, ROJO) as Int
            else -> ROJO
        }
    }
}
