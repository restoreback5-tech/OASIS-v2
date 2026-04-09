package com.oasis.app

import android.content.Context
import android.media.MediaPlayer
import android.util.SparseArray

class SoundModule(private val ctx: Context) {
    
    // Mapa para guardar sonidos precargados
    private val players = SparseArray<MediaPlayer>()
    
    // Precargar sonidos críticos (llamar esto en onCreate de MainActivity)
    fun preload(vararg resIds: Int) {
        for (resId in resIds) {
            try {
                val player = MediaPlayer.create(ctx, resId)
                player?.isLooping = false
                players.put(resId, player)
            } catch (e: Exception) { /* ignorar */ }
        }
    }
    
    fun play(resId: Int) {
        if (!Config.ENABLE_SOUNDS || Config.SAFE_MODE) return
        
        // Intentar usar el precargado (INSTANTÁNEO)
        val player = players.get(resId)
        if (player != null) {
            player.seekTo(0)  // Reinicia si ya sonó
            player.start()    // ← SUENA YA (sin delay)
        } else {
            // Fallback si no está precargado (más lento pero funciona)
            try {
                MediaPlayer.create(ctx, resId)?.apply {
                    start()
                    setOnCompletionListener { release() }
                }
            } catch (e: Exception) { Config.SAFE_MODE = true }
        }
    }
    
    fun release() {
        for (i in 0 until players.size()) {
            players.valueAt(i)?.release()
        }
        players.clear()
    }
}
