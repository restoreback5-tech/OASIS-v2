package com.oasis.app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.appcompat.app.AppCompatActivity
import java.util.*

class VoiceCaptureActivity : AppCompatActivity() {

    private var speechRecognizer: SpeechRecognizer? = null
    private val REQUEST_CODE_SPEECH = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ventana transparente: no se ve, solo captura voz
        window.setBackgroundDrawableResource(android.R.color.transparent)
        
        // Iniciar reconocimiento inmediatamente
        startListening()
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().language)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Escuchando...")
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH)
        } catch (e: Exception) {
            // Si falla, devolvemos error y cerramos
            sendResult(null, "Error al iniciar micrófono")
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SPEECH && resultCode == RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val command = results?.firstOrNull()?.trim()?.lowercase(Locale.getDefault())
            
            if (!command.isNullOrEmpty()) {
                // Enviamos el comando al servicio via broadcast
                val broadcast = Intent("com.oasis.app.VOICE_COMMAND").apply {
                    putExtra("command_text", command)
                    `package` = packageName
                }
                sendBroadcast(broadcast)
            }
        }
        
        // Siempre cerramos esta actividad invisible
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    private fun sendResult(command: String?, error: String?) {
        val broadcast = Intent("com.oasis.app.VOICE_COMMAND").apply {
            command?.let { putExtra("command_text", it) }
            error?.let { putExtra("error", it) }
            `package` = packageName
        }
        sendBroadcast(broadcast)
    }
}
