package com.oasis.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Copia de seguridad de todos los ajustes en un archivo JSON. */
object Backup {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    fun export(ctx: Context): String {
        val root = JSONObject().put("app", "OASIS").put("version", 1)
        val data = JSONObject()
        for ((k, v) in prefs(ctx).all) {
            val o = JSONObject()
            when (v) {
                is Boolean -> o.put("t", "b").put("v", v)
                is Int -> o.put("t", "i").put("v", v)
                is Long -> o.put("t", "l").put("v", v)
                is Float -> o.put("t", "f").put("v", v.toDouble())
                is String -> o.put("t", "s").put("v", v)
                is Set<*> -> o.put("t", "ss").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            data.put(k, o)
        }
        return root.put("data", data).toString(2)
    }

    /** @return cuántos ajustes se restauraron, o -1 si el archivo no es una copia de OASIS. */
    fun import(ctx: Context, json: String): Int = try {
        val root = JSONObject(json)
        if (root.optString("app") != "OASIS") -1 else {
            val data = root.getJSONObject("data")
            val e = prefs(ctx).edit()
            var n = 0
            for (k in data.keys()) {
                val o = data.getJSONObject(k)
                when (o.getString("t")) {
                    "b" -> e.putBoolean(k, o.getBoolean("v"))
                    "i" -> e.putInt(k, o.getInt("v"))
                    "l" -> e.putLong(k, o.getLong("v"))
                    "f" -> e.putFloat(k, o.getDouble("v").toFloat())
                    "s" -> e.putString(k, o.getString("v"))
                    "ss" -> {
                        val a = o.getJSONArray("v")
                        e.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                    else -> continue
                }
                n++
            }
            e.apply()
            FontManager.invalidate()
            n
        }
    } catch (e: Exception) { -1 }
}
