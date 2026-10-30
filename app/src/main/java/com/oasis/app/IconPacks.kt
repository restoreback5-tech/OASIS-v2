package com.oasis.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Soporte de paquetes de iconos externos (formato ADW/Nova/Apex: archivo appfilter.xml).
 * Solo usa el icono que el paquete define para cada app; si no define uno, se queda el original.
 */
object IconPacks {

    data class Pack(val pkg: String, val label: String)

    private val ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.gau.go.launcherex.theme"
    )

    private val COMPONENT = Regex("ComponentInfo\\{([^/}]+)/([^}]+)\\}")

    private var loadedPack: String? = null
    private var mapping: Map<String, String> = emptyMap()
    private var resources: Resources? = null

    /** Paquetes de iconos instalados en el teléfono. */
    fun installed(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        return ACTIONS
            .flatMap { action ->
                try { pm.queryIntentActivities(Intent(action), 0) } catch (e: Exception) { emptyList() }
            }
            .map { Pack(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    /** Extrae "paquete/clase" -> nombre de dibujo desde el contenido de un appfilter.xml. */
    internal fun parseAppFilter(parser: XmlPullParser): Map<String, String> {
        val out = HashMap<String, String>()
        var type = parser.eventType
        while (type != XmlPullParser.END_DOCUMENT) {
            if (type == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                val m = if (component != null) COMPONENT.find(component) else null
                if (m != null && !drawable.isNullOrBlank()) {
                    val pkg = m.groupValues[1]
                    var cls = m.groupValues[2]
                    if (cls.startsWith(".")) cls = pkg + cls
                    out["$pkg/$cls"] = drawable
                }
            }
            type = parser.next()
        }
        return out
    }

    @Synchronized
    private fun ensureLoaded(ctx: Context, pack: String) {
        if (loadedPack == pack) return
        loadedPack = pack
        mapping = emptyMap()
        resources = null
        try {
            val res = ctx.packageManager.getResourcesForApplication(pack)
            resources = res
            val xmlId = res.getIdentifier("appfilter", "xml", pack)
            mapping = if (xmlId != 0) {
                parseAppFilter(res.getXml(xmlId))
            } else {
                val assets = ctx.createPackageContext(pack, 0).assets
                assets.open("appfilter.xml").use { stream ->
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(stream, null)
                    parseAppFilter(parser)
                }
            }
        } catch (e: Exception) {
            Log.w("IconPacks", "No pude leer el paquete $pack: ${e.message}")
        }
    }

    /** Icono que el paquete [pack] define para [cn], o null si no tiene uno. */
    fun drawableFor(ctx: Context, pack: String, cn: ComponentName): Drawable? {
        ensureLoaded(ctx, pack)
        val res = resources ?: return null
        val name = mapping[cn.flattenToString()] ?: return null
        return try {
            var id = res.getIdentifier(name, "drawable", pack)
            if (id == 0) id = res.getIdentifier(name, "mipmap", pack)
            if (id == 0) null else ResourcesCompat.getDrawable(res, id, null)
        } catch (e: Exception) {
            null
        }
    }

    /** Fuerza releer el paquete (p. ej. después de cambiarlo en Ajustes). */
    @Synchronized
    fun invalidate() {
        loadedPack = null
        mapping = emptyMap()
        resources = null
    }
}
