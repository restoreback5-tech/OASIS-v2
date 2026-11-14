package com.oasis.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * Contenedor de la página «Widgets»: cada tarjeta ocupa el ancho completo o la mitad
 * (dos mitades seguidas van lado a lado). Mantén presionada una tarjeta y arrástrala para moverla.
 */
class CellFlow @JvmOverloads constructor(c: Context, a: AttributeSet? = null) : ViewGroup(c, a) {

    private val dp = resources.displayMetrics.density
    private val gap = (12 * dp).toInt()
    private val slop = ViewConfiguration.get(c).scaledTouchSlop

    /** Tarjetas que se pueden mover (las demás vistas son fijas). */
    var draggable: Set<View> = emptySet()
    /** Tarjetas a la mitad del ancho. */
    val halfViews = HashSet<View>()
    /** Se llama al soltar una tarjeta en otro lugar. */
    var onReordered: (() -> Unit)? = null
    /** Se llama al soltar sin mover (para mostrar el menú de la tarjeta). */
    var onTapHeld: ((View) -> Unit)? = null

    private val pos = HashMap<View, IntArray>()
    private var dragView: View? = null
    private var grabDx = 0f
    private var grabDy = 0f
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var moved = false
    private var ignoreCancel = false
    private var lastSwap = 0L
    private var pending: Runnable? = null

    // ---------- medida y posición ----------
    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams = MarginLayoutParams(context, attrs)
    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams = MarginLayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    override fun generateLayoutParams(p: ViewGroup.LayoutParams): ViewGroup.LayoutParams = MarginLayoutParams(p)
    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is MarginLayoutParams

    private fun measureKid(k: View, w: Int, exactH: Int) {
        val lp = k.layoutParams as MarginLayoutParams
        val ws = MeasureSpec.makeMeasureSpec(max(0, w - lp.leftMargin - lp.rightMargin), MeasureSpec.EXACTLY)
        val hs = when {
            exactH >= 0 -> MeasureSpec.makeMeasureSpec(max(0, exactH - lp.topMargin - lp.bottomMargin), MeasureSpec.EXACTLY)
            lp.height >= 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
            else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        }
        k.measure(ws, hs)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val total = MeasureSpec.getSize(wSpec)
        val avail = total - paddingLeft - paddingRight
        val halfW = (avail - gap) / 2
        pos.clear()
        var y = paddingTop
        var i = 0
        val n = childCount
        while (i < n) {
            val a = getChildAt(i)
            if (a.visibility == GONE) { i++; continue }
            var b: View? = null
            var next = i + 1
            if (a in halfViews) {
                var j = i + 1
                while (j < n && getChildAt(j).visibility == GONE) j++
                if (j < n && getChildAt(j) in halfViews) { b = getChildAt(j); next = j + 1 }
            }
            val la = a.layoutParams as MarginLayoutParams
            if (b == null) {
                measureKid(a, avail, -1)
                pos[a] = intArrayOf(paddingLeft + la.leftMargin, y + la.topMargin)
                y += a.measuredHeight + la.topMargin + la.bottomMargin
            } else {
                val lb = b.layoutParams as MarginLayoutParams
                measureKid(a, halfW, -1)
                measureKid(b, halfW, -1)
                val rowH = max(a.measuredHeight + la.topMargin + la.bottomMargin, b.measuredHeight + lb.topMargin + lb.bottomMargin)
                measureKid(a, halfW, rowH)
                measureKid(b, halfW, rowH)
                pos[a] = intArrayOf(paddingLeft + la.leftMargin, y + la.topMargin)
                pos[b] = intArrayOf(paddingLeft + halfW + gap + lb.leftMargin, y + lb.topMargin)
                y += rowH
            }
            i = next
        }
        setMeasuredDimension(total, resolveSize(y + paddingBottom, hSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val k = getChildAt(i)
            if (k.visibility == GONE) continue
            val p = pos[k] ?: continue
            k.layout(p[0], p[1], p[0] + k.measuredWidth, p[1] + k.measuredHeight)
        }
    }

    // ---------- arrastrar ----------
    private fun cellAt(x: Float, y: Float): View? {
        for (i in childCount - 1 downTo 0) {
            val k = getChildAt(i)
            if (k.visibility != VISIBLE || k !in draggable) continue
            if (x >= k.left && x <= k.right && y >= k.top && y <= k.bottom) return k
        }
        return null
    }

    private fun cancelPending() {
        pending?.let { removeCallbacks(it) }
        pending = null
    }

    private fun armLongPress() {
        cancelPending()
        val r = Runnable { pending = null; startDrag() }
        pending = r
        postDelayed(r, 380L)
    }

    private fun startDrag() {
        val v = cellAt(downX, downY) ?: return
        dragView = v
        moved = false
        grabDx = downX - v.left
        grabDy = downY - v.top
        lastRawX = rawFromLocalX(downX)
        lastRawY = rawFromLocalY(downY)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        parent?.requestDisallowInterceptTouchEvent(true)
        v.animate().cancel()
        v.translationZ = 16 * dp
        v.animate().scaleX(1.04f).scaleY(1.04f).setDuration(140).start()
        // Los hijos dejan de recibir este toque (botones, textos…)
        ignoreCancel = true
        val c = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0)
        dispatchTouchEvent(c)
        c.recycle()
        ignoreCancel = false
        post(autoScroll)
    }

