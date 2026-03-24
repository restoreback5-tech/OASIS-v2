package com.oasis.app

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Reproducir sonido de inicio
        playSound(R.raw.inicio)

        // Animar orbe central
        animateOrb()

        // Configurar botones
        setupButtons()
    }

    private fun animateOrb() {
        val orb = findViewById<ImageView>(R.id.orb_view)
        
        orb.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .alpha(0.9f)
            .setDuration(1500)
            .repeatMode(android.animation.ValueAnimator.REVERSE)
            .repeatCount(android.animation.ValueAnimator.INFINITE)
            .start()
    }

    private fun setupButtons() {
        val btnCall = findViewById<MaterialButton>(R.id.btn_call)
        val btnMessage = findViewById<MaterialButton>(R.id.btn_message)
        val btnContacts = findViewById<MaterialButton>(R.id.btn_contacts)
        val btnApps = findViewById<MaterialButton>(R.id.btn_apps)

        // Botón Llamar
        btnCall.setOnClickListener {
            playSound(R.raw.touch)
            showToast("Llamar")
        }

        // Botón Enviar mensaje
        btnMessage.setOnClickListener {
            playSound(R.raw.touch)
            showToast("Enviar mensaje")
        }

        // Botón Contactos
        btnContacts.setOnClickListener {
            playSound(R.raw.touch)
            showToast("Contactos")
        }

        // Botón Apps
        btnApps.setOnClickListener {
            playSound(R.raw.touch)
            showToast("Apps")
        }
    }

    private fun playSound(soundResId: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, soundResId)
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showToast(message: String) {
        val greetingText = findViewById<TextView>(R.id.greeting_text)
        greetingText.text = message
        playSound(R.raw.confirmar)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
