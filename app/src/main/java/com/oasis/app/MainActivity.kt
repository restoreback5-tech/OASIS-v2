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
        val neuralAnim = android.view.animation.AnimationUtils
            .loadAnimation(this, R.anim.orb_neural_pulse)
        orb.startAnimation(neuralAnim)

        sound = SoundModule(this)
        sound.preload(R.raw.cancelar, R.raw.confirmar)

        toast = ToastModule(this)
        anim = AnimationModule(orb)
        tts = TTSModule(this)
        stt = STTModule(this)
        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        applyTheme()
        checkMicPermission()

        stt.setOnCommandListener { command ->
            runOnUiThread { processCommand(command) }
        }

        sound.play(R.raw.inicio)

        orb.postDelayed({
            tts.speak("Bienvenido a OASIS")
        }, 1000)

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
                val format = if (is24Hour) {
                    SimpleDateFormat("HH:mm", Locale.getDefault())
                } else {
                    SimpleDateFormat("hh:mm a", Locale.getDefault())
                }
                findViewById<TextView>(R.id.clock_text).text =
                    format.format(System.currentTimeMillis())
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
            pulseAnimation(it)
            toast.show(text)
            tts.speak(text)
            action()
        }
    }

    private fun processCommand(cmd: String) {
        val cmdLower = cmd.lowercase().trim()
        toast.show("Comando: $cmd")

        when {
            cmdLower.contains("abrir") || cmdLower.contains("abre") || cmdLower.contains("lanza") -> {
                val appName = extractAppName(cmdLower)
                if (appName.isNotEmpty()) openSpecificApp(appName)
                else tts.speak("¿Qué aplicación quieres abrir?")
            }

            cmdLower.contains("llamar") || cmdLower.contains("llama a") -> {
                val contactName = extractContactName(cmdLower)
                if (contactName.isNotEmpty()) {
                    if (contactName.any { it.isDigit() }) {
                        dialNumber(contactName)
                    } else {
                        tts.speak("Buscando a $contactName")
                        openDialer()
                    }
                } else tts.speak("¿A quién quieres llamar?")
            }

            cmdLower.contains("mensaje") || cmdLower.contains("mandar") || cmdLower.contains("enviar") -> {
                val contactName = extractContactName(cmdLower)
                if (contactName.isNotEmpty()) openSmsToContact(contactName)
                else openSms()
            }

            cmdLower.contains("contacto") -> openContacts()
            cmdLower.contains("app") || cmdLower.contains("menú") -> openLauncher()
            cmdLower.contains("hola") -> tts.speak("Hola, soy OASIS. ¿En qué puedo ayudarte?")
            cmdLower.contains("ayuda") || cmdLower.contains("qué puedes hacer") ->
                tts.speak("Puedo abrir apps, hacer llamadas y enviar mensajes.")

            else -> tts.speak("No entendí. Intenta de nuevo.")
        }
    }

    private fun extractAppName(cmd: String): String {
        var result = cmd
        listOf("abrir", "abre", "lanza", "inicia", "la app", "el", "la", "aplicación")
            .forEach { result = result.replace(it, "").trim() }

        result = result.replace(
            Regex("\\b(el|la|los|las|un|una|a|de|del|para|por)\\b"),
            ""
        ).trim()

        return result.replace(Regex("[^a-zA-Záéíóúñ\\s]"), "").trim()
    }

    private fun extractContactName(cmd: String): String {
        var result = cmd
        listOf("llamar", "llama a", "mensaje", "mandar", "enviar", "a")
            .forEach { result = result.replace(it, "").trim() }

        result = result.replace(
            Regex("\\b(el|la|los|las|un|una|de|del|para|por)\\b"),
            ""
        ).trim()

        return result.replace(Regex("[^a-zA-Záéíóúñ\\s]"), "").trim()
    }

    private fun openSpecificApp(appName: String) {
        val normalized = appName.lowercase()

        val packageName = when {
            normalized.contains("whatsapp") -> "com.whatsapp"
            normalized.contains("facebook") -> "com.facebook.katana"
            normalized.contains("instagram") -> "com.instagram.android"
            normalized.contains("youtube") -> "com.google.android.youtube"
            normalized.contains("chrome") -> "com.android.chrome"
            normalized.contains("spotify") -> "com.spotify.music"
            else -> null
        }

        if (packageName != null) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                tts.speak("Abriendo $appName")
            } else tts.speak("No instalada")
        } else tts.speak("No reconozco $appName")
    }

    private fun openDialer() {
        startActivity(Intent(Intent.ACTION_DIAL))
    }

    private fun openSms() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.type = "vnd.android-dir/mms-sms"
        startActivity(intent)
    }

    private fun openContacts() {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = android.provider.ContactsContract.Contacts.CONTENT_URI
        startActivity(intent)
    }

    private fun openLauncher() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        startActivity(intent)
    }

    private fun dialNumber(number: String) {
        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = android.net.Uri.parse("tel:$number")
        startActivity(intent)
    }

    private fun openSmsToContact(contactName: String) {
        openSms()
        tts.speak("Mensaje para $contactName")
    }

    private fun pulseAnimation(view: View) {
        val sx = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f)
        val sy = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f)
        AnimatorSet().apply {
            playTogether(sx, sy)
            duration = 180
            start()
        }
    }

    private fun applyTheme() {
        val theme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"

        val bg = when (theme) {
            "oscuro" -> R.color.oscuro_background
            else -> R.color.amanecer_background
        }

        window.setBackgroundDrawableResource(bg)
    }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
        tts.shutdown()
        stt.destroy()
    }
}
