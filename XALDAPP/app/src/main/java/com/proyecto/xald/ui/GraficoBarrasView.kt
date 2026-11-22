package com.proyecto.xald.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.proyecto.xald.corefinanciero.PuntoSerie
import kotlin.math.max
import kotlin.math.min

/**
 * Gráfico de barras agrupadas: entradas (verde) y salidas (rojo) por tramo de tiempo.
 * Las barras crecen desde abajo al cambiar los datos. El tramo actual se resalta.
 * Dibujado a mano con Canvas: no necesita librerías externas.
 */
class GraficoBarrasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var puntos: List<PuntoSerie> = emptyList()
    private var progreso = 1f
    private var animador: ValueAnimator? = null

    private val pintaEntradas = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_ENTRADA }
    private val pintaSalidas = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_SALIDA }
    private val pintaGuia = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = COLOR_GUIA
        strokeWidth = dp(1f)
    }
    private val pintaResaltado = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = COLOR_RESALTADO }
    private val pintaEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(11f)
    }
    private val pintaVacio = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = sp(13f)
        color = COLOR_ETIQUETA
    }
    private val rect = RectF()

    fun mostrar(nuevos: List<PuntoSerie>, animar: Boolean = true) {
        puntos = nuevos
        // Sin montos: el lector de pantalla no debe leerlos en modo privacidad.
        contentDescription = "Gráfico de entradas y salidas por ${nuevos.size} tramos"
        animador?.cancel()
        if (!animar) {
            progreso = 1f
            invalidate()
            return
        }
        progreso = 0f
        invalidate()
        post {
            animador?.cancel()
            animador = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 520L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    progreso = it.animatedValue as Float
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
        val ancho = width.toFloat() - paddingLeft - paddingRight
        val altoEtiquetas = dp(24f)
        val arriba = paddingTop + dp(6f)
        val base = height - paddingBottom - altoEtiquetas
        val altoUtil = base - arriba
        if (ancho <= 0f || altoUtil <= 0f) return

        // Líneas guía (4 niveles).
        for (i in 0..3) {
            val y = base - altoUtil * i / 3f
            canvas.drawLine(paddingLeft.toFloat(), y, paddingLeft + ancho, y, pintaGuia)
        }

        val maximo = puntos.maxOfOrNull { max(it.entradas, it.salidas) } ?: 0.0
        if (puntos.isEmpty() || maximo <= 0.0) {
            canvas.drawText("Sin movimientos en este periodo", paddingLeft + ancho / 2f, arriba + altoUtil / 2f, pintaVacio)
            dibujarEtiquetas(canvas, ancho, base)
            return
        }

        val anchoGrupo = ancho / puntos.size
        val anchoBarra = min(dp(12f), anchoGrupo * 0.3f)
        val separacion = min(dp(3f), anchoGrupo * 0.06f)
        val minimoVisible = dp(3f)

        puntos.forEachIndexed { i, punto ->
            val centro = paddingLeft + anchoGrupo * (i + 0.5f)
            if (punto.esActual) {
                rect.set(centro - anchoGrupo / 2f + dp(2f), arriba - dp(2f), centro + anchoGrupo / 2f - dp(2f), base + altoEtiquetas)
                canvas.drawRoundRect(rect, dp(10f), dp(10f), pintaResaltado)
            }
            dibujarBarra(canvas, centro - separacion / 2f - anchoBarra, anchoBarra, base, alturaDe(punto.entradas, maximo, altoUtil, minimoVisible), pintaEntradas)
            dibujarBarra(canvas, centro + separacion / 2f, anchoBarra, base, alturaDe(punto.salidas, maximo, altoUtil, minimoVisible), pintaSalidas)
        }
        dibujarEtiquetas(canvas, ancho, base)
    }

    private fun alturaDe(valor: Double, maximo: Double, altoUtil: Float, minimo: Float): Float {
        if (valor <= 0.0) return 0f
        return max(minimo, (valor / maximo).toFloat() * altoUtil) * progreso
    }

    private fun dibujarBarra(canvas: Canvas, izquierda: Float, ancho: Float, base: Float, alto: Float, pinta: Paint) {
        if (alto <= 0f) return
        rect.set(izquierda, base - alto, izquierda + ancho, base)
        val radio = min(ancho / 2f, alto / 2f)
        canvas.drawRoundRect(rect, radio, radio, pinta)
    }

    private fun dibujarEtiquetas(canvas: Canvas, ancho: Float, base: Float) {
        if (puntos.isEmpty()) return
        val anchoGrupo = ancho / puntos.size
        val y = base + dp(17f)
        puntos.forEachIndexed { i, punto ->
            pintaEtiqueta.color = if (punto.esActual) COLOR_ETIQUETA_ACTUAL else COLOR_ETIQUETA
            pintaEtiqueta.typeface = if (punto.esActual) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(punto.etiqueta, paddingLeft + anchoGrupo * (i + 0.5f), y, pintaEtiqueta)
        }
    }

    private fun dp(valor: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valor, resources.displayMetrics)
    private fun sp(valor: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valor, resources.displayMetrics)

    companion object {
        const val COLOR_ENTRADA = 0xFF12B76A.toInt()
        const val COLOR_SALIDA = 0xFFF04438.toInt()
        private const val COLOR_GUIA = 0xFFF2F4F7.toInt()
        private const val COLOR_RESALTADO = 0xFFF5F3FF.toInt()
        private const val COLOR_ETIQUETA = 0xFF98A2B3.toInt()
        private const val COLOR_ETIQUETA_ACTUAL = 0xFF101828.toInt()
    }
}
