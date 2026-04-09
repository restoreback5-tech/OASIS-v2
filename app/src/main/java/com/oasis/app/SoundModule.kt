package com.oasis.app

import android.content.Context
import android.media.MediaPlayer
import android.util.SparseArray

class SoundModule(private val context: Context) {

    private val players = SparseArray<MediaPlayer>()

    init {
        // Precargar todos los sonidos necesarios
        preload(R.raw.touch)
        preload(R.raw.confirmar)
        preload(R.raw.cancelar)
        preload(R.raw.error)
        preload(R.raw.inicio)
        preload(R.raw.burbuja)
        preload(R.raw.alerta)
        preload(R.raw.escuchando)
        preload(R.raw.deslizar)
        preload(R.raw.check_on)
        preload(R.raw.apps_launch)
    }

    private fun preload(resId: Int) {
        try {
            val player = MediaPlayer.create(context, resId)
            player?.isLooping = false
            players.put(resId, player)
        } catch (e: Exception) {
            // Ignorar error de precarga
        }
    }

    fun play(resId: Int) {
        val player = players.get(resId)
        if (player != null) {
            // Reiniciar si estaba en curso
            if (player.isPlaying) player.seekTo(0)
            player.start()
        } else {
            // Fallback: cargar sobre la marcha (no debería ocurrir)
            try {
                MediaPlayer.create(context, resId)?.apply {
                    start()
                    setOnCompletionListener { release() }
                }
            } catch (e: Exception) { }
        }
    }

    fun release() {
        for (i in 0 until players.size()) {
            players.valueAt(i)?.release()
        }
        players.clear()
    }
}
