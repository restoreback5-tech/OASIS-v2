package com.oasis.app

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Sonido inicio
        try { mediaPlayer?.release(); mediaPlayer = MediaPlayer.create(this, R.raw.inicio); mediaPlayer?.start() } catch(_: Exception) {}
        
        // Toast bienvenida
        findViewById<TextView>(R.id.greeting_text).text = "Bienvenido a OASIS"
        Toast.makeText(this, "Bienvenido a OASIS", Toast.LENGTH_SHORT).show()
        
        // Orbe click
        findViewById<ImageView>(R.id.orb_view).setOnClickListener {
            try { mediaPlayer?.release(); mediaPlayer = MediaPlayer.create(this, R.raw.touch); mediaPlayer?.start() } catch(_: Exception) {}
            Toast.makeText(this, "Toca un botón", Toast.LENGTH_SHORT).show()
        }
        
        // Botón Llamar
        findViewById<MaterialButton>(R.id.btn_call).setOnClickListener {
            try { mediaPlayer?.release(); mediaPlayer = MediaPlayer.create(this, R.raw.touch); mediaPlayer?.start() } catch(_: Exception) {}
            Toast.makeText(this, "Llamar", Toast.LENGTH_SHORT).show()
            try { startActivity(Intent(Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:") }) } catch(_: Exception) {}
        }
        
        // Botón Mensaje
        findViewById<MaterialButton>(R.id.btn_message).setOnClickListener {
            try { mediaPlayer?.release(); mediaPlayer = MediaPlayer.create(this, R.raw.touch); mediaPlayer?.start() } catch(_: Exception) {}
            Toast.makeText(this, "Enviar mensaje", Toast.LENGTH_SHORT).show()
            try { startActivity(Intent(Intent.ACTION_VIEW).apply { type = "vnd.android-dir/mms-sms" }) } catch(_: Exception) {}
        }
        
        // Botón Contactos
        findViewById<MaterialButton>(R.id.btn_contacts).setOnClickListener {
            try { mediaPlayer?.release(); mediaPlayer = MediaPlayer.create(this, R.raw.touch); mediaPlayer?.start() } catch(_: Exception) {}
            Toast.makeText(this, "Contactos", Toast.LENGTH_SHORT).show()
            try { startActivity(Intent(android.provider.ContactsContract.Contacts.CONTENT_URI).apply { action = Intent.ACTION_VIEW }) } catch(_: Exception) {}
        }
        
        // Botón Apps
        findViewById<MaterialButton>(R.id.btn_apps).setOnClickListener {
            try { mediaPlayer?.release(); mediaPlayer = MediaPlayer.create(this, R.raw.touch); mediaPlayer?.start() } catch(_: Exception) {}
            Toast.makeText(this, "Apps", Toast.LENGTH_SHORT).show()
            try { startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) }) } catch(_: Exception) {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
