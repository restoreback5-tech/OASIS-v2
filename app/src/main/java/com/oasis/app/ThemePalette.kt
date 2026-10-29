package com.oasis.app

import android.content.Context
import androidx.core.content.ContextCompat

/**
 * Paleta de cada tema ("amanecer", "caribe", "noche").
 * Única fuente de colores para MainActivity y SettingsActivity.
 *
 * Si el texto se lee mal sobre algún fondo de pantalla, sube el alfa de [scrim]
 * (los dos primeros dígitos hex: 0xB8 = 72 %).
 */
data class ThemePalette(
    val key: String,
    val background: Int,   // fondo sólido (pantalla de ajustes)
    val scrim: Int,        // velo translúcido sobre el fondo de pantalla (launcher)
    val card: Int,
    val dock: Int,
    val text: Int,
    val textSecondary: Int,
    val accent: Int,
    val lightSystemBarIcons: Boolean // true = iconos oscuros en la barra de estado
) {
    companion object {
        fun from(ctx: Context, key: String?): ThemePalette = when (key) {
            "caribe" -> ThemePalette(
                key = "caribe",
                background = ContextCompat.getColor(ctx, R.color.caribe_background),
                scrim = 0xB8E0F7FA.toInt(),
                card = 0xFFFFFFFF.toInt(),
                dock = 0xD9FFFFFF.toInt(),
                text = ContextCompat.getColor(ctx, R.color.caribe_text),
                textSecondary = 0xFF4F8F94.toInt(),
                accent = 0xFF00ACC1.toInt(),
                lightSystemBarIcons = true
            )
            "noche" -> ThemePalette(
                key = "noche",
                background = 0xFF121212.toInt(),
                scrim = 0xB3000000.toInt(),
                card = 0xFF1E1E20.toInt(),
                dock = 0xD92C2C2E.toInt(),
                text = 0xFFFFFFFF.toInt(),
                textSecondary = 0xFFA1A1A6.toInt(),
                accent = 0xFF4FACFE.toInt(),
                lightSystemBarIcons = false
            )
            else -> ThemePalette(
                key = "amanecer",
                background = ContextCompat.getColor(ctx, R.color.amanecer_background),
                scrim = 0xB8FFF8E7.toInt(),
                card = 0xFFFFFFFF.toInt(),
                dock = 0xD9FFFFFF.toInt(),
                text = ContextCompat.getColor(ctx, R.color.amanecer_text),
                textSecondary = 0xFF8D6E63.toInt(),
                accent = 0xFFF57C00.toInt(),
                lightSystemBarIcons = true
            )
        }
    }
}
