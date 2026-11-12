package com.oasis.app

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import java.util.Calendar
import java.util.Locale

/** Lecturas del estado del teléfono para el dashboard. Todo es de solo lectura. */
object SystemStats {

    data class Battery(val pct: Int, val charging: Boolean, val tempC: Float)
    data class Memory(val used: Long, val total: Long) { val pct get() = if (total > 0) (used * 100 / total).toInt() else 0 }
    data class Net(val type: String, val linkMbps: Int, val level: Int, val band: String)
    data class NetApp(val pkg: String, val label: String, val bytes: Long)

    fun battery(ctx: Context): Battery {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return Battery(pct, charging, temp)
    }

    fun ram(ctx: Context): Memory {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return Memory((mi.totalMem - mi.availMem).coerceAtLeast(0), mi.totalMem)
    }

    fun storage(): Memory = try {
        val st = StatFs(Environment.getDataDirectory().path)
        val total = st.blockCountLong * st.blockSizeLong
        Memory(total - st.availableBlocksLong * st.blockSizeLong, total)
    } catch (e: Exception) { Memory(0, 0) }

    @Suppress("DEPRECATION")
    fun network(ctx: Context): Net {
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> Net("Sin conexión", 0, 0, "")
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                    val info = wm.connectionInfo
                    val speed = info?.linkSpeed ?: 0
                    val level = if (info != null) WifiManager.calculateSignalLevel(info.rssi, 5) else 0
                    val freq = info?.frequency ?: 0
                    val band = when {
                        freq > 5900 -> "6 GHz"
                        freq > 4900 -> "5 GHz"
                        freq > 2000 -> "2.4 GHz"
                        else -> ""
                    }
                    Net("Wi-Fi", speed.coerceAtLeast(0), level, band)
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Net("Datos móviles", 0, 0, "")
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Net("Cable", 0, 0, "")
                else -> Net("Conectado", 0, 0, "")
            }
        } catch (e: Exception) { Net("Sin datos", 0, 0, "") }
    }

    /** Velocidad en vivo (bytes por segundo) calculada entre dos lecturas. */
    class TrafficMeter {
        private var lastRx = -1L
        private var lastTx = -1L
        private var lastT = 0L

        /** @return par (bajada, subida) en bytes/s, o null en la primera lectura / si el equipo no lo soporta. */
        fun sample(): Pair<Long, Long>? {
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val now = System.nanoTime()
            if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) return null
            val out = if (lastRx >= 0 && now > lastT) {
                val dt = (now - lastT) / 1_000_000_000.0
                Pair(((rx - lastRx) / dt).toLong().coerceAtLeast(0), ((tx - lastTx) / dt).toLong().coerceAtLeast(0))
            } else null
            lastRx = rx; lastTx = tx; lastT = now
            return out
        }
    }

    @Suppress("DEPRECATION")
    fun hasUsageAccess(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    /** Apps que más datos usaron. [days]: 1 = desde hoy 00:00, 7, 30. Llamar fuera del hilo principal. */
    fun topNetworkApps(ctx: Context, days: Int, limit: Int): List<NetApp> {
        val nsm = ctx.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager ?: return emptyList()
        val end = System.currentTimeMillis()
        val start = if (days <= 1) {
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else end - days * 86_400_000L

        val totals = HashMap<Int, Long>()
        for (type in intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)) {
            try {
                val stats = nsm.querySummary(type, null, start, end)
                val b = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(b)
                    totals[b.uid] = (totals[b.uid] ?: 0L) + b.rxBytes + b.txBytes
                }
                stats.close()
            } catch (e: Exception) { /* este tipo de red no está disponible: se ignora */ }
        }

        val pm = ctx.packageManager
        return totals.entries
            .filter { it.key >= 10000 && it.value > 0 }
            .mapNotNull { (uid, bytes) ->
                val pkg = pm.getPackagesForUid(uid)?.firstOrNull() ?: return@mapNotNull null
                val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                NetApp(pkg, label, bytes)
            }
            .groupBy { it.pkg }
            .map { (pkg, list) -> NetApp(pkg, list.first().label, list.sumOf { it.bytes }) }
            .sortedByDescending { it.bytes }
            .take(limit)
    }

    /** Apps más usadas hoy (tiempo en pantalla). Requiere acceso a uso. Llamar fuera del hilo principal. */
    fun topScreenTime(ctx: Context, limit: Int): List<Pair<NetApp, Long>> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return emptyList()
        val end = System.currentTimeMillis()
        val start = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val pm = ctx.packageManager
        return try {
            usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, start, end)
                .filter { it.totalTimeInForeground > 60_000 && it.packageName != ctx.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
                .groupBy { it.packageName }
                .map { (pkg, l) ->
                    val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                    NetApp(pkg, label, 0) to l.sumOf { it.totalTimeInForeground }
                }
                .sortedByDescending { it.second }
                .take(limit)
        } catch (e: Exception) { emptyList() }
    }

    fun fmtDuration(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
    }

    fun fmtBytes(b: Long): String {
        val kb = 1024.0
        val v = b.toDouble()
        return when {
            v >= kb * kb * kb -> String.format(Locale.getDefault(), "%.1f GB", v / (kb * kb * kb))
            v >= kb * kb -> String.format(Locale.getDefault(), "%.0f MB", v / (kb * kb))
            v >= kb -> String.format(Locale.getDefault(), "%.0f KB", v / kb)
            else -> "$b B"
        }
    }

    /** Memoria / almacenamiento en decimal como lo anuncian los fabricantes (GB). */
    fun fmtGb(b: Long): String = String.format(Locale.getDefault(), "%.1f GB", b / 1_000_000_000.0)

    fun fmtRate(bytesPerSec: Long): String {
        val bits = bytesPerSec * 8.0
        return when {
            bits >= 1_000_000 -> String.format(Locale.getDefault(), "%.1f Mb/s", bits / 1_000_000)
            bits >= 1_000 -> String.format(Locale.getDefault(), "%.0f Kb/s", bits / 1_000)
            else -> "0 Kb/s"
        }
    }
}
