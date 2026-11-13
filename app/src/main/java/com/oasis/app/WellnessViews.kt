package com.oasis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/** Anillo de progreso genérico (pasos, hábitos…): fracción 0..1 con texto grande y pequeño al centro. */
class ProgressRing @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LiveView(c, a) {
    var ink = 0xFF222222.toInt()
        set(v) { field = v; invalidate() }
    var accent = 0xFF22C55E.toInt()
        set(v) { field = v; invalidate() }
    var big = "0"
        set(v) { field = v; invalidate() }
    var small = ""
        set(v) { field = v; invalidate() }
    private var shown = 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; textAlign = Paint.Align.CENTER }
    private val oval = RectF()

    fun setFraction(f: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, f.coerceIn(0f, 1f)).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f; val cy = height / 2f
        val stroke = 10f * dp
        val r = min(width, height) / 2f - stroke
        oval.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE; p.strokeWidth = stroke
        p.color = ink; p.alpha = 40
        canvas.drawArc(oval, 0f, 360f, false, p)
        p.color = accent; p.alpha = 255
        canvas.drawArc(oval, -90f, 360f * shown, false, p)
        p.style = Paint.Style.FILL
        p.color = ink; p.alpha = 255; p.isFakeBoldText = true; p.textSize = 22f * dp
        canvas.drawText(big, cx, cy + 4f * dp, p)
        p.isFakeBoldText = false; p.textSize = 11f * dp; p.alpha = 160
        canvas.drawText(small, cx, cy + 20f * dp, p)
        if (anim?.isRunning == true) invalidate()
    }
}

/** Círculo que se infla, se sostiene y se encoge al ritmo de la respiración (4 s · 4 s · 6 s). */
class BreathView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LiveView(c, a) {
    var tint = 0xFF38BDF8.toInt()
    var ink = 0xFF222222.toInt()
    var onPhase: ((String) -> Unit)? = null
    private var lastPhase = ""
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    companion object { const val IN = 4000L; const val HOLD = 4000L; const val OUT = 6000L }

    override fun onDraw(canvas: Canvas) {
        val t = now() % (IN + HOLD + OUT)
        val (label, k) = when {
            t < IN -> "Inhala" to t / IN.toFloat()
            t < IN + HOLD -> "Sostén" to 1f
            else -> "Exhala" to 1f - (t - IN - HOLD) / OUT.toFloat()
        }
        if (label != lastPhase) { lastPhase = label; onPhase?.invoke(label) }
        val cx = width / 2f; val cy = height / 2f
        val max = min(width, height) / 2f - 8 * dp
        val r = max * (0.45f + 0.55f * k)
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 1.25f, (tint and 0x00FFFFFF) or 0x99000000.toInt(), tint and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, r * 1.25f, p)
        p.shader = null
        p.color = tint; p.alpha = 210
        canvas.drawCircle(cx, cy, r, p)
        p.color = 0xFFFFFFFF.toInt(); p.textSize = 22f * dp; p.isFakeBoldText = true
        canvas.drawText(label, cx, cy + 8f * dp, p)
        loop()
    }
}
