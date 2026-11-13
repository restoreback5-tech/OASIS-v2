package com.oasis.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Recordatorios de medicinas con confirmación; si no se confirma en 30 min se avisa al cuidador por SMS. */
data class Med(val id: Int, val name: String, val hour: Int, val minute: Int)

object MedStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)
    private fun today() = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    fun list(ctx: Context): List<Med> = try {
        val a = JSONArray(prefs(ctx).getString("meds_json", "[]") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map { Med(it.getInt("id"), it.getString("n"), it.getInt("h"), it.getInt("m")) }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    } catch (e: Exception) { emptyList() }

    private fun save(ctx: Context, l: List<Med>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("n", it.name).put("h", it.hour).put("m", it.minute)) }
        prefs(ctx).edit().putString("meds_json", a.toString()).apply()
    }

    fun add(ctx: Context, name: String, hour: Int, minute: Int): Med {
        val l = list(ctx)
        val m = Med((l.maxOfOrNull { it.id } ?: 0) + 1, name.trim().ifEmpty { "Medicina" }, hour, minute)
        save(ctx, l + m)
        return m
    }

    fun remove(ctx: Context, id: Int) = save(ctx, list(ctx).filter { it.id != id })

    fun get(ctx: Context, id: Int): Med? = list(ctx).firstOrNull { it.id == id }

    fun isTakenToday(ctx: Context, id: Int): Boolean =
        (prefs(ctx).getString("meds_taken_" + today(), "") ?: "").split(",").contains(id.toString())

    fun markTaken(ctx: Context, id: Int) {
        val k = "meds_taken_" + today()
        val set = (prefs(ctx).getString(k, "") ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()
        set.add(id.toString())
        prefs(ctx).edit().putString(k, set.joinToString(",")).apply()
    }
}

object MedScheduler {
    const val ACTION_ALARM = "com.oasis.app.MED_ALARM"
    const val ACTION_TAKEN = "com.oasis.app.MED_TAKEN"
    const val ACTION_SNOOZE = "com.oasis.app.MED_SNOOZE"
    const val ACTION_MISSED = "com.oasis.app.MED_MISSED"
    const val EXTRA_ID = "med_id"
    private const val CHANNEL = "oasis_meds"
    private const val MISSED_AFTER_MS = 30 * 60_000L
    private const val SNOOZE_MS = 10 * 60_000L

    private fun pi(ctx: Context, action: String, id: Int, slot: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id * 10 + slot,
            Intent(ctx, MedReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setAt(ctx: Context, at: Long, p: PendingIntent) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            else am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
        } catch (e: SecurityException) { am.set(AlarmManager.RTC_WAKEUP, at, p) }
    }

    /** Próxima vez que toca la medicina (hoy si aún no pasó la hora, si no mañana). */
    private fun nextTrigger(m: Med): Long {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, m.hour); set(Calendar.MINUTE, m.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        if (c.timeInMillis <= System.currentTimeMillis() + 1000) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun schedule(ctx: Context, m: Med) = setAt(ctx, nextTrigger(m), pi(ctx, ACTION_ALARM, m.id, 1))

    fun scheduleAll(ctx: Context) = MedStore.list(ctx).forEach { schedule(ctx, it) }

    fun cancel(ctx: Context, m: Med) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        listOf(1, 2, 3).forEach { am.cancel(pi(ctx, ACTION_ALARM, m.id, it)) }
        am.cancel(pi(ctx, ACTION_MISSED, m.id, 2))
        NotificationManagerCompat.from(ctx).cancel(1000 + m.id)
    }

    fun scheduleMissed(ctx: Context, m: Med) = setAt(ctx, System.currentTimeMillis() + MISSED_AFTER_MS, pi(ctx, ACTION_MISSED, m.id, 2))
    fun cancelMissed(ctx: Context, id: Int) = (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi(ctx, ACTION_MISSED, id, 2))
    fun snooze(ctx: Context, m: Med) = setAt(ctx, System.currentTimeMillis() + SNOOZE_MS, pi(ctx, ACTION_ALARM, m.id, 3))

    fun notifyNow(ctx: Context, m: Med) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Medicinas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Recordatorios para tomar tus medicinas"
            })
        }
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("💊 Hora de tu medicina")
            .setContentText("${m.name} · ${"%02d:%02d".format(m.hour, m.minute)}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "✔ Ya la tomé", pi(ctx, ACTION_TAKEN, m.id, 4))
            .addAction(0, "Recuérdame en 10 min", pi(ctx, ACTION_SNOOZE, m.id, 5))
            .build()
        try { NotificationManagerCompat.from(ctx).notify(1000 + m.id, n) } catch (_: SecurityException) {}
    }
}

class MedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getIntExtra(MedScheduler.EXTRA_ID, -1)
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> MedScheduler.scheduleAll(ctx)
            MedScheduler.ACTION_ALARM -> {
                val m = MedStore.get(ctx, id) ?: return
                MedScheduler.notifyNow(ctx, m)
                MedScheduler.scheduleMissed(ctx, m)
                MedScheduler.schedule(ctx, m)             // ya queda programada la de mañana
                Safety.checkBattery(ctx)
            }
            MedScheduler.ACTION_TAKEN -> {
                MedStore.markTaken(ctx, id)
                MedScheduler.cancelMissed(ctx, id)
                NotificationManagerCompat.from(ctx).cancel(1000 + id)
            }
            MedScheduler.ACTION_SNOOZE -> {
                val m = MedStore.get(ctx, id) ?: return
                NotificationManagerCompat.from(ctx).cancel(1000 + id)
                MedScheduler.snooze(ctx, m)
            }
            MedScheduler.ACTION_MISSED -> {
                val m = MedStore.get(ctx, id) ?: return
                if (MedStore.isTakenToday(ctx, id)) return
                val cg = Safety.caregiver(ctx) ?: return
                val who = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE).getString("med_name", "")?.takeIf { it.isNotBlank() } ?: "Tu contacto"
                Safety.sendSms(ctx, cg.number, "💊 $who no ha confirmado haber tomado: ${m.name} (${"%02d:%02d".format(m.hour, m.minute)}).")
            }
        }
    }
}
