package com.oasis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.core.app.ActivityCompat
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
            // Fallback: Google Dialer
            val dialerIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.dialer")
            if (dialerIntent != null) {
                dialerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(dialerIntent)
                return
            }
            // Fallback: Motorola Dialer
            val motorolaDialer = context.packageManager.getLaunchIntentForPackage("com.motorola.dialer")
            if (motorolaDialer != null) {
                motorolaDialer.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(motorolaDialer)
                return
            }
            // Fallback: Android Dialer genérico
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

    fun dialNumber(number: String) {
        try {
            val cleanNumber = number.replace(Regex("[^0-9+#*]"), "")
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
            }
            startActivitySafe(intent, "No se pudo iniciar la llamada")
            tts.speak("Marcando $number")
        } catch (e: Exception) {
            tts.speak("No se pudo marcar el número")
        }
    }

    fun searchContactAndDial(contactName: String) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            tts.speak("Necesito permiso para acceder a tus contactos")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = ContactsContract.Contacts.CONTENT_URI
                putExtra(ContactsContract.Intents.Insert.NAME, contactName)
            }
            startActivitySafe(intent, "No se encontró la aplicación de contactos")
        } catch (e: Exception) {
            openDialer()
        }
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

    fun openSmsToContact(contactName: String) {
        val lowerContact = contactName.lowercase()
        if (lowerContact.contains("whatsapp") || lowerContact.contains("wasap")) {
            openWhatsApp()
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$contactName")
            }
            startActivitySafe(intent, "No hay aplicación de mensajes")
            tts.speak("Mensaje para $contactName")
        } catch (e: Exception) {
            tts.speak("No pude abrir mensajes")
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

            // Fallback: Google Contacts
            val googleContacts = context.packageManager.getLaunchIntentForPackage("com.google.android.contacts")
            if (googleContacts != null) {
                googleContacts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(googleContacts)
                return
            }

            // Fallback: Motorola Contacts
            val motorolaContacts = context.packageManager.getLaunchIntentForPackage("com.motorola.contacts")
            if (motorolaContacts != null) {
                motorolaContacts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(motorolaContacts)
                return
            }

            // Fallback: Android Contacts genérico
            val genericContacts = context.packageManager.getLaunchIntentForPackage("com.android.contacts")
            if (genericContacts != null) {
                genericContacts.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(genericContacts)
                return
            }

            // Fallback por categoría
            val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CONTACTS)
            }
            startActivitySafe(fallbackIntent, "No se encontró aplicación de contactos")
        } catch (e: Exception) {
            tts.speak("No pude abrir contactos")
        }
    }

    // === LANZADOR DE APPS ===

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
