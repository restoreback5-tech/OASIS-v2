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
                    // Mismos mensajes que antes, solo formateados uno por línea
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

    private fun parseAndExecute(commandText: String) {
        val params = mutableMapOf<String, String>()
        var command = ""

        when {
            // --- COMANDO: ABRIR APP ---
            commandText.contains("abrir") || commandText.contains("abre") ||
                    commandText.contains("lanza") || commandText.contains("inicia") -> {
                command = "open_app"
                // Extraemos el nombre de la app (MISMA función, MISMO resultado)
                val rawAppName = extractName(
                    commandText,
                    listOf("abrir", "abre", "lanza", "inicia", "la app", "el", "la")
                )
                // Antes: appAliases.entries.find{...}?.value ?: rawAppName
                // Ahora: AppRegistry resuelve alias del registro central
                val normalizedAppName = AppRegistry.canonicalAliasFor(rawAppName)
                params["app"] = normalizedAppName
            }

            // --- COMANDO: LLAMAR ---
            commandText.contains("llamar") || commandText.contains("llama a") ||
                    commandText.contains("telefonear") -> {
                command = "call"
                params["contact"] = extractName(
                    commandText,
                    listOf("llamar", "llama a", "telefonear", "a")
                )
            }

            // --- COMANDO: MENSAJE ---
            commandText.contains("mensaje") || commandText.contains("mandar") ||
                    commandText.contains("enviar") || commandText.contains("escribir") -> {
                command = "message"
                params["contact"] = extractName(
                    commandText,
                    listOf("mensaje", "mandar", "enviar", "escribir", "a")
                )
            }

            // --- COMANDO: CANCELAR / DETENER ---
            commandText.contains("cancelar") || commandText.contains("detener") ||
                    commandText.contains("parar") || commandText.contains("basta") -> {
                command = "cancel"
            }

            // --- COMANDO: AYUDA ---
            commandText.contains("ayuda") || commandText.contains("qué puedes hacer") ||
                    commandText.contains("que haces") -> {
                command = "help"
            }

            else -> {
                onError("No entendí. Intenta: 'Abre una aplicación', 'Haz una llamada'")
                return
            }
        }

        if (command.isNotEmpty()) {
            onCommandDetected(command, params)
        }
    }

    /**
     * Extrae el nombre relevante eliminando palabras clave y artículos.
     * Ej: "abre la aplicación de whatsapp" -> "whatsapp"
     *
     * ⚠️ Esta función se mantiene IDÉNTICA al original. NO se debe tocar
     * sin tests exhaustivos, porque de aquí depende la comunicación con
     * el usuario.
     */
    private fun extractName(text: String, keywords: List<String>): String {
        var result = text

        // 1. Eliminar palabras clave de acción
        for (kw in keywords) {
            result = result.replace(kw, "").trim()
        }

        // 2. Eliminar artículos y preposiciones comunes en español
        result = result.replace(
            Regex("\\b(el|la|los|las|un|una|unos|unas|a|de|del|para|por|con|sin|sobre|tras|hacia|hasta)\\b"),
            ""
        ).trim()

        // 3. Eliminar caracteres especiales, dejando solo letras y espacios
        result = result.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]"), "").trim()

        // 4. Si queda vacío, devolvemos un string genérico para evitar errores
        return if (result.isEmpty()) "desconocido" else result
    }
}
