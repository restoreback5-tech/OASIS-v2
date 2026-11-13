package com.oasis.app

import android.Manifest
import android.content.Context
import android.provider.ContactsContract
import java.util.Calendar

/** Próximos cumpleaños de tus contactos (usa los cumpleaños guardados en la agenda del teléfono). */
object Birthdays {
    data class B(val name: String, val daysLeft: Int, val day: Int, val month: Int)

    fun upcoming(ctx: Context, limit: Int = 4, withinDays: Int = 45): List<B> {
        if (!TodayData.has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = ArrayList<B>()
        try {
            ctx.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.CommonDataKinds.Event.START_DATE),
                ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.CommonDataKinds.Event.TYPE + "=?",
                arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()),
                null
            )?.use { c ->
                val today = Calendar.getInstance()
                while (c.moveToNext()) {
                    val name = c.getString(0) ?: continue
                    val date = c.getString(1) ?: continue
                    // formatos: 1980-05-23, --05-23, 19800523
                    val m = Regex("(?:--|\\d{4}-?)(\\d{2})-?(\\d{2})").find(date) ?: continue
                    val month = m.groupValues[1].toIntOrNull() ?: continue
                    val day = m.groupValues[2].toIntOrNull() ?: continue
                    if (month !in 1..12 || day !in 1..31) continue
                    val next = Calendar.getInstance().apply { set(Calendar.MONTH, month - 1); set(Calendar.DAY_OF_MONTH, day); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                    val base = (today.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
                    if (next.before(base)) next.add(Calendar.YEAR, 1)
                    val left = ((next.timeInMillis - base.timeInMillis) / 86_400_000L).toInt()
                    if (left <= withinDays) out.add(B(name, left, day, month))
                }
            }
        } catch (e: Exception) { /* sin permiso o sin cumpleaños */ }
        return out.sortedBy { it.daysLeft }.distinctBy { it.name }.take(limit)
    }

    fun describe(b: B): String = when (b.daysLeft) {
        0 -> "${b.name} cumple años HOY 🎂"
        1 -> "${b.name} cumple años mañana"
        else -> "${b.name} cumple años en ${b.daysLeft} días"
    }
}
