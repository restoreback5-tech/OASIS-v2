package com.oasis.app

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    
    private lateinit var splash: SplashModule
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        
        splash = SplashModule(this)
        
        // Sonido de burbuja
        splash.playBubbleSound()
        
        // Animación de burbuja
        val bubble = findViewById<ImageView>(R.id.bubble_view)
        splash.animateBubble(bubble)
        
        // Ir a MainActivity después de 1.5 segundos
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1500)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        splash.release()
    }
}
