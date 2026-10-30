package com.oasis.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val context: Context,
    apps: List<ResolveInfo>,
    private val packageManager: PackageManager,
    private val isPinned: (ComponentName) -> Boolean = { false },
    private val onTogglePin: ((ComponentName) -> Unit)? = null,
    private val onLaunched: () -> Unit = {}
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private class Entry(val info: ResolveInfo, val label: String)

    private val iconPx = (UiPrefs.drawerIconDp(context) * context.resources.displayMetrics.density).toInt()
    private val showLabels = UiPrefs.showLabels(context)

    // Las etiquetas se cargan una sola vez (loadLabel es costoso) y quedan ordenadas A-Z
    private val all: List<Entry> = apps
        .map { Entry(it, it.loadLabel(packageManager).toString()) }
        .sortedBy { it.label.lowercase() }
    private var shown: List<Entry> = all

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_app_icon)
        val name: TextView = view.findViewById(R.id.tv_app_name)
    }

    fun filter(query: String) {
        val q = query.trim().lowercase()
        shown = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
        notifyDataSetChanged()
    }

    /** Si queda exactamente una coincidencia, la abre (tecla "buscar" del teclado). */
    fun launchSingleMatch(): Boolean {
        val only = shown.singleOrNull() ?: return false
        launch(only)
        return true
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val entry = shown[position]
        holder.name.text = entry.label
        holder.name.visibility = if (showLabels) View.VISIBLE else View.GONE
        holder.icon.layoutParams = holder.icon.layoutParams.apply { width = iconPx; height = iconPx }
        holder.name.typeface = FontManager.typeface(context, false)
        val ai = entry.info.activityInfo
        holder.icon.setImageDrawable(
            IconProvider.load(
                context, ComponentName(ai.packageName, ai.name),
                entry.info.loadIcon(packageManager), iconPx
            )
        )
        holder.itemView.setOnClickListener { launch(entry) }
        holder.itemView.setOnLongClickListener { v ->
            val toggle = onTogglePin ?: return@setOnLongClickListener false
            val cn = ComponentName(entry.info.activityInfo.packageName, entry.info.activityInfo.name)
            PopupMenu(context, v).apply {
                menu.add(if (isPinned(cn)) "Quitar de inicio" else "Fijar en inicio")
                setOnMenuItemClickListener { toggle(cn); true }
            }.show()
            true
        }
    }

    private fun launch(entry: Entry) {
        val ai = entry.info.activityInfo
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(ai.packageName, ai.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
            onLaunched()
        } catch (_: Exception) { /* la app pudo desinstalarse mientras se mostraba */ }
    }

    override fun getItemCount(): Int = shown.size
}
