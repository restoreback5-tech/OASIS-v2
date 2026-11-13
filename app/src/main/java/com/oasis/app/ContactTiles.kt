package com.oasis.app

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory

/** Mosaico de un contacto favorito: foto (o inicial), tocar llama, mantener presionado ofrece mensaje o WhatsApp. */
object ContactTiles {

    fun build(
        act: AppCompatActivity, parent: ViewGroup, f: TodayData.Fav, sizeDp: Int, accent: Int,
        phone: PhoneActionsModule, speak: (String) -> Unit = {}
    ): View {
        val t = act.layoutInflater.inflate(R.layout.tile_contact, parent, false)
        val d = act.resources.displayMetrics.density
        t.findViewById<FrameLayout>(R.id.tile_frame).layoutParams =
            (t.findViewById<FrameLayout>(R.id.tile_frame).layoutParams).apply { width = (sizeDp * d).toInt(); height = (sizeDp * d).toInt() }
        val initial = t.findViewById<TextView>(R.id.tile_initial)
        initial.text = f.name.trim().take(1).uppercase()
        initial.textSize = if (sizeDp >= 64) 30f else 22f
        initial.backgroundTintList = ColorStateList.valueOf((accent and 0x00FFFFFF) or 0x55000000)
        val photo = t.findViewById<ImageView>(R.id.tile_photo)
        if (f.photo != null) {
            val r = RoundedBitmapDrawableFactory.create(act.resources, f.photo)
            r.isCircular = true
            photo.setImageDrawable(r)
            photo.visibility = View.VISIBLE
        }
        t.findViewById<TextView>(R.id.tile_name).apply {
            text = f.name.substringBefore(' ')
            if (sizeDp >= 64) textSize = 15f
        }
        t.contentDescription = "Llamar a ${f.name}"
        t.setOnClickListener { speak("Llamando a ${f.name.substringBefore(' ')}"); phone.dialNumber(f.number, f.name) }
        t.setOnLongClickListener { v ->
            PopupMenu(act, v).apply {
                menu.add(0, 1, 0, "Llamar")
                menu.add(0, 2, 1, "Mensaje de texto")
                menu.add(0, 3, 2, "Abrir chat de WhatsApp")
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> phone.dialNumber(f.number, f.name)
                        2 -> phone.openSmsToContact(f.name)
                        3 -> whatsapp(act, f.number)
                    }
                    true
                }
            }.show()
            true
        }
        Fx.pressable(t, 0.9f)
        return t
    }

    private fun whatsapp(act: AppCompatActivity, number: String) {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return
        val uri = Uri.parse("https://wa.me/$digits")
        try {
            act.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try { act.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (e2: Exception) { Toast.makeText(act, "WhatsApp no está disponible", Toast.LENGTH_SHORT).show() }
        }
    }
}
