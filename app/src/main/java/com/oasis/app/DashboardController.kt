package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Páginas "Hoy" y "Mi teléfono" del dashboard, más los puntos de página.
 * Cada tarjeta es interactiva: abre el ajuste correspondiente, llama, repite un comando, etc.
 */
class DashboardController(
    private val act: AppCompatActivity,
    private val phone: PhoneActionsModule,
    private val onTalk: () -> Unit,
    private val onReplay: (String, Map<String, String>) -> Unit,
    private val onFlash: () -> Unit = {}
) {
    companion object {
        const val REQ_CALENDAR = 201
        const val REQ_CONTACTS = 202
    }

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val meter = SystemStats.TrafficMeter()
    private val prefs = act.getSharedPreferences("oasis_settings", AppCompatActivity.MODE_PRIVATE)

    private lateinit var pager: PageScroller
    private lateinit var dots: PageIndicator
    private lateinit var pageToday: View
    private lateinit var pageSystem: View
    private lateinit var pageTools: View

    private var alive = true
    private var running = false
    private var tick = 0

    // Colores del tema actual (los fija MainActivity.applyTheme)
    private var fg = 0xFF1C1C1E.toInt()
    private var fg2 = 0xFF6B6B70.toInt()
    private var card = 0x8CFFFFFF.toInt()
    private var accent = 0xFFF57C00.toInt()
    private var shadow = false

    private fun <T : View> v(id: Int): T = act.findViewById(id)
    private fun tv(id: Int): TextView = act.findViewById(id)

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            updateTraffic()
            if (tick % 5 == 0) updateQuickStats()
            tick++
            handler.postDelayed(this, 1000)
        }
    }

    // ====================== ENLACE ======================
    fun bind() {
        pager = v(R.id.pager)
        dots = v(R.id.page_dots)
        pageToday = v(R.id.page_today)
        pageSystem = v(R.id.page_system)
        pageTools = v(R.id.page_tools)
        bindTools()
        pager.onPageChanged = {
            updateDots(); refreshForPage(); playEntrance()
            // la barrita "desliza hacia arriba" solo tiene sentido en Inicio
            v<View>(R.id.swipe_handle).animate().alpha(if (atHome && UiPrefs.swipeUp(act)) 1f else 0f).setDuration(200).start()
        }
        pager.onScrollX = { x -> parallax(x); if (pager.width > 0) dots.position = x / pager.width.toFloat() }
        listOf(R.id.card_battery, R.id.card_net, R.id.card_ram, R.id.card_rom, R.id.card_weather)
            .forEach { Fx.pressable(v(it), 0.97f) }
        Fx.pressable(v(R.id.btn_talk), 0.97f)

        tv(R.id.tv_today_date).text =
            SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault()).format(Date()).replaceFirstChar { it.uppercase() }

        v<View>(R.id.card_battery).setOnClickListener { open(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) }
        v<View>(R.id.card_net).setOnClickListener { open(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        v<View>(R.id.card_ram).setOnClickListener { open(Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)) }
        v<View>(R.id.card_rom).setOnClickListener { open(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) }

        v<View>(R.id.card_weather).setOnClickListener {
            WeatherRepo.promptCity(act) { refreshWeather(true) }
        }
        v<View>(R.id.card_weather).setOnLongClickListener { refreshWeather(true); true }
        v<View>(R.id.card_agenda).setOnClickListener {
            if (!TodayData.has(act, Manifest.permission.READ_CALENDAR)) {
                ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.READ_CALENDAR), REQ_CALENDAR)
            } else openCalendar()
        }
        tv(R.id.tv_favs_empty).setOnClickListener {
            if (!TodayData.has(act, Manifest.permission.READ_CONTACTS))
                ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.READ_CONTACTS), REQ_CONTACTS)
        }
        v<View>(R.id.btn_talk).setOnClickListener { onTalk() }
        v<View>(R.id.btn_voice_clear).setOnClickListener { VoiceHistory.clear(act); refreshVoice() }

        val range = v<MaterialButtonToggleGroup>(R.id.range_group)
        range.check(intArrayOf(R.id.range_0, R.id.range_1, R.id.range_2)[prefs.getInt("netapps_range", 0).coerceIn(0, 2)])
        range.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                prefs.edit().putInt("netapps_range", intArrayOf(R.id.range_0, R.id.range_1, R.id.range_2).indexOf(id)).apply()
                refreshNetApps()
            }
        }
        v<View>(R.id.btn_usage_perm).setOnClickListener { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        applyPagePrefs()
    }

    // ====================== CICLO ======================
    fun onResume() {
        alive = true
        parallax(pager.scrollX)
        running = true
        tick = 0
        updateQuickStats()
        handler.removeCallbacks(loop)
        handler.post(loop)
        refreshForPage()
    }

    fun onPause() {
        running = false
        handler.removeCallbacks(loop)
    }

    fun destroy() {
        alive = false
        onPause()
        io.shutdownNow()
    }

    /** Muestra u oculta páginas según Ajustes y reconstruye los puntos. */
    fun applyPagePrefs() {
        pageToday.visibility = if (prefs.getBoolean("page_today", true)) View.VISIBLE else View.GONE
        pageSystem.visibility = if (prefs.getBoolean("page_system", true)) View.VISIBLE else View.GONE
        pageTools.visibility = if (prefs.getBoolean("page_tools", true)) View.VISIBLE else View.GONE
        pager.relayoutPages()
        if (pager.page >= pager.pageCount()) pager.goTo(0, false)
        updateDots()
        parallax(pager.scrollX)
    }

    fun goHome() = pager.goTo(0, true)
    val atHome: Boolean get() = pager.page == 0

    private fun currentPageView(): View? {
        val strip = v<ViewGroup>(R.id.pages)
        var n = 0
        for (i in 0 until strip.childCount) {
            val c = strip.getChildAt(i)
            if (c.visibility == View.GONE) continue
            if (n == pager.page) return c
            n++
        }
        return null
    }

    private fun refreshForPage() {
        when (currentPageView()?.id) {
            R.id.page_today -> { refreshWeather(false); refreshToday(); refreshVoice() }
            R.id.page_system -> { updateQuickStats(); refreshNetApps() }
            R.id.page_tools -> refreshTools()
        }
    }

    // ====================== PUNTOS ======================
    private fun updateDots() {
        val n = pager.pageCount()
        dots.count = n
        dots.color = fg
        dots.visibility = if (n <= 1) View.GONE else View.VISIBLE
        if (pager.width > 0) dots.position = pager.scrollX / pager.width.toFloat() else dots.position = pager.page.toFloat()
    }

    // ====================== ANIMACIONES ======================
    /** Transición entre páginas según Ajustes: profundidad, cubo, pila, carrusel, fundido o deslizar. */
    private fun parallax(x: Int) {
        val w = pager.width
        if (w == 0) return
        val mode = if (Fx.enabled(act)) UiPrefs.pageFx(act) else 5
        val strip = v<ViewGroup>(R.id.pages)
        var i = 0
        for (k in 0 until strip.childCount) {
            val c = strip.getChildAt(k)
            if (c.visibility == View.GONE) continue
            val dd = (i * w - x).toFloat() / w      // 0 = esta página está centrada
            val a = abs(dd).coerceAtMost(1f)
            c.alpha = 1f; c.scaleX = 1f; c.scaleY = 1f; c.translationX = 0f
            c.rotationY = 0f; c.pivotX = w / 2f; c.pivotY = c.height / 2f
            c.cameraDistance = 14000f * act.resources.displayMetrics.density
            when (mode) {
                0 -> { // profundidad
                    c.alpha = 1f - a * 0.55f
                    c.scaleX = 1f - a * 0.07f; c.scaleY = 1f - a * 0.07f
                    c.translationX = dd * w * 0.18f
                }
                1 -> { // cubo
                    c.pivotX = if (dd < 0) w.toFloat() else 0f
                    c.rotationY = -60f * dd
                    c.alpha = 1f - a * 0.3f
                }
                2 -> { // pila: la página que sale se queda quieta debajo
                    if (dd < 0) {
                        c.translationX = -dd * w
                        c.scaleX = 1f - a * 0.1f; c.scaleY = 1f - a * 0.1f
                        c.alpha = 1f - a
                    }
                }
                3 -> { // carrusel
                    c.rotationY = -35f * dd
                    c.scaleX = 1f - a * 0.15f; c.scaleY = 1f - a * 0.15f
                    c.alpha = 1f - a * 0.4f
                }
                4 -> { // fundido
                    c.translationX = -dd * w
                    c.alpha = 1f - a
                }
                // 5: deslizar normal
            }
            i++
        }
    }

    /** Al llegar a una página, sus elementos entran en cascada. */
    private fun playEntrance() {
        val page = currentPageView() ?: return
        if (page.id == R.id.page_home) { Fx.popIn(v(R.id.orb_holder)); return }
        val cards = ArrayList<View>()
        collectCards(page, cards)
        Fx.cascade(cards)
    }

    private fun collectCards(view: View, out: MutableList<View>) {
        if (view.tag == "card") { out.add(view); return }
        if (view is ViewGroup) for (i in 0 until view.childCount) collectCards(view.getChildAt(i), out)
    }

    private val shownPct = HashMap<Int, Int>()
    private fun setPct(id: Int, value: Int) {
        Fx.countUp(tv(id), shownPct[id] ?: 0, value, "%")
        shownPct[id] = value
    }

    // ====================== TEMA ======================
    fun restyle(fg: Int, fg2: Int, card: Int, accent: Int, shadow: Boolean) {
        this.fg = fg; this.fg2 = fg2; this.card = card; this.accent = accent; this.shadow = shadow
        style(v(R.id.page_today))
        style(v(R.id.page_system))
        style(v(R.id.page_tools))
        updateDots()
        // las filas dinámicas ya creadas se repintan
        listOf(R.id.ll_agenda, R.id.ll_favs, R.id.ll_voice, R.id.ll_netapps, R.id.ll_notes, R.id.ll_screen).forEach { style(v(it)) }
        updateQuickStats()
    }

    private fun style(view: View) {
        if (view.tag == "card") {
            if (Fx.enabled(act)) {
                view.backgroundTintList = null
                view.background = Fx.glass(act, card, 22f, androidx.core.graphics.ColorUtils.calculateLuminance(fg) > 0.5)
            } else {
                view.setBackgroundResource(R.drawable.bg_card)
                view.backgroundTintList = ColorStateList.valueOf(card)
            }
        }
        when (view) {
            is android.widget.SeekBar -> {
                view.thumbTintList = ColorStateList.valueOf(accent)
                view.progressTintList = ColorStateList.valueOf(accent)
            }
            is MaterialButton -> {
                if (view.id == R.id.btn_talk || view.id == R.id.btn_usage_perm || view.id == R.id.btn_usage_perm2 || view.id == R.id.btn_add_note) {
                    view.backgroundTintList = ColorStateList.valueOf(accent)
                    view.setTextColor(0xFFFFFFFF.toInt())
                } else {
                    view.setTextColor(fg)
                    view.rippleColor = ColorStateList.valueOf((fg and 0x00FFFFFF) or 0x22000000)
                    if (view.parent is MaterialButtonToggleGroup) view.strokeColor = ColorStateList.valueOf((fg and 0x00FFFFFF) or 0x55000000)
                }
            }
            is TextView -> {
                view.setTextColor(if (view.tag == "strong") fg else fg2)
                if (shadow) view.setShadowLayer(6f, 0f, 2f, 0x66000000) else view.setShadowLayer(0f, 0f, 0f, 0)
            }
            is ProgressBar -> {
                view.progressTintList = ColorStateList.valueOf(accent)
                view.progressBackgroundTintList = ColorStateList.valueOf((fg and 0x00FFFFFF) or 0x33000000)
            }
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) style(view.getChildAt(i))
    }

    private fun barColor(pct: Int) = when {
        pct >= 90 -> 0xFFEF4444.toInt()
        pct >= 75 -> 0xFFF59E0B.toInt()
        else -> accent
    }

    // ====================== MI TELÉFONO ======================
    private fun updateQuickStats() {
        if (!alive) return
        try {
            val b = SystemStats.battery(act)
            if (shownPct[R.id.tv_batt_pct] != b.pct) setPct(R.id.tv_batt_pct, b.pct)
            v<ProgressBar>(R.id.pb_batt).apply {
                setProgress(b.pct, Fx.enabled(act))
                progressTintList = ColorStateList.valueOf(if (b.pct <= 15 && !b.charging) 0xFFEF4444.toInt() else accent)
            }
            tv(R.id.tv_batt_info).text =
                (if (b.charging) "⚡ Cargando" else if (b.pct <= 15) "Batería baja" else "En uso") +
                    String.format(Locale.getDefault(), " · %.0f°C", b.tempC)

            val n = SystemStats.network(act)
            tv(R.id.tv_net_type).text = n.type
            tv(R.id.tv_net_link).text = when {
                n.type == "Wi-Fi" && n.linkMbps > 0 -> "Enlace ${n.linkMbps} Mbps" + if (n.band.isNotEmpty()) " · ${n.band}" else ""
                n.type == "Wi-Fi" -> "Señal ${n.level + 1}/5"
                else -> ""
            }

            val r = SystemStats.ram(act)
            if (shownPct[R.id.tv_ram_pct] != r.pct) setPct(R.id.tv_ram_pct, r.pct)
            v<ProgressBar>(R.id.pb_ram).apply { setProgress(r.pct, Fx.enabled(act)); progressTintList = ColorStateList.valueOf(barColor(r.pct)) }
            tv(R.id.tv_ram_info).text = "${SystemStats.fmtGb(r.used)} usados de ${SystemStats.fmtGb(r.total)} · libre ${SystemStats.fmtGb(r.total - r.used)}"

            val s = SystemStats.storage()
            if (shownPct[R.id.tv_rom_pct] != s.pct) setPct(R.id.tv_rom_pct, s.pct)
            v<ProgressBar>(R.id.pb_rom).apply { setProgress(s.pct, Fx.enabled(act)); progressTintList = ColorStateList.valueOf(barColor(s.pct)) }
            tv(R.id.tv_rom_info).text = "${SystemStats.fmtGb(s.used)} usados de ${SystemStats.fmtGb(s.total)} · libre ${SystemStats.fmtGb(s.total - s.used)}"
        } catch (e: Exception) {
            android.util.Log.w("Dashboard", "updateQuickStats: ${e.message}")
        }
    }

    private fun updateTraffic() {
        val r = meter.sample() ?: return
        tv(R.id.tv_net_down).text = "↓ ${SystemStats.fmtRate(r.first)}"
        tv(R.id.tv_net_up).text = "↑ ${SystemStats.fmtRate(r.second)}"
    }

    fun refreshNetApps() {
        val list = v<LinearLayout>(R.id.ll_netapps)
        val hint = tv(R.id.tv_netapps_hint)
        val perm = v<View>(R.id.btn_usage_perm)
        if (!SystemStats.hasUsageAccess(act)) {
            list.removeAllViews()
            hint.visibility = View.VISIBLE
            hint.text = "Para ver qué apps gastan más datos, permite el acceso a uso de apps a OASIS."
            perm.visibility = View.VISIBLE
            return
        }
        perm.visibility = View.GONE
        hint.visibility = View.VISIBLE
        hint.text = "Calculando…"
        val days = intArrayOf(1, 7, 30)[prefs.getInt("netapps_range", 0).coerceIn(0, 2)]
        io.execute {
            val apps = try { SystemStats.topNetworkApps(act, days, 5) } catch (e: Exception) { emptyList() }
            handler.post {
                if (!alive) return@post
                list.removeAllViews()
                if (apps.isEmpty()) {
                    hint.text = "Sin datos todavía para este periodo."
                } else {
                    hint.visibility = View.GONE
                    val max = apps.first().bytes.coerceAtLeast(1)
                    apps.forEach { a ->
                        val icon = try { act.packageManager.getApplicationIcon(a.pkg) } catch (e: Exception) { null }
                        addRow(list, icon, a.label, "${a.bytes * 100 / max}% del mayor", SystemStats.fmtBytes(a.bytes)) {
                            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                        }
                    }
                }
            }
        }
    }

    // ====================== HOY ======================
    fun refreshWeather(force: Boolean) {
        val city = WeatherRepo.city(act)
        if (city.isBlank()) { renderWeather(null, true); return }
        WeatherRepo.cached(act)?.let { renderWeather(it, false) }
        WeatherRepo.refresh(act, force) { w -> if (alive) renderWeather(w, false) }
    }

    private fun renderWeather(w: WeatherRepo.Weather?, noCity: Boolean) {
        if (noCity) {
            tv(R.id.tv_weather_icon).text = "🌤️"
            tv(R.id.tv_weather_city).text = "Clima"
            tv(R.id.tv_weather_desc).text = "Toca para elegir tu ciudad"
            tv(R.id.tv_weather_range).text = ""
            tv(R.id.tv_weather_temp).text = "--°"
            return
        }
        if (w == null) {
            tv(R.id.tv_weather_desc).text = "Sin conexión · toca para cambiar la ciudad"
            return
        }
        val (icon, desc) = WeatherRepo.describe(w.code)
        tv(R.id.tv_weather_icon).text = icon
        tv(R.id.tv_weather_city).text = WeatherRepo.displayName(act)
        tv(R.id.tv_weather_desc).text = desc
        tv(R.id.tv_weather_range).text = "Máx ${w.max}° · Mín ${w.min}°"
        tv(R.id.tv_weather_temp).text = "${w.temp}°"
    }

    fun refreshToday() {
        val agenda = v<LinearLayout>(R.id.ll_agenda)
        val agendaEmpty = tv(R.id.tv_agenda_empty)
        val favs = v<LinearLayout>(R.id.ll_favs)
        val favsEmpty = tv(R.id.tv_favs_empty)
        io.execute {
            val events = TodayData.events(act)
            val fav = TodayData.favorites(act)
            handler.post {
                if (!alive) return@post
                agenda.removeAllViews()
                if (!TodayData.has(act, Manifest.permission.READ_CALENDAR)) {
                    agendaEmpty.visibility = View.VISIBLE
                    agendaEmpty.text = "Toca para permitir ver tu calendario"
                } else if (events.isEmpty()) {
                    agendaEmpty.visibility = View.VISIBLE
                    agendaEmpty.text = "Nada programado en los próximos 7 días 🎉"
                } else {
                    agendaEmpty.visibility = View.GONE
                    events.forEach { e -> addRow(agenda, null, e.title, whenLabel(e), "") { openCalendar() } }
                }

                favs.removeAllViews()
                if (!TodayData.has(act, Manifest.permission.READ_CONTACTS)) {
                    favsEmpty.visibility = View.VISIBLE
                    favsEmpty.text = "Toca aquí para permitir ver tus contactos"
                } else if (fav.isEmpty()) {
                    favsEmpty.visibility = View.VISIBLE
                    favsEmpty.text = "Marca contactos con ⭐ en la app Contactos y aparecerán aquí"
                } else {
                    favsEmpty.visibility = View.GONE
                    fav.chunked(4).forEach { rowFavs -> favs.addView(favRow(rowFavs)) }
                }
            }
        }
    }

    private fun favRow(items: List<TodayData.Fav>): LinearLayout {
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        items.forEach { f ->
            val t = act.layoutInflater.inflate(R.layout.tile_contact, row, false)
            val initial = t.findViewById<TextView>(R.id.tile_initial)
            initial.text = f.name.trim().take(1).uppercase()
            initial.backgroundTintList = ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x55000000)
            t.findViewById<TextView>(R.id.tile_name).text = f.name.substringBefore(' ')
            t.contentDescription = "Llamar a ${f.name}"
            t.setOnClickListener { phone.dialNumber(f.number, f.name) }
            t.setOnLongClickListener { phone.openSmsToContact(f.name); true }
            Fx.pressable(t, 0.9f)
            style(t)
            FontManager.apply(act, t)
            row.addView(t)
        }
        // relleno para que los últimos mosaicos no se estiren
        repeat(4 - items.size) {
            row.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        }
        return row
    }

    fun refreshVoice() {
        val list = v<LinearLayout>(R.id.ll_voice)
        val empty = tv(R.id.tv_voice_empty)
        list.removeAllViews()
        val items = VoiceHistory.list(act).take(5)
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        v<View>(R.id.btn_voice_clear).visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        val tf = DateFormat.getTimeFormat(act)
        items.forEach { e ->
            addRow(list, null, e.label, tf.format(Date(e.ts)) + " · toca para repetir", "↻") {
                onReplay(e.command, e.params)
            }
        }
    }

    // ====================== UTILIDADES ======================
    private fun whenLabel(e: TodayData.Event): String {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        val ev = Calendar.getInstance().apply { timeInMillis = e.begin }
        val day = when {
            ev.get(Calendar.YEAR) == year && ev.get(Calendar.DAY_OF_YEAR) == today -> "Hoy"
            ev.get(Calendar.YEAR) == year && ev.get(Calendar.DAY_OF_YEAR) == today + 1 -> "Mañana"
            else -> SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(e.begin))
        }
        return if (e.allDay) "$day · Todo el día" else "$day · " + DateFormat.getTimeFormat(act).format(Date(e.begin))
    }

    private fun addRow(parent: LinearLayout, icon: Drawable?, title: String, sub: String?, value: String?, onValue: (() -> Unit)? = null, onClick: () -> Unit) {
        val row = act.layoutInflater.inflate(R.layout.row_item, parent, false)
        val iv = row.findViewById<ImageView>(R.id.ro_icon)
        if (icon != null) iv.setImageDrawable(icon) else iv.visibility = View.GONE
        row.findViewById<TextView>(R.id.ro_title).text = title
        row.findViewById<TextView>(R.id.ro_sub).apply { text = sub.orEmpty(); visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE }
        row.findViewById<TextView>(R.id.ro_value).apply { text = value.orEmpty(); visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE }
        row.setOnClickListener { onClick() }
        if (onValue != null) row.findViewById<TextView>(R.id.ro_value).apply {
            setOnClickListener { onValue() }
            setPadding(24, 16, 8, 16)
        }
        Fx.pressable(row, 0.97f)
        style(row)
        FontManager.apply(act, row)
        parent.addView(row)
    }

    private fun openCalendar() {
        val uri = Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}")
        open(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun open(intent: Intent) {
        try {
            act.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(act, "Esta opción no está disponible en tu teléfono", Toast.LENGTH_SHORT).show()
        }
    }

    // ====================== HERRAMIENTAS ======================
    private fun bindTools() {
        listOf(R.id.btn_flash, R.id.btn_timer, R.id.btn_alarm, R.id.btn_sos).forEach { Fx.pressable(v(it), 0.93f) }
        v<View>(R.id.btn_flash).setOnClickListener { onFlash() }
        v<View>(R.id.btn_alarm).setOnClickListener { open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
        v<View>(R.id.btn_timer).setOnClickListener { askTimer() }
        v<View>(R.id.btn_sos).setOnClickListener {
            val n = prefs.getString("sos_number", "") ?: ""
            if (n.isBlank()) askSos() else phone.dialNumber(n, "emergencia")
        }
        v<View>(R.id.btn_sos).setOnLongClickListener { askSos(); true }
        v<View>(R.id.btn_usage_perm2).setOnClickListener { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }

        val am = act.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        fun seek(id: Int, stream: Int) {
            val sb = v<android.widget.SeekBar>(id)
            sb.max = am.getStreamMaxVolume(stream)
            sb.progress = am.getStreamVolume(stream)
            sb.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: android.widget.SeekBar, p: Int, fromUser: Boolean) {
                    if (fromUser) try { am.setStreamVolume(stream, p, 0) } catch (e: Exception) {
                        Toast.makeText(act, "No se puede cambiar ahora (¿No molestar activo?)", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onStartTrackingTouch(s: android.widget.SeekBar) {}
                override fun onStopTrackingTouch(s: android.widget.SeekBar) {}
            })
        }
        seek(R.id.sb_media, android.media.AudioManager.STREAM_MUSIC)
        seek(R.id.sb_ring, android.media.AudioManager.STREAM_RING)

        val add = {
            val et = v<android.widget.EditText>(R.id.et_note)
            NotesStore.add(act, et.text.toString()); et.setText(""); refreshNotes()
        }
        v<View>(R.id.btn_add_note).setOnClickListener { add() }
        v<android.widget.EditText>(R.id.et_note).setOnEditorActionListener { _, _, _ -> add(); true }
    }

    private fun refreshTools() {
        val am = act.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        v<android.widget.SeekBar>(R.id.sb_media).progress = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        v<android.widget.SeekBar>(R.id.sb_ring).progress = am.getStreamVolume(android.media.AudioManager.STREAM_RING)
        refreshNotes()
        refreshScreenTime()
    }

    private fun refreshNotes() {
        val list = v<LinearLayout>(R.id.ll_notes)
        list.removeAllViews()
        val notes = NotesStore.list(act)
        notes.forEachIndexed { i, n ->
            val title = if (n.done) android.text.SpannableString(n.text).apply {
                setSpan(android.text.style.StrikethroughSpan(), 0, length, 0)
            } else n.text
            addRow(list, null, title.toString(), null, "✕", onValue = { NotesStore.remove(act, i); refreshNotes() }) {
                NotesStore.toggle(act, i); refreshNotes()
            }
            if (n.done) (list.getChildAt(list.childCount - 1).findViewById<TextView>(R.id.ro_title)).apply {
                paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG; alpha = 0.55f
            }
        }
    }

    private fun refreshScreenTime() {
        val list = v<LinearLayout>(R.id.ll_screen)
        val hint = tv(R.id.tv_screen_hint)
        val perm = v<View>(R.id.btn_usage_perm2)
        if (!SystemStats.hasUsageAccess(act)) {
            list.removeAllViews()
            hint.visibility = View.VISIBLE
            hint.text = "Permite el acceso a uso de apps para ver cuánto usaste cada una hoy."
            perm.visibility = View.VISIBLE
            return
        }
        perm.visibility = View.GONE
        io.execute {
            val top = SystemStats.topScreenTime(act, 5)
            handler.post {
                if (!alive) return@post
                list.removeAllViews()
                hint.visibility = if (top.isEmpty()) View.VISIBLE else View.GONE
                hint.text = "Aún no hay uso registrado hoy."
                top.forEach { (a, ms) ->
                    val icon = try { act.packageManager.getApplicationIcon(a.pkg) } catch (e: Exception) { null }
                    addRow(list, icon, a.label, null, SystemStats.fmtDuration(ms)) {
                        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")))
                    }
                }
            }
        }
    }

    private fun askTimer() {
        val input = android.widget.EditText(act).apply {
            hint = "Minutos (ej. 10)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER; setPadding(56, 32, 56, 16)
        }
        androidx.appcompat.app.AlertDialog.Builder(act).setTitle("Temporizador").setView(input)
            .setPositiveButton("Iniciar") { _, _ ->
                val m = input.text.toString().toIntOrNull() ?: 0
                if (m > 0) open(Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                    .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, m * 60)
                    .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "OASIS"))
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun askSos() {
        val input = android.widget.EditText(act).apply {
            hint = "Número de emergencia"; inputType = android.text.InputType.TYPE_CLASS_PHONE
            setText(prefs.getString("sos_number", "")); setPadding(56, 32, 56, 16)
        }
        androidx.appcompat.app.AlertDialog.Builder(act).setTitle("¿A quién llamar en una emergencia?").setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit().putString("sos_number", input.text.toString().trim()).apply()
                Toast.makeText(act, "Guardado. Toca 🆘 para llamar; mantén presionado para cambiarlo.", Toast.LENGTH_LONG).show()
            }.setNegativeButton("Cancelar", null).show()
    }

    /** MainActivity lo llama cuando el usuario responde un permiso del dashboard. */
    fun onPermissionResult(requestCode: Int) {
        if (requestCode == REQ_CALENDAR || requestCode == REQ_CONTACTS) refreshToday()
    }
}
