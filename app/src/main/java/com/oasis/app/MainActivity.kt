package com.oasis.app

import android.graphics.drawable.AnimationDrawable
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

        playSound(R.raw.inicio)
        startOrbAnimation()
        setupButtons()
    }

    private fun startOrbAnimation() {
        val orb = findViewById<ImageView>(R.id.orb_view)
        orb.setImageResource(R.drawable.orb_animate)
        val animation = orb.drawable as AnimationDrawable
        animation.start()
    }

    private fun setupButtons() {
        val btnCall = findViewById<MaterialButton>(R.id.btn_call)
        val btnMessage = findViewById<MaterialButton>(R.id.btn_message)
        val btnContacts = findViewById<MaterialButton>(R.id.btn_contacts)
        val btnApps = findViewById<MaterialButton>(R.id.btn_apps)

        btnCall.setOnClickListener {
            playSound(R.raw.touch)
            showToast("Llamar")
        }

        btnMessage.setOnClickListener {
            playSound(R.raw.touch)
            showToast("Enviar mensaje")
        }

        btnContacts.setOnClickListener {
            playSound(R.raw.touch)
            showToast("Contactos")
        }

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
        }
    }

    private fun showToast(message: String) {
        val greetingText = findViewById<TextView>(R.id.greeting_text)
        greetingText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        playSound(R.raw.confirmar)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}
