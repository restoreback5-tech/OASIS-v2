package com.oasis.app

import android.content.ComponentName
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Diálogos de carpetas: agregar una app a una carpeta, abrirla, renombrarla o borrarla. */
object FolderUi {

    /** Pregunta a qué carpeta mandar la app (o crea una nueva). */
    fun pickFolderFor(ctx: Context, cn: ComponentName, onDone: () -> Unit) {
        val names = Folders.names(ctx)
        val items = (names + "➕ Carpeta nueva…").toTypedArray()
        AlertDialog.Builder(ctx).setTitle("¿En qué carpeta la guardo?")
            .setItems(items) { _, i ->
                if (i < names.size) { Folders.add(ctx, names[i], cn); onDone() }
                else askName(ctx, "Nueva carpeta", "") { n ->
                    if (!Folders.add(ctx, n, cn)) Toast.makeText(ctx, "Solo puedes tener ${Folders.MAX_FOLDERS} carpetas", Toast.LENGTH_LONG).show()
                    onDone()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun askName(ctx: Context, title: String, initial: String, ok: (String) -> Unit) {
        val et = EditText(ctx).apply { setText(initial); hint = "Nombre (ej. Familia, Juegos)"; setSingleLine() }
        AlertDialog.Builder(ctx).setTitle(title).setView(et)
            .setPositiveButton("Guardar") { _, _ -> et.text.toString().trim().take(14).takeIf { it.isNotEmpty() }?.let(ok) }
            .setNegativeButton("Cancelar", null).show()
    }

    /** Muestra las apps de la carpeta; tocar abre, mantener presionado la saca de la carpeta. */
    fun open(ctx: Context, name: String, launch: (ComponentName) -> Boolean, onChanged: () -> Unit) {
        val folder = Folders.get(ctx, name) ?: return
        val pm = ctx.packageManager
        data class It(val cn: ComponentName, val label: String, val icon: android.graphics.drawable.Drawable)
        val items = folder.apps.mapNotNull { cn ->
            try { val ai = pm.getActivityInfo(cn, 0); It(cn, ai.loadLabel(pm).toString(), ai.loadIcon(pm)) } catch (e: Exception) { null }
        }
        val grid = GridView(ctx).apply { numColumns = 3; verticalSpacing = 12; setPadding(16, 24, 16, 8) }
        val iconPx = (UiPrefs.homeIconDp(ctx) * ctx.resources.displayMetrics.density).toInt()
        lateinit var dialog: AlertDialog
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int) = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup): View {
                val v = convert ?: LayoutInflater.from(ctx).inflate(R.layout.item_pinned, parent, false)
                val it = items[p]
                v.findViewById<ImageView>(R.id.iv_pinned_icon).apply {
                    layoutParams = layoutParams.apply { width = iconPx; height = iconPx }
                    setImageDrawable(IconProvider.load(ctx, it.cn, it.icon, iconPx))
                }
                v.findViewById<TextView>(R.id.tv_pinned_name).apply { text = it.label; setTextColor(0xFF1C1C1E.toInt()) }
                return v
            }
        }
        grid.setOnItemClickListener { _, _, p, _ -> if (launch(items[p].cn)) dialog.dismiss() }
        grid.setOnItemLongClickListener { _, _, p, _ ->
            AlertDialog.Builder(ctx).setTitle("¿Sacar ${items[p].label} de la carpeta?")
                .setPositiveButton("Sacar") { _, _ -> Folders.remove(ctx, name, items[p].cn); dialog.dismiss(); onChanged() }
                .setNegativeButton("No", null).show()
            true
        }
        dialog = AlertDialog.Builder(ctx).setTitle(name).setView(grid)
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Renombrar") { _, _ -> askName(ctx, "Renombrar carpeta", name) { Folders.rename(ctx, name, it); onChanged() } }
            .setNegativeButton("Borrar") { _, _ ->
                AlertDialog.Builder(ctx).setTitle("¿Borrar la carpeta «$name»?").setMessage("Las apps no se desinstalan.")
                    .setPositiveButton("Borrar") { _, _ -> Folders.delete(ctx, name); onChanged() }
                    .setNegativeButton("No", null).show()
            }.show()
    }
}
