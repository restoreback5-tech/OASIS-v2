package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

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

        sound = SoundModule(this)
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

        findViewById<ImageView>(R.id.orb_view).postDelayed({
            tts.speak("Bienvenido a OASIS")        }, 1000)

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
                findViewById<TextView>(R.id.clock_text).text = format.format(System.currentTimeMillis())
                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(clockRunnable)

        findViewById<ImageView>(R.id.orb_view).setOnClickListener {
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
            toast.show(text)
            tts.speak(text)
            action()
        }
    }

    private fun openDialer() {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:")            



    startActivity(intent)
        } catch(_: Exception) {
        }
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
        } catch(_: Exception) {
        }
    }

    private fun processCommand(cmd: String) {
        toast.show("Comando: $cmd")
        when {
            cmd.contains("llamar") -> openDialer()
            cmd.contains("mensaje") -> openSms()
            cmd.contains("contacto") -> openContacts()
            cmd.contains("app") -> openLauncher()
            cmd.contains("hola") -> tts.speak("Hola, soy OASIS")
            else -> tts.speak("No entendí")
        }
    }

    private fun openLauncher() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            startActivity(intent)
        } catch(_: Exception) {
        }
    }
    private fun applyTheme() {
        val isDayMode = prefs.getBoolean("day_mode", true)
        val rootView = findViewById<View>(android.R.id.content)
        val clockText = findViewById<TextView>(R.id.clock_text)
        val statusText = findViewById<TextView>(R.id.greeting_text)
        if (isDayMode) {
            rootView.setBackgroundResource(R.drawable.bg_gradient_day)
            clockText.setTextColor(ContextCompat.getColor(this, R.color.oasis_text))
            statusText.setTextColor(ContextCompat.getColor(this, R.color.oasis_text))
        } else {
        rootView.setBackgroundResource(R.color.night_background)
        clockText.setTextColor(0xFFFFFFFF.toInt()) // Blanco puro #FFFFFF
        statusText.setTextColor(0xFFFFFFFF.toInt()) // Blanco puro #FFFFFF
        }
    }

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tts.speak("Permiso de micrófono concedido")
            } else {
                tts.speak("Necesito permiso de micrófono para escucharte")
            }
        }
    }

   override fun onResume() {
        super.onResume()
        // Re-aplicar tema por si cambió en SettingsActivity
        applyTheme()
    }   

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
        tts.shutdown()
        stt.destroy()
    }
}
