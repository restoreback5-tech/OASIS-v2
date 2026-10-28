package com.oasis.app

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import android.util.SparseArray

class SoundModule(private val context: Context) {

    private val players = SparseArray<MediaPlayer>()

    init {
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
            Log.w("SoundModule", "Preload falló para $resId: ${e.message}")
        }
    }

    fun play(resId: Int) {
        val player = players.get(resId)
        if (player != null) {
            try {
                if (player.isPlaying) {
                    player.pause()
                }
                player.seekTo(0)
                player.start()
            } catch (e: IllegalStateException) {
                Log.w("SoundModule", "Player inválido, recreando: ${e.message}")
                try { player.release() } catch (_: Exception) {}
                players.remove(resId)
                preload(resId)
                try { players.get(resId)?.start() } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.w("SoundModule", "Error al reproducir: ${e.message}")
            }
        } else {
            try {
                MediaPlayer.create(context, resId)?.apply {
                    start()
                    setOnCompletionListener { release() }
                }
            } catch (e: Exception) {
                Log.w("SoundModule", "Fallback play falló: ${e.message}")
            }
        }
    }

    fun release() {
        for (i in 0 until players.size()) {
            try { players.valueAt(i)?.release() } catch (_: Exception) {}
        }
        players.clear()
    }
}
