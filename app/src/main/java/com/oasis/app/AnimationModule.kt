package com.oasis.app

import android.view.animation.AnimationUtils
import android.widget.ImageView

class AnimationModule(private val view: ImageView) {
    
    fun startRippleAnimation() {
        if (!Config.ENABLE_ANIMATIONS || Config.SAFE_MODE) return
        try {
            val rippleAnim = AnimationUtils.loadAnimation(view.context, R.anim.orb_ripple)
            view.startAnimation(rippleAnim)
        } catch (e: Exception) { 
            Config.SAFE_MODE = true 
        }
    }
    
    fun stopAnimation() {
        try { view.clearAnimation() } catch(_: Exception) {}
    }
}
