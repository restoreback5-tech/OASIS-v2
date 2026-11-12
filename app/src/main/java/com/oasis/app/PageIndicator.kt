package com.oasis.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Indicador de páginas: puntos pequeños y una "oruga" que se desliza siguiendo tu dedo.
 * Mide siempre lo mismo (nunca queda un óvalo suelto).
 */
class PageIndicator @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var count = 1
        set(v) { field = v.coerceAtLeast(1); requestLayout(); invalidate() }
    var position = 0f
        set(v) { field = v; invalidate() }
    var color = 0xFFFFFFFF.toInt()
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val r = 3.5f * d
    private val gap = 16f * d
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private fun cx(i: Int) = paddingLeft + r + i * gap

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = (paddingLeft + paddingRight + 2 * r + (count - 1) * gap).toInt()
        val h = (2 * r + paddingTop + paddingBottom).toInt() + (4 * d).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val cy = height / 2f
        paint.color = color
        paint.alpha = 90
        for (i in 0 until count) canvas.drawCircle(cx(i), cy, r, paint)

        val p = position.coerceIn(0f, (count - 1).toFloat())
        val i = floor(p).toInt()
        val f = p - i
        val x0 = cx(i)
        val x1 = cx(min(i + 1, count - 1))
        val head = x0 + (x1 - x0) * min(1f, f * 2f)
        val tail = x0 + (x1 - x0) * max(0f, f * 2f - 1f)
        paint.alpha = 235
        rect.set(tail - r, cy - r, head + r, cy + r)
        canvas.drawRoundRect(rect, r, r, paint)
    }
}
