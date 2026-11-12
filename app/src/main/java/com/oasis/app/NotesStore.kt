package com.oasis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Notas / pendientes rápidos guardados en Ajustes. */
object NotesStore {
    private const val KEY = "quick_notes"
    data class Note(val text: String, val done: Boolean)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Note> = try {
        val a = JSONArray(prefs(ctx).getString(KEY, "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Note(o.optString("t"), o.optBoolean("d")) } }
    } catch (e: Exception) { emptyList() }

    private fun save(ctx: Context, l: List<Note>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.text).put("d", it.done)) }
        prefs(ctx).edit().putString(KEY, a.toString()).apply()
    }

    fun add(ctx: Context, text: String) { if (text.isNotBlank()) save(ctx, listOf(Note(text.trim(), false)) + list(ctx)) }
    fun toggle(ctx: Context, i: Int) = save(ctx, list(ctx).mapIndexed { k, n -> if (k == i) n.copy(done = !n.done) else n })
    fun remove(ctx: Context, i: Int) = save(ctx, list(ctx).filterIndexed { k, _ -> k != i })
}
