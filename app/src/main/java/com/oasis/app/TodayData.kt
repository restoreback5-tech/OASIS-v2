package com.oasis.app

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Agenda y contactos favoritos para la página "Hoy". Llamar fuera del hilo principal. */
object TodayData {
    data class Event(val title: String, val begin: Long, val end: Long, val allDay: Boolean)
    data class Fav(val name: String, val number: String)

    fun has(ctx: Context, perm: String) =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    fun events(ctx: Context, days: Int = 7, limit: Int = 4): List<Event> {
        if (!has(ctx, Manifest.permission.READ_CALENDAR)) return emptyList()
        return try {
            val now = System.currentTimeMillis()
            val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(b, now)
            ContentUris.appendId(b, now + days * 86_400_000L)
            val out = ArrayList<Event>()
            ctx.contentResolver.query(
                b.build(),
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                null, null, CalendarContract.Instances.BEGIN + " ASC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val allDay = c.getInt(3) == 1
                    val end = c.getLong(2)
                    if (!allDay && end < now) continue
                    out.add(Event(c.getString(0)?.takeIf { it.isNotBlank() } ?: "(Sin título)", c.getLong(1), end, allDay))
                }
            }
            out
        } catch (e: Exception) { emptyList() }
    }

    /** Contactos marcados con estrella, máximo [limit]. */
    fun favorites(ctx: Context, limit: Int = 8): List<Fav> {
        if (!has(ctx, Manifest.permission.READ_CONTACTS)) return emptyList()
        return try {
            val out = LinkedHashMap<String, Fav>()
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                ContactsContract.CommonDataKinds.Phone.STARRED + "=1", null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val name = c.getString(0) ?: continue
                    val num = c.getString(1) ?: continue
                    if (name !in out) out[name] = Fav(name, num)
                }
            }
            out.values.toList()
        } catch (e: Exception) { emptyList() }
    }
}
