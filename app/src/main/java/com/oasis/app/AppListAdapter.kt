package com.oasis.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
    private val onLaunched: () -> Unit = {},
    private val labelColor: Int = 0xFF1C1C1E.toInt(),
    private val onFolderChanged: (() -> Unit)? = null
) : RecyclerView.Adapter<AppListAdapter.AppViewHolder>() {

    private class Entry(val info: ResolveInfo, val label: String)

    private val iconPx = (UiPrefs.drawerIconDp(context) * context.resources.displayMetrics.density).toInt()
    private val showLabels = UiPrefs.showLabels(context)

    // Las etiquetas se cargan una sola vez (loadLabel es costoso) y quedan ordenadas A-Z
    private var all: List<Entry> = apps
        .map { Entry(it, it.loadLabel(packageManager).toString()) }
        .sortedBy { it.label.lowercase() }
    private var shown: List<Entry> = all

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_app_icon)
        val name: TextView = view.findViewById(R.id.tv_app_name)
        val badge: TextView = view.findViewById(R.id.tv_app_badge)
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
        holder.name.setTextColor(labelColor)
        val ai = entry.info.activityInfo
        holder.icon.setImageDrawable(
            IconProvider.load(
                context, ComponentName(ai.packageName, ai.name),
                entry.info.loadIcon(packageManager), iconPx
            )
        )
        val count = OasisNotificationListener.counts[ai.packageName] ?: 0
        holder.badge.visibility = if (count > 0) View.VISIBLE else View.GONE
        holder.badge.text = if (count > 99) "99+" else count.toString()
        holder.itemView.setOnClickListener { launch(entry) }
        holder.itemView.setOnLongClickListener { v ->
            val ai2 = entry.info.activityInfo
            val cn = ComponentName(ai2.packageName, ai2.name)
            PopupMenu(context, v).apply {
                onTogglePin?.let { menu.add(0, 1, 0, if (isPinned(cn)) "Quitar de inicio" else "Fijar en inicio") }
                menu.add(0, 2, 1, "Información de la app")
                menu.add(0, 3, 2, "Desinstalar")
                menu.add(0, 4, 3, "Ocultar del cajón")
                if (onFolderChanged != null) menu.add(0, 5, 4, "Guardar en una carpeta")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> onTogglePin?.invoke(cn)
                        2 -> startSafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${cn.packageName}")))
                        3 -> {
                            val del = { startSafe(Intent(Intent.ACTION_DELETE, Uri.parse("package:${cn.packageName}"))) }
                            val a = context as? android.app.Activity
                            if (a != null) CaregiverPin.require(a, onOk = { del() }) else del()
                        }
                        4 -> hide(cn.packageName)
                        5 -> FolderUi.pickFolderFor(context, cn) { onFolderChanged?.invoke() }
                    }
                    true
                }
            }.show()
            true
        }
    }

    private fun startSafe(i: Intent) {
        try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onLaunched() } catch (_: Exception) {
            Toast.makeText(context, "No disponible en este teléfono", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hide(pkg: String) {
        HiddenApps.hide(context, pkg)
        all = all.filter { it.info.activityInfo.packageName != pkg }
        shown = shown.filter { it.info.activityInfo.packageName != pkg }
        notifyDataSetChanged()
        Toast.makeText(context, "Oculta. Restáurala en Ajustes > Apps ocultas", Toast.LENGTH_LONG).show()
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
