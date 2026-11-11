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

/**
 * Páginas "Hoy" y "Mi teléfono" del dashboard, más los puntos de página.
 * Cada tarjeta es interactiva: abre el ajuste correspondiente, llama, repite un comando, etc.
 */
class DashboardController(
    private val act: AppCompatActivity,
    private val phone: PhoneActionsModule,
    private val onTalk: () -> Unit,
    private val onReplay: (String, Map<String, String>) -> Unit
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
    private lateinit var dots: LinearLayout
    private lateinit var pageToday: View
    private lateinit var pageSystem: View

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
        pager.onPageChanged = { updateDots(); refreshForPage() }

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
        pager.relayoutPages()
        if (pager.page >= pager.pageCount()) pager.goTo(0, false)
        updateDots()
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
        }
    }

    // ====================== PUNTOS ======================
    private fun updateDots() {
        val n = pager.pageCount()
        dots.removeAllViews()
        dots.visibility = if (n <= 1) View.GONE else View.VISIBLE
        val d = act.resources.displayMetrics.density
        for (i in 0 until n) {
            val dot = View(act)
            val size = (8 * d).toInt()
            dot.layoutParams = LinearLayout.LayoutParams(size, size).apply { marginStart = (4 * d).toInt(); marginEnd = (4 * d).toInt() }
            dot.setBackgroundResource(R.drawable.bg_dot)
            dot.backgroundTintList = ColorStateList.valueOf(fg)
            dot.alpha = if (i == pager.page) 0.95f else 0.35f
            dots.addView(dot)
        }
    }

    // ====================== TEMA ======================
    fun restyle(fg: Int, fg2: Int, card: Int, accent: Int, shadow: Boolean) {
        this.fg = fg; this.fg2 = fg2; this.card = card; this.accent = accent; this.shadow = shadow
        style(v(R.id.page_today))
        style(v(R.id.page_system))
        updateDots()
        // las filas dinámicas ya creadas se repintan
        listOf(R.id.ll_agenda, R.id.ll_favs, R.id.ll_voice, R.id.ll_netapps).forEach { style(v(it)) }
        updateQuickStats()
    }

    private fun style(view: View) {
        if (view.tag == "card") view.backgroundTintList = ColorStateList.valueOf(card)
        when (view) {
            is MaterialButton -> {
                if (view.id == R.id.btn_talk || view.id == R.id.btn_usage_perm) {
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
            tv(R.id.tv_batt_pct).text = "${b.pct}%"
            v<ProgressBar>(R.id.pb_batt).apply {
                progress = b.pct
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
            tv(R.id.tv_ram_pct).text = "${r.pct}%"
            v<ProgressBar>(R.id.pb_ram).apply { progress = r.pct; progressTintList = ColorStateList.valueOf(barColor(r.pct)) }
            tv(R.id.tv_ram_info).text = "${SystemStats.fmtGb(r.used)} usados de ${SystemStats.fmtGb(r.total)} · libre ${SystemStats.fmtGb(r.total - r.used)}"

            val s = SystemStats.storage()
            tv(R.id.tv_rom_pct).text = "${s.pct}%"
            v<ProgressBar>(R.id.pb_rom).apply { progress = s.pct; progressTintList = ColorStateList.valueOf(barColor(s.pct)) }
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

    private fun addRow(parent: LinearLayout, icon: Drawable?, title: String, sub: String?, value: String?, onClick: () -> Unit) {
        val row = act.layoutInflater.inflate(R.layout.row_item, parent, false)
        val iv = row.findViewById<ImageView>(R.id.ro_icon)
        if (icon != null) iv.setImageDrawable(icon) else iv.visibility = View.GONE
        row.findViewById<TextView>(R.id.ro_title).text = title
        row.findViewById<TextView>(R.id.ro_sub).apply { text = sub.orEmpty(); visibility = if (sub.isNullOrEmpty()) View.GONE else View.VISIBLE }
        row.findViewById<TextView>(R.id.ro_value).apply { text = value.orEmpty(); visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE }
        row.setOnClickListener { onClick() }
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

    /** MainActivity lo llama cuando el usuario responde un permiso del dashboard. */
    fun onPermissionResult(requestCode: Int) {
        if (requestCode == REQ_CALENDAR || requestCode == REQ_CONTACTS) refreshToday()
    }
}
