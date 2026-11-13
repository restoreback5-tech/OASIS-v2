package com.oasis.app

import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Rutinas habladas: «Buenos días» (resumen del día) y «Buenas noches» (mañana + modo silencio). */
object Routines {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** @param night false = buenos días, true = buenas noches. [done] se llama en el hilo principal con lo que hay que decir. */
    fun run(ctx: Context, night: Boolean, done: (String) -> Unit) {
        io.execute {
            val text = try { if (night) nightText(ctx) else morningText(ctx) } catch (e: Exception) { "Listo." }
            main.post { done(text) }
        }
    }

    private fun clock(): String {
        val c = Calendar.getInstance()
        val h24 = c.get(Calendar.HOUR_OF_DAY)
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val m = c.get(Calendar.MINUTE)
        return (if (h12 == 1) "Es la una" else "Son las $h12") + (if (m == 0) " en punto" else " con $m")
    }

    private fun morningText(ctx: Context): String {
        val sb = StringBuilder("¡Buenos días! ").append(clock()).append(". ")
        sb.append("Hoy es ").append(SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault()).format(Date())).append(". ")
        WeatherRepo.cached(ctx)?.let {
            sb.append("Afuera hay ${it.temp} grados, máxima de ${it.max}. ")
        }
        val tf = DateFormat.getTimeFormat(ctx)
        val ev = TodayData.events(ctx, 1, 3)
        if (ev.isNotEmpty()) sb.append("En tu agenda: ").append(ev.joinToString(". ") { (if (it.allDay) "todo el día" else "a las " + tf.format(Date(it.begin))) + ", " + it.title }).append(". ")
        val notes = NotesStore.list(ctx).filter { !it.done }.take(2)
        if (notes.isNotEmpty()) sb.append("Pendientes: ").append(notes.joinToString(". ") { it.text }).append(". ")
        val b = SystemStats.battery(ctx)
        sb.append("Tu batería está al ${b.pct} por ciento").append(if (b.pct < 30 && !b.charging) ". Conviene cargarla." else ".")
        if (dnd(ctx, false)) sb.append(" Quité el modo silencio.")
        return sb.toString()
    }

    private fun nightText(ctx: Context): String {
        val sb = StringBuilder("¡Buenas noches! ")
        val tf = DateFormat.getTimeFormat(ctx)
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val ev = TodayData.events(ctx, 2, 6).filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.begin }
            c.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
        }.take(3)
        if (ev.isEmpty()) sb.append("Mañana no tienes nada en la agenda. ")
        else sb.append("Mañana tienes: ").append(ev.joinToString(". ") { (if (it.allDay) "todo el día" else "a las " + tf.format(Date(it.begin))) + ", " + it.title }).append(". ")
        val b = SystemStats.battery(ctx)
        if (b.pct < 50 && !b.charging) sb.append("Pon el teléfono a cargar. ")
        sb.append(if (dnd(ctx, true)) "Activé el modo silencio; solo sonarán las alarmas. " else "Para que también ponga el modo silencio, permite No molestar a OASIS en Ajustes. ")
        sb.append("Que descanses.")
        return sb.toString()
    }

    /** Activa o quita «solo alarmas». @return true si pudo cambiarlo (requiere el permiso No molestar). */
    private fun dnd(ctx: Context, on: Boolean): Boolean = try {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) false
        else {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_ALARMS else NotificationManager.INTERRUPTION_FILTER_ALL)
            true
        }
    } catch (e: Exception) { false }
}
