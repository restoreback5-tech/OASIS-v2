package com.oasis.app

import android.content.ComponentName
import android.content.Context

/** Apps fijadas en la pantalla de inicio (se guardan en el orden en que se fijan). */
object PinnedApps {
    const val MAX = 8
    private const val KEY = "pinned_apps"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    fun get(ctx: Context): List<ComponentName> =
        prefs(ctx).getString(KEY, "").orEmpty()
            .split("|")
            .filter { it.isNotBlank() }
            .mapNotNull { ComponentName.unflattenFromString(it) }

    fun isPinned(ctx: Context, cn: ComponentName): Boolean = cn in get(ctx)

    /** @return false si la pantalla de inicio ya está llena. */
    fun pin(ctx: Context, cn: ComponentName): Boolean {
        val list = get(ctx).toMutableList()
        if (cn in list) return true
        if (list.size >= MAX) return false
        list.add(cn)
        save(ctx, list)
        return true
    }

    fun unpin(ctx: Context, cn: ComponentName) {
        save(ctx, get(ctx).filter { it != cn })
    }

    fun save(ctx: Context, list: List<ComponentName>) {
        prefs(ctx).edit()
            .putString(KEY, list.joinToString("|") { it.flattenToString() })
            .apply()
    }
}
