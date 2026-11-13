package com.oasis.app

import android.content.Context

/** Preferencias de la pantalla de inicio (se editan en Ajustes). */
object UiPrefs {

    private fun p(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    // Tamaño de iconos: 0 pequeño, 1 mediano, 2 grande
    fun iconSize(ctx: Context): Int = p(ctx).getInt("icon_size", 1).coerceIn(0, 2)
    fun setIconSize(ctx: Context, v: Int) = p(ctx).edit().putInt("icon_size", v).apply()
    fun homeIconDp(ctx: Context): Int = if (simpleMode(ctx)) 68 else intArrayOf(44, 52, 64)[iconSize(ctx)]
    fun drawerIconDp(ctx: Context): Int = if (simpleMode(ctx)) 72 else intArrayOf(48, 56, 68)[iconSize(ctx)]

    // Forma de iconos: 0 original, 1 círculo, 2 cuadrado redondeado
    fun iconShape(ctx: Context): Int = p(ctx).getInt("icon_shape", 0).coerceIn(0, 2)
    fun setIconShape(ctx: Context, v: Int) = p(ctx).edit().putInt("icon_shape", v).apply()

    fun showLabels(ctx: Context): Boolean = simpleMode(ctx) || p(ctx).getBoolean("show_labels", true)
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

    // Fuente: "googlesans", "system", "serif", "mono", "condensed", "cursive" o "custom" (importada)
    fun fontKey(ctx: Context): String = p(ctx).getString("font_key", "googlesans") ?: "googlesans"
    fun setFontKey(ctx: Context, v: String) = p(ctx).edit().putString("font_key", v).apply()

    // Estilo de iconos: 0 originales de cada app, 1 monocromo (Android 13+)
    fun iconStyle(ctx: Context): Int = p(ctx).getInt("icon_style", 0).coerceIn(0, 1)
    fun setIconStyle(ctx: Context, v: Int) = p(ctx).edit().putInt("icon_style", v).apply()

    // Paquete de iconos externo (vacío = ninguno)
    fun iconPack(ctx: Context): String = p(ctx).getString("icon_pack", "") ?: ""
    fun setIconPack(ctx: Context, v: String) = p(ctx).edit().putString("icon_pack", v).apply()

    // Sonido de bienvenida (inicio.ogg dura 60 s): apagado por defecto
    fun startupSound(ctx: Context): Boolean = p(ctx).getBoolean("sound_startup", false)
    fun setStartupSound(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("sound_startup", v).apply()

    // Fondo del cajón de apps: 0 automático (según tema y modo noche), 1 claro suave, 2 oscuro
    fun drawerStyle(ctx: Context): Int = p(ctx).getInt("drawer_style", 0).coerceIn(0, 2)
    fun setDrawerStyle(ctx: Context, v: Int) = p(ctx).edit().putInt("drawer_style", v).apply()

    // Tema automático (de noche usa "Noche" si el elegido es claro) y acento propio (0 = el del tema)
    fun themeAuto(ctx: Context): Boolean = p(ctx).getBoolean("theme_auto", false)
    fun setThemeAuto(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("theme_auto", v).apply()
    fun accentCustom(ctx: Context): Int = p(ctx).getInt("accent_custom", 0)
    fun setAccentCustom(ctx: Context, v: Int) = p(ctx).edit().putInt("accent_custom", v).apply()

    // Fondo animado: 0 ninguno, 1 aurora, 2 burbujas, 3 estrellas
    fun bgEffect(ctx: Context): Int = p(ctx).getInt("bg_effect", 1).coerceIn(0, 3)
    fun setBgEffect(ctx: Context, v: Int) = p(ctx).edit().putInt("bg_effect", v).apply()

    fun tilt(ctx: Context): Boolean = p(ctx).getBoolean("tilt_fx", true)
    fun setTilt(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("tilt_fx", v).apply()

    // Transición entre páginas: 0 profundidad, 1 cubo, 2 pila, 3 carrusel, 4 fundido, 5 deslizar
    val PAGE_FX = listOf("Profundidad", "Cubo", "Pila", "Carrusel", "Fundido", "Deslizar")
    fun pageFx(ctx: Context): Int = p(ctx).getInt("page_fx", 0).coerceIn(0, 5)
    fun setPageFx(ctx: Context, v: Int) = p(ctx).edit().putInt("page_fx", v).apply()

    // Gestos en Inicio: 0 nada, 1 notificaciones, 2 cajón de apps, 3 linterna, 4 ajustes, 5 hablar
    val GESTURES = listOf("Nada", "Abrir notificaciones", "Abrir cajón de apps", "Linterna", "Ajustes de OASIS", "Hablar con OASIS", "Bloquear pantalla")
    fun gestureDouble(ctx: Context): Int = p(ctx).getInt("gesture_double", 0).coerceIn(0, 6)
    fun setGestureDouble(ctx: Context, v: Int) = p(ctx).edit().putInt("gesture_double", v).apply()
    fun gestureDown(ctx: Context): Int = p(ctx).getInt("gesture_down", 1).coerceIn(0, 6)
    fun setGestureDown(ctx: Context, v: Int) = p(ctx).edit().putInt("gesture_down", v).apply()

    // Efectos de cristal y animaciones
    fun glassFx(ctx: Context): Boolean = p(ctx).getBoolean("glass_fx", true)
    fun setGlassFx(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("glass_fx", v).apply()

    fun pageToday(ctx: Context): Boolean = p(ctx).getBoolean("page_today", true)
    fun setPageToday(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("page_today", v).apply()
    fun pageTools(ctx: Context): Boolean = p(ctx).getBoolean("page_tools", true)
    fun setPageTools(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("page_tools", v).apply()
    // Modo simple: solo la pantalla de inicio, iconos grandes con nombre, contactos con foto y voz que anuncia cada toque
    fun simpleMode(ctx: Context): Boolean = p(ctx).getBoolean("simple_mode", false)
    fun setSimpleMode(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("simple_mode", v).apply()
    fun readMessages(ctx: Context): Boolean = p(ctx).getBoolean("read_messages", false)
    fun setReadMessages(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("read_messages", v).apply()
    fun wakeWord(ctx: Context): Boolean = p(ctx).getBoolean("wake_word", false)
    fun setWakeWord(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("wake_word", v).apply()
    fun pageWidgets(ctx: Context): Boolean = p(ctx).getBoolean("page_widgets", true)
    fun setPageWidgets(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("page_widgets", v).apply()
    fun pageSystem(ctx: Context): Boolean = p(ctx).getBoolean("page_system", true)
    fun setPageSystem(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean("page_system", v).apply()

    /** Vuelve a los valores de fábrica de la pantalla de inicio (no toca tema, voz ni apps fijadas). */
    fun resetHome(ctx: Context) {
        val e = p(ctx).edit()
        listOf(
            "icon_size", "icon_shape", "show_labels", "drawer_columns", "scrim_percent",
            "swipe_up", "clock_24h", "orb_size", "font_key", "icon_style", "icon_pack",
            "glass_fx", "theme_auto", "accent_custom", "bg_effect", "tilt_fx", "page_fx", "gesture_double", "gesture_down", "drawer_style", "page_today", "page_tools", "page_system", "page_widgets", "widgets_hidden", "simple_mode", "read_messages", "wake_word", "assistant_memory", "folders_json", "dock_call", "dock_message", "dock_contacts", "water_count", "water_day", "sound_startup"
        ).forEach { e.remove(it) }
        e.apply()
        FontManager.invalidate()
    }
}
