package com.oasis.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.math.sqrt

/** Contactos de emergencia, ubicación, SMS, datos médicos y avisos de batería. */
object Safety {
    const val REQ_SMS = 204
    const val REQ_ACTIVITY = 205
    const val REQ_LOCATION = 206

    data class Contact(val name: String, val number: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    // ---------- contactos de emergencia (máx. 3; el primero es el cuidador) ----------
    fun contacts(ctx: Context): List<Contact> = try {
        val a = JSONArray(prefs(ctx).getString("sos_contacts", "[]") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it) }.map { Contact(it.optString("n"), it.optString("p")) }
            .filter { it.number.isNotBlank() }
    } catch (e: Exception) { emptyList() }

    fun saveContacts(ctx: Context, list: List<Contact>) {
        val a = JSONArray()
        list.filter { it.number.isNotBlank() }.take(3).forEach { a.put(JSONObject().put("n", it.name).put("p", it.number)) }
        prefs(ctx).edit().putString("sos_contacts", a.toString()).apply()
    }

    fun caregiver(ctx: Context): Contact? = contacts(ctx).firstOrNull()

    // ---------- permisos ----------
    fun has(ctx: Context, perm: String) = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    /** @return true si ya se puede mandar SMS; si no, pide el permiso y devuelve false. */
    fun ensureSms(act: Activity): Boolean {
        if (has(act, Manifest.permission.SEND_SMS)) return true
        ActivityCompat.requestPermissions(act, arrayOf(Manifest.permission.SEND_SMS), REQ_SMS)
        return false
    }

    // ---------- ubicación ----------
    @Suppress("MissingPermission")
    fun lastLocation(ctx: Context): Location? {
        if (!has(ctx, Manifest.permission.ACCESS_FINE_LOCATION) && !has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: Exception) { null }
    }

    fun locationLink(ctx: Context): String? =
        lastLocation(ctx)?.let { "https://maps.google.com/?q=${"%.6f".format(java.util.Locale.US, it.latitude)},${"%.6f".format(java.util.Locale.US, it.longitude)}" }

    // ---------- SMS ----------
    fun sendSms(ctx: Context, number: String, text: String): Boolean {
        if (!has(ctx, Manifest.permission.SEND_SMS) || number.isBlank()) return false
        return try {
            val sms = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
            sms.sendMultipartTextMessage(number, null, sms.divideMessage(text), null, null)
            true
        } catch (e: Exception) { false }
    }

    // ---------- datos médicos ----------
    val MED_FIELDS = listOf(
        "med_name" to "Nombre completo", "med_blood" to "Tipo de sangre", "med_allergies" to "Alergias",
        "med_conditions" to "Enfermedades o condiciones", "med_meds" to "Medicinas que toma", "med_notes" to "Notas para quien me ayude"
    )

    fun medicalText(ctx: Context): String {
        val p = prefs(ctx)
        val rows = MED_FIELDS.mapNotNull { (k, label) -> p.getString(k, "")?.takeIf { it.isNotBlank() }?.let { "$label: $it" } }
        return rows.joinToString("\n")
    }

    // ---------- SOS ----------
    fun sosMessage(ctx: Context): String {
        val name = prefs(ctx).getString("med_name", "")?.takeIf { it.isNotBlank() } ?: "Tu contacto"
        val link = locationLink(ctx)
        return "🆘 $name necesita ayuda. " + (if (link != null) "Ubicación: $link" else "No pude obtener la ubicación.")
    }

    /** Manda el SMS de ayuda a todos los contactos. @return cuántos se enviaron. */
    fun sendSosSms(ctx: Context): Int = contacts(ctx).count { sendSms(ctx, it.number, sosMessage(ctx)) }

    // ---------- aviso de batería baja al cuidador ----------
    fun checkBattery(ctx: Context) {
        val p = prefs(ctx)
        if (!p.getBoolean("batt_alert", false)) return
        val cg = caregiver(ctx) ?: return
        val i = ctx.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100 / maxOf(1, i.getIntExtra(BatteryManager.EXTRA_SCALE, 100))
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val now = System.currentTimeMillis()
        if (level <= 15 && !charging && now - p.getLong("batt_alert_at", 0L) > 6 * 3600_000L) {
            val name = p.getString("med_name", "")?.takeIf { it.isNotBlank() } ?: "Tu contacto"
            if (sendSms(ctx, cg.number, "🔋 A $name le queda $level% de batería en el teléfono.")) p.edit().putLong("batt_alert_at", now).apply()
        }
    }
}

/** PIN de cuidador: protege Ajustes y desinstalar apps. Se guarda solo un resumen (hash), nunca el PIN. */
object CaregiverPin {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("oasis_pin", Context.MODE_PRIVATE)
    @Volatile private var unlockedUntil = 0L
    private var failures = 0
    private var lockedUntil = 0L

    fun isSet(ctx: Context) = prefs(ctx).getString("hash", null) != null

