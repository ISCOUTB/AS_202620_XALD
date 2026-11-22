package com.proyecto.xald.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Barra segmentada de entradas (verde) contra salidas (rojo). El ancho de cada tramo es su
 * proporción del dinero movido; anima el cambio cuando llegan datos nuevos.
 */
class BarraFlujoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Fracción 0..1 de entradas, o null si no hubo movimientos (barra gris). */
    private var proporcion: Float? = null
    private var proporcionDibujada = 0f
    private var animador: ValueAnimator? = null

    private val pintaPista = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEEF0F5.toInt() }
    private val pintaEntradas = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GraficoBarrasView.COLOR_ENTRADA }
    private val pintaSalidas = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = GraficoBarrasView.COLOR_SALIDA }
    private val rect = RectF()
    private val recorte = Path()

    fun mostrar(proporcionEntradas: Double?, animar: Boolean = true) {
        val nueva = proporcionEntradas?.toFloat()?.coerceIn(0f, 1f)
        val desde = if (proporcion == null) 0.5f else proporcionDibujada
        proporcion = nueva
        animador?.cancel()
        if (nueva == null) {
            invalidate()
            return
        }
        if (!animar) {
            proporcionDibujada = nueva
            invalidate()
            return
        }
        proporcionDibujada = desde
        post {
            animador?.cancel()
            animador = ValueAnimator.ofFloat(desde, nueva).apply {
                duration = 480L
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

        if (proporcion == null) {
            canvas.drawRoundRect(rect, radio, radio, pintaPista)
            return
        }

        // Se recorta con la forma redondeada para que ambos tramos compartan los extremos curvos.
        recorte.reset()
        recorte.addRoundRect(rect, radio, radio, Path.Direction.CW)
        val guardado = canvas.save()
        canvas.clipPath(recorte)
        val separacion = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3f, resources.displayMetrics)
        val corte = ancho * proporcionDibujada
        if (corte > 0f) canvas.drawRect(0f, 0f, corte, alto, pintaEntradas)
        if (corte < ancho) {
            val inicioSalidas = if (corte > 0f) corte + separacion else 0f
            canvas.drawRect(inicioSalidas, 0f, ancho, alto, pintaSalidas)
        }
        canvas.restoreToCount(guardado)
    }
}
