package com.oasis.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class STTModule(private val ctx: Context) {
    
    private var recognizer: SpeechRecognizer? = null
    private var onCommand: ((String) -> Unit)? = null
    
    init {
        if (Config.ENABLE_STT && !Config.SAFE_MODE && SpeechRecognizer.isRecognitionAvailable(ctx)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) { if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) Config.SAFE_MODE = true }
                override fun onResults(results: Bundle?) {
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { onCommand?.invoke(it.lowercase()) }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }
    
    fun setOnCommandListener(listener: (String) -> Unit) { onCommand = listener }
    
    fun startListening() {
        if (!Config.ENABLE_STT || Config.SAFE_MODE) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            }
            recognizer?.startListening(intent)
        } catch (e: Exception) { Config.SAFE_MODE = true }
    }
    
    fun destroy() { try { recognizer?.destroy() } catch(_: Exception) {} }
}
