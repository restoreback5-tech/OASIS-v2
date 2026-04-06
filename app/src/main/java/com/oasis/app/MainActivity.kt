package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.res.ColorStateList

class MainActivity : AppCompatActivity() {

    private lateinit var sound: SoundModule
    private lateinit var toast: ToastModule
    private lateinit var anim: AnimationModule
    private lateinit var tts: TTSModule
    private lateinit var stt: STTModule
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val orb = findViewById<ImageView>(R.id.orb_view)
        val neuralAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.orb_neural_pulse)
        orb.startAnimation(neuralAnim)

        sound = SoundModule(this)
        sound.preload(R.raw.cancelar, R.raw.confirmar)
        toast = ToastModule(this)
        anim = AnimationModule(findViewById(R.id.orb_view))
        tts = TTSModule(this)
        stt = STTModule(this)
        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        applyTheme()
        checkMicPermission()
        stt.setOnCommandListener { command ->
            runOnUiThread { processCommand(command) }
        }

        sound.play(R.raw.inicio)
        orb.postDelayed({ tts.speak("Bienvenido a OASIS") }, 1000)
        anim.startRippleAnimation()

        findViewById<ImageView>(R.id.btn_settings).setOnClickListener {
            sound.play(R.raw.touch)
            tts.speak("Ajustes")
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        val clockHandler = Handler(Looper.getMainLooper())
        val clockRunnable = object : Runnable {
            override fun run() {
                val is24Hour = prefs.getBoolean("clock_24h", true)
                val format = if (is24Hour) SimpleDateFormat("HH:mm", Locale.getDefault())
                else SimpleDateFormat("hh:mm a", Locale.getDefault())
                findViewById<TextView>(R.id.clock_text).text = format.format(System.currentTimeMillis())
                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(clockRunnable)

        orb.setOnClickListener {
            sound.play(R.raw.touch)
            toast.show("Escuchando...")
            stt.startListening()
        }

        setupBtn(R.id.btn_call, "Llamar") { openDialer() }
        setupBtn(R.id.btn_message, "Enviar mensaje") { openSms() }
        setupBtn(R.id.btn_contacts, "Contactos") { openContacts() }
        setupBtn(R.id.btn_apps, "Apps") { openLauncher() }
    }

    private fun setupBtn(id: Int, text: String, action: () -> Unit) {
        findViewById<MaterialButton>(id).setOnClickListener {
            sound.play(R.raw.touch)
            pulseAnimation(findViewById<View>(id))
            toast.show(text)
            tts.speak(text)
            action()
        }
    }

    private fun openDialer() {        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:")
            startActivity(intent)
        } catch(_: Exception) { }
    }

    private fun openSms() {
        try {
            val whatsappIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (whatsappIntent != null) {
                startActivity(whatsappIntent)
            } else {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.type = "vnd.android-dir/mms-sms"
                startActivity(intent)
            }
        } catch(_: Exception) {
            toast.show("No hay app de mensajes")
        }
    }

    private fun openContacts() {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = android.provider.ContactsContract.Contacts.CONTENT_URI
            startActivity(intent)
        } catch(_: Exception) { }
    }

    private fun openLauncher() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            startActivity(intent)
        } catch(_: Exception) { }
    }

    private fun processCommand(cmd: String) {
        val cmdLower = cmd.lowercase().trim()
        toast.show("Comando: $cmd")

        when {
            cmdLower.contains("abrir") || cmdLower.contains("abre") || cmdLower.contains("lanza") -> {
                val appName = extractAppName(cmdLower)
                if (appName.isNotEmpty()) openSpecificApp(appName)
                else tts.speak("¿Qué aplicación deseas abrir?")
            }
            cmdLower.contains("llamar") || cmdLower.contains("llama a") -> {
                val contactName = extractContactName(cmdLower)                if (contactName.isNotEmpty()) {
                    if (contactName.any { it.isDigit() }) dialNumber(contactName)
                    else {
                        tts.speak("Buscando el contacto…")
                        openDialer()
                    }
                } else tts.speak("¿A quién deseas llamar?")
            }
            cmdLower.contains("mensaje") || cmdLower.contains("mandar") || cmdLower.contains("enviar") -> {
                val contactName = extractContactName(cmdLower)
                if (contactName.isNotEmpty()) openSmsToContact(contactName)
                else openSms()
            }
            cmdLower.contains("contacto") -> openContacts()
            cmdLower.contains("app") || cmdLower.contains("menú") -> openLauncher()
            cmdLower.contains("hola") -> tts.speak("Hola, soy OASIS.")
            cmdLower.contains("ayuda") || cmdLower.contains("qué puedes hacer") -> {
                tts.speak("Puedo abrir aplicaciones, hacer llamadas y enviar mensajes.")
            }
            else -> tts.speak("No entendí. Intenta decir: 'Abre WhatsApp' o 'Haz una llamada'.")
        }
    }

    private fun extractAppName(cmd: String): String {
        var result = cmd
        listOf("abrir", "abre", "lanza", "inicia", "la app", "el", "la", "aplicación").forEach { kw ->
            result = result.replace(kw, "").trim()
        }
        result = result.replace(Regex("\\b(el|la|los|las|un|una|a|de|del|para|por)\\b"), "").trim()
        return result.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]"), "").trim()
    }

    private fun extractContactName(cmd: String): String {
        var result = cmd
        listOf("llamar", "llama a", "mensaje", "mandar", "enviar", "a").forEach { kw ->
            result = result.replace(kw, "").trim()
        }
        result = result.replace(Regex("\\b(el|la|los|las|un|una|de|del|para|por)\\b"), "").trim()
        return result.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]"), "").trim()
    }

    private fun openSpecificApp(appName: String) {
        val normalizedApp = appName.lowercase().trim()
        val packageName = when {
            normalizedApp.contains("whatsapp") || normalizedApp.contains("wasap") -> "com.whatsapp"
            normalizedApp.contains("youtube") || normalizedApp.contains("tubo") -> "com.google.android.youtube"
            normalizedApp.contains("facebook") || normalizedApp.contains("fb") -> "com.facebook.katana"
            normalizedApp.contains("instagram") || normalizedApp.contains("insta") -> "com.instagram.android"
            normalizedApp.contains("chrome") || normalizedApp.contains("navegador") -> "com.android.chrome"
            normalizedApp.contains("camara") || normalizedApp.contains("cámara") -> "com.android.camera2"            normalizedApp.contains("ajustes") || normalizedApp.contains("configuración") -> "com.android.settings"
            normalizedApp.contains("reloj") || normalizedApp.contains("alarma") -> "com.google.android.deskclock"
            normalizedApp.contains("calculadora") -> "com.android.calculator2"
            normalizedApp.contains("spotify") -> "com.spotify.music"
            normalizedApp.contains("maps") || normalizedApp.contains("mapas") -> "com.google.android.apps.maps"
            normalizedApp.contains("telegram") -> "org.telegram.messenger"
            normalizedApp.contains("twitter") || normalizedApp.contains("x") -> "com.twitter.android"
            normalizedApp.contains("netflix") -> "com.netflix.mediaclient"
            normalizedApp.contains("tiktok") -> "com.zhiliaoapp.musically"
            else -> null
        }

        if (packageName != null) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    tts.speak("Abriendo $appName")
                } else {
                    tts.speak("Esta aplicación no está instalada")
                }
            } catch (e: Exception) {
                tts.speak("No se pudo abrir esta aplicación")
            }
        } else {
            tts.speak("No reconozco esa aplicación")
        }
    }

    private fun dialNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:$number")
            startActivity(intent)
            tts.speak("Marcando")
        } catch (e: Exception) {
            tts.speak("No se pudo marcar")
        }
    }

    private fun openSmsToContact(contactName: String) {
        try {
            if (contactName.lowercase().contains("whatsapp") || contactName.lowercase().contains("wasap")) {
                val whatsappIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (whatsappIntent != null) {
                    startActivity(whatsappIntent)
                    tts.speak("Abriendo WhatsApp")
                } else {
                    tts.speak("WhatsApp no está instalado")                }
            } else {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.type = "vnd.android-dir/mms-sms"
                startActivity(intent)
                tts.speak("Abriendo mensajes")
            }
        } catch (e: Exception) {
            toast.show("No se encontró app de mensajes")
        }
    }

    private fun applyTheme() {
        val selectedTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        
        val bgRes = when (selectedTheme) {
            "caribe" -> R.color.caribe_background
            "oscuro" -> R.color.oscuro_background
            else -> R.color.amanecer_background
        }
        window.setBackgroundDrawableResource(bgRes)

        val textColor = when (selectedTheme) {
            "caribe" -> ContextCompat.getColor(this, R.color.caribe_text)
            "oscuro" -> ContextCompat.getColor(this, R.color.oscuro_text)
            else -> ContextCompat.getColor(this, R.color.amanecer_text)
        }
        findViewById<TextView>(R.id.clock_text).setTextColor(textColor)
        findViewById<TextView>(R.id.greeting_text).setTextColor(textColor)
    }

    private fun pulseAnimation(view: View) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f)
        val set = AnimatorSet()
        set.playTogether(scaleX, scaleY)
        set.duration = 180
        set.interpolator = android.view.animation.DecelerateInterpolator()
        set.start()
    }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tts.speak("Permiso de micrófono concedido")
            } else {
                tts.speak("Necesito permiso de micrófono para escucharte")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
        tts.shutdown()
        stt.destroy()
    }
}
