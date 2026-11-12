package com.oasis.app

import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import java.util.Calendar

/**
 * Paleta de cada tema. Única fuente de colores para el inicio, el dashboard, el cajón,
 * los iconos y Ajustes. [dark] = tema oscuro (texto claro siempre, sin depender del fondo de pantalla).
 */
data class ThemePalette(
    val key: String,
    val name: String,
    val background: Int,   // fondo sólido (pantalla de ajustes)
    val scrim: Int,        // velo translúcido sobre el fondo de pantalla (launcher)
    val card: Int,
    val dock: Int,
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    val accent2: Int,      // segundo color: degradados y fondo animado
    val dark: Boolean
) {
    val lightSystemBarIcons: Boolean get() = !dark

    companion object {
        val KEYS = listOf(
            "amanecer", "caribe", "cristal", "sakura", "bosque", "atardecer",
            "noche", "midnight", "oled", "mocha", "aurora", "neon", "oceano", "dinamico"
        )

        private fun t(
            key: String, name: String, bg: Long, scrim: Long, card: Long, dock: Long,
            text: Long, text2: Long, accent: Long, accent2: Long, dark: Boolean
        ) = ThemePalette(key, name, bg.toInt(), scrim.toInt(), card.toInt(), dock.toInt(),
            text.toInt(), text2.toInt(), accent.toInt(), accent2.toInt(), dark)

        fun nameOf(key: String): String = when (key) {
            "amanecer" -> "Amanecer"; "caribe" -> "Caribe"; "cristal" -> "Cristal"
            "sakura" -> "Sakura"; "bosque" -> "Bosque"; "atardecer" -> "Atardecer"
            "noche" -> "Noche"; "midnight" -> "Medianoche"; "oled" -> "OLED"
            "mocha" -> "Moka"; "aurora" -> "Aurora"; "neon" -> "Neón"
            "oceano" -> "Océano"; "dinamico" -> "Dinámico (tu fondo)"
            else -> "Amanecer"
        }

        fun from(ctx: Context, key: String?): ThemePalette = when (key) {
            "caribe" -> ThemePalette(
                key = "caribe", name = "Caribe",
                background = ContextCompat.getColor(ctx, R.color.caribe_background),
                scrim = 0x29E0F7FA.toInt(), card = 0xFFFFFFFF.toInt(), dock = 0x80FFFFFF.toInt(),
                text = ContextCompat.getColor(ctx, R.color.caribe_text),
                textSecondary = 0xFF4F8F94.toInt(), accent = 0xFF00ACC1.toInt(),
                accent2 = 0xFF4DD0E1.toInt(), dark = false
            )
            "noche" -> t("noche", "Noche", 0xFF121212, 0x66000000, 0xFF1E1E20, 0x73000000,
                0xFFFFFFFF, 0xFFA1A1A6, 0xFF4FACFE, 0xFF7B61FF, true)
            "cristal" -> t("cristal", "Cristal", 0xFFEEF3F9, 0x1AFFFFFF, 0xFFFFFFFF, 0x66FFFFFF,
                0xFF1B2433, 0xFF6B7A90, 0xFF3D7BFF, 0xFF9B5CFF, false)
            "sakura" -> t("sakura", "Sakura", 0xFFFFF1F5, 0x29FFE4EC, 0xFFFFFFFF, 0x80FFFFFF,
                0xFF4A2630, 0xFFB0707F, 0xFFE91E63, 0xFFFF8FB1, false)
            "bosque" -> t("bosque", "Bosque", 0xFFF0F7EE, 0x29E4F4E0, 0xFFFFFFFF, 0x80FFFFFF,
                0xFF1F3A26, 0xFF5E8A66, 0xFF2E7D32, 0xFF9CCC65, false)
            "atardecer" -> t("atardecer", "Atardecer", 0xFFFFF3E6, 0x29FFD9B8, 0xFFFFFFFF, 0x80FFFFFF,
                0xFF3B1E10, 0xFFA0603C, 0xFFFF5722, 0xFFFFB300, false)
            "midnight" -> t("midnight", "Medianoche", 0xFF0B1020, 0x66050A1A, 0xFF141B33, 0x80101830,
                0xFFE6EBFF, 0xFF8C98C8, 0xFF6C8CFF, 0xFFB06CFF, true)
            "oled" -> t("oled", "OLED", 0xFF000000, 0x99000000, 0xFF0E0E0E, 0x99000000,
                0xFFFFFFFF, 0xFF9A9A9A, 0xFF00E5A0, 0xFF00B3FF, true)
            "mocha" -> t("mocha", "Moka", 0xFF1E1713, 0x66201812, 0xFF2A201A, 0x802A201A,
                0xFFF3E5D8, 0xFFB99C86, 0xFFD9A066, 0xFFB5651D, true)
            "aurora" -> t("aurora", "Aurora", 0xFF0A1F1C, 0x55062018, 0xFF12302B, 0x800B2420,
                0xFFE8FFF6, 0xFF86C9B5, 0xFF2EE6A6, 0xFF7C5CFF, true)
            "neon" -> t("neon", "Neón", 0xFF0D0221, 0x66100030, 0xFF1A0A3A, 0x80140A32,
                0xFFFFFFFF, 0xFFC09CFF, 0xFFFF2BD6, 0xFF00F0FF, true)
            "oceano" -> t("oceano", "Océano", 0xFF06182B, 0x66031425, 0xFF0D2A47, 0x800A2036,
                0xFFEAF6FF, 0xFF7FB2D9, 0xFF29B6F6, 0xFF00E5FF, true)
            "dinamico" -> dynamic(ctx)
            else -> ThemePalette(
                key = "amanecer", name = "Amanecer",
                background = ContextCompat.getColor(ctx, R.color.amanecer_background),
                scrim = 0x29FFF8E7.toInt(), card = 0xFFFFFFFF.toInt(), dock = 0x80FFFFFF.toInt(),
                text = ContextCompat.getColor(ctx, R.color.amanecer_text),
                textSecondary = 0xFF8D6E63.toInt(), accent = 0xFFF57C00.toInt(),
                accent2 = 0xFFFFB74D.toInt(), dark = false
            )
        }

        /** Tema elegido + modo automático día/noche + color de acento personalizado. */
        fun current(ctx: Context): ThemePalette {
            val key = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)
                .getString("selected_theme", "amanecer")
            var p = from(ctx, key)
            if (UiPrefs.themeAuto(ctx) && !p.dark) {
                val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                if (h >= 20 || h < 7) p = from(ctx, "noche")
            }
            val c = UiPrefs.accentCustom(ctx)
            if (c != 0) p = p.copy(accent = c, accent2 = ColorUtils.blendARGB(c, Color.WHITE, 0.35f))
            return p
        }

        /** Colores sacados del fondo de pantalla del teléfono (estilo Material You). */
        private fun dynamic(ctx: Context): ThemePalette {
            var primary = 0xFF6C8CFF.toInt()
            var secondary = 0xFFB06CFF.toInt()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    val wc = WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                    if (wc != null) {
                        primary = wc.primaryColor.toArgb()
                        secondary = wc.secondaryColor?.toArgb() ?: primary
                    }
                }
            } catch (e: Exception) { /* se usan los colores por defecto */ }
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val accent = vivid(primary, night)
            val accent2 = vivid(secondary, night)
            val bg = ColorUtils.blendARGB(if (night) 0xFF0F1115.toInt() else 0xFFF5F6FA.toInt(), accent, 0.10f)
            val card = ColorUtils.blendARGB(if (night) 0xFF1B1E24.toInt() else 0xFFFFFFFF.toInt(), accent, 0.06f)
            return ThemePalette(
                key = "dinamico", name = "Dinámico (tu fondo)", background = bg,
                scrim = if (night) 0x66000000 else 0x29FFFFFF, card = card,
                dock = if (night) 0x73000000 else 0x80FFFFFF.toInt(),
                text = if (night) 0xFFF1F3F7.toInt() else 0xFF171A21.toInt(),
                textSecondary = if (night) 0xFFA3A9B8.toInt() else 0xFF616977.toInt(),
                accent = accent, accent2 = accent2, dark = night
            )
        }

        private fun vivid(color: Int, night: Boolean): Int {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(color, hsl)
            hsl[1] = hsl[1].coerceAtLeast(0.45f)
            hsl[2] = if (night) 0.66f else 0.42f
            return ColorUtils.HSLToColor(hsl)
        }
    }
}
