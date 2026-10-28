package com.oasis.app

import android.util.Log
import android.view.animation.AnimationUtils
import android.widget.ImageView

class AnimationModule(private val view: ImageView) {
    
    fun startRippleAnimation() {
        if (!Config.ENABLE_ANIMATIONS || Config.SAFE_MODE) return
        try {
            val rippleAnim = AnimationUtils.loadAnimation(view.context, R.anim.orb_ripple)
            view.startAnimation(rippleAnim)
        } catch (e: Exception) {
            Log.w("AnimationModule", "Ripple falló: ${e.message}")
        }
    }
    
    fun stopAnimation() {
        try { view.clearAnimation() } catch(_: Exception) {}
    }
}
