package com.oasis.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Base: se repinta solo mientras la vista es visible en pantalla (ahorra batería cuando está fuera de la página). */
abstract class LiveView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    protected val dp = resources.displayMetrics.density
    private val vis = Rect()
    protected open val frameMs = 33L
    protected val t0 = System.currentTimeMillis()

    protected fun now() = (System.currentTimeMillis() - t0)

    /** Llamar al final de onDraw. */
    protected fun loop() {
        if (!isAttachedToWindow) return
        postInvalidateDelayed(if (getGlobalVisibleRect(vis)) frameMs else 400L)
    }
}

// ====================== RELOJ ANALÓGICO ======================
class AnalogClockView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LiveView(c, a) {
    var ink = 0xFF222222.toInt()
        set(v) { field = v; invalidate() }
    var accent = 0xFFF57C00.toInt()
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 4 * dp
        p.style = Paint.Style.STROKE
        p.strokeWidth = 2f * dp
        p.color = ink; p.alpha = 70
        canvas.drawCircle(cx, cy, r, p)
        for (i in 0 until 60) {
            val len = if (i % 5 == 0) 8 * dp else 3 * dp
            val a = i * 6.0 * PI / 180.0
            p.strokeWidth = if (i % 5 == 0) 2.2f * dp else 1f * dp
            p.alpha = if (i % 5 == 0) 200 else 90
            canvas.drawLine(
                cx + ((r - len) * sin(a)).toFloat(), cy - ((r - len) * cos(a)).toFloat(),
                cx + (r * sin(a)).toFloat(), cy - (r * cos(a)).toFloat(), p
            )
        }
        val c = Calendar.getInstance()
        val sec = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000f
        val mins = c.get(Calendar.MINUTE) + sec / 60f
        val hour = (c.get(Calendar.HOUR) % 12) + mins / 60f
        hand(canvas, cx, cy, hour * 30f, r * 0.5f, 5f * dp, ink, 255)
        hand(canvas, cx, cy, mins * 6f, r * 0.76f, 3.5f * dp, ink, 255)
        hand(canvas, cx, cy, sec * 6f, r * 0.86f, 1.6f * dp, accent, 255)
        p.style = Paint.Style.FILL
        p.color = accent; p.alpha = 255
        canvas.drawCircle(cx, cy, 4.5f * dp, p)
        loop()
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float, color: Int, alpha: Int) {
        val a = deg * PI / 180.0
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.color = color; p.alpha = alpha
        c.drawLine(cx, cy, cx + (len * sin(a)).toFloat(), cy - (len * cos(a)).toFloat(), p)
    }
}

// ====================== ANILLO DE BATERÍA ======================
class RingView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LiveView(c, a) {
    var ink = 0xFF222222.toInt()
        set(v) { field = v; invalidate() }
    var accent = 0xFFF57C00.toInt()
        set(v) { field = v; invalidate() }
    private var shown = 0f
    private var charging = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; textAlign = Paint.Align.CENTER }
    private val oval = RectF()
    private var anim: ValueAnimator? = null

    fun setValue(pct: Int, isCharging: Boolean) {
        charging = isCharging
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, pct.toFloat()).apply {
            duration = 800
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val stroke = 11f * dp
        val r = min(width, height) / 2f - stroke
        oval.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.color = ink; p.alpha = 45
        canvas.drawArc(oval, 0f, 360f, false, p)
        val col = when {
            shown <= 15f && !charging -> 0xFFEF4444.toInt()
            shown <= 30f && !charging -> 0xFFF59E0B.toInt()
            charging -> 0xFF22C55E.toInt()
            else -> accent
        }
        if (charging) {
            val pulse = (0.5f + 0.5f * sin(now() / 350.0)).toFloat()
            p.color = col; p.alpha = (40 + 60 * pulse).toInt(); p.strokeWidth = stroke + 6f * dp * pulse
            canvas.drawArc(oval, -90f, 360f * shown / 100f, false, p)
        }
        p.strokeWidth = stroke
        p.color = col; p.alpha = 255
        canvas.drawArc(oval, -90f, 360f * shown / 100f, false, p)
        p.style = Paint.Style.FILL
        p.color = ink; p.alpha = 255
        p.textSize = 26f * dp
        p.isFakeBoldText = true
        canvas.drawText("${shown.toInt()}%", cx, cy + 4f * dp, p)
        p.isFakeBoldText = false
        p.textSize = 11f * dp
        p.alpha = 160
        canvas.drawText(if (charging) "⚡ cargando" else "batería", cx, cy + 20f * dp, p)
        if (charging) loop() else if (anim?.isRunning == true) invalidate()
    }
}

