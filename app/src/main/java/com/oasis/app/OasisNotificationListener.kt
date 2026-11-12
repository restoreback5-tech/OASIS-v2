package com.oasis.app

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import java.util.concurrent.ConcurrentHashMap

/** Cuenta las notificaciones activas por app (para insignias en los iconos). Solo cuenta, no lee su contenido. */
class OasisNotificationListener : NotificationListenerService() {

    companion object {
        val counts = ConcurrentHashMap<String, Int>()
        @Volatile var onChange: (() -> Unit)? = null

        fun isEnabled(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }

    override fun onListenerConnected() { recount() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) { recount() }
    override fun onNotificationRemoved(sbn: StatusBarNotification?) { recount() }

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
