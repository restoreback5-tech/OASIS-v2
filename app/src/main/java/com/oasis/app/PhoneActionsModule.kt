package com.oasis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

class PhoneActionsModule(
    private val context: Context,
    private val tts: TTSModule,
    private val sound: SoundModule
) {

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun startActivitySafe(intent: Intent, errorMessage: String) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                tts.speak(errorMessage)
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            tts.speak(errorMessage)
        }
    }

    // === ACCIONES DE TELÉFONO ===

    fun openDialer() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:") }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
            val dialerIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.dialer")
            if (dialerIntent != null) {
                dialerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(dialerIntent)
                return
            }
            val motorolaDialer = context.packageManager.getLaunchIntentForPackage("com.motorola.dialer")
            if (motorolaDialer != null) {
                motorolaDialer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(motorolaDialer)
                return
            }
            val genericDialer = context.packageManager.getLaunchIntentForPackage("com.android.dialer")
            if (genericDialer != null) {
                genericDialer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(genericDialer)
                return
            }
            tts.speak("No se encontró aplicación de teléfono")
        } catch (e: Exception) {
            tts.speak("No pude abrir el marcador")
        }
    }

    fun dialNumber(number: String, spokenName: String? = null) {
        try {
            val cleanNumber = number.replace(Regex("[^0-9+#*]"), "")
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
            }
            startActivitySafe(intent, "No se pudo iniciar la llamada")
            tts.speak(if (spokenName.isNullOrBlank()) "Marcando $number" else "Llamando a $spokenName")
        } catch (e: Exception) {
            tts.speak("No se pudo marcar el número")
        }
    }

    /** Busca el contacto por nombre y abre el marcador con su número (el usuario confirma con un toque). */
    fun searchContactAndDial(contactName: String) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            tts.speak("Necesito permiso para acceder a tus contactos")
            return
        }
        val number = lookupContactNumber(contactName)
        if (!number.isNullOrEmpty()) {
            dialNumber(number, contactName)
            return
        }
        tts.speak("No encontré a $contactName")
        openContacts()
    }

    // === MENSAJES Y SMS ===

    fun openSms() {
        // Intentar WhatsApp primero
        val whatsappIntent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (whatsappIntent != null) {
            whatsappIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(whatsappIntent)
            tts.speak("Abriendo WhatsApp")
            return
        }

        // Intentar app de SMS predeterminada
        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply { data = Uri.parse("smsto:") }
            smsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (smsIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(smsIntent)
                return
            }
        } catch (e: Exception) { }

        // Fallback: Google Messages
        val messagesIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.messaging")
        if (messagesIntent != null) {
            messagesIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(messagesIntent)
            tts.speak("Abriendo mensajes")
            return
        }

        tts.speak("No hay aplicación de mensajes disponible")
    }

    /**
     * Envía SMS a un contacto. Distingue:
     *  - "whatsapp" → abre WhatsApp
     *  - número con ≥3 dígitos → smsto:<numero>
     *  - nombre → busca número en Contactos y abre smsto:<numero>
     *  - no encontrado → avisa por TTS + abre app SMS genérica
     */
    fun openSmsToContact(contactName: String) {
        val lowerContact = contactName.lowercase()

        // Caso 1: WhatsApp directo
        if (lowerContact.contains("whatsapp") || lowerContact.contains("wasap")) {
            openWhatsApp()
            return
        }

        // Caso 2: ya es un número (o contiene dígitos suficientes)
        val digits = contactName.replace(Regex("[^0-9+#*]"), "")
        if (digits.length >= 3) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$digits")
                }
                startActivitySafe(intent, "No hay aplicación de mensajes")
                tts.speak("Mensaje para $contactName")
            } catch (e: Exception) {
                tts.speak("No pude abrir mensajes")
            }
            return
        }

        // Caso 3: es un nombre → buscar su número en Contactos
        val number = lookupContactNumber(contactName)
        if (number != null) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$number")
                }
                startActivitySafe(intent, "No hay aplicación de mensajes")
                tts.speak("Mensaje para $contactName")
            } catch (e: Exception) {
                tts.speak("No pude abrir mensajes")
            }
        } else {
            tts.speak("No encontré el contacto $contactName")
            openSms()
        }
    }

    /**
     * Busca el primer número de teléfono asociado al nombre del contacto.
     * Devuelve null si no hay permiso, no se encuentra, o hay error.
     */
    private fun lookupContactNumber(name: String): String? {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return null
        return try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val nameCol = ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            val numCol = ContactsContract.CommonDataKinds.Phone.NUMBER

            // 1) Rápido: LIKE de SQLite (no distingue acentos: "Maria" no encuentra "María")
            val quick = context.contentResolver.query(
                uri, arrayOf(numCol), "$nameCol LIKE ?", arrayOf("%$name%"), null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            if (quick != null) return quick.replace(Regex("[^0-9+#*]"), "")

            // 2) Sin acentos y con tolerancia a errores de voz, comparando en memoria
            val target = TextMatch.normalize(name)
            val limit = TextMatch.fuzzyLimit(target.length)
            var best: Pair<Int, String>? = null
            context.contentResolver.query(uri, arrayOf(nameCol, numCol), null, null, null)?.use { c ->
                var seen = 0
                while (c.moveToNext() && seen++ < 5000) {
                    val display = TextMatch.normalize(c.getString(0).orEmpty())
                    val number = c.getString(1) ?: continue
                    val dist = when {
                        display.contains(target) -> 0
                        else -> display.split(" ")
                            .minOfOrNull { TextMatch.levenshtein(it, target) } ?: Int.MAX_VALUE
                    }
                    if (dist <= limit && (best == null || dist < best!!.first)) best = dist to number
                }
            }
            best?.second?.replace(Regex("[^0-9+#*]"), "")
        } catch (e: Exception) {
            Log.w("PhoneActions", "lookupContactNumber falló: ${e.message}")
            null
        }
    }

    private fun openWhatsApp() {
        val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            tts.speak("Abriendo WhatsApp")
        } else {
            tts.speak("WhatsApp no está instalado")
            try {
                val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp"))
                playIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(playIntent)
            } catch (e: Exception) {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp"))
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(webIntent)
            }
        }
    }

    // === CONTACTOS ===

    fun openContacts() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            tts.speak("Necesito permiso para acceder a tus contactos")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = ContactsContract.Contacts.CONTENT_URI
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return
            }
            val googleContacts = context.packageManager.getLaunchIntentForPackage("com.google.android.contacts")
            if (googleContacts != null) {
                googleContacts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(googleContacts)
                return
            }
            val motorolaContacts = context.packageManager.getLaunchIntentForPackage("com.motorola.contacts")
            if (motorolaContacts != null) {
                motorolaContacts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(motorolaContacts)
                return
            }
            val genericContacts = context.packageManager.getLaunchIntentForPackage("com.android.contacts")
            if (genericContacts != null) {
                genericContacts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(genericContacts)
                return
            }
            val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CONTACTS)
            }
            startActivitySafe(fallbackIntent, "No se encontró aplicación de contactos")
        } catch (e: Exception) {
            tts.speak("No pude abrir contactos")
        }
    }

    // === LANZADOR ===

    fun openAppDrawer() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                tts.speak("No se encontró el lanzador de aplicaciones")
            }
        } catch (e: Exception) {
            tts.speak("No pude abrir las aplicaciones")
        }
    }
}