// ====================== CLIMA ANIMADO ======================
class WeatherAnimView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LiveView(c, a) {
    var code = 0
        set(v) { field = v; invalidate() }
    var ink = 0xFF222222.toInt()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dropX = FloatArray(16) { (it * 37 % 100) / 100f }
    private val dropV = FloatArray(16) { 0.6f + (it * 53 % 40) / 100f }
    private val dropO = FloatArray(16) { (it * 29 % 100) / 100f }

    private fun kind(): Int = when (code) {
        0, 1 -> 0                      // despejado
        2 -> 1                         // parcial
        3 -> 2                         // nublado
        45, 48 -> 3                    // niebla
        in 51..67, in 80..82 -> 4      // lluvia
        in 71..77, 85, 86 -> 5         // nieve
        in 95..99 -> 6                 // tormenta
        else -> 1
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = now()
        val k = kind()
        val night = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it < 6 || it >= 20 }
        when (k) {
            0 -> sun(canvas, w * 0.5f, h * 0.5f, min(w, h) * 0.22f, t, night)
            1 -> { sun(canvas, w * 0.4f, h * 0.42f, min(w, h) * 0.17f, t, night); cloud(canvas, w * 0.58f + 6 * dp * sin(t / 1800.0).toFloat(), h * 0.6f, w * 0.34f, 0xFFFFFFFF.toInt()) }
            2 -> { cloud(canvas, w * 0.38f + 8 * dp * sin(t / 2200.0).toFloat(), h * 0.45f, w * 0.3f, 0xFFE5E7EB.toInt()); cloud(canvas, w * 0.62f - 8 * dp * sin(t / 2000.0).toFloat(), h * 0.62f, w * 0.36f, 0xFFFFFFFF.toInt()) }
            3 -> fog(canvas, w, h, t)
            4 -> { cloud(canvas, w * 0.5f, h * 0.36f, w * 0.4f, 0xFFCBD5E1.toInt()); rain(canvas, w, h, t, 0xFF60A5FA.toInt()) }
            5 -> { cloud(canvas, w * 0.5f, h * 0.36f, w * 0.4f, 0xFFE2E8F0.toInt()); snow(canvas, w, h, t) }
            else -> {
                cloud(canvas, w * 0.5f, h * 0.36f, w * 0.42f, 0xFF64748B.toInt()); rain(canvas, w, h, t, 0xFF93C5FD.toInt())
                if ((t / 120) % 28 == 0L) { p.style = Paint.Style.FILL; p.color = 0xFFFDE68A.toInt(); p.alpha = 110; canvas.drawRect(0f, 0f, w, h, p) }
            }
        }
        loop()
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, t: Long, night: Boolean) {
        val core = if (night) 0xFFE2E8F0.toInt() else 0xFFFBBF24.toInt()
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy, r * 2.2f, (core and 0x00FFFFFF) or 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r * 2.2f, p)
        p.shader = null
        if (!night) {
            p.style = Paint.Style.STROKE; p.strokeWidth = 3f * dp; p.strokeCap = Paint.Cap.ROUND; p.color = core
            for (i in 0 until 8) {
                val a = (i * 45.0 + t * 0.02) * PI / 180.0
                c.drawLine(cx + (r * 1.35f * cos(a)).toFloat(), cy + (r * 1.35f * sin(a)).toFloat(),
                    cx + (r * 1.75f * cos(a)).toFloat(), cy + (r * 1.75f * sin(a)).toFloat(), p)
            }
        }
        p.style = Paint.Style.FILL; p.color = core
        c.drawCircle(cx, cy, r, p)
    }

    private fun cloud(c: Canvas, cx: Float, cy: Float, w: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = color
        val r = w * 0.28f
        c.drawCircle(cx - w * 0.22f, cy, r, p)
        c.drawCircle(cx + w * 0.05f, cy - r * 0.55f, r * 1.2f, p)
        c.drawCircle(cx + w * 0.28f, cy + r * 0.1f, r * 0.9f, p)
        c.drawRoundRect(cx - w * 0.5f, cy + r * 0.2f, cx + w * 0.5f, cy + r * 1.0f, r * 0.4f, r * 0.4f, p)
    }

    private fun rain(c: Canvas, w: Float, h: Float, t: Long, color: Int) {
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * dp; p.strokeCap = Paint.Cap.ROUND; p.color = color
        for (i in dropX.indices) {
            val f = ((t / 900f) * dropV[i] + dropO[i]) % 1f
            val x = w * (0.2f + 0.6f * dropX[i])
            val y = h * (0.5f + 0.5f * f)
            c.drawLine(x, y, x - 3 * dp, y + 9 * dp, p)
        }
    }

    private fun snow(c: Canvas, w: Float, h: Float, t: Long) {
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt()
        for (i in dropX.indices) {
            val f = ((t / 2600f) * dropV[i] + dropO[i]) % 1f
            val x = w * (0.2f + 0.6f * dropX[i]) + 6 * dp * sin(f * 12.0).toFloat()
            c.drawCircle(x, h * (0.5f + 0.5f * f), (1.5f + dropV[i] * 1.5f) * dp, p)
        }
    }

    private fun fog(c: Canvas, w: Float, h: Float, t: Long) {
        p.style = Paint.Style.FILL
        for (i in 0 until 4) {
            p.color = 0xFFE5E7EB.toInt(); p.alpha = 150
            val off = 14 * dp * sin(t / 1500.0 + i).toFloat()
            c.drawRoundRect(w * 0.18f + off, h * (0.3f + i * 0.14f), w * 0.82f + off, h * (0.3f + i * 0.14f) + 7 * dp, 4 * dp, 4 * dp, p)
        }
    }
}

