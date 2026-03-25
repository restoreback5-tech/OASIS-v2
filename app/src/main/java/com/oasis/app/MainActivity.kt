package com.oasis.app

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    
    private lateinit var sound: SoundModule
    private lateinit var toast: ToastModule
    private lateinit var anim: AnimationModule
    private lateinit var tts: TTSModule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Inicializar módulos
        sound = SoundModule(this)
        toast = ToastModule(this)
        anim = AnimationModule(findViewById(R.id.orb_view))
        tts = TTSModule(this)
        
        // Bienvenida con voz
        findViewById<ImageView>(R.id.orb_view).postDelayed({
            tts.speak("Bienvenido a OASIS")
        }, 1000)
        
        // Animar orbe
        anim.pulse()
        
        // Orbe click
        findViewById<ImageView>(R.id.orb_view).setOnClickListener {
            sound.play(R.raw.touch)
            toast.show("Toca un botón")
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
        tts.shutdown()  // ✅ Liberar TTS
    }
}
