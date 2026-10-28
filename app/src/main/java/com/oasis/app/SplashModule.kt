package com.oasis.app

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.widget.ImageView

class SplashModule(private val ctx: Context) {
    
    private var player: MediaPlayer? = null
    
    fun playBubbleSound() {
        if (!Config.ENABLE_SOUNDS || Config.SAFE_MODE) return
        try {
            player = MediaPlayer.create(ctx, R.raw.burbuja)
            player?.start()
        } catch (e: Exception) {
            Log.w("SplashModule", "Sonido burbuja falló: ${e.message}")
        }
    }
    
    fun animateBubble(view: ImageView) {
        if (!Config.ENABLE_ANIMATIONS || Config.SAFE_MODE) return
        try {
            view.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .alpha(0f)
                .setDuration(800)
                .start()
        } catch (e: Exception) {
            Log.w("SplashModule", "Animación burbuja falló: ${e.message}")
        }
    }
    
    fun release() {
        try { player?.release() } catch(_: Exception) {}
        player = null
    }
}
