package com.oasis.app

import android.content.Context

/** Preferencias de la pantalla de inicio (se editan en Ajustes). */
object UiPrefs {

    private fun p(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    // Tamaño de iconos: 0 pequeño, 1 mediano, 2 grande
    fun iconSize(ctx: Context): Int = p(ctx).getInt("icon_size", 1).coerceIn(0, 2)
    fun setIconSize(ctx: Context, v: Int) = p(ctx).edit().putInt("icon_size", v).apply()
    fun homeIconDp(ctx: Context): Int = intArrayOf(44, 52, 64)[iconSize(ctx)]
    fun drawerIconDp(ctx: Context): Int = intArrayOf(48, 56, 68)[iconSize(ctx)]

    // Forma de iconos: 0 original, 1 círculo, 2 cuadrado redondeado
    fun iconShape(ctx: Context): Int = p(ctx).getInt("icon_shape", 0).coerceIn(0, 2)
    fun setIconShape(ctx: Context, v: Int) = p(ctx).edit().putInt("icon_shape", v).apply()

    fun showLabels(ctx: Context): Boolean = p(ctx).getBoolean("show_labels", true)
    fun setShowLabels(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("show_labels", v).apply()

    // Columnas del cajón de apps: 3, 4 o 5
    fun drawerColumns(ctx: Context): Int = p(ctx).getInt("drawer_columns", 4).coerceIn(3, 5)
    fun setDrawerColumns(ctx: Context, v: Int) = p(ctx).edit().putInt("drawer_columns", v).apply()

    // Intensidad del velo sobre el fondo de pantalla, 0-70 %. -1 = automático (según el tema)
    fun scrimPercent(ctx: Context): Int = p(ctx).getInt("scrim_percent", -1)
    fun setScrimPercent(ctx: Context, v: Int) = p(ctx).edit().putInt("scrim_percent", v).apply()

    fun swipeUp(ctx: Context): Boolean = p(ctx).getBoolean("swipe_up", true)
    fun setSwipeUp(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("swipe_up", v).apply()

    fun clock24(ctx: Context): Boolean = p(ctx).getBoolean("clock_24h", false)
    fun setClock24(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("clock_24h", v).apply()

    // Tamaño del orbe: 0 pequeño, 1 mediano, 2 grande
    fun orbSize(ctx: Context): Int = p(ctx).getInt("orb_size", 1).coerceIn(0, 2)
    fun setOrbSize(ctx: Context, v: Int) = p(ctx).edit().putInt("orb_size", v).apply()
    fun orbDp(ctx: Context): Int = intArrayOf(150, 180, 220)[orbSize(ctx)]

    // Fuente: "googlesans", "sfpro", "system", "serif", "mono", "condensed", "cursive" o "custom" (importada)
    fun fontKey(ctx: Context): String = p(ctx).getString("font_key", "googlesans") ?: "googlesans"
    fun setFontKey(ctx: Context, v: String) = p(ctx).edit().putString("font_key", v).apply()

    // Estilo de iconos: 0 originales de cada app, 1 monocromo (Android 13+)
    fun iconStyle(ctx: Context): Int = p(ctx).getInt("icon_style", 0).coerceIn(0, 1)
    fun setIconStyle(ctx: Context, v: Int) = p(ctx).edit().putInt("icon_style", v).apply()

    // Paquete de iconos externo (vacío = ninguno)
    fun iconPack(ctx: Context): String = p(ctx).getString("icon_pack", "") ?: ""
    fun setIconPack(ctx: Context, v: String) = p(ctx).edit().putString("icon_pack", v).apply()

    /** Vuelve a los valores de fábrica de la pantalla de inicio (no toca tema, voz ni apps fijadas). */
    fun resetHome(ctx: Context) {
        val e = p(ctx).edit()
        listOf(
            "icon_size", "icon_shape", "show_labels", "drawer_columns", "scrim_percent",
            "swipe_up", "clock_24h", "orb_size", "font_key", "icon_style", "icon_pack"
        ).forEach { e.remove(it) }
        e.apply()
        FontManager.invalidate()
    }
}
