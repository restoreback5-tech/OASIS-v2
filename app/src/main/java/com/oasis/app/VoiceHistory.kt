package com.oasis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Últimos comandos de voz entendidos (para la tarjeta "Actividad de voz"). */
object VoiceHistory {
    private const val KEY = "voice_history"
    private const val MAX = 15

    data class Entry(val ts: Long, val command: String, val params: Map<String, String>, val label: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    fun labelFor(command: String, params: Map<String, String>): String? = when (command) {
        "open_app" -> params["app"]?.takeIf { it.isNotBlank() && it != "desconocido" }?.let { "Abrir $it" }
        "call" -> params["contact"]?.takeIf { it.isNotBlank() }?.let { "Llamar a $it" }
        "message" -> params["contact"]?.takeIf { it.isNotBlank() }?.let { "Mensaje a $it" } ?: "Enviar mensaje"
        else -> null
    }

    fun add(ctx: Context, command: String, params: Map<String, String>) {
        val label = labelFor(command, params) ?: return
        val list = list(ctx).filter { it.label != label }.toMutableList()
        list.add(0, Entry(System.currentTimeMillis(), command, params, label))
        save(ctx, list.take(MAX))
    }

    fun list(ctx: Context): List<Entry> = try {
        val arr = JSONArray(prefs(ctx).getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.optJSONObject("p")
            val map = HashMap<String, String>()
            p?.keys()?.forEach { k -> map[k] = p.optString(k) }
            Entry(o.optLong("t"), o.optString("c"), map, o.optString("l"))
        }
    } catch (e: Exception) { emptyList() }

    fun clear(ctx: Context) = prefs(ctx).edit().remove(KEY).apply()

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().put("t", e.ts).put("c", e.command).put("l", e.label)
                .put("p", JSONObject().also { o -> e.params.forEach { (k, v) -> o.put(k, v) } }))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }
}
