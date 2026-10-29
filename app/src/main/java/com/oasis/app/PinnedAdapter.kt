package com.oasis.app

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/** Rejilla de apps fijadas en la pantalla de inicio. */
class PinnedAdapter(
    private val context: Context,
    private val onLaunch: (ComponentName) -> Unit,
    private val onRemove: (ComponentName) -> Unit
) : RecyclerView.Adapter<PinnedAdapter.VH>() {

    private class Item(val component: ComponentName, val label: String, val icon: Drawable)

    private var items: List<Item> = emptyList()

    var textColor: Int = Color.WHITE
    var useShadow: Boolean = false

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_pinned_icon)
        val name: TextView = view.findViewById(R.id.tv_pinned_name)
    }

    /** Relee las apps fijadas (descarta las desinstaladas). Devuelve cuántas hay. */
    fun reload(): Int {
        val pm = context.packageManager
        val stored = PinnedApps.get(context)
        val valid = stored.mapNotNull { cn ->
            try {
                val ai = pm.getActivityInfo(cn, 0)
                Item(cn, ai.loadLabel(pm).toString(), ai.loadIcon(pm))
            } catch (e: Exception) {
                null
            }
        }
        if (valid.size != stored.size) PinnedApps.save(context, valid.map { it.component })
        items = valid
        notifyDataSetChanged()
        return items.size
    }

    fun restyle(color: Int, shadow: Boolean) {
        textColor = color
        useShadow = shadow
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(context).inflate(R.layout.item_pinned, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.name.text = item.label
        holder.name.setTextColor(textColor)
        if (useShadow) holder.name.setShadowLayer(6f, 0f, 1.5f, 0x99000000.toInt())
        else holder.name.setShadowLayer(0f, 0f, 0f, 0)
        holder.icon.setImageDrawable(item.icon)

        holder.itemView.setOnClickListener { onLaunch(item.component) }
        holder.itemView.setOnLongClickListener { v ->
            PopupMenu(context, v).apply {
                menu.add("Quitar de inicio")
                setOnMenuItemClickListener { onRemove(item.component); true }
            }.show()
            true
        }
    }

    override fun getItemCount(): Int = items.size
}
