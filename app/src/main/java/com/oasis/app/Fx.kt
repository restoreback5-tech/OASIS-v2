package com.oasis.app

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import kotlin.math.PI
import kotlin.math.sin

/** Efectos "liquid glass" y animaciones. Todo se apaga con el ajuste o si el teléfono quitó las animaciones. */
object Fx {

    fun enabled(ctx: Context): Boolean {
        if (!UiPrefs.glassFx(ctx)) return false
        return try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        } catch (e: Exception) { true }
    }

    /**
     * Cristal: relleno translúcido + brillo superior + borde de luz.
     * [dark] = true cuando el fondo es oscuro (el borde y el brillo son más discretos).
     */
    fun glass(ctx: Context, base: Int, radiusDp: Float, dark: Boolean): Drawable {
        val d = ctx.resources.displayMetrics.density
        val r = radiusDp * d
        val fill = GradientDrawable().apply { cornerRadius = r; setColor(base) }
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(if (dark) 0x30FFFFFF else 0x70FFFFFF, 0x00FFFFFF, if (dark) 0x12FFFFFF else 0x26FFFFFF)
        ).apply { cornerRadius = r }
        val rim = GradientDrawable().apply {
            cornerRadius = r
            setColor(Color.TRANSPARENT)
            setStroke((1.2f * d).toInt().coerceAtLeast(1), if (dark) 0x40FFFFFF else 0xA0FFFFFF.toInt())
        }
        return LayerDrawable(arrayOf(fill, sheen, rim))
    }

    /** Al tocar se hunde y al soltar rebota como un resorte. No intercepta el toque. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View, scale: Float = 0.94f) {
        v.setOnTouchListener { view, e ->
            if (enabled(view.context)) when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(scale).scaleY(scale)
                    .setDuration(110).setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f)
                    .setDuration(420).setInterpolator(OvershootInterpolator(3.2f)).start()
            }
            false
        }
    }

    /** Entrada en cascada: cada vista sube, crece y aparece con un pequeño rebote. */
    fun cascade(views: List<View>, startDelay: Long = 0L) {
        if (views.isEmpty()) return
        val on = enabled(views[0].context)
        val dy = views[0].resources.displayMetrics.density * 36f
        views.forEachIndexed { i, v ->
            v.animate().cancel()
            if (!on) { v.alpha = 1f; v.translationY = 0f; v.scaleX = 1f; v.scaleY = 1f; return@forEachIndexed }
            v.alpha = 0f; v.translationY = dy; v.scaleX = 0.96f; v.scaleY = 0.96f
            v.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                .setStartDelay(startDelay + i * 70L).setDuration(560)
                .setInterpolator(OvershootInterpolator(1.0f)).start()
        }
    }

    /** Aparece "inflándose" (usado por el orbe al volver a Inicio). */
    fun popIn(v: View) {
        v.animate().cancel()
        if (!enabled(v.context)) { v.scaleX = 1f; v.scaleY = 1f; v.alpha = 1f; return }
        v.scaleX = 0.8f; v.scaleY = 0.8f; v.alpha = 0.4f
        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(600)
            .setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    /** El número "corre" desde su valor anterior hasta el nuevo. */
    fun countUp(tv: TextView, from: Int, to: Int, suffix: String) {
        if (!enabled(tv.context) || from == to) { tv.text = "$to$suffix"; return }
        ValueAnimator.ofInt(from, to).apply {
            duration = 700
            interpolator = DecelerateInterpolator()
            addUpdateListener { tv.text = "${it.animatedValue as Int}$suffix" }
            start()
        }
    }

    /** Anima el ancho de una vista (puntos de página que se estiran). */
    fun animateWidth(v: View, toPx: Int) {
        val lp = v.layoutParams
        if (!enabled(v.context)) { lp.width = toPx; v.layoutParams = lp; return }
        ValueAnimator.ofInt(lp.width, toPx).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.6f)
            addUpdateListener { lp.width = it.animatedValue as Int; v.layoutParams = lp }
            start()
        }
    }
}

/** Orbe "líquido": al escuchar late de forma orgánica y emite ondas. */
class OrbFx(private val orb: View, private val ring1: View, private val ring2: View) {
    private var wobble: ValueAnimator? = null
    private var waves: List<ValueAnimator> = emptyList()

    fun start() {
        if (!Fx.enabled(orb.context)) return
        stop()
        wobble = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
            duration = 1700
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                orb.scaleX = 1f + 0.05f * sin(t)
                orb.scaleY = 1f + 0.05f * sin(t + (PI / 2).toFloat())
            }
            start()
        }
        waves = listOf(ring1 to 0L, ring2 to 800L).map { (ring, delay) ->
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1600
                startDelay = delay
                repeatCount = ValueAnimator.INFINITE
                interpolator = DecelerateInterpolator()
                addUpdateListener { a ->
                    val f = a.animatedValue as Float
                    ring.scaleX = 1f + 0.7f * f
                    ring.scaleY = 1f + 0.7f * f
                    ring.alpha = 0.6f * (1f - f)
                }
                start()
            }
        }
    }

    fun stop() {
        wobble?.cancel(); wobble = null
        waves.forEach { it.cancel() }; waves = emptyList()
        orb.scaleX = 1f; orb.scaleY = 1f
        ring1.alpha = 0f; ring2.alpha = 0f
    }
}
