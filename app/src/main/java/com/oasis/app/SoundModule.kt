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
        // inicio (60 s) y burbuja (agua) ya no se precargan: solo suenan si el usuario los activa
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

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Respeta Ajustes: "Sonidos UI" apaga todo; el sonido de bienvenida/agua solo suena si se activa. */
    private fun allowed(resId: Int): Boolean {
        if (!Config.ENABLE_SOUNDS || Config.SAFE_MODE) return false
        val p = context.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)
        if (!p.getBoolean("sounds_enabled", true)) return false
        if (resId == R.raw.inicio || resId == R.raw.burbuja) return UiPrefs.startupSound(context)
        return true
    }

    fun play(resId: Int) {
        if (!allowed(resId)) return
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
                    // El sonido de bienvenida dura 60 s: se corta a los 4 s
                    if (resId == R.raw.inicio || resId == R.raw.burbuja) {
                        handler.postDelayed({ try { stop(); release() } catch (_: Exception) {} }, 4000)
                    }
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
