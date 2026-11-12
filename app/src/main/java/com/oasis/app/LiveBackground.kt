package com.oasis.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.PowerManager
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Fondo vivo detrás del contenido: 1 aurora (manchas de luz que flotan), 2 burbujas, 3 estrellas.
 * Se pausa solo cuando la pantalla no está visible o el teléfono está en ahorro de batería.
 */
class LiveBackground @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var mode = 0
        set(v) { field = v; rebuild(); kick() }

    private var colors = intArrayOf(0xFF4FACFE.toInt(), 0xFF7B61FF.toInt(), 0xFF00E5FF.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var running = false
    private val rnd = Random(7)

    private class Blob(val fx: Float, val fy: Float, val sx: Float, val sy: Float, val r: Float, val ph: Float)
    private var blobs: List<Blob> = emptyList()
    private var shaders: List<RadialGradient> = emptyList()
    private var blobRadius = 0f

    private val n = 24
    private val bx = FloatArray(n) { rnd.nextFloat() }
    private val by = FloatArray(n) { rnd.nextFloat() }
    private val br = FloatArray(n) { 0.6f + rnd.nextFloat() * 1.4f }
    private val bs = FloatArray(n) { 0.02f + rnd.nextFloat() * 0.05f }
    private val bp = FloatArray(n) { rnd.nextFloat() * 6.28f }

    fun setColors(vararg c: Int) { colors = c; rebuild(); invalidate() }

    private fun rebuild() {
        if (width == 0) return
        blobRadius = width * 0.75f
        blobs = listOf(
            Blob(0.25f, 0.20f, 0.00031f, 0.00023f, 1f, 0f),
            Blob(0.75f, 0.45f, 0.00027f, 0.00035f, 1f, 2f),
            Blob(0.40f, 0.80f, 0.00022f, 0.00029f, 1f, 4f)
        )
        shaders = blobs.indices.map { i ->
            RadialGradient(0f, 0f, blobRadius, intArrayOf(withAlpha(colors[i % colors.size], 0x55), withAlpha(colors[i % colors.size], 0)),
                null, Shader.TileMode.CLAMP)
        }
    }

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    fun start() { running = true; kick() }
    fun stop() { running = false }

    private fun powerSave(): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode
    } catch (e: Exception) { false }

    private fun kick() {
        if (running && mode != 0 && isAttachedToWindow) postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { super.onSizeChanged(w, h, oldw, oldh); rebuild() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); kick() }

    private var last = 0L

    override fun onDraw(canvas: Canvas) {
        if (mode == 0 || width == 0) return
        val t = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 16f else (t - last).coerceIn(1L, 64L).toFloat()
        last = t
        when (mode) {
            1 -> drawAurora(canvas, t)
            2 -> drawBubbles(canvas, dt)
            3 -> drawStars(canvas, t)
        }
        if (running && !powerSave()) postInvalidateDelayed(28)
    }

    private fun drawAurora(c: Canvas, t: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        blobs.forEachIndexed { i, b ->
            val x = w * (b.fx + 0.30f * sin(t * b.sx + b.ph))
            val y = h * (b.fy + 0.25f * cos(t * b.sy + b.ph))
            paint.shader = shaders[i]
            c.save(); c.translate(x, y); c.drawCircle(0f, 0f, blobRadius, paint); c.restore()
        }
        paint.shader = null
    }

    private fun drawBubbles(c: Canvas, dt: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        val base = colors[0]
        for (i in 0 until n) {
            by[i] -= bs[i] * dt / 1000f * 2.2f
            if (by[i] < -0.1f) { by[i] = 1.1f; bx[i] = rnd.nextFloat() }
            val x = w * (bx[i] + 0.02f * sin(by[i] * 9f + bp[i]))
            val y = h * by[i]
            val r = br[i] * w * 0.022f
            paint.style = Paint.Style.FILL
            paint.color = withAlpha(base, 0x22)
            c.drawCircle(x, y, r, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f
            paint.color = withAlpha(colors[1 % colors.size], 0x66)
            c.drawCircle(x, y, r, paint)
        }
        paint.style = Paint.Style.FILL
    }

    private fun drawStars(c: Canvas, t: Long) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.style = Paint.Style.FILL
        for (i in 0 until n) {
            val tw = 0.5f + 0.5f * sin(t * 0.0015f * (0.6f + bs[i] * 10f) + bp[i])
            paint.color = withAlpha(0xFFFFFFFF.toInt(), (40 + 150 * tw).toInt())
            c.drawCircle(w * bx[i], h * by[i], br[i] * w * 0.004f + 1.2f, paint)
        }
    }
}
