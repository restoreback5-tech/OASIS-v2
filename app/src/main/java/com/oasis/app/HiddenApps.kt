package com.oasis.app

import android.content.Context

/** Paquetes que el usuario ocultó del cajón de apps (se restauran en Ajustes). */
object HiddenApps {
    private const val KEY = "hidden_apps"
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    fun all(ctx: Context): Set<String> = prefs(ctx).getStringSet(KEY, emptySet())?.toSet() ?: emptySet()
    fun isHidden(ctx: Context, pkg: String) = pkg in all(ctx)
    fun hide(ctx: Context, pkg: String) = prefs(ctx).edit().putStringSet(KEY, all(ctx) + pkg).apply()
    fun show(ctx: Context, pkg: String) = prefs(ctx).edit().putStringSet(KEY, all(ctx) - pkg).apply()
}
