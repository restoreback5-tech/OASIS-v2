package com.oasis.app

import android.app.Notification
import android.content.Context
import android.os.PowerManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationManagerCompat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 1) Cuenta las notificaciones activas por app (insignias en los iconos).
 * 2) Si el usuario lo activa en Ajustes, lee en voz alta los mensajes que llegan (solo con la pantalla encendida).
 */
class OasisNotificationListener : NotificationListenerService() {

    companion object {
        val counts = ConcurrentHashMap<String, Int>()
        @Volatile var onChange: (() -> Unit)? = null

        private val MESSAGE_PACKAGES = setOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"
        )

        fun isEnabled(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastKey = ""
    private var lastAt = 0L

    override fun onListenerConnected() { recount() }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        recount()
        if (sbn != null) try { handleMessage(sbn) } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { recount() }

    override fun onDestroy() {
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null
        super.onDestroy()
    }

    private fun handleMessage(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val isMessage = n.category == Notification.CATEGORY_MESSAGE || sbn.packageName in MESSAGE_PACKAGES
        if (!isMessage) return
        val who = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (who.isEmpty() || text.isEmpty()) return
        val key = sbn.key + text
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastAt < 4000) return
        lastKey = key; lastAt = now

        Inbox.add(Inbox.Msg(sbn.packageName, who, text, now))

        val prefs = getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("read_messages", false)) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return          // con la pantalla apagada no se lee nada (privacidad)
        speak("Mensaje de $who: ${text.take(200)}")
    }

    private fun speak(text: String) {
        if (tts == null) {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    val t = tts ?: return@TextToSpeech
                    if (t.isLanguageAvailable(Locale("es", "MX")) >= TextToSpeech.LANG_AVAILABLE) t.language = Locale("es", "MX")
                    else t.language = Locale("es", "ES")
                    t.speak(text, TextToSpeech.QUEUE_ADD, null, "oasis-msg")
                }
            }
        } else if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "oasis-msg")
        }
    }

    private fun recount() {
        try {
            val fresh = HashMap<String, Int>()
            activeNotifications?.forEach { n ->
                if (n.isOngoing.not()) fresh[n.packageName] = (fresh[n.packageName] ?: 0) + 1
            }
            counts.clear()
            counts.putAll(fresh)
            onChange?.invoke()
        } catch (e: Exception) { /* el sistema aún no conectó el servicio */ }
    }
}
