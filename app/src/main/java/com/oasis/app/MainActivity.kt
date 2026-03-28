package com.oasis.app

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.Intent

class MainActivity : AppCompatActivity() {
    
    private lateinit var sound: SoundModule
    private lateinit var toast: ToastModule
    private lateinit var anim: AnimationModule
    private lateinit var tts: TTSModule
    private lateinit var stt: STTModule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Inicializar módulos
        sound = SoundModule(this)
        toast = ToastModule(this)
        anim = AnimationModule(findViewById(R.id.orb_view))
        tts = TTSModule(this)
        
        stt = STTModule(this)

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


       // Reloj en tiempo real
   val clockHandler = Handler(Looper.getMainLooper())
   val clockRunnable = object : Runnable {
       override fun run() {
           val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
           findViewById<TextView>(R.id.clock_text).text = sdf.format(System.currentTimeMillis())
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
            val intent = Intent(Intent.ACTION_VIEW)
            intent.type = "vnd.android-dir/mms-sms"
            startActivity(intent)
        } catch(_: Exception) {}
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
}
