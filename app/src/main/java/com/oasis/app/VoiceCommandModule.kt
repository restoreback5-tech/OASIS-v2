package com.oasis.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import java.util.*

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
                        val command = matches[0].lowercase(Locale.getDefault()).trim()                        parseCommand(command)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onError(error: Int) {
                    isListening = false
                    onListening(false)
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                        SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permiso de micrófono denegado"
                        SpeechRecognizer.ERROR_NETWORK -> "Sin conexión"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No entendí, intenta de nuevo"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ocupado, espera un momento"
                        SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Tiempo de espera agotado"
                        else -> "Error desconocido: $error"
                    }
                    onError(errorMsg)
                }
            })
        }
    }

    fun startListening() {
        if (!isSpeechAvailable()) {
            onError("Reconocimiento de voz no disponible. Instala Google Voice Search.")
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dime, ¿en qué puedo ayudarte?")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: SecurityException) {
            onError("Permiso de micrófono no concedido")
        } catch (e: Exception) {
            onError("Error al iniciar escucha: ${e.message}")
        }
    }
    fun stopListening() {
        speechRecognizer?.stopListening()
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

    internal fun parseCommand(command: String) {
        // Comandos básicos para adultos mayores (simples y claros)
        val params = mutableMapOf<String, String>()

        when {
            // 📞 LLAMADAS
            command.contains("llamar") || command.contains("llama a") || command.contains("telefonear") -> {
                val name = extractName(command, listOf("llamar", "llama a", "telefonear"))
                if (name.isNotEmpty()) {
                    params["contact"] = name
                    onCommandDetected("call", params)
                } else {
                    onError("¿A quién quieres llamar?")
                }
            }

            // 💬 MENSAJES
            command.contains("mensaje") || command.contains("mandar") || command.contains("enviar") -> {
                val name = extractName(command, listOf("mensaje", "mandar", "enviar", "a"))
                params["contact"] = name
                onCommandDetected("message", params)
            }

            // 📱 ABRIR APPS
            command.contains("abrir") || command.contains("abre") -> {
                val app = extractName(command, listOf("abrir", "abre", "la app", "el"))
                if (app.isNotEmpty()) {
                    params["app"] = app
                    onCommandDetected("open_app", params)
                }            }

            // ❌ CANCELAR / DETENER
            command.contains("cancelar") || command.contains("detener") || command.contains("parar") -> {
                onCommandDetected("cancel", emptyMap())
            }

            // ❓ AYUDA
            command.contains("ayuda") || command.contains("qué puedes hacer") -> {
                onCommandDetected("help", emptyMap())
            }

            // 🔄 REPETIR
            command.contains("repetir") || command.contains("otra vez") -> {
                onCommandDetected("repeat", emptyMap())
            }

            else -> {
                onError("No entendí. Prueba: 'Llamar a mamá', 'Abrir WhatsApp', 'Enviar mensaje'")
            }
        }
    }

    private fun extractName(command: String, keywords: List<String>): String {
        var result = command
        for (kw in keywords) {
            result = result.replace(kw, "").trim()
        }
        // Limpiar artículos y preposiciones comunes
        result = result.replace(Regex("\\b(el|la|los|las|un|una|a|de|del|para)\\b"), "").trim()
        return result.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]"), "").trim()
    }
}
