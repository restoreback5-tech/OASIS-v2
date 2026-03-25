package com.oasis.app

import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var mediaPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inicializar TTS
        tts = TextToSpeech(this, this)

        // Inicializar reconocimiento de voz
        setupSpeechRecognizer()

        // Configurar UI
        setupOrb()
        setupButtons()

        // Bienvenida después de 1 segundo
        findViewById<ImageView>(R.id.orb_view).postDelayed({
            speak("Bienvenido a OASIS")
        }, 1000)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("es", "ES")
        }
    }
    private fun setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    playSound(R.raw.escuchando)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    isListening = false
                    val errorMessage = when(error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                        SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos de micrófono"
                        else -> "Error en reconocimiento"
                    }
                    showToast(errorMessage)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.get(0)?.let { command ->
                        processVoiceCommand(command.lowercase())
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun processVoiceCommand(command: String) {
        showToast("Comando: $command")
        
        when {
            command.contains("llamar") -> {
                speak("¿A quién quieres llamar?")
                // TODO: Implementar llamada
            }
            command.contains("mensaje") || command.contains("enviar") -> {
                speak("¿A quién quieres enviar el mensaje?")
                // TODO: Implementar mensaje            }
            command.contains("contacto") -> {
                speak("Abriendo contactos")
                openContacts()
            }
            command.contains("aplicación") || command.contains("apps") -> {
                speak("Abriendo aplicaciones")
                openApps()
            }
            command.contains("hola") || command.contains("buenas") -> {
                speak("Hola, soy OASIS. ¿En qué puedo ayudarte?")
            }
            else -> {
                speak("No entendí. Intenta decir: llamar, mensaje, contactos o aplicaciones")
            }
        }
    }

    private fun startListening() {
        if (!isListening) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            speechRecognizer?.startListening(intent)
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        playSound(R.raw.confirmar)
    }

    private fun setupOrb() {
        val orb = findViewById<ImageView>(R.id.orb_view)
        
        // Click en el orbe para activar voz
        orb.setOnClickListener {
            playSound(R.raw.touch)
            startListening()
        }
        
        // Animación simple
        orb.animate()
            .scaleX(1.05f)
            .scaleY(1.05f)
            .setDuration(1000)
            .repeatMode(android.view.animation.Animation.REVERSE)
            .repeatCount(android.view.animation.Animation.INFINITE)            .start()
    }

    private fun setupButtons() {
        val btnCall = findViewById<MaterialButton>(R.id.btn_call)
        val btnMessage = findViewById<MaterialButton>(R.id.btn_message)
        val btnContacts = findViewById<MaterialButton>(R.id.btn_contacts)
        val btnApps = findViewById<MaterialButton>(R.id.btn_apps)

        btnCall.setOnClickListener {
            playSound(R.raw.touch)
            speak("Llamar")
            makeCall()
        }

        btnMessage.setOnClickListener {
            playSound(R.raw.touch)
            speak("Enviar mensaje")
            sendMessage()
        }

        btnContacts.setOnClickListener {
            playSound(R.raw.touch)
            speak("Contactos")
            openContacts()
        }

        btnApps.setOnClickListener {
            playSound(R.raw.touch)
            speak("Aplicaciones")
            openApps()
        }
    }

    private fun makeCall() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:")
            }
            startActivity(intent)
        } catch (e: Exception) {
            speak("No puedo abrir el marcador")
        }
    }

    private fun sendMessage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                type = "vnd.android-dir/mms-sms"
            }            startActivity(intent)
        } catch (e: Exception) {
            speak("No puedo abrir mensajes")
        }
    }

    private fun openContacts() {
        try {
            val intent = Intent(android.provider.ContactsContract.Contacts.CONTENT_URI).apply {
                action = Intent.ACTION_VIEW
            }
            startActivity(intent)
        } catch (e: Exception) {
            speak("No puedo abrir contactos")
        }
    }

    private fun openApps() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            startActivity(intent)
        } catch (e: Exception) {
            speak("No puedo abrir el menú de aplicaciones")
        }
    }

    private fun playSound(soundResId: Int) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer.create(this, soundResId)
            mediaPlayer?.start()
        } catch (e: Exception) {
            // Silencio si falla
        }
    }

    private fun showToast(message: String) {
        val greetingText = findViewById<TextView>(R.id.greeting_text)
        greetingText.text = message
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()    }
}
