package com.oasis.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Página «Widgets»: vistas animadas propias + widgets reales de Android (AppWidgetHost). */
class WidgetsController(private val act: Activity) {

    companion object {
        const val REQ_BIND = 301
        const val REQ_CONFIGURE = 302
        private const val HOST_ID = 1024
        private const val GOAL = 8
    }

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val prefs = act.getSharedPreferences("oasis_settings", Activity.MODE_PRIVATE)
    private val meter = SystemStats.TrafficMeter()
    private val host = AppWidgetHost(act, HOST_ID)
    private val manager = AppWidgetManager.getInstance(act)

    private var running = false
    private var tick = 0
    private var fg = 0xFF1C1C1E.toInt()
    private var accent = 0xFFF57C00.toInt()
    private var pendingId = -1

    private fun <T : View> v(id: Int): T = act.findViewById(id)
    private fun tv(id: Int): TextView = act.findViewById(id)

    private val loop = object : Runnable {
        override fun run() {
            if (!running) return
            meter.sample()?.let { v<SparklineView>(R.id.w_spark).push(it.first, it.second); tv(R.id.w_tv_down).text = "↓ ${SystemStats.fmtRate(it.first)}"; tv(R.id.w_tv_up).text = "↑ ${SystemStats.fmtRate(it.second)}" }
            if (tick % 3 == 0) { updateBattery(); updateNowPlaying() }
            if (tick % 600 == 0) updateWeather()
            tick++
            handler.postDelayed(this, 1000)
        }
    }

