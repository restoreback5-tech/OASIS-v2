package com.oasis.app

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class TTSModule(ctx: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    /** true cuando onInit() ya terminó con éxito */
    @Volatile
    private var isReady = false

    /** Cola de mensajes pedidos antes de estar listo */
    private val pendingQueue = mutableListOf<Pair<String, Boolean>>()

    init {
        tts = TextToSpeech(ctx, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                // 1. Elegir idioma con fallback
                val esMx = Locale("es", "MX")
                val es = Locale("es", "ES")
                val langResult = when {
                    engine.isLanguageAvailable(esMx) >= TextToSpeech.LANG_AVAILABLE ->
                        engine.setLanguage(esMx)
                    engine.isLanguageAvailable(es) >= TextToSpeech.LANG_AVAILABLE ->
                        engine.setLanguage(es)
                    else -> {
                        Log.w("TTSModule", "Español no disponible, usando default")
                        engine.setLanguage(Locale.getDefault())
                    }
                }

                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w("TTSModule", "Idioma no soportado, usando default")
                    engine.language = Locale.getDefault()
                }

                // 2. Aplicar speed/pitch (los que el usuario ya haya guardado)
                applySpeechRateAndPitch()

                isReady = true
                Log.d("TTSModule", "TTS listo")

                // 3. Reproducir mensajes que llegaron antes del init
                flushPending()
            }
        } else {
            Log.e("TTSModule", "TTS init falló con status=$status")
        }
    }

    private fun applySpeechRateAndPitch() {
        val speed = prefs.getFloat("voice_speed", 1.0f).coerceIn(0.5f, 2.0f)
        val pitch = prefs.getFloat("voice_pitch", 1.0f).coerceIn(0.5f, 2.0f)
        try {
            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)
        } catch (e: Exception) {
            Log.w("TTSModule", "applySpeechRateAndPitch falló: ${e.message}")
        }
    }

    /**
     * Habla el texto.
     * @param text  texto a decir
     * @param flush si true (default) corta lo anterior; si false, se encola después
     */
    fun speak(text: String, flush: Boolean = true) {
        if (!prefs.getBoolean("tts_enabled", true)) return
        if (text.isBlank()) return

        if (!isReady) {
            // Guardar para cuando el motor esté listo (máximo 20 en cola)
            if (pendingQueue.size < 20) pendingQueue.add(text to flush)
            return
        }

        val mode = if (flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        try {
            tts?.speak(text, mode, null, "oasis-${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.w("TTSModule", "speak falló: ${e.message}")
        }
    }

    /**
     * Habla varios fragmentos en secuencia (el primero corta, los demás se encolan).
     * Ej: speakSequence("Bienvenido", "a", "OASIS")
     */
    fun speakSequence(vararg parts: String) {
        if (!prefs.getBoolean("tts_enabled", true)) return
        parts.filter { it.isNotBlank() }.forEachIndexed { i, part ->
            speak(part, flush = i == 0)
        }
    }

    private fun flushPending() {
        if (pendingQueue.isEmpty()) return
        val queue = pendingQueue.toList()
        pendingQueue.clear()
        queue.forEachIndexed { i, (text, flush) ->
            // El primero mantiene su flush; los siguientes se encolan
            speak(text, flush = if (i == 0) flush else false)
        }
    }

    /**
     * Re-aplica speed/pitch (llamado desde SettingsActivity cuando cambian los sliders).
     * Si el motor no está listo, se aplicará en onInit.
     */
    fun updateSpeechSettings() {
        if (isReady) applySpeechRateAndPitch()
    }

    /** Detiene cualquier habla en curso (sin apagar el motor) */
    fun stop() {
        try { tts?.stop() } catch (_: Exception) {}
        pendingQueue.clear()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w("TTSModule", "shutdown falló: ${e.message}")
        }
        tts = null
        isReady = false
        pendingQueue.clear()
    }
}
