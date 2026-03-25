package com.oasis.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSModule(ctx: Context) : TextToSpeech.OnInitListener {
    
    private var tts: TextToSpeech? = null
    private var ready = false
    
    init {
        if (Config.ENABLE_TTS && !Config.SAFE_MODE) {
            tts = TextToSpeech(ctx, this)
        }
    }
    
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "ES")
            ready = true
        } else {
            Config.SAFE_MODE = true
        }
    }
    
    fun speak(text: String) {
        if (!Config.ENABLE_TTS || Config.SAFE_MODE || !ready) return
        try { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null) } 
        catch (e: Exception) { Config.SAFE_MODE = true }
    }
    
    fun shutdown() {
        try { tts?.stop(); tts?.shutdown() } catch(_: Exception) {}
    }
}
