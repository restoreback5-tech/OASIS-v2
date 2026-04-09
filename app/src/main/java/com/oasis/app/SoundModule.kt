package com.oasis.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build

class SoundModule(private val context: Context) {

    private var soundPool: SoundPool
    private val sounds = mutableMapOf<Int, Int>()

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()

        // Precargar todos los sonidos (puedes añadir más si es necesario)
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
        val soundId = soundPool.load(context, resId, 1)
        sounds[resId] = soundId
    }

    fun play(resId: Int) {
        val soundId = sounds[resId]
        if (soundId != null) {
            soundPool.play(soundId, 1.0f, 1.0f, 1, 0, 1.0f)
        } else {
            // Si no está precargado, cargar sobre la marcha (no debería ocurrir)
            val tempId = soundPool.load(context, resId, 1)
            soundPool.setOnLoadCompleteListener { _, _, _ ->
                soundPool.play(tempId, 1.0f, 1.0f, 1, 0, 1.0f)
            }
        }
    }

    fun release() {
        soundPool.release()
    }
}
