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
    private val onCommandDetected: (String, Map<String, String>) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

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
                    val errorMsg = when (error) {
                        1 -> "Error de audio"
                        2 -> "Error del cliente"
                        3 -> "Permiso denegado"
                        4 -> "Sin conexión"
                        5 -> "No entendí"
                        6 -> "Ocupado"
                        7 -> "Error del servidor"
                        8 -> "Tiempo agotado"
                        else -> "Error $error"
                    }
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
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: SecurityException) {
            onError("Permiso de micrófono no concedido")
        } catch (e: Exception) {
            onError("Error al iniciar escucha")
        }
    }

    fun stopListening() {        speechRecognizer?.stopListening()
        isListening = false
        onListening(false)
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun isSpeechAvailable(): Boolean {
        val pm = context.packageManager
        val activities = pm.queryIntentActivities(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return activities.isNotEmpty()
    }

    private fun parseAndExecute(commandText: String) {
        val params = mutableMapOf<String, String>()
        var command = ""

        when {
            commandText.contains("llamar") || commandText.contains("llama a") -> {
                command = "call"
                params["contact"] = extractName(commandText, listOf("llamar", "llama a", "telefonear"))
            }
            commandText.contains("mensaje") || commandText.contains("mandar") || commandText.contains("enviar") -> {
                command = "message"
                params["contact"] = extractName(commandText, listOf("mensaje", "mandar", "enviar", "a"))
            }
            commandText.contains("abrir") -> {
                command = "open_app"
                params["app"] = extractName(commandText, listOf("abrir", "abre", "la app", "el"))
            }
            commandText.contains("cancelar") || commandText.contains("detener") || commandText.contains("parar") -> {
                command = "cancel"
            }
            commandText.contains("ayuda") || commandText.contains("qué puedes hacer") -> {
                command = "help"
            }
            else -> {
                onError("No entendí. Prueba: 'Llamar a mamá', 'Abrir WhatsApp'")
                return
            }
        }

        if (command.isNotEmpty()) {
            onCommandDetected(command, params)        }
    }

    private fun extractName(text: String, keywords: List<String>): String {
        var result = text
        for (kw in keywords) {
            result = result.replace(kw, "").trim()
        }
        result = result.replace(Regex("\\b(el|la|los|las|un|una|a|de|del|para)\\b"), "").trim()
        return result.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]"), "").trim()
    }
}