    private fun rawFromLocalX(x: Float): Float { val l = IntArray(2); getLocationOnScreen(l); return x + l[0] }
    private fun rawFromLocalY(y: Float): Float { val l = IntArray(2); getLocationOnScreen(l); return y + l[1] }

    /** Coloca la tarjeta arrastrada bajo el dedo y reordena si cruza a otra. */
    private fun follow() {
        val v = dragView ?: return
        val l = IntArray(2); getLocationOnScreen(l)
        val fx = lastRawX - l[0]
        val fy = lastRawY - l[1]
        v.translationX = fx - grabDx - v.left
        v.translationY = fy - grabDy - v.top
        if (abs(fx - downX) > slop || abs(fy - downY) > slop * 2) moved = true
        if (SystemClock.uptimeMillis() - lastSwap < 220) return
        val cx = v.left + v.translationX + v.width / 2f
        val cy = v.top + v.translationY + v.height / 2f
        val from = indexOfChild(v)
        for (i in 0 until childCount) {
            val s = getChildAt(i)
            if (s === v || s.visibility != VISIBLE || s !in draggable) continue
            if (cx < s.left || cx > s.right || cy < s.top || cy > s.bottom) continue
            val after = i > from
            val mid = s.top + s.height * 0.5f
            val half = s.width < width * 0.75f && v.width < width * 0.75f
            val crossed = if (half) true else if (after) cy > mid else cy < mid
            if (!crossed) break
            lastSwap = SystemClock.uptimeMillis()
            reflow { removeView(v); addView(v, i) }
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            break
        }
    }

    private val autoScroll = object : Runnable {
        override fun run() {
            val v = dragView ?: return
            val sv = parent as? ScrollView
            if (sv != null) {
                val l = IntArray(2); sv.getLocationOnScreen(l)
                val inSv = lastRawY - l[1]
                val step = (14 * dp).toInt()
                val edge = 90 * dp
                var d = 0
                if (inSv < edge) d = -step else if (inSv > sv.height - edge) d = step
                if (d != 0) { sv.scrollBy(0, d); follow() }
            }
            if (v === dragView) postOnAnimation(this)
        }
    }

    private fun endDrag() {
        val v = dragView ?: return
        dragView = null
        removeCallbacks(autoScroll)
        v.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f).setDuration(200)
            .withEndAction { v.translationZ = 0f }.start()
        if (moved) onReordered?.invoke() else onTapHeld?.invoke(v)
    }

    /** Hace [block] (cambiar el orden) y desliza suavemente las demás tarjetas a su nuevo sitio. */
    private fun reflow(block: () -> Unit) {
        val old = HashMap<View, IntArray>()
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c.visibility != GONE) old[c] = intArrayOf(c.left, c.top)
        }
        block()
        viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                viewTreeObserver.removeOnPreDrawListener(this)
                for ((c, o) in old) {
                    if (c === dragView) continue
                    val dx = o[0] - c.left
                    val dy = o[1] - c.top
                    if (dx != 0 || dy != 0) {
                        c.animate().cancel()
                        c.translationX = dx.toFloat()
                        c.translationY = dy.toFloat()
                        c.animate().translationX(0f).translationY(0f).setDuration(240).start()
                    }
                }
                follow()
                return true
            }
        })
        requestLayout()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; downTime = ev.downTime
                if (cellAt(ev.x, ev.y) != null && draggable.isNotEmpty()) armLongPress()
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) { if (dragView == null) cancelPending() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (dragView == null) cancelPending()
        }
        return dragView != null && ev.actionMasked != MotionEvent.ACTION_CANCEL
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> return cellAt(ev.x, ev.y) != null
            MotionEvent.ACTION_MOVE -> {
                if (dragView != null) { lastRawX = ev.rawX; lastRawY = ev.rawY; follow(); return true }
                if (abs(ev.x - downX) > slop || abs(ev.y - downY) > slop) cancelPending()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragView != null) { if (!ignoreCancel) endDrag(); return true }
                cancelPending()
            }
        }
        return dragView != null
    }

    override fun onDetachedFromWindow() {
        cancelPending()
        removeCallbacks(autoScroll)
        super.onDetachedFromWindow()
    }
}

