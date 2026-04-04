package com.oasis.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var sound: SoundModule
    private var tts: TTSModule? = null
    private var voiceModule: VoiceCommandModule? = null

    private var floatingView: View? = null
    private var listeningView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        sound = SoundModule(this)
        tts = TTSModule(this)

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
       // 1. Crear Canal de Notificación (Obligatorio para Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "oasis_overlay_channel",
                "OASIS Overlay",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        // 2. Crear la Notificación
        val notification = androidx.core.app.NotificationCompat.Builder(this, "oasis_overlay_channel")
            .setContentTitle("OASIS Activo")
            .setContentText("Asistente flotante listo")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Icono del sistema para evitar errores
            .build()

        // 3. Iniciar en Primer Plano (Esto activa el micrófono)
        startForeground(1, notification)
        createFloatingButton()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createFloatingButton() {
        removeViews()
        
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_overlay_float, null)

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

        windowManager.addView(floatingView, params)
        setupTouchListener(floatingView!!, params)
    }

    private fun setupTouchListener(view: View, params: WindowManager.LayoutParams) {
        view.findViewById<ImageView>(R.id.btn_overlay_float).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    v.alpha = 0.7f
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (kotlin.math.abs(event.rawX - initialTouchX) > 10 || 
                        kotlin.math.abs(event.rawY - initialTouchY) > 10) {
                        isDragging = true
                    }
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {                    v.alpha = 1.0f
                    if (!isDragging) {
                        sound.play(R.raw.touch)
                        voiceModule?.startListening()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleListeningUI(isListening: Boolean) {
        if (isListening) {
            removeViews()
            listeningView = LayoutInflater.from(this).inflate(R.layout.layout_voice_listening, null)
            
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
            createFloatingButton()
        }
    }

    private fun handleCommand(command: String, params: Map<String, String>) {
        val response = when (command) {
            "call" -> {
                val contact = params["contact"] ?: "contacto"
                "Llamando a $contact"
            }
            "message" -> {
                val contact = params["contact"] ?: "contacto"
                "Preparando mensaje para $contact"
            }
            "open_app" -> {
                val app = params["app"] ?: "la aplicación"
                "Abriendo $app"            }
            "cancel" -> "Cancelado"
            "help" -> "Puedo llamar, enviar mensajes o abrir aplicaciones"
            else -> "No entendí el comando"
        }
        tts?.speak(response)
    }

    private fun removeViews() {
        try {
            floatingView?.let { windowManager.removeView(it) }
            listeningView?.let { windowManager.removeView(it) }
        } catch (e: Exception) { /* Ignorar si ya fue removida */ }
        floatingView = null
        listeningView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeViews()
        voiceModule?.destroy()
        tts?.shutdown()
        sound.release()
    }
}
