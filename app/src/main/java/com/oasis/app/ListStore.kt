package com.oasis.app

import android.content.Context
import org.json.JSONArray

/** Lista del súper (se llena por voz o desde el widget). */
object ListStore {
    private const val KEY = "list_super"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    fun items(ctx: Context): List<String> = try {
        val a = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { emptyList() }

    private fun save(ctx: Context, l: List<String>) = prefs(ctx).edit().putString(KEY, JSONArray(l).toString()).apply()

    fun add(ctx: Context, item: String) {
        val t = item.trim().replaceFirstChar { it.uppercase() }
        if (t.isNotEmpty() && items(ctx).none { it.equals(t, true) }) save(ctx, (items(ctx) + t).takeLast(60))
    }

    /** Quita el primer elemento que contenga el texto. @return true si quitó algo. */
    fun remove(ctx: Context, item: String): Boolean {
        val l = items(ctx).toMutableList()
        val i = l.indexOfFirst { it.contains(item.trim(), true) }
        if (i < 0) return false
        l.removeAt(i); save(ctx, l); return true
    }

    fun clear(ctx: Context) = save(ctx, emptyList())
}
