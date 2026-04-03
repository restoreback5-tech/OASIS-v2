package com.oasis.app
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.sqrt
import kotlin.random.Random

class NeuralOrbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintDot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00E5FF.toInt() }
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF00E5FF.toInt(); strokeWidth = 2f }
    private val dots = ArrayList<Dot>()
    private val count = 80
    private val range = 150f
    private var ready = false

    init { setWillNotDraw(false) }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && !ready) {
            dots.clear()
            for (i in 0 until count) dots.add(Dot(w.toFloat(), h.toFloat()))
            ready = true
            postInvalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!ready || width <= 0 || height <= 0) { postInvalidate(); return }
        
        canvas.drawColor(0xFF050510.toInt())
        
        for (i in 0 until dots.size) {
            val a = dots[i]
            a.move()
            for (j in i + 1 until dots.size) {
                val b = dots[j]
                val dx = a.x - b.x; val dy = a.y - b.y
                val dist = sqrt(dx*dx + dy*dy)
                if (dist < range) {
                    paintLine.alpha = ((1 - dist/range) * 180).toInt()
                    canvas.drawLine(a.x, a.y, b.x, b.y, paintLine)
                }
            }
        }
        for (d in dots) canvas.drawCircle(d.x, d.y, 3f, paintDot)
        postInvalidate()
    }

    private class Dot(val w: Float, val h: Float) {
        var x = Random.nextFloat() * w
        var y = Random.nextFloat() * h
        var vx = (Random.nextFloat() - 0.5f) * 3
        var vy = (Random.nextFloat() - 0.5f) * 3
        fun move() {
            x += vx; y += vy
            if (x < 0 || x > w) vx *= -1
            if (y < 0 || y > h) vy *= -1
        }
    }
}
