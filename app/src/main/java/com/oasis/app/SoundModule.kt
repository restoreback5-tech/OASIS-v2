package com.oasis.app

import android.content.Context
import android.media.MediaPlayer

class SoundModule(private val ctx: Context) {
    private var player: MediaPlayer? = null
    
    fun play(resId: Int) {
        if (!Config.ENABLE_SOUNDS || Config.SAFE_MODE) return
        try {
            player?.release()
            player = MediaPlayer.create(ctx, resId)
            player?.start()
        } catch (e: Exception) { Config.SAFE_MODE = true }
    }
    
    fun release() { try { player?.release() } catch(_: Exception) {} }
}