    fun bind() {
        tv(R.id.w_tv_month).text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date()).replaceFirstChar { it.uppercase() }
        v<View>(R.id.w_card_weather).setOnClickListener { WeatherRepo.promptCity(act) { updateWeather(true) } }
        v<View>(R.id.w_water_add).setOnClickListener { addWater(1) }
        v<View>(R.id.w_water_sub).setOnClickListener { addWater(-1) }
        listOf(R.id.w_water_add, R.id.w_water_sub, R.id.w_np_prev, R.id.w_np_play, R.id.w_np_next).forEach { Fx.pressable(v(it), 0.9f) }
        v<View>(R.id.w_np_prev).setOnClickListener { controller()?.transportControls?.skipToPrevious() }
        v<View>(R.id.w_np_play).setOnClickListener {
            val c = controller() ?: return@setOnClickListener
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        }
        v<View>(R.id.w_np_next).setOnClickListener { controller()?.transportControls?.skipToNext() }
        v<View>(R.id.w_np_perm).setOnClickListener {
            try { act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) {}
        }
        v<View>(R.id.w_add_widget).setOnClickListener { pickWidget() }
        updateWater()
        restoreHosted()
    }

    fun onResume() {
        if (running) return
        running = true
        try { host.startListening() } catch (_: Exception) {}
        updateWater()
        handler.post(loop)
        updateWeather()
    }

    fun onPause() {
        running = false
        handler.removeCallbacks(loop)
        try { host.stopListening() } catch (_: Exception) {}
    }

    fun destroy() { onPause(); io.shutdownNow() }

    fun restyle(fg: Int, accent: Int) {
        this.fg = fg; this.accent = accent
        v<AnalogClockView>(R.id.w_clock).let { it.ink = fg; it.accent = accent }
        v<RingView>(R.id.w_ring).let { it.ink = fg; it.accent = accent }
        v<MonthView>(R.id.w_month).let { it.ink = fg; it.accent = accent }
        v<WaterView>(R.id.w_water).ink = fg
        v<SparklineView>(R.id.w_spark).lineDown = accent
        v<WeatherAnimView>(R.id.w_weather).ink = fg
    }

    // ---------- batería ----------
    private fun updateBattery() {
        try {
            val b = SystemStats.battery(act)
            v<RingView>(R.id.w_ring).setValue(b.pct, b.charging)
        } catch (_: Exception) {}
    }

    // ---------- clima ----------
    private fun updateWeather(force: Boolean = false) {
        WeatherRepo.cached(act)?.let { showWeather(it) }
        if (force || !WeatherRepo.isFresh(WeatherRepo.cached(act))) {
            WeatherRepo.refresh(act, force) { w -> if (w != null) act.runOnUiThread { showWeather(w) } }
        }
    }

    private fun showWeather(w: WeatherRepo.Weather) {
        tv(R.id.w_tv_temp).text = "${w.temp}°"
        tv(R.id.w_tv_wsub).text = "${w.city} · ${WeatherRepo.describe(w.code).first}\n↑${w.max}°  ↓${w.min}°"
        v<WeatherAnimView>(R.id.w_weather).code = w.code
    }

    // ---------- agua ----------
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun waterCount(): Int {
        if (prefs.getString("water_day", "") != today()) prefs.edit().putString("water_day", today()).putInt("water_count", 0).apply()
        return prefs.getInt("water_count", 0)
    }

    private fun addWater(d: Int) {
        val n = (waterCount() + d).coerceIn(0, 20)
        prefs.edit().putInt("water_count", n).apply()
        updateWater()
        if (d > 0 && n == GOAL) Toast.makeText(act, "¡Meta de agua cumplida!", Toast.LENGTH_SHORT).show()
    }

    private fun updateWater() {
        val n = waterCount()
        tv(R.id.w_tv_water).text = "$n de $GOAL vasos"
        v<WaterView>(R.id.w_water).setFraction(n / GOAL.toFloat())
    }

    // ---------- música ----------
    private fun controller(): MediaController? {
        if (!OasisNotificationListener.isEnabled(act)) return null
        return try {
            val msm = act.getSystemService(Activity.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(ComponentName(act, OasisNotificationListener::class.java))
            list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        } catch (_: SecurityException) { null } catch (_: Exception) { null }
    }

    private fun updateNowPlaying() {
        val perm = v<View>(R.id.w_np_perm)
        if (!OasisNotificationListener.isEnabled(act)) { perm.visibility = View.VISIBLE; return }
        perm.visibility = View.GONE
        val c = controller()
        val md = c?.metadata
        if (c == null || md == null) {
            tv(R.id.w_np_title).text = "Nada reproduciéndose"
            tv(R.id.w_np_artist).text = "Abre tu música y aparecerá aquí"
            return
        }
        tv(R.id.w_np_title).text = md.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: "Sin título"
        tv(R.id.w_np_artist).text = md.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        tv(R.id.w_np_play).text = if (c.playbackState?.state == PlaybackState.STATE_PLAYING) "⏸" else "▶"
    }

    // ---------- widgets reales de Android ----------
    private fun savedIds(): MutableList<Int> =
        (prefs.getString("hosted_widgets", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }.toMutableList()

    private fun saveIds(l: List<Int>) = prefs.edit().putString("hosted_widgets", l.joinToString(",")).apply()

    private fun restoreHosted() {
        val box = v<ViewGroup>(R.id.w_hosted)
        box.removeAllViews()
        for (id in savedIds()) {
            val info = manager.getAppWidgetInfo(id)
            if (info == null) { saveIds(savedIds().also { it.remove(id) }); continue }
            addHostView(id, info)
        }
    }

    private fun pickWidget() {
        val list = try { manager.installedProviders.sortedBy { it.loadLabel(act.packageManager).toString().lowercase() } } catch (_: Exception) { emptyList() }
        if (list.isEmpty()) { Toast.makeText(act, "Este teléfono no tiene widgets disponibles", Toast.LENGTH_LONG).show(); return }
        val names = list.map { it.loadLabel(act.packageManager).toString() }.toTypedArray()
        AlertDialog.Builder(act).setTitle("Elige un widget")
            .setItems(names) { _, i -> bindProvider(list[i]) }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun bindProvider(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingId = id
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureOrAdd(id, info)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            try { act.startActivityForResult(i, REQ_BIND) } catch (_: Exception) { host.deleteAppWidgetId(id); pendingId = -1 }
        }
    }

    private fun configureOrAdd(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try { host.startAppWidgetConfigureActivityForResult(act, id, 0, REQ_CONFIGURE, null); return } catch (_: Exception) {}
        }
        finish(id)
    }

    private fun finish(id: Int) {
        val info = manager.getAppWidgetInfo(id)
        if (info == null) { host.deleteAppWidgetId(id); return }
        val ids = savedIds(); if (!ids.contains(id)) ids.add(id); saveIds(ids)
        addHostView(id, info)
    }

    /** Devuelve true si el resultado era de un widget. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQ_BIND && requestCode != REQ_CONFIGURE) return false
        val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingId) ?: pendingId
        if (resultCode != Activity.RESULT_OK || id < 0) { if (id >= 0) host.deleteAppWidgetId(id); pendingId = -1; return true }
        val info = manager.getAppWidgetInfo(id)
        if (requestCode == REQ_BIND && info != null) configureOrAdd(id, info) else finish(id)
        pendingId = -1
        return true
    }

    private fun addHostView(id: Int, info: AppWidgetProviderInfo) {
        val d = act.resources.displayMetrics.density
        val hv = host.createView(act, id, info)
        hv.setAppWidget(id, info)
        val wrap = FrameLayout(act)
        val h = (info.minHeight.coerceIn(60, 400) * d).toInt()
        wrap.addView(hv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
        val x = TextView(act).apply {
            text = "✕"; textSize = 14f; setTextColor(fg); gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_icon_button)
            setOnClickListener {
                AlertDialog.Builder(act).setTitle("¿Quitar este widget?")
                    .setPositiveButton("Quitar") { _, _ ->
                        host.deleteAppWidgetId(id)
                        saveIds(savedIds().also { it.remove(id) })
                        (wrap.parent as? ViewGroup)?.removeView(wrap)
                    }.setNegativeButton("No", null).show()
            }
        }
        wrap.addView(x, FrameLayout.LayoutParams((32 * d).toInt(), (32 * d).toInt(), Gravity.TOP or Gravity.END))
        val lp = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = (8 * d).toInt()
        v<ViewGroup>(R.id.w_hosted).addView(wrap, lp)
    }
}