    private fun hash(salt: String, pin: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun set(ctx: Context, pin: String) {
        val salt = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        prefs(ctx).edit().putString("salt", salt).putString("hash", hash(salt, pin)).apply()
        unlockedUntil = System.currentTimeMillis() + 5 * 60_000L
    }

    fun clear(ctx: Context) { prefs(ctx).edit().clear().apply(); unlockedUntil = 0L }

    fun verify(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        return p.getString("hash", null) == hash(p.getString("salt", "") ?: "", pin)
    }

    fun unlockedNow() = System.currentTimeMillis() < unlockedUntil

    /** Pide el PIN si hace falta y luego ejecuta [onOk]. Tras acertar, queda abierto 5 minutos. */
    fun require(act: Activity, onOk: () -> Unit, onCancel: () -> Unit = {}) {
        if (!isSet(act) || unlockedNow()) { onOk(); return }
        val et = android.widget.EditText(act).apply {
            hint = "PIN de cuidador"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(56, 32, 56, 16)
        }
        androidx.appcompat.app.AlertDialog.Builder(act).setTitle("Solo para el cuidador").setView(et).setCancelable(false)
            .setPositiveButton("Entrar") { _, _ ->
                val now = System.currentTimeMillis()
                if (now < lockedUntil) {
                    android.widget.Toast.makeText(act, "Espera un momento e intenta de nuevo", android.widget.Toast.LENGTH_SHORT).show(); onCancel()
                } else if (verify(act, et.text.toString())) {
                    failures = 0; unlockedUntil = now + 5 * 60_000L; onOk()
                } else {
                    failures++
                    if (failures >= 5) { lockedUntil = now + 30_000L; failures = 0 }
                    android.widget.Toast.makeText(act, "PIN incorrecto", android.widget.Toast.LENGTH_SHORT).show(); onCancel()
                }
            }
            .setNegativeButton("Cancelar") { _, _ -> onCancel() }.show()
    }
}

/** Sirena fuerte y luz parpadeante en código SOS (··· −−− ···). */
object Siren {
    private val main = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    @Volatile private var sirenOn = false
    @Volatile private var torchOn = false

    fun isSirenOn() = sirenOn
    fun isTorchOn() = torchOn

    private val beep = object : Runnable {
        override fun run() {
            if (!sirenOn) return
            try { tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 500) } catch (_: Exception) {}
            main.postDelayed(this, 650)
        }
    }

    fun startSiren() {
        if (sirenOn) return
        try { tone = ToneGenerator(AudioManager.STREAM_ALARM, 100) } catch (e: Exception) { return }
        sirenOn = true
        main.post(beep)
    }

    fun stopSiren() {
        sirenOn = false
        main.removeCallbacks(beep)
        try { tone?.release() } catch (_: Exception) {}
        tone = null
    }

    /** Un solo pitido corto (cuenta regresiva de caídas). */
    fun tick() {
        try { ToneGenerator(AudioManager.STREAM_ALARM, 100).also { it.startTone(ToneGenerator.TONE_PROP_BEEP, 150); main.postDelayed({ it.release() }, 400) } } catch (_: Exception) {}
    }

    fun startTorchSos(ctx: Context) {
        if (torchOn) return
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = try {
            cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        } catch (e: Exception) { null } ?: return
        torchOn = true
        Thread {
            val unit = 220L
            // · · · − − − · · ·
            val pattern = listOf(1, 1, 1, 3, 3, 3, 1, 1, 1)
            try {
                while (torchOn) {
                    for (d in pattern) {
                        if (!torchOn) break
                        cm.setTorchMode(id, true); Thread.sleep(unit * d)
                        cm.setTorchMode(id, false); Thread.sleep(unit)
                    }
                    Thread.sleep(unit * 6)
                }
            } catch (_: Exception) {
            } finally { try { cm.setTorchMode(id, false) } catch (_: Exception) {} ; torchOn = false }
        }.start()
    }

    fun stopTorchSos() { torchOn = false }
}

/**
 * Detector de caídas con el acelerómetro (solo mientras OASIS está abierta y activada la opción):
 * caída libre seguida de un golpe fuerte → llama a [onFall].
 */
class FallDetector(private val ctx: Context, private val onFall: () -> Unit) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var freeFallAt = 0L
    private var lastFall = 0L
    private var running = false

    fun start() {
        if (running) return
        val s = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        running = true
    }

    fun stop() {
        if (!running) return
        sm.unregisterListener(this)
        running = false
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
        val now = System.currentTimeMillis()
        if (g < 2.5f) { if (freeFallAt == 0L) freeFallAt = now }
        else {
            if (freeFallAt != 0L && g > 26f && now - freeFallAt in 80..1200 && now - lastFall > 20_000L) {
                lastFall = now
                freeFallAt = 0L
                onFall()
            }
            if (now - freeFallAt > 1200) freeFallAt = 0L
        }
    }
}