// ====================== RED EN VIVO ======================
class SparklineView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : View(c, a) {
    var lineDown = 0xFF3B82F6.toInt()
        set(v) { field = v; invalidate() }
    var lineUp = 0xFFF59E0B.toInt()
        set(v) { field = v; invalidate() }
    private val n = 60
    private val down = FloatArray(n)
    private val up = FloatArray(n)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val d = resources.displayMetrics.density

    fun push(bytesDown: Long, bytesUp: Long) {
        System.arraycopy(down, 1, down, 0, n - 1); down[n - 1] = bytesDown.toFloat()
        System.arraycopy(up, 1, up, 0, n - 1); up[n - 1] = bytesUp.toFloat()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val top = max(60_000f, max(down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f) * 1.15f)
        series(canvas, up, top, w, h, lineUp, false)
        series(canvas, down, top, w, h, lineDown, true)
    }

    private fun series(c: Canvas, s: FloatArray, top: Float, w: Float, h: Float, color: Int, fill: Boolean) {
        path.reset()
        val step = w / (n - 1)
        for (i in 0 until n) {
            val x = i * step
            val y = h - (s[i] / top) * (h - 4 * d) - 2 * d
            if (i == 0) path.moveTo(x, y) else {
                val px = (i - 1) * step
                val py = h - (s[i - 1] / top) * (h - 4 * d) - 2 * d
                path.quadTo(px, py, (px + x) / 2f, (py + y) / 2f)
            }
        }
        if (fill) {
            val f = Path(path)
            f.lineTo(w, h); f.lineTo(0f, h); f.close()
            p.style = Paint.Style.FILL
            p.shader = LinearGradient(0f, 0f, 0f, h, (color and 0x00FFFFFF) or 0x66000000, (color and 0x00FFFFFF), Shader.TileMode.CLAMP)
            c.drawPath(f, p)
            p.shader = null
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = color
        c.drawPath(path, p)
    }
}

// ====================== MINI CALENDARIO ======================
class MonthView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LiveView(c, a) {
    override val frameMs = 60L
    var ink = 0xFF222222.toInt()
        set(v) { field = v; invalidate() }
    var accent = 0xFFF57C00.toInt()
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    override fun onDraw(canvas: Canvas) {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val first = (cal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val fdow = cal.firstDayOfWeek
        val offset = (first.get(Calendar.DAY_OF_WEEK) - fdow + 7) % 7
        val letters = arrayOf("D", "L", "M", "M", "J", "V", "S")
        val cw = width / 7f
        val ch = height / 7f
        p.textSize = 11f * dp
        p.style = Paint.Style.FILL
        for (i in 0 until 7) {
            p.color = ink; p.alpha = 120
            canvas.drawText(letters[(fdow - 1 + i) % 7], cw * (i + 0.5f), ch * 0.7f, p)
        }
        for (day in 1..days) {
            val idx = offset + day - 1
            val col = idx % 7
            val row = idx / 7 + 1
            val x = cw * (col + 0.5f)
            val y = ch * (row + 0.5f)
            if (day == today) {
                val pulse = (0.5f + 0.5f * sin(now() / 500.0)).toFloat()
                p.color = accent; p.alpha = (50 + 60 * pulse).toInt()
                canvas.drawCircle(x, y - 3 * dp, min(cw, ch) * (0.46f + 0.1f * pulse), p)
                p.color = accent; p.alpha = 255
                canvas.drawCircle(x, y - 3 * dp, min(cw, ch) * 0.42f, p)
                p.color = 0xFFFFFFFF.toInt()
            } else { p.color = ink; p.alpha = 215 }
            canvas.drawText(day.toString(), x, y, p)
        }
        loop()
    }
}

// ====================== VASO DE AGUA ======================
class WaterView @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : LiveView(c, a) {
    var water = 0xFF38BDF8.toInt()
        set(v) { field = v; invalidate() }
    var ink = 0xFF222222.toInt()
        set(v) { field = v; invalidate() }
    private var level = 0f
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glass = Path()
    private val wave = Path()
    private val box = RectF()

