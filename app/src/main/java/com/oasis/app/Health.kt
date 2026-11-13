package com.oasis.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.json.JSONArray

private fun healthPrefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)
private fun dayKey(offset: Int = 0): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }.time)

/** Contador de pasos con el sensor del teléfono (necesita el permiso «actividad física»). */
class StepCounter(private val ctx: Context, private val onSteps: (Int) -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var running = false

    companion object {
        fun goal(ctx: Context) = healthPrefs(ctx).getInt("steps_goal", 6000)
        fun setGoal(ctx: Context, g: Int) = healthPrefs(ctx).edit().putInt("steps_goal", g.coerceIn(500, 50000)).apply()
        fun lastKnown(ctx: Context): Int = if (healthPrefs(ctx).getString("steps_day", "") == dayKey()) healthPrefs(ctx).getInt("steps_today", 0) else 0
        fun available(ctx: Context) = (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    fun start() {
        if (running) return
        val s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        running = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() { if (running) { sm.unregisterListener(this); running = false } }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val total = e.values[0].toLong()
        val p = healthPrefs(ctx)
        val today = dayKey()
        // El sensor cuenta desde que se prendió el teléfono: guardamos el valor con el que empezó el día.
        if (p.getString("steps_day", "") != today || total < p.getLong("steps_base", 0L)) {
            p.edit().putString("steps_day", today).putLong("steps_base", total).putInt("steps_today", 0).apply()
        }
        val steps = (total - p.getLong("steps_base", total)).toInt().coerceAtLeast(0)
        p.edit().putInt("steps_today", steps).apply()
        onSteps(steps)
    }
}

/** Hábitos diarios con racha (días seguidos). */
object HabitStore {
    private val DEFAULTS = listOf("Tomar agua", "Caminar 30 min", "Dormir 8 horas", "Estirarme")
    private const val KEEP = 90

    fun list(ctx: Context): List<String> {
    return try {
        val a = JSONArray(healthPrefs(ctx).getString("habits_json", null) ?: return DEFAULTS)
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { DEFAULTS }
}

    private fun save(ctx: Context, l: List<String>) = healthPrefs(ctx).edit().putString("habits_json", JSONArray(l).toString()).apply()

    fun add(ctx: Context, name: String) { if (name.isNotBlank() && name !in list(ctx)) save(ctx, list(ctx) + name.trim().take(30)) }
    fun remove(ctx: Context, name: String) { save(ctx, list(ctx) - name); healthPrefs(ctx).edit().remove("habit_days_$name").apply() }

    private fun days(ctx: Context, name: String): MutableList<String> =
        (healthPrefs(ctx).getString("habit_days_$name", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableList()

    fun doneToday(ctx: Context, name: String) = dayKey() in days(ctx, name)

    fun toggleToday(ctx: Context, name: String) {
        val d = days(ctx, name)
        if (!d.remove(dayKey())) d.add(dayKey())
        healthPrefs(ctx).edit().putString("habit_days_$name", d.takeLast(KEEP).joinToString(",")).apply()
    }

    /** Días seguidos terminando hoy (o ayer, si hoy todavía no se marca). */
    fun streak(ctx: Context, name: String): Int {
        val d = days(ctx, name).toSet()
        var offset = if (dayKey() in d) 0 else -1
        var n = 0
        while (dayKey(offset) in d) { n++; offset-- }
        return n
    }
}

/** Respiración guiada: ventana con el círculo animado y la voz del orbe diciendo «inhala / sostén / exhala». */
object Breathing {
    fun show(act: AppCompatActivity, say: (String) -> Unit) {
        val view = BreathView(act).apply {
            val pal = ThemePalette.current(act)
            tint = pal.accent
            ink = pal.text
            onPhase = { say(it) }
        }
        val box = FrameLayout(act)
        val d = act.resources.displayMetrics.density
        box.addView(view, FrameLayout.LayoutParams((260 * d).toInt(), (260 * d).toInt(), android.view.Gravity.CENTER))
        AlertDialog.Builder(act).setTitle("🌬 Respiremos juntos").setMessage("Sigue el círculo. Inhala por la nariz, sostén y suelta el aire despacio.")
            .setView(box).setPositiveButton("Listo", null).show()
    }
}
