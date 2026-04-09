package com.oasis.app

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSModule(ctx: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val prefs: SharedPreferences = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    init {
        tts = TextToSpeech(ctx, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "MX")
            applySpeechRateAndPitch()
        }
    }

    private fun applySpeechRateAndPitch() {
        val speed = prefs.getFloat("voice_speed", 1.0f)
        val pitch = prefs.getFloat("voice_pitch", 1.0f)
        tts?.setSpeechRate(speed)
        tts?.setPitch(pitch)
    }

    fun speak(text: String) {
        // Solo habla si la preferencia tts_enabled es true (por defecto true)
        if (prefs.getBoolean("tts_enabled", true)) {
            try {
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            } catch (e: Exception) {
                // Silenciar errores
            }
        }
    }

    fun updateSpeechSettings() {
        applySpeechRateAndPitch()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
