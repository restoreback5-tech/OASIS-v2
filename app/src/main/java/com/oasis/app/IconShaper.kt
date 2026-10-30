package com.oasis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Cambia la forma y el estilo de los iconos adaptables (círculo / cuadrado redondeado / monocromo).
 * Los iconos antiguos (no adaptables) se dejan tal cual.
 */
object IconShaper {
    const val ORIGINAL = 0
    const val CIRCLE = 1
    const val ROUNDED = 2

    private fun outline(shape: Int, size: Float): Path = Path().apply {
        if (shape == CIRCLE) addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)
        else addRoundRect(RectF(0f, 0f, size, size), size * 0.28f, size * 0.28f, Path.Direction.CW)
    }

    /** Dibuja una capa de 108 dp de un icono adaptable: solo se ven los 72 dp centrales. */
    private fun drawLayer(layer: Drawable?, canvas: Canvas, sizePx: Int) {
        if (layer == null) return
        val full = (sizePx * 108f / 72f).toInt()
        val off = -(full - sizePx) / 2
        layer.setBounds(off, off, off + full, off + full)
        layer.draw(canvas)
    }

    fun shape(context: Context, icon: Drawable, sizePx: Int, shape: Int): Drawable {
        if (shape == ORIGINAL || sizePx <= 0 ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || icon !is AdaptiveIconDrawable
        ) return icon

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.clipPath(outline(shape, sizePx.toFloat()))
        drawLayer(icon.background, canvas, sizePx)
        drawLayer(icon.foreground, canvas, sizePx)
        return BitmapDrawable(context.resources, bmp)
    }

    /**
     * Icono monocromo (Android 13+): la silueta de la app teñida con [tint] sobre un fondo suave.
     * Devuelve null si la app no trae capa monocromática.
     */
    fun monochrome(context: Context, icon: Drawable, sizePx: Int, shape: Int, tint: Int, background: Int): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || sizePx <= 0 || icon !is AdaptiveIconDrawable) return null
        val mono = icon.monochrome ?: return null

        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val path = outline(if (shape == ORIGINAL) ROUNDED else shape, sizePx.toFloat())
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
        canvas.clipPath(path)
        val layer = mono.mutate()
        layer.setTint(tint)
        drawLayer(layer, canvas, sizePx)
        return BitmapDrawable(context.resources, bmp)
    }
}
