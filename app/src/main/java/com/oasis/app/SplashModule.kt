package com.oasis.app

import android.content.Context
import android.media.MediaPlayer
import android.widget.ImageView
import android.view.animation.AnimationUtils

class SplashModule(private val ctx: Context) {
    
    private var player: MediaPlayer? = null
    
    fun playBubbleSound() {
        if (!Config.ENABLE_SOUNDS || Config.SAFE_MODE) return
        try {
            player = MediaPlayer.create(ctx, R.raw.burbuja)
            player?.start()
        } catch (e: Exception) { Config.SAFE_MODE = true }
    }
    
    fun animateBubble(view: ImageView) {
        if (!Config.ENABLE_ANIMATIONS || Config.SAFE_MODE) return
        try {
            // Animación de escala (como burbuja explotando)
            view.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .alpha(0f)
                .setDuration(800)
                .start()
        } catch (e: Exception) { Config.SAFE_MODE = true }
    }
    
    fun release() {
        try { player?.release() } catch(_: Exception) {}
    }
}
