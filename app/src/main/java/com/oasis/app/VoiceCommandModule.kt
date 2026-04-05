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

    // Mapeo de nombres comunes a Paquetes de Android (para ayudar al reconocimiento)
    private val appAliases = mapOf(
        "whatsapp" to "whatsapp",
        "wasap" to "whatsapp",
        "wsp" to "whatsapp",
        "facebook" to "facebook",
        "fb" to "facebook",
        "instagram" to "instagram",
        "insta" to "instagram",
        "youtube" to "youtube",
        "tubo" to "youtube",
        "chrome" to "chrome",
        "navegador" to "chrome",
        "google" to "chrome",
        "camara" to "camara",
        "cámara" to "camara",
        "fotos" to "galeria",
        "galería" to "galeria",
        "ajustes" to "ajustes",
        "configuracion" to "ajustes",
        "configuración" to "ajustes",
        "settings" to "ajustes",
        "reloj" to "reloj",
        "alarma" to "reloj",
        "calculadora" to "calculadora",
        "spotify" to "spotify",
        "maps" to "maps",
        "mapas" to "maps",
        "uber" to "uber",
        "netflix" to "netflix"    )

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
                        7 -> "Error del servidor"                        8 -> "Tiempo agotado"
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
    private fun parseAndExecute(commandText: String) {
        val params = mutableMapOf<String, String>()
        var command = ""

        when {
            // --- COMANDO: ABRIR APP ---
            commandText.contains("abrir") || commandText.contains("abre") || commandText.contains("lanza") || commandText.contains("inicia") -> {
                command = "open_app"
                // Extraemos el nombre de la app y lo normalizamos
                val rawAppName = extractName(commandText, listOf("abrir", "abre", "lanza", "inicia", "la app", "el", "la"))
                
                // Buscamos si es un alias conocido (ej: "wasap" -> "whatsapp")
                val normalizedAppName = appAliases.entries.find { rawAppName.contains(it.key) }?.value ?: rawAppName
                
                params["app"] = normalizedAppName
            }

            // --- COMANDO: LLAMAR ---
            commandText.contains("llamar") || commandText.contains("llama a") || commandText.contains("telefonear") -> {
                command = "call"
                params["contact"] = extractName(commandText, listOf("llamar", "llama a", "telefonear", "a"))
            }

            // --- COMANDO: MENSAJE ---
            commandText.contains("mensaje") || commandText.contains("mandar") || commandText.contains("enviar") || commandText.contains("escribir") -> {
                command = "message"
                params["contact"] = extractName(commandText, listOf("mensaje", "mandar", "enviar", "escribir", "a"))
            }

            // --- COMANDO: CANCELAR / DETENER ---
            commandText.contains("cancelar") || commandText.contains("detener") || commandText.contains("parar") || commandText.contains("basta") -> {
                command = "cancel"
            }

            // --- COMANDO: AYUDA ---
            commandText.contains("ayuda") || commandText.contains("qué puedes hacer") || commandText.contains("que haces") -> {
                command = "help"
            }

            else -> {
                onError("No entendí. Prueba: 'Abre WhatsApp', 'Llama a mamá'")
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
     */
    private fun extractName(text: String, keywords: List<String>): String {
        var result = text
        
        // 1. Eliminar palabras clave de acción
        for (kw in keywords) {
            result = result.replace(kw, "").trim()
        }
        
        // 2. Eliminar artículos y preposiciones comunes en español
        result = result.replace(Regex("\\b(el|la|los|las|un|una|unos|unas|a|de|del|para|por|con|sin|sobre|tras|hacia|hasta)\\b"), "").trim()
        
        // 3. Eliminar caracteres especiales, dejando solo letras y espacios
        result = result.replace(Regex("[^a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]"), "").trim()
        
        // 4. Si queda vacío, devolvemos un string genérico para evitar errores
        return if (result.isEmpty()) "desconocido" else result
    }
}
