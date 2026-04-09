package com.oasis.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.widget.Button
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager

class AppLauncherModule(
    private val context: Context
) {
    private val pm: PackageManager = context.packageManager

    // Mapeo de aliases a package names (VERSIÓN COMPLETA)
    private val appAliases = mapOf(
        // Mensajería
        "whatsapp" to "com.whatsapp",
        "wasap" to "com.whatsapp",
        "wsp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "signal" to "org.thoughtcrime.securesms",

        // Redes Sociales
        "facebook" to "com.facebook.katana",
        "fb" to "com.facebook.katana",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "tiktok" to "com.zhiliaoapp.musically",

        // Google
        "youtube" to "com.google.android.youtube",
        "tubo" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "navegador" to "com.android.chrome",
        "google" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "mapas" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "drive" to "com.google.android.apps.docs",
        "fotos" to "com.google.android.apps.photos",
        "galeria" to "com.google.android.apps.photos",
        "galería" to "com.google.android.apps.photos",

        // Entretenimiento
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "disney" to "com.disney.disneyplus",
        "hbo" to "com.hbo.hbonow",
        "prime" to "com.amazon.avod.thirdpartyclient",
        "amazon" to "com.amazon.avod.thirdpartyclient",

        // Utilidades
        "camara" to "com.google.android.camera",
        "cámara" to "com.google.android.camera",
        "calculadora" to "com.google.android.calculator",
        "reloj" to "com.google.android.deskclock",
        "alarma" to "com.google.android.deskclock",
        "ajustes" to "com.android.settings",
        "configuración" to "com.android.settings",
        "settings" to "com.android.settings",
        "archivos" to "com.google.android.documentsui",
        "contactos" to "com.google.android.contacts",
        "calendario" to "com.google.android.calendar",

        // Transporte
        "uber" to "com.ubercab",
        "didi" to "com.sdu.didi.psnger",
        "waze" to "com.waze",

        // Financieras
        "paypal" to "com.paypal.android.p2pmobile",
        "bbva" to "com.bbva.netcash",
        "santander" to "com.santander.app"
    )

    /**
     * Muestra un diálogo con aplicaciones para lanzar
     */
    fun showAppsMenu() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.apps_menu_dialog, null)
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        // Configurar botones del menú
        dialogView.findViewById<Button>(R.id.app_calculator)?.setOnClickListener {
            launchCalculatorApp()
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.app_camera)?.setOnClickListener {
            launchCameraApp()
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.app_files)?.setOnClickListener {
            launchFilesApp()
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.app_settings)?.setOnClickListener {
            launchSettingsApp()
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.app_flashlight)?.setOnClickListener {
            showFlashlightMessage()
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.app_calendar)?.setOnClickListener {
            launchCalendarApp()
            dialog.dismiss()
        }
        dialogView.findViewById<Button>(R.id.btn_close_menu)?.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    fun launchApp(appName: String): Boolean {
        val normalized = appName.lowercase().trim()

        // 1. Buscar en aliases
        val knownPackage = appAliases.entries.find {
            normalized.contains(it.key)
        }?.value

        if (knownPackage != null) {
            if (launchPackage(knownPackage)) return true
        }

        // 2. Casos especiales
        when {
            normalized.contains("camara") || normalized.contains("cámara") ->
                return launchCameraApp()
            normalized.contains("calculadora") ->
                return launchCalculatorApp()
        }

        // 3. Búsqueda por nombre visible
        return searchAndLaunchByLabel(normalized)
    }

    private fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCameraApp(): Boolean {
        return try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCalculatorApp(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun launchFilesApp(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun launchSettingsApp(): Boolean {
        return try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCalendarApp(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALENDAR)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun showFlashlightMessage() {
        Toast.makeText(context, "Activa la linterna desde el panel de notificaciones", Toast.LENGTH_LONG).show()
    }

    private fun searchAndLaunchByLabel(name: String): Boolean {
        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(intent, 0)
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(name)
        }

        return if (match != null) {
            launchPackage(match.activityInfo.packageName)
        } else false
    }
 
    fun showAllApps() {
    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(intent, 0)
    
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_all_apps, null)
    val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_apps)
    recyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(context, 3)
    recyclerView.adapter = AppListAdapter(context, apps, pm)
    
    val dialog = AlertDialog.Builder(context)
        .setView(dialogView)
        .setCancelable(true)
        .create()
    
    dialogView.findViewById<Button>(R.id.btn_close_apps).setOnClickListener {
        dialog.dismiss()
    }
    
    dialog.show()
}

}
