package com.oasis.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Animación de voz alternativa al orbe: ondas, barras o anillo de partículas.
 * Reacciona a tu voz (level), a cada palabra del asistente (kick) y a los estados escuchar / pensar / hablar.
 * Se dibuja solo mientras es visible. No recibe toques (el orbe de debajo sigue siendo el botón).
 */
class VoiceFxView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LiveView(c, a) {

    companion object {
        const val IDLE = 0
        const val LISTEN = 1
        const val THINK = 2
        const val SPEAK = 3
        const val STYLE_ORB = 0
        const val STYLE_WAVES = 1
        const val STYLE_BARS = 2
        const val STYLE_RING = 3
    }

    var style = STYLE_WAVES
        set(v) { field = v; invalidate() }
    var color = 0xFFF57C00.toInt()
        set(v) { field = v; invalidate() }
    var state = IDLE
    /** Vista previa de Ajustes: recorre sola los cuatro estados. */
    var demo = false

    override val frameMs = 16L

    private var amp = 0.1f
    private var lvl = 0f
    private var kickAmt = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun level(l: Float) { lvl = max(lvl, l.coerceIn(0f, 1f)) }
    fun kick() { kickAmt = 1f }

    private fun withAlpha(a: Float): Int {
        val al = (a.coerceIn(0f, 1f) * 255f).toInt()
        return (color and 0x00FFFFFF) or (al shl 24)
    }

    override fun onDraw(canvas: Canvas) {
        val t = now() / 1000f
        var st = state
        if (demo) {
            st = ((t / 2.6f).toInt()) % 4
            lvl = max(lvl, 0.5f + 0.5f * sin(t * 6f))
            if (st == SPEAK && sin(t * 9f) > 0.8f) kickAmt = 1f
        }

        // Objetivo de amplitud según el estado
        val envelope = abs(sin(t * 7.3f) * cos(t * 3.1f))
        val target = when (st) {
            LISTEN -> 0.14f + 0.86f * lvl
            THINK -> 0.38f
            SPEAK -> 0.22f + 0.78f * max(envelope * 0.55f, kickAmt)
            else -> 0.10f
        }
        amp += (target - amp) * 0.18f
        lvl *= 0.9f
        kickAmt *= 0.88f
        val speed = if (st == THINK) 2.6f else if (st == IDLE) 0.7f else 1.4f

        when (style) {
            STYLE_WAVES -> drawWaves(canvas, t * speed)
            STYLE_BARS -> drawBars(canvas, t * speed, st)
            STYLE_RING -> drawRing(canvas, t * speed, st)
            else -> drawOrb(canvas, t)
        }
        loop()
    }

    private fun drawWaves(c: Canvas, t: Float) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val cy = h / 2f
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        for (layer in 0 until 4) {
            val a = amp * h * 0.34f * (1f - layer * 0.18f)
            val freq = 1.4f + layer * 0.55f
            val phase = t * (2.4f + layer * 0.9f) * (if (layer % 2 == 0) 1f else -1f)
            p.strokeWidth = (3.2f - layer * 0.5f) * dp
            p.color = withAlpha(1f - layer * 0.24f)
            path.reset()
            val steps = 48
            for (i in 0..steps) {
                val f = i / steps.toFloat()
                val taper = sin(PI.toFloat() * f)
                val y = cy + a * taper * taper * sin(f * freq * 2f * PI.toFloat() + phase)
                if (i == 0) path.moveTo(f * w, y) else path.lineTo(f * w, y)
            }
            c.drawPath(path, p)
        }
    }

    private fun drawBars(c: Canvas, t: Float, st: Int) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val n = 11
        val span = w * 0.86f
        val slot = span / n
        val bw = slot * 0.55f
        val x0 = (w - span) / 2f
        val minH = 6f * dp
        val maxH = h * 0.92f
        val mid = (n - 1) / 2f
        p.style = Paint.Style.FILL
        for (i in 0 until n) {
            val shape = 1f - abs(i - mid) / (mid + 1f) * 0.55f
            val wob = 0.35f + 0.65f * abs(sin(t * 3.1f + i * 0.9f) * cos(t * 1.7f + i * 0.5f))
            var k = amp * shape * wob
            if (st == THINK) {
                val sweep = ((t * 1.6f) % 1f) * (n + 2) - 1f
                k = 0.16f + 0.5f * max(0f, 1f - abs(i - sweep) / 2.2f)
            }
            val bh = minH + (maxH - minH) * k.coerceIn(0f, 1f)
            val cx = x0 + slot * (i + 0.5f)
            rect.set(cx - bw / 2f, (h - bh) / 2f, cx + bw / 2f, (h + bh) / 2f)
            p.color = withAlpha(0.55f + 0.45f * k)
            c.drawRoundRect(rect, bw / 2f, bw / 2f, p)
        }
    }

    private fun drawRing(c: Canvas, t: Float, st: Int) {
        val cx = width / 2f; val cy = height / 2f
        val half = min(width, height) / 2f
        if (half <= 0f) return
        val base = half * 0.56f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.5f * dp
        p.color = withAlpha(0.22f)
        c.drawCircle(cx, cy, base + amp * half * 0.08f, p)
        p.style = Paint.Style.FILL
        val n = 40
        val rot = t * (if (st == THINK) 1.6f else 0.35f)
        for (i in 0 until n) {
            val ang = rot + i * (2f * PI.toFloat() / n)
            val push = amp * half * 0.30f * (0.5f + 0.5f * sin(t * 2.4f + i * 1.7f))
            val r = base + push
            val x = cx + r * cos(ang)
            val y = cy + r * sin(ang)
            val dr = (1.8f + 2.6f * amp * (0.4f + 0.6f * abs(sin(i * 0.8f + t * 2f)))) * dp
            p.color = withAlpha(0.45f + 0.55f * amp.coerceIn(0f, 1f))
            c.drawCircle(x, y, dr, p)
        }
    }

    /** Solo para la vista previa de Ajustes: círculo que late. */
    private fun drawOrb(c: Canvas, t: Float) {
        val cx = width / 2f; val cy = height / 2f
        val half = min(width, height) / 2f
        p.style = Paint.Style.FILL
        p.color = withAlpha(0.22f)
        c.drawCircle(cx, cy, half * (0.62f + amp * 0.34f), p)
        p.color = withAlpha(0.9f)
        c.drawCircle(cx, cy, half * (0.42f + amp * 0.12f + 0.02f * sin(t * 2f)), p)
    }
}