    fun setFraction(f: Float) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(level, f.coerceIn(0f, 1f)).apply {
            duration = 900
            interpolator = DecelerateInterpolator()
            addUpdateListener { level = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val gw = w * 0.56f
        val l = (w - gw) / 2f
        val top = h * 0.06f
        val bottom = h * 0.94f
        glass.reset()
        glass.moveTo(l, top); glass.lineTo(l + gw, top)
        glass.lineTo(l + gw * 0.86f, bottom - 8 * dp)
        glass.quadTo(l + gw * 0.84f, bottom, l + gw * 0.7f, bottom)
        glass.lineTo(l + gw * 0.3f, bottom)
        glass.quadTo(l + gw * 0.16f, bottom, l + gw * 0.14f, bottom - 8 * dp)
        glass.close()

        canvas.save()
        canvas.clipPath(glass)
        val y = bottom - (bottom - top) * level
        val t = now() / 600.0
        for (layer in 0 until 2) {
            wave.reset()
            wave.moveTo(l - 4, bottom + 4)
            var x = l - 4
            while (x <= l + gw + 4) {
                wave.lineTo(x, y + (4.5f * dp * sin(t * (1 + layer * 0.4) + x / (14 * dp) + layer * 2)).toFloat())
                x += 4f
            }
            wave.lineTo(l + gw + 4, bottom + 4)
            wave.close()
            p.style = Paint.Style.FILL
            p.color = water; p.alpha = if (layer == 0) 120 else 200
            canvas.drawPath(wave, p)
        }
        canvas.restore()

        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * dp; p.color = ink; p.alpha = 200
        canvas.drawPath(glass, p)
        loop()
    }
}