/** Qué tarjetas hay en pantalla, en qué orden y de qué tamaño. Se guarda en preferencias. */
object WidgetLayoutStore {
    class Slot(val key: String, var half: Boolean)

    private const val K = "widgets_layout"
    val HALF_DEFAULT = setOf("w_cell_clock", "w_cell_battery")
    private val DEFAULT = listOf("w_cell_shortcuts", "w_cell_clock", "w_cell_battery", "w_card_weather")

    fun load(prefs: SharedPreferences, order: List<String>): MutableList<Slot> {
        val raw = prefs.getString(K, null)
        if (raw != null) {
            try {
                val a = JSONArray(raw)
                val out = ArrayList<Slot>()
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    val k = o.getString("k")
                    if (k in order && out.none { it.key == k }) out.add(Slot(k, o.optBoolean("h", false)))
                }
                return out
            } catch (_: Exception) { /* cae a lo de abajo */ }
        }
        // Migración: si había widgets quitados, se respetan; si no, empieza con lo básico
        val hidden = prefs.getString("widgets_hidden", null)
        val keys = if (hidden != null) {
            val h = hidden.split(",").filter { it.isNotBlank() }.toSet()
            order.filter { it !in h }
        } else DEFAULT.filter { it in order }
        return keys.mapTo(ArrayList()) { Slot(it, it in HALF_DEFAULT) }
    }

    fun save(prefs: SharedPreferences, slots: List<Slot>) {
        val a = JSONArray()
        slots.forEach { a.put(JSONObject().put("k", it.key).put("h", it.half)) }
        prefs.edit().putString(K, a.toString()).apply()
    }
}

