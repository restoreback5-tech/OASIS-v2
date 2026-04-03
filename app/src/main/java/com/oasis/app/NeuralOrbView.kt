package com.oasis.app

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.*

class NeuralOrbView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paintParticle = Paint().apply {
        color = Color.parseColor("#00e5ff") // Cyan neón
        isAntiAlias = true
    }

    private val paintLine = Paint().apply {
        color = Color.parseColor("#8000e5ff") // Cyan semi-transparente
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    private val particles = mutableListOf<Particle>()
    private val particleCount = 150 // Menos que 500 para rendimiento inicial
    private val connectRadius = 100f
    private var time = 0f

    init {
        initParticles()
    }

    private fun initParticles() {
        particles.clear()
        val w = if (width > 0) width.toFloat() else 1080f
        val h = if (height > 0) height.toFloat() else 1920f
        for (i in 0 until particleCount) {
            particles.add(Particle(w, h))
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initParticles()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Fondo oscuro para contraste
        canvas.drawColor(Color.parseColor("#050510"))

        time += 0.01f

        // Actualizar y dibujar partículas
        for (p in particles) {
            p.update(time)
            p.draw(canvas, paintParticle)
        }

        // Dibujar conexiones
        for (i in particles.indices) {
            for (j in i + 1 until particles.size) {
                val p1 = particles[i]
                val p2 = particles[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val dist = sqrt(dx * dx + dy * dy)

                if (dist < connectRadius) {
                    val alpha = (1 - dist / connectRadius) * 0.6f
                    paintLine.alpha = (alpha * 255).toInt()
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paintLine)
                }
            }
        }

        invalidate() // Animación continua
    }

    inner class Particle(private val viewWidth: Float, private val viewHeight: Float) {
        var x = Math.random().toFloat() * viewWidth
        var y = Math.random().toFloat() * viewHeight
        private val vx = ((Math.random() - 0.5) * 1.5).toFloat()
        private val vy = ((Math.random() - 0.5) * 1.5).toFloat()
        private val radius = 2f + Math.random().toFloat() * 2f

        fun update(t: Float) {
            x += vx
            y += vy

            // Rebote en bordes
            if (x < 0 || x > viewWidth) x = viewWidth / 2
            if (y < 0 || y > viewHeight) y = viewHeight / 2
        }

        fun draw(canvas: Canvas, paint: Paint) {
            canvas.drawCircle(x, y, radius, paint)
        }
    }
}
