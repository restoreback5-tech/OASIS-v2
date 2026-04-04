
package com.oasis.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service(), TextToSpeech.OnInitListener {

    private lateinit var windowManager: WindowManager
    private lateinit var sound: SoundModule
    private var tts: TTSModule? = null
    private var voiceModule: VoiceCommandModule? = null

    // Vistas
    private var floatingButtonView: View? = null
    private var listeningView: View? = null

    // Posición para arrastrar el botón
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        sound = SoundModule(this)
        tts = TTSModule(this)
        
        // Inicializar módulo de voz
        voiceModule = VoiceCommandModule(
            context = this,
            onCommandDetected = { command, params -> handleCommand(command, params) },
            onListening = { isListening -> toggleListeningUI(isListening) },
            onError = { msg -> 
                tts?.speak(msg)
                toggleListeningUI(false)
            }
        )
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingButton()
    }

    private fun createFloatingButton() {
        val inflater = LayoutInflater.from(this)
        floatingButtonView = inflater.inflate(R.layout.layout_overlay_float, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        
        params.gravity = Gravity.TOP or Gravity.END
        params.x = 0
        params.y = 200

        windowManager.addView(floatingButtonView, params)
        setupButtonTouchListener(floatingButtonView!!, params)
    }

    private fun setupButtonTouchListener(view: View, params: WindowManager.LayoutParams) {
        view.findViewById<ImageView>(R.id.btn_overlay_float).setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    v.alpha = 0.7f
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val diffX = kotlin.math.abs(event.rawX - initialTouchX)
                    val diffY = kotlin.math.abs(event.rawY - initialTouchY)
                    
                    if (diffX > 10 || diffY > 10) isDragging = true

                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true                }
                android.view.MotionEvent.ACTION_UP -> {
                    v.alpha = 1.0f
                    if (!isDragging) {
                        sound.play(R.raw.touch)
                        // ¡Aquí iniciamos la escucha!
                        voiceModule?.startListening()
                    }
                    true
                }
                else -> false
            }
        }
    }

    // Cambia la vista del botón a "Escuchando..."
    private fun toggleListeningUI(isListening: Boolean) {
        windowManager.removeView(floatingButtonView)
        floatingButtonView = null

        if (isListening) {
            val inflater = LayoutInflater.from(this)
            listeningView = inflater.inflate(R.layout.layout_voice_listening, null)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )
            params.gravity = Gravity.TOP or Gravity.END
            params.x = 0
            params.y = 200
            
            windowManager.addView(listeningView, params)
        } else {
            // Volver al botón
            createFloatingButton()
        }
    }

    // El cerebro: ejecuta lo que dice el usuario
    private fun handleCommand(command: String, params: Map<String, String>) {
        when (command) {
            "call" -> {
                val name = params["contact"] ?: "contacto"                tts?.speak("Llamando a $name")
                // TODO: Implementar intento de llamada real aquí
                // Intent(Intent.ACTION_CALL, Uri.parse("tel:..."))
            }
            "message" -> {
                val name = params["contact"] ?: "contacto"
                tts?.speak("Preparando mensaje para $name")
            }
            "open_app" -> {
                val appName = params["app"] ?: "la aplicación"
                tts?.speak("Abriendo $appName")
                // TODO: Implementar búsqueda de paquete y abrir app
            }
            "cancel" -> {
                tts?.speak("Cancelado")
            }
            "help" -> {
                tts?.speak("Puedo llamar, enviar mensajes o abrir aplicaciones. Dime, ¿qué necesitas?")
            }
            else -> {
                tts?.speak("No entendí el comando")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        if (floatingButtonView != null) windowManager.removeView(floatingButtonView)
        if (listeningView != null) windowManager.removeView(listeningView)
        voiceModule?.destroy()
        tts?.shutdown()
        sound.release()
    }
}
