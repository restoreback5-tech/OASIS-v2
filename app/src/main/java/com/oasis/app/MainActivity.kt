package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // === DECLARACIÓN DE MÓDULOS ===
    private lateinit var sound: SoundModule
    private lateinit var toast: ToastModule
    private lateinit var anim: AnimationModule
    private lateinit var tts: TTSModule
    private lateinit var voice: VoiceCommandModule
    private lateinit var appLauncher: AppLauncherModule
    private lateinit var prefs: SharedPreferences
    private lateinit var phoneActions: PhoneActionsModule

    // === ORBE VIVO - VARIABLES ===
    private var orbState = "idle"
    private lateinit var orbView: ImageView
    private lateinit var orbBreatheAnim: android.view.animation.Animation
    private lateinit var orbFastAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Configuración del Orbe Vivo
        orbView = findViewById(R.id.orb_view)
        orbBreatheAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.orb_breathe)
        orbFastAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.orb_fast_pulse)
        setOrbToListening()

        // 2. Inicialización de Módulos
        sound = SoundModule(this)
        toast = ToastModule(this)
        anim = AnimationModule(orbView)
        tts = TTSModule(this)
        phoneActions = PhoneActionsModule(this, tts, sound)

        // VoiceCommandModule con callbacks
        voice = VoiceCommandModule(
            context = this,
	    sound = sound,
            onCommandDetected = { command, params ->
                runOnUiThread { handleVoiceCommand(command, params) }
            },
            onListening = { isListening ->
                runOnUiThread {
                    if (isListening) {
                        setOrbToActive()
                        toast.show("Escuchando...")
                    } else {
                        setOrbToListening()
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    toast.show(error)
                    tts.speak(error)
                    resetOrbToIdle()
                }
            }
        )

        // AppLauncherModule - Lanzador de aplicaciones
	appLauncher = AppLauncherModule(this, sound)

        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

	// Aplicar ocultar reloj si está activado
	val hideClock = prefs.getBoolean("hide_clock", false)
	val clockText = findViewById<TextView>(R.id.clock_text)
	if (hideClock) {
    clockText.visibility = View.GONE
} else {
    clockText.visibility = View.VISIBLE
}

        // 3. Tema y Permisos
        applyTheme()
        checkMicPermission()

        // 4. Secuencia de inicio
        sound.play(R.raw.inicio)
        orbView.postDelayed({ tts.speak("Bienvenido a OASIS") }, 1000)
        anim.startRippleAnimation()

        // 5. Botón Ajustes
        findViewById<ImageView>(R.id.btn_settings).setOnClickListener {
            sound.play(R.raw.touch)
            tts.speak("Ajustes")
            startActivity(Intent(this, SettingsActivity::class.java))
        }

	// 6. Reloj en tiempo real (formato 12h con AM/PM pequeño)
	val clockHandler = Handler(Looper.getMainLooper())
	val clockRunnable = object : Runnable {
        override fun run() {
        val calendar = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("hh:mm", Locale.getDefault())
        val amPmFormat = SimpleDateFormat("a", Locale.getDefault())
        val timeStr = timeFormat.format(calendar.time)
        val amPmStr = amPmFormat.format(calendar.time)
        findViewById<TextView>(R.id.tv_time).text = timeStr
        findViewById<TextView>(R.id.tv_ampm).text = amPmStr
        clockHandler.postDelayed(this, 1000)
    }
}
clockHandler.post(clockRunnable)

        // 7. Click en el Orbe - Usa VoiceModule
        orbView.setOnClickListener {
            sound.play(R.raw.touch)
            voice.startListening()
        }

        // 8. Botones principales
        setupBtn(R.id.btn_call, "Llamar") { phoneActions.openDialer() }
        setupBtn(R.id.btn_message, "Enviar mensaje") { phoneActions.openSms() }
        setupBtn(R.id.btn_contacts, "Contactos") { phoneActions.openContacts() }
        setupBtn(R.id.btn_apps, "Apps") { appLauncher.showAllApps() }
    }

    private fun setupBtn(id: Int, text: String, action: () -> Unit) {
        findViewById<MaterialButton>(id).setOnClickListener {
            sound.play(R.raw.touch)
            pulseAnimation(findViewById(id))
            toast.show(text)
            tts.speak(text)
            action()
        }
    }

    private fun pulseAnimation(view: View) {
        val scaleX = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f)
        android.animation.AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 180
            interpolator = android.view.animation.DecelerateInterpolator()
            start()
        }
    }

    // ====================== ORBE VIVO ======================
    private fun setOrbToListening() {
        orbState = "idle"
        orbView.clearAnimation()
        orbView.setImageResource(R.drawable.orb_listening)
        (orbView.drawable as? android.graphics.drawable.Animatable)?.start()
        orbView.startAnimation(orbBreatheAnim)
    }

    private fun setOrbToActive() {
        orbState = "active"
        orbView.clearAnimation()
        orbView.setImageResource(R.drawable.orb_active)
        (orbView.drawable as? android.graphics.drawable.Animatable)?.start()
        orbView.startAnimation(orbFastAnim)
    }

    private fun resetOrbToIdle() {
        if (orbState == "active") {
            setOrbToListening()
        }
    }

    // ====================== MANEJO DE COMANDOS (VoiceModule) ======================
    private fun handleVoiceCommand(command: String, params: Map<String, String>) {
        toast.show("Comando: $command")

        when (command) {
            "open_app" -> {
                val appName = params["app"] ?: "desconocido"
                if (appName != "desconocido") {
                    val success = appLauncher.launchApp(appName)
                    if (success) {
                        tts.speak("Abriendo $appName")
                    } else {
                        tts.speak("No encontré la aplicación $appName")
                    }
                } else {
                    tts.speak("¿Qué aplicación quieres abrir?")
                }
            }

            "call" -> {
                val contact = params["contact"] ?: ""
                if (contact.isNotEmpty()) {
                    if (contact.any { it.isDigit() }) {
                        phoneActions.dialNumber(contact)
                    } else {
                        tts.speak("Buscando a $contact")
                        phoneActions.searchContactAndDial(contact)
                    }
                } else {
                    tts.speak("¿A quién quieres llamar?")
                }
            }

            "message" -> {
                val contact = params["contact"] ?: ""
                if (contact.isNotEmpty()) {
                    phoneActions.openSmsToContact(contact)
                } else {
                    phoneActions.openSms()
                }
            }

            "cancel" -> {
                sound.play(R.raw.cancelar)
                tts.speak("Cancelando")
                resetOrbToIdle()
            }

            "help" -> {
                tts.speak("Puedo abrir apps, hacer llamadas y enviar mensajes")
                resetOrbToIdle()
            }

            else -> {
                tts.speak("No entendí. Intenta de nuevo")
                resetOrbToIdle()
            }
        }
    }

    // === TEMAS ===
    private fun applyTheme() {
        val selectedTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val bgRes = when (selectedTheme) {
            "caribe" -> R.color.caribe_background
            "noche" -> R.drawable.noche_background
            else -> R.color.amanecer_background
        }
        window.setBackgroundDrawableResource(bgRes)

        val statusBarColor = when (selectedTheme) {
            "caribe" -> R.color.status_bar_caribe
            "noche" -> R.color.status_bar_oscuro
            else -> R.color.status_bar_amanecer
        }
        window.statusBarColor = ContextCompat.getColor(this, statusBarColor)

        val isLightStatusBar = selectedTheme != "oscuro"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = if (isLightStatusBar) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                0
            }
        }

        val textColor = when (selectedTheme) {
            "caribe" -> ContextCompat.getColor(this, R.color.caribe_text)
            "oscuro" -> ContextCompat.getColor(this, R.color.oscuro_text)
            else -> ContextCompat.getColor(this, R.color.amanecer_text)
        }
	findViewById<TextView>(R.id.tv_time).setTextColor(textColor)
	findViewById<TextView>(R.id.tv_ampm).setTextColor(textColor)
        findViewById<TextView>(R.id.greeting_text)?.setTextColor(textColor)
    }

    // === PERMISOS ===

    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            100 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    tts.speak("Permiso de micrófono concedido")
                } else {
                    tts.speak("Necesito permiso de micrófono para escucharte")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        tts.updateSpeechSettings()
	val hideClock = prefs.getBoolean("hide_clock", false)
	findViewById<TextView>(R.id.clock_text).visibility = if (hideClock) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
        tts.shutdown()
        voice.destroy()
    }
}
