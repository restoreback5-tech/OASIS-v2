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

    private val paintParticle = Paint().apply {
        color = Color.parseColor("#00e5ff")
        isAntiAlias = true
    }

    private val paintLine = Paint().apply {
        color = Color.parseColor("#00e5ff")
        strokeWidth = 1.5f
        isAntiAlias = true
    }

    private val particles = mutableListOf<Particle>()
    private val particleCount = 100 // Reducido a 100 para máxima estabilidad
    private val connectRadius = 120f
    private var time = 0f

    // IMPORTANTE: No inicializamos partículas aquí. Esperamos a onSizeChanged.

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            initParticles(w.toFloat(), h.toFloat())
        }
    }

    private fun initParticles(w: Float, h: Float) {
        particles.clear()
        for (i in 0 until particleCount) {
            particles.add(Particle(w, h))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Seguridad: Si no hay partículas, no dibujar nada y esperar
        if (particles.isEmpty()) {            invalidate()
            return
        }

        // Fondo
        canvas.drawColor(Color.parseColor("#050510"))
        time += 0.016f // ~60fps

        // Dibujar líneas primero (para que queden detrás)
        for (i in particles.indices) {
            val p1 = particles[i]
            p1.update()
            // Rebote
            if (p1.x < 0 || p1.x > width) p1.vx *= -1
            if (p1.y < 0 || p1.y > height) p1.vy *= -1
            
            for (j in i + 1 until particles.size) {
                val p2 = particles[j]
                val dx = p1.x - p2.x
                val dy = p1.y - p2.y
                val dist = sqrt(dx * dx + dy * dy)

                if (dist < connectRadius) {
                    val alpha = ((1 - dist / connectRadius) * 0.5f * 255).toInt()
                    paintLine.alpha = alpha.coerceIn(0, 255)
                    canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paintLine)
                }
            }
        }

        // Dibujar puntos
        for (p in particles) {
            canvas.drawCircle(p.x, p.y, p.radius, paintParticle)
        }

        invalidate()
    }

    inner class Particle(private val w: Float, private val h: Float) {
        var x = Random.nextFloat() * w
        var y = Random.nextFloat() * h
        var vx = (Random.nextFloat() - 0.5f) * 2f
        var vy = (Random.nextFloat() - 0.5f) * 2f
        val radius = 2f + Random.nextFloat() * 2f

        fun update() {
            x += vx
            y += vy
        }
    }}
