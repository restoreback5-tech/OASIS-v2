package com.oasis.app

import android.content.Context
import org.json.JSONArray

/** Memoria del asistente: datos que el usuario le pide recordar ("mi hijo se llama Luis"). */
object MemoryStore {
    private const val KEY = "assistant_memory"
    private const val MAX = 30

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<String> = try {
        val a = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
        (0 until a.length()).map { a.getString(it) }
    } catch (e: Exception) { emptyList() }

    fun add(ctx: Context, fact: String) {
        val l = list(ctx).toMutableList()
        l.add(fact.trim().replaceFirstChar { it.uppercase() })
        while (l.size > MAX) l.removeAt(0)
        prefs(ctx).edit().putString(KEY, JSONArray(l).toString()).apply()
    }

    fun clear(ctx: Context) = prefs(ctx).edit().remove(KEY).apply()

    /** Busca el dato que más palabras comparte con la pregunta (mínimo 2 palabras útiles en común). */
    fun lookup(ctx: Context, question: String): String? {
        val stop = setOf("el", "la", "los", "las", "de", "del", "un", "una", "que", "es", "se", "mi", "mis", "como", "cual", "donde", "quien", "cuando", "me", "te", "en", "y", "a", "lo", "su", "tu")
        val q = TextMatch.normalize(question).split(' ').filter { it.length > 2 && it !in stop }.toSet()
        if (q.isEmpty()) return null
        var best: String? = null
        var bestScore = 0
        for (f in list(ctx)) {
            val words = TextMatch.normalize(f).split(' ').toSet()
            val score = q.count { it in words }
            if (score > bestScore) { bestScore = score; best = f }
        }
        return if (bestScore >= 2) best else null
    }
}
