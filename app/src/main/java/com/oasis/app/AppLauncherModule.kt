package com.oasis.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

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
