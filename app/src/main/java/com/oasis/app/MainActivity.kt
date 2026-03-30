package com.oasis.app

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.os.Handler
import android.os.Looper
import android.content.SharedPreferences
import android.widget.TextView
import android.view.View
import androidx.core.content.ContextCompat
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
        
        // Inicializar módulos
        sound = SoundModule(this)
        toast = ToastModule(this)
        anim = AnimationModule(findViewById(R.id.orb_view))
        tts = TTSModule(this)
        
        stt = STTModule(this)
        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)
        applyTheme()
        checkMicPermission()

        // Setup comandos de voz
        stt.setOnCommandListener { command ->
            runOnUiThread { processCommand(command) }
        }
        
        // Sonido de inicio
        sound.play(R.raw.inicio)
        
        // Bienvenida con voz
        findViewById<ImageView>(R.id.orb_view).postDelayed({
            tts.speak("Bienvenido a OASIS")
        }, 1000)
        
        // Animar orbe
        anim.startRippleAnimation()
        
       // Icono de ajustes
findViewById<ImageView>(R.id.btn_settings).setOnClickListener {
    sound.play(R.raw.touch)
    tts.speak("Ajustes")
    startActivity(Intent(this, SettingsActivity::class.java))
}

    // === Aplicar Tema Día/Noche ===
    private fun applyTheme() {
        checkMicPermission()
        val isDayMode = prefs.getBoolean("day_mode", true)
        val rootView = findViewById<View>(android.R.id.content)
        val clockText = findViewById<TextView>(R.id.tv_current_time)
        val statusText = findViewById<TextView>(R.id.speech_bubble)
        
        if (isDayMode) {
            // Modo Día: Degradado turquesa
            rootView.setBackgroundResource(R.drawable.bg_gradient_day)
            clockText.setTextColor(ContextCompat.getColor(this, R.color.oasis_text))
            statusText.setTextColor(ContextCompat.getColor(this, R.color.oasis_text))
        } else {
            // Modo Noche: AMOLED negro
            rootView.setBackgroundResource(R.color.night_background)
            clockText.setTextColor(ContextCompat.getColor(this, R.color.night_text))
            statusText.setTextColor(ContextCompat.getColor(this, R.color.night_text))
        }
    }


       // Reloj en tiempo real
   val clockHandler = Handler(Looper.getMainLooper())
   val clockRunnable = object : Runnable {
       override fun run() {
    val prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)
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

        // Orbe click
        findViewById<ImageView>(R.id.orb_view).setOnClickListener {
            sound.play(R.raw.touch)
            toast.show("Escuchando..."); stt.startListening()
        }
        
        // Botones
        setupBtn(R.id.btn_call, "Llamar") { openDialer() }
        setupBtn(R.id.btn_message, "Enviar mensaje") { openSms() }
        setupBtn(R.id.btn_contacts, "Contactos") { openContacts() }
        setupBtn(R.id.btn_apps, "Apps") { openLauncher() }
    }
    
    private fun setupBtn(id: Int, text: String, action: () -> Unit) {
        findViewById<MaterialButton>(id).setOnClickListener {
            sound.play(R.raw.touch)
            toast.show(text)
            tts.speak(text)  // ✅ Habla el nombre del botón
            action()
        }
    }
    
    private fun openDialer() {
        try {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:")
            startActivity(intent)
        } catch(_: Exception) {}
    }
    
    private fun openSms() {
    try {
        // Intentar abrir WhatsApp primero
        val whatsappIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (whatsappIntent != null) {
            startActivity(whatsappIntent)
        } else {
            // Si no hay WhatsApp, abrir SMS normal
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
        } catch(_: Exception) {}
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
        } catch(_: Exception) {}
    }    
    override fun onDestroy() { 
        super.onDestroy()
        sound.release()
        tts.shutdown()
        stt.destroy()  // ✅ Liberar TTS
    }

    // === Verificar Permiso de Micrófono ===
    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tts.speak("Permiso de micrófono concedido")
            } else {
                tts.speak("Necesito permiso de micrófono para escucharte")
            }
        }
    }
}
