package com.oasis.app

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Elegir con una lista las apps que se ven en la pantalla de inicio (máximo [PinnedApps.MAX]). */
object HomePicker {
    fun show(act: Activity, onDone: () -> Unit) {
        val pm = act.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != act.packageName }
            .filter { !HiddenApps.isHidden(act, it.activityInfo.packageName) }
            .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) to it.loadLabel(pm).toString() }
            .sortedBy { it.second.lowercase() }
        if (apps.isEmpty()) return
        val pinned = PinnedApps.get(act)
        val checked = BooleanArray(apps.size) { apps[it].first in pinned }
        val labels = apps.map { it.second }.toTypedArray()
        val dialog = AlertDialog.Builder(act)
            .setTitle("Apps en tu inicio (máx. ${PinnedApps.MAX})")
            .setMultiChoiceItems(labels, checked) { d, i, on ->
                checked[i] = on
                if (checked.count { it } > PinnedApps.MAX) {
                    checked[i] = false
                    (d as AlertDialog).listView.setItemChecked(i, false)
                    Toast.makeText(act, "Máximo ${PinnedApps.MAX} apps. Quita una para poner otra.", Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton("Listo") { _, _ ->
                // Conserva el orden de las que ya estaban y agrega las nuevas al final
                val keep = pinned.filter { cn -> apps.indexOfFirst { it.first == cn }.let { it >= 0 && checked[it] } }
                val added = apps.indices.filter { checked[it] && apps[it].first !in keep }.map { apps[it].first }
                PinnedApps.save(act, keep + added)
                onDone()
            }
            .setNegativeButton("Cancelar", null)
            .create()
        dialog.show()
    }
}
