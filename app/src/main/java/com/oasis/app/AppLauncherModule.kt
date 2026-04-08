package com.oasis.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppLauncherModule(
    private val context: Context
) {
    private val pm: PackageManager = context.packageManager

    // Mapeo de aliases a package names (¡tu lista está excelente!)
    private val appAliases = mapOf(
        "whatsapp" to "com.whatsapp",
        "wasap" to "com.whatsapp",
        "wsp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "facebook" to "com.facebook.katana",
        "fb" to "com.facebook.katana",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "tiktok" to "com.zhiliaoapp.musically",
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
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "ajustes" to "com.android.settings",
        "configuración" to "com.android.settings",
        "settings" to "com.android.settings",
        "reloj" to "com.google.android.deskclock",
        "alarma" to "com.google.android.deskclock"
    )

    /**
     * Intenta abrir una aplicación
     * @return true si se abrió, false si no se encontró
     */
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
                return launchCamera()
            normalized.contains("calculadora") -> 
                return launchCalculator()
        }

        // 3. Búsqueda por nombre visible
        return searchAndLaunchByLabel(normalized)
    }

    private fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = pm.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCamera(): Boolean {
        return try {
            val intent = Intent("android.media.action.IMAGE_CAPTURE")
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }

    private fun launchCalculator(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CALCULATOR)
            }
            if (intent.resolveActivity(pm) != null) {
                context.startActivity(intent)
                true
            } else false
        } catch (e: Exception) {
            false
        }
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
}
