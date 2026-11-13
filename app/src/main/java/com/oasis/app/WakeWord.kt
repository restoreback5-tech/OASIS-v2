package com.oasis.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * "Hola Oasis" (experimental). Mientras OASIS está abierta y encendida la opción en Ajustes, escucha en bucle
 * y avisa cuando oye la palabra «oasis». No funciona con la pantalla apagada ni en segundo plano.
 */
class WakeWord(
    private val ctx: Context,
    private val isBusy: () -> Boolean,      // true si el orbe o la voz están ocupados: se ignora lo que se oiga
    private val onWake: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var active = false
    private var fired = false

    private val restart = Runnable { listenOnce() }

    fun start() {
        if (active) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return
        active = true
        fired = false
        main.postDelayed(restart, 600)
    }

    fun stop() {
        active = false
        main.removeCallbacks(restart)
        try { rec?.destroy() } catch (_: Exception) {}
        rec = null
    }

    /** Pausa breve (p. ej. mientras el orbe habla) y vuelve a escuchar. */
    fun pauseFor(ms: Long) {
        if (!active) return
        main.removeCallbacks(restart)
        try { rec?.cancel() } catch (_: Exception) {}
        main.postDelayed(restart, ms)
    }

    private fun listenOnce() {
        if (!active) return
        if (isBusy()) { main.postDelayed(restart, 1500); return }
        try {
            if (rec == null) {
                rec = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                        override fun onPartialResults(partialResults: Bundle?) { check(partialResults) }
                        override fun onResults(results: Bundle?) { check(results); again(300) }
                        override fun onError(error: Int) {
                            // sin voz / sin coincidencia es lo normal; ocupado o error real, esperamos más
                            again(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) 300 else 2500)
                        }
                    })
                }
            }
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            }
            rec?.startListening(i)
        } catch (e: Exception) {
            again(3000)
        }
    }

    private fun again(ms: Long) {
        if (!active || fired) return
        main.removeCallbacks(restart)
        main.postDelayed(restart, ms)
    }

    private fun check(b: Bundle?) {
        if (fired || isBusy()) return
        val list = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        val hit = list.any { TextMatch.normalize(it).split(' ').any { w -> w == "oasis" || w == "oasys" || w == "oasi" } }
        if (hit) {
            fired = true
            try { rec?.cancel() } catch (_: Exception) {}
            main.post {
                onWake()
                fired = false
                again(4000)
            }
        }
    }
}
