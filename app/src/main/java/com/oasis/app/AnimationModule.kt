package com.oasis.app

import android.widget.ImageView

class AnimationModule(private val view: ImageView) {
    
    fun pulse() {
        if (!Config.ENABLE_ANIMATIONS || Config.SAFE_MODE) return
        try {
            // Animación simple SIN repeatMode (compatible con minSdk 24)
            view.animate()
                .scaleX(1.05f)
                .scaleY(1.05f)
                .setDuration(1000)
                .start()
        } catch (e: Exception) { Config.SAFE_MODE = true }
    }
}
