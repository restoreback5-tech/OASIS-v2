package com.oasis.app

import android.content.ComponentName
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Carpetas de la pantalla de inicio: un nombre y una lista de apps. */
object Folders {
    const val MAX_FOLDERS = 4
    private const val KEY = "folders_json"

    data class Folder(val name: String, val apps: List<ComponentName>)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    fun all(ctx: Context): List<Folder> = try {
        val arr = JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val a = o.getJSONArray("a")
            Folder(
                o.getString("n"),
                (0 until a.length()).mapNotNull { ComponentName.unflattenFromString(a.getString(it)) }
            )
        }
    } catch (e: Exception) { emptyList() }

    fun names(ctx: Context): List<String> = all(ctx).map { it.name }

    fun get(ctx: Context, name: String): Folder? = all(ctx).firstOrNull { it.name == name }

    private fun save(ctx: Context, list: List<Folder>) {
        val arr = JSONArray()
        list.forEach { f ->
            arr.put(JSONObject().put("n", f.name).put("a", JSONArray(f.apps.map { it.flattenToString() })))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Crea la carpeta si no existe. @return false si ya hay demasiadas carpetas. */
    fun add(ctx: Context, name: String, cn: ComponentName): Boolean {
        val list = all(ctx).toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx < 0) {
            if (list.size >= MAX_FOLDERS) return false
            list.add(Folder(name, listOf(cn)))
        } else if (cn !in list[idx].apps) {
            list[idx] = Folder(name, list[idx].apps + cn)
        }
        save(ctx, list)
        return true
    }

    fun remove(ctx: Context, name: String, cn: ComponentName) {
        val list = all(ctx).map { if (it.name == name) Folder(it.name, it.apps.filter { a -> a != cn }) else it }
            .filter { it.apps.isNotEmpty() }
        save(ctx, list)
    }

    fun delete(ctx: Context, name: String) = save(ctx, all(ctx).filter { it.name != name })

    fun rename(ctx: Context, old: String, new: String) {
        if (new.isBlank() || names(ctx).contains(new)) return
        save(ctx, all(ctx).map { if (it.name == old) Folder(new.trim(), it.apps) else it })
    }
}
