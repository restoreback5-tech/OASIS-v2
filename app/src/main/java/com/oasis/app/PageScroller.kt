package com.oasis.app

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import kotlin.math.abs

/**
 * Contenedor horizontal con "páginas" que se deslizan y se alinean solas.
 * Su único hijo es un LinearLayout horizontal; cada hijo visible de ése es una página
 * y ocupa exactamente el ancho de la pantalla. Todo vive en una sola jerarquía de vistas,
 * así que findViewById funciona igual que en una pantalla normal.
 */
class PageScroller @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : HorizontalScrollView(context, attrs) {

    var onPageChanged: ((Int) -> Unit)? = null
    var onScrollX: ((Int) -> Unit)? = null
    var page = 0
        private set

    private var downPage = 0
    private var tracker: VelocityTracker? = null

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
    }

    private fun strip(): ViewGroup? = getChildAt(0) as? ViewGroup

    fun pageCount(): Int {
        val s = strip() ?: return 1
        var n = 0
        for (i in 0 until s.childCount) if (s.getChildAt(i).visibility != GONE) n++
        return n.coerceAtLeast(1)
    }

    private fun nearest(): Int =
        if (width == 0) 0 else ((scrollX + width / 2) / width).coerceIn(0, pageCount() - 1)

    /** Reajusta el ancho de las páginas (llamar tras mostrar u ocultar alguna). */
    fun relayoutPages() {
        val s = strip() ?: return
        if (width == 0) return
        for (i in 0 until s.childCount) {
            val c = s.getChildAt(i)
            val lp = c.layoutParams
            if (lp.width != width) { lp.width = width; c.layoutParams = lp }
        }
        s.requestLayout()
    }

    fun goTo(index: Int, smooth: Boolean = true) {
        val i = index.coerceIn(0, pageCount() - 1)
        val x = i * width
        if (smooth) smoothScrollTo(x, 0) else scrollTo(x, 0)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        relayoutPages()
        post { scrollTo(page * width, 0) }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrollX?.invoke(l)
        val n = nearest()
        if (n != page) { page = n; onPageChanged?.invoke(n) }
    }

    private fun track(ev: MotionEvent) {
        if (tracker == null) tracker = VelocityTracker.obtain()
        tracker?.addMovement(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            tracker?.recycle(); tracker = null
            downPage = nearest()
        }
        track(ev)
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        val handled = super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            var vx = 0f
            tracker?.let { it.computeCurrentVelocity(1000); vx = it.xVelocity; it.recycle() }
            tracker = null
            val target = if (abs(vx) > 600f) downPage + (if (vx < 0) 1 else -1) else nearest()
            goTo(target, true)
        }
        return handled
    }
}
