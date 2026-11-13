package com.oasis.app

import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Dock editable: mantén presionado Llamar, Mensaje o Contactos para elegir otra app (p. ej. WhatsApp). */
class DockEditor(private val act: AppCompatActivity, private val launcher: AppLauncherModule) {

    private val slots = mapOf(R.id.btn_call to "call", R.id.btn_message to "message", R.id.btn_contacts to "contacts")
    private val prefs = act.getSharedPreferences("oasis_settings", AppCompatActivity.MODE_PRIVATE)

    private class Original(val icon: Drawable?, val padding: Int, val label: CharSequence, val desc: CharSequence?)
    private val originals = HashMap<Int, Original>()

    private fun key(id: Int) = "dock_" + slots[id]

    private fun custom(id: Int): ComponentName? =
        prefs.getString(key(id), null)?.let { ComponentName.unflattenFromString(it) }

    private fun parts(id: Int): Pair<ImageView, TextView>? {
        val box = act.findViewById<ViewGroup>(id) ?: return null
        val iv = box.getChildAt(0) as? ImageView ?: return null
        val tv = box.getChildAt(1) as? TextView ?: return null
        return iv to tv
    }

    /** Pinta los iconos y nombres personalizados (o los originales). */
    fun refresh() {
        val pm = act.packageManager
        for (id in slots.keys) {
            val (iv, tv) = parts(id) ?: continue
            val o = originals.getOrPut(id) { Original(iv.drawable, iv.paddingLeft, tv.text, act.findViewById<View>(id).contentDescription) }
            val cn = custom(id)
            val info = cn?.let { try { pm.getActivityInfo(it, 0) } catch (_: Exception) { null } }
            if (cn != null && info != null) {
                val px = (36 * act.resources.displayMetrics.density).toInt()
                iv.setImageDrawable(IconProvider.load(act, cn, info.loadIcon(pm), px))
                val p = (10 * act.resources.displayMetrics.density).toInt()
                iv.setPadding(p, p, p, p)
                val label = info.loadLabel(pm).toString()
                tv.text = label
                act.findViewById<View>(id).contentDescription = label
            } else {
                if (cn != null) prefs.edit().remove(key(id)).apply()   // la app ya no existe
                iv.setImageDrawable(o.icon)
                iv.setPadding(o.padding, o.padding, o.padding, o.padding)
                tv.text = o.label
                act.findViewById<View>(id).contentDescription = o.desc
            }
        }
    }

    /** Abre la app elegida para este botón, o ejecuta la acción original. */
    fun launchOr(id: Int, default: () -> Unit) {
        val cn = custom(id)
        if (cn != null && launcher.launchComponent(cn)) return
        default()
    }

    fun edit(id: Int) {
        val pm = act.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != act.packageName }
            .map { it to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
        if (apps.isEmpty()) { Toast.makeText(act, "No encontré apps", Toast.LENGTH_SHORT).show(); return }
        val b = AlertDialog.Builder(act)
            .setTitle("¿Qué app abre este botón?")
            .setItems(apps.map { it.second }.toTypedArray()) { _, i ->
                val ai = apps[i].first.activityInfo
                prefs.edit().putString(key(id), ComponentName(ai.packageName, ai.name).flattenToString()).apply()
                refresh()
                Toast.makeText(act, "Listo: ${apps[i].second}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
        if (custom(id) != null) b.setNeutralButton("Restaurar original") { _, _ -> prefs.edit().remove(key(id)).apply(); refresh() }
        b.show()
    }
}
