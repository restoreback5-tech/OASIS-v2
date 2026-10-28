package com.oasis.app

/**
 * Registro central de apps conocidas.
 * Único punto de verdad para resolver "wasap" → "com.whatsapp", etc.
 *
 * Lo usan:
 *  - VoiceCommandModule (para normalizar lo que el usuario dijo)
 *  - AppLauncherModule (para lanzar el package correcto)
 */
object AppRegistry {

    /**
     * Mapa de alias (lowercase, sin acentos) → package name de Android.
     * Varias claves pueden apuntar al mismo package (wasap / whatsapp / wsp).
     */
    val aliasesToPackage: Map<String, String> = mapOf(
        // === Mensajería ===
        "whatsapp" to "com.whatsapp",
        "wasap" to "com.whatsapp",
        "wsp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "signal" to "org.thoughtcrime.securesms",

        // === Redes sociales ===
        "facebook" to "com.facebook.katana",
        "fb" to "com.facebook.katana",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "twitter" to "com.twitter.android",
        "tiktok" to "com.zhiliaoapp.musically",

        // === Google ===
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

        // === Entretenimiento ===
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "disney" to "com.disney.disneyplus",
        "hbo" to "com.hbo.hbonow",
        "prime" to "com.amazon.avod.thirdpartyclient",
        "amazon" to "com.amazon.avod.thirdpartyclient",

        // === Utilidades ===
        "reloj" to "com.google.android.deskclock",
        "alarma" to "com.google.android.deskclock",
        "contactos" to "com.google.android.contacts",
        "configuración" to "com.android.settings",
        "configuracion" to "com.android.settings",
        "settings" to "com.android.settings",

        // === Transporte ===
        "uber" to "com.ubercab",
        "didi" to "com.sdu.didi.psnger",
        "waze" to "com.waze",

        // === Financieras ===
        "paypal" to "com.paypal.android.p2pmobile",
        "bbva" to "com.bbva.netcash",
        "santander" to "com.santander.app"
    )

    /**
     * Devuelve el package name asociado a un texto libre.
     * Ej: "abre wasap por favor" → "com.whatsapp"
     * Devuelve null si no encuentra ningún alias.
     */
    fun packageFor(input: String): String? {
        val normalized = input.lowercase().trim()
        return aliasesToPackage.entries
            .firstOrNull { normalized.contains(it.key) }
            ?.value
    }

    /**
     * Devuelve el alias canónico (la clave del mapa) que aparece en el texto.
     * Ej: "wasap" → "whatsapp". Útil para hablar: "Abriendo whatsapp".
     * Si no encuentra alias, devuelve el texto original limpio.
     */
    fun canonicalAliasFor(input: String): String {
        val normalized = input.lowercase().trim()
        val found = aliasesToPackage.keys.firstOrNull { normalized.contains(it) }
        return found ?: normalized
    }
}
