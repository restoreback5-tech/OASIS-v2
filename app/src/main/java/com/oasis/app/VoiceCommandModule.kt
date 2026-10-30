package com.oasis.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class VoiceCommandModule(
    private val context: Context,
    private val sound: SoundModule,
    private val onCommandDetected: (String, Map<String, String>) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    /** Cache: ¿el dispositivo soporta reconocimiento de voz? (no cambia en runtime) */
    private var speechAvailableCache: Boolean? = null

    init {
        setupSpeechRecognizer()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    onListening(true)
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListening = false
                    onListening(false)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    onListening(false)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val commandText = matches[0].lowercase(Locale.getDefault()).trim()
                        parseAndExecute(commandText)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    isListening = false
                    onListening(false)
                    val errorMsg = errorMessage(error)
                    onError(errorMsg)
                }
            })
        }
    }

    fun startListening() {
        if (!isSpeechAvailable()) {
            onError("Reconocimiento de voz no disponible")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime, ¿en qué puedo ayudarte?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: SecurityException) {
            onError("Permiso de micrófono no concedido")
        } catch (e: Exception) {
            onError("Error al iniciar escucha")
        }
    }

    fun stopListening() {
        try { speechRecognizer?.stopListening() } catch (_: Exception) {}
        isListening = false
        onListening(false)
    }

    fun destroy() {
        try { speechRecognizer?.destroy() } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun isSpeechAvailable(): Boolean {
        speechAvailableCache?.let { return it }
        val pm = context.packageManager
        val activities = pm.queryIntentActivities(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val result = activities.isNotEmpty()
        speechAvailableCache = result
        return result
    }

    /** Códigos oficiales de SpeechRecognizer (antes estaban mal asignados). */
    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_NETWORK -> "Sin conexión a internet"
        SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
        SpeechRecognizer.ERROR_SERVER -> "Error del servidor de voz"
        SpeechRecognizer.ERROR_CLIENT -> "Error del reconocedor de voz"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No te escuché. Intenta de nuevo"
        SpeechRecognizer.ERROR_NO_MATCH -> "No entendí. Intenta de nuevo"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Estoy ocupado. Intenta de nuevo"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso del micrófono"
        10 -> "Demasiadas peticiones. Espera un momento"
        11 -> "Se perdió la conexión de voz"
        12, 13 -> "El idioma de voz no está disponible"
        else -> "Error de voz ($code)"
    }

    private fun parseAndExecute(commandText: String) {
        val result = CommandParser.parse(commandText)
        if (result == null) {
            onError("No entendí. Intenta: 'Abre una aplicación', 'Haz una llamada'")
            return
        }
        val params = result.params.toMutableMap()
        if (result.command == "open_app") {
            // El registro central resuelve alias: "wasap" -> "whatsapp"
            params["app"] = AppRegistry.canonicalAliasFor(params["app"].orEmpty())
        }
        onCommandDetected(result.command, params)
    }
}
