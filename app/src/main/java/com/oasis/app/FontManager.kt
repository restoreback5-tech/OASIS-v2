package com.oasis.app

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import java.io.File

/**
 * Fuente de toda la interfaz. Incluye fuentes del sistema, las del proyecto y una fuente
 * importada por el usuario (.ttf / .otf). Los textos con android:tag="strong" usan el peso fuerte.
 */
object FontManager {

    data class Option(val key: String, val label: String)

    private val BUILT_IN = listOf(
        Option("googlesans", "Google Sans"),
        Option("sfpro", "SF Pro"),
        Option("system", "Sistema"),
        Option("condensed", "Condensada"),
        Option("serif", "Serif"),
        Option("mono", "Monoespaciada"),
        Option("cursive", "Manuscrita")
    )

    private const val MAX_FONT_BYTES = 15L * 1024 * 1024
    private val cache = HashMap<String, Typeface>()

    private fun customFile(ctx: Context) = File(ctx.filesDir, "custom_font")

    fun hasCustom(ctx: Context): Boolean = customFile(ctx).exists()

    fun options(ctx: Context): List<Option> =
        if (hasCustom(ctx)) BUILT_IN + Option("custom", "Mi fuente (importada)") else BUILT_IN

    fun currentLabel(ctx: Context): String =
        options(ctx).firstOrNull { it.key == UiPrefs.fontKey(ctx) }?.label ?: "Google Sans"

    @Synchronized
    fun invalidate() = cache.clear()

    @Synchronized
    fun typeface(ctx: Context, strong: Boolean): Typeface {
        val key = UiPrefs.fontKey(ctx)
        return cache.getOrPut("$key:$strong") { build(ctx, key, strong) }
    }

    private fun build(ctx: Context, key: String, strong: Boolean): Typeface = try {
        when (key) {
            "googlesans" -> ResourcesCompat.getFont(
                ctx, if (strong) R.font.googlesans_medium else R.font.googlesans_regular
            )
            "sfpro" -> ResourcesCompat.getFont(
                ctx, if (strong) R.font.sf_pro_display_medium else R.font.sf_pro_display_regular
            )
            "serif" -> Typeface.create("serif", if (strong) Typeface.BOLD else Typeface.NORMAL)
            "mono" -> Typeface.create("monospace", if (strong) Typeface.BOLD else Typeface.NORMAL)
            "condensed" -> Typeface.create("sans-serif-condensed", if (strong) Typeface.BOLD else Typeface.NORMAL)
            "cursive" -> Typeface.create("cursive", if (strong) Typeface.BOLD else Typeface.NORMAL)
            "custom" -> Typeface.createFromFile(customFile(ctx)).let {
                if (strong) Typeface.create(it, Typeface.BOLD) else it
            }
            else -> Typeface.create(if (strong) "sans-serif-medium" else "sans-serif", Typeface.NORMAL)
        } ?: Typeface.DEFAULT
    } catch (e: Exception) {
        Typeface.DEFAULT
    }

    /** Aplica la fuente elegida a todos los textos de [root]. */
    fun apply(ctx: Context, root: View) {
        if (root is TextView) root.typeface = typeface(ctx, root.tag == "strong")
        if (root is ViewGroup) for (i in 0 until root.childCount) apply(ctx, root.getChildAt(i))
    }

    /**
     * Copia el archivo elegido por el usuario como fuente propia.
     * @return null si todo salió bien, o el mensaje de error para mostrar.
     */
    fun importFrom(ctx: Context, uri: Uri): String? {
        val tmp = File(ctx.filesDir, "custom_font.tmp")
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return "No pude abrir el archivo"
            input.use { src ->
                tmp.outputStream().use { out ->
                    var total = 0L
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = src.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_FONT_BYTES) return "El archivo es demasiado grande (máx. 15 MB)"
                        out.write(buf, 0, n)
                    }
                }
            }
            Typeface.createFromFile(tmp) // lanza excepción si no es una fuente válida
            val dest = customFile(ctx)
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) return "No pude guardar la fuente"
            invalidate()
            UiPrefs.setFontKey(ctx, "custom")
            null
        } catch (e: Exception) {
            "El archivo no es una fuente válida (.ttf o .otf)"
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }
}
