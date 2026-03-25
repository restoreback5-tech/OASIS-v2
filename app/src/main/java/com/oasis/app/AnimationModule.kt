package com.oasis.app

import android.widget.ImageView

class AnimationModule(private val view: ImageView) {
    
    fun pulse() {
        if (!Config.ENABLE_ANIMATIONS || Config.SAFE_MODE) return
        try {
            view.animate()
                .scaleX(1.05f).scaleY(1.05f)
                .setDuration(1000)
                .repeatMode(android.view.animation.Animation.REVERSE)
                .repeatCount(android.view.animation.Animation.INFINITE)
                .start()
        } catch (e: Exception) { Config.SAFE_MODE = true }
    }
}