/** Pone en pantalla los widgets elegidos y ofrece la galería, el menú de cada tarjeta y el movimiento. */
class WidgetBoard(
    private val act: Activity,
    private val flow: CellFlow,
    private val cells: List<Pair<Int, String>>,
    private val onAndroidWidget: () -> Unit
) {
    private val prefs = act.getSharedPreferences("oasis_settings", Activity.MODE_PRIVATE)
    private val order = cells.map { act.resources.getResourceEntryName(it.first) }
    private val views = LinkedHashMap<String, View>()
    private val names = HashMap<String, String>()
    private var slots: MutableList<WidgetLayoutStore.Slot>
    private val head: Int

    init {
        cells.forEach { (id, name) ->
            val k = act.resources.getResourceEntryName(id)
            views[k] = act.findViewById(id)
            names[k] = name
        }
        head = views.values.minOf { flow.indexOfChild(it) }
        slots = WidgetLayoutStore.load(prefs, order)
        flow.onReordered = { syncFromViews() }
        flow.onTapHeld = { v -> menu(v) }
        apply()
    }

    private fun keyOf(v: View) = views.entries.firstOrNull { it.value === v }?.key

    private fun info(key: String): Pair<String, String> = when (key) {
        "w_cell_shortcuts" -> "⚡" to "Buenos días, buenas noches y leer con cámara"
        "w_cell_clock" -> "🕐" to "Reloj analógico en vivo"
        "w_cell_battery" -> "🔋" to "Nivel y estado de carga"
        "w_card_weather" -> "⛅" to "Temperatura y pronóstico del día"
        "w_cell_net" -> "📶" to "Velocidad de bajada y subida en vivo"
        "w_cell_month" -> "📅" to "El mes con el día de hoy marcado"
        "w_cell_water" -> "💧" to "Cuenta tus vasos de agua del día"
        "w_cell_music" -> "🎵" to "Controla lo que está sonando"
        "w_cell_safety" -> "🆘" to "SOS, ubicación, datos médicos, sirena y linterna"
        "w_cell_steps" -> "👟" to "Anillo con tu meta de pasos"
        "w_cell_habits" -> "✅" to "Hábitos diarios con rachas"
        "w_cell_meds" -> "💊" to "Avisos de medicinas con hora"
        "w_cell_list" -> "🛒" to "Lista del súper para agregar y tachar"
        "w_cell_bday" -> "🎂" to "Próximos cumpleaños de tus contactos"
        "w_cell_ai" -> "🤖" to "Chatea con la IA y prueba la conexión"
        else -> "▫️" to ""
    }

    /** Ordena las tarjetas según lo guardado: primero las visibles, luego las ocultas. */
    fun apply() {
        views.values.forEach { flow.removeView(it) }
        flow.halfViews.clear()
        var idx = head
        val shown = HashSet<String>()
        for (s in slots) {
            val v = views[s.key] ?: continue
            v.visibility = View.VISIBLE
            v.translationX = 0f; v.translationY = 0f
            flow.addView(v, idx++)
            if (s.half) flow.halfViews.add(v)
            shown.add(s.key)
        }
        for ((k, v) in views) if (k !in shown) { v.visibility = View.GONE; flow.addView(v, idx++) }
        flow.draggable = views.filterKeys { it in shown }.values.toSet()
        act.findViewById<View>(R.id.w_empty).visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        flow.requestLayout()
    }

    private fun save() = WidgetLayoutStore.save(prefs, slots)

    private fun syncFromViews() {
        val byView = (0 until flow.childCount).map { flow.getChildAt(it) }
            .filter { v -> v.visibility == View.VISIBLE && keyOf(v) != null }
        slots = byView.mapNotNull { v -> slots.firstOrNull { views[it.key] === v } }.toMutableList()
        save()
    }

    private fun slotOf(v: View) = slots.firstOrNull { views[it.key] === v }

    // ---------- menú de una tarjeta ----------
    private fun menu(v: View) {
        val s = slotOf(v) ?: return
        val i = slots.indexOf(s)
        val title = names[s.key] ?: "Widget"
        val items = ArrayList<Pair<String, () -> Unit>>()
        items.add((if (s.half) "↔ Ocupar todo el ancho" else "▥ Ocupar la mitad (se junta con otra mitad)") to {
            s.half = !s.half; save(); apply()
        })
        if (i > 0) items.add("↑ Subir" to { slots.removeAt(i); slots.add(i - 1, s); save(); apply() })
        if (i < slots.size - 1) items.add("↓ Bajar" to { slots.removeAt(i); slots.add(i + 1, s); save(); apply() })
        items.add("✕ Quitar de la pantalla" to {
            slots.remove(s); save(); apply()
            Toast.makeText(act, "Quitado. Vuelve a ponerlo desde «＋ Agregar widget».", Toast.LENGTH_SHORT).show()
        })
        AlertDialog.Builder(act).setTitle(title)
            .setItems(items.map { it.first }.toTypedArray()) { _, w -> items[w].second() }
            .setNegativeButton("Cerrar", null).show()
    }

    // ---------- galería ----------
    fun showGallery() {
        val d = act.resources.displayMetrics.density
        val dialog = BottomSheetDialog(act)
        val ink = MaterialColors.getColor(act, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
        val soft = (ink and 0x00FFFFFF) or 0xAA000000.toInt()
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * d).toInt(), (18 * d).toInt(), (20 * d).toInt(), (24 * d).toInt())
        }
        col.addView(TextView(act).apply { text = "Galería de widgets"; textSize = 20f; setTextColor(ink); setTypeface(typeface, android.graphics.Typeface.BOLD) })
        col.addView(TextView(act).apply { text = "Toca uno para ponerlo o quitarlo de tu pantalla."; textSize = 13f; setTextColor(soft); setPadding(0, (4 * d).toInt(), 0, (12 * d).toInt()) })

        fun statusText(k: String) = if (slots.any { it.key == k }) "✓ En pantalla" else "＋ Agregar"
        for (k in order) {
            val (emoji, desc) = info(k)
            val row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (10 * d).toInt(), 0, (10 * d).toInt())
                isClickable = true
            }
            row.addView(TextView(act).apply { text = emoji; textSize = 26f; gravity = Gravity.CENTER; minWidth = (44 * d).toInt() })
            val txt = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            txt.addView(TextView(act).apply { text = names[k]; textSize = 16f; setTextColor(ink) })
            txt.addView(TextView(act).apply { text = desc; textSize = 12f; setTextColor(soft) })
            row.addView(txt, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            val st = TextView(act).apply { text = statusText(k); textSize = 13f; setTextColor(ink); setPadding((8 * d).toInt(), 0, 0, 0) }
            row.addView(st)
            row.setOnClickListener {
                val s = slots.firstOrNull { it.key == k }
                if (s != null) slots.remove(s) else slots.add(WidgetLayoutStore.Slot(k, k in WidgetLayoutStore.HALF_DEFAULT))
                save(); apply()
                st.text = statusText(k)
            }
            col.addView(row)
        }
        col.addView(TextView(act).apply {
            text = "＋ Widgets de otras apps de Android…"
            textSize = 16f; setTextColor(ink)
            setPadding(0, (16 * d).toInt(), 0, (8 * d).toInt())
            setOnClickListener { dialog.dismiss(); onAndroidWidget() }
        })
        col.addView(TextView(act).apply {
            text = "Consejo: en la pantalla de widgets, mantén presionada una tarjeta y arrástrala para moverla. Si la sueltas sin mover, verás más opciones."
            textSize = 12f; setTextColor(soft); setPadding(0, (8 * d).toInt(), 0, 0)
        })
        val sv = ScrollView(act).apply { addView(col) }
        dialog.setContentView(sv)
        dialog.show()
    }
}
