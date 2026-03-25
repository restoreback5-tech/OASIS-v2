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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Inicializar módulos
        sound = SoundModule(this)
        toast = ToastModule(this)
        anim = AnimationModule(findViewById(R.id.orb_view))
        
        // Bienvenida
        toast.show("Bienvenido a OASIS")
        sound.play(R.raw.inicio)
        
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
            action()
        }
    }
    
    private fun openDialer() { try { startActivity(Intent(Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:") }) } catch(_: Exception) {} }
    private fun openSms() { try { startActivity(Intent(Intent.ACTION_VIEW).apply { type = "vnd.android-dir/mms-sms" }) } catch(_: Exception) {} }
    private fun openContacts() { try { startActivity(Intent(android.provider.ContactsContract.Contacts.CONTENT_URI).apply { action = Intent.ACTION_VIEW }) } catch(_: Exception) {} }
    private fun openLauncher() { try { startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }) } catch(_: Exception) {} }
    
    override fun onDestroy() { super.onDestroy(); sound.release() }
}
