package com.oasis.app

import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.graphics.ColorUtils

/** Punto único para obtener el icono final de una app según los Ajustes. */
object IconProvider {

    fun load(ctx: Context, cn: ComponentName, original: Drawable, sizePx: Int): Drawable {
        // 1) Paquete de iconos externo
        val pack = UiPrefs.iconPack(ctx)
        if (pack.isNotEmpty()) {
            IconPacks.drawableFor(ctx, pack, cn)?.let { return it }
        }
        // 2) Monocromo del sistema (Android 13+) teñido con el color del tema
        if (UiPrefs.iconStyle(ctx) == 1) {
            val theme = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)
                .getString("selected_theme", "amanecer")
            val accent = ThemePalette.from(ctx, theme).accent
            IconShaper.monochrome(
                ctx, original, sizePx, UiPrefs.iconShape(ctx),
                tint = accent, background = ColorUtils.setAlphaComponent(accent, 0x2E)
            )?.let { return it }
        }
        // 3) Original, con la forma elegida
        return IconShaper.shape(ctx, original, sizePx, UiPrefs.iconShape(ctx))
    }

    /** Llamar al cambiar el estilo/paquete en Ajustes. */
    fun invalidate() = IconPacks.invalidate()
}
