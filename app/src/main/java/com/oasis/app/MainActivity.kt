package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // === MÓDULOS ===
    private lateinit var sound: SoundModule
    private lateinit var toast: ToastModule
    private lateinit var anim: AnimationModule
    private lateinit var tts: TTSModule
    private lateinit var voice: VoiceCommandModule
    private lateinit var appLauncher: AppLauncherModule
    private lateinit var prefs: SharedPreferences
    private lateinit var phoneActions: PhoneActionsModule

    // === ORBE ===
    private var orbState = "idle"
    private lateinit var orbView: ImageView
    private lateinit var orbBreatheAnim: android.view.animation.Animation
    private lateinit var orbFastAnim: android.view.animation.Animation

    // === RELOJ ===
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            val cal = Calendar.getInstance()
            tv(R.id.tv_time)?.text = SimpleDateFormat("hh:mm", Locale.getDefault()).format(cal.time)
            tv(R.id.tv_ampm)?.text = SimpleDateFormat("a", Locale.getDefault()).format(cal.time)
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        // 1. Orbe
        orbView = findViewById(R.id.orb_view)
        orbBreatheAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.orb_breathe)
        orbFastAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.orb_fast_pulse)
        setOrbToListening()

        // 2. Módulos
        sound = SoundModule(this)
        toast = ToastModule(this)
        anim = AnimationModule(orbView)
        tts = TTSModule(this)
        phoneActions = PhoneActionsModule(this, tts, sound)
        appLauncher = AppLauncherModule(this, sound)

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

        // 3. Tema, reloj y permisos
        applyTheme()
        applyClockVisibility()
        checkMicPermission()

        // 4. Secuencia de inicio
        sound.play(R.raw.inicio)
        orbView.postDelayed({ tts.speak("Bienvenido a OASIS") }, 1000)
        anim.startRippleAnimation()

        // 5. Botón Ajustes
        findViewById<ImageView?>(R.id.btn_settings)?.setOnClickListener {
            sound.play(R.raw.touch)
            tts.speak("Ajustes")
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 6. Reloj en tiempo real
        clockHandler.post(clockRunnable)

        // 7. Click en el Orbe
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

    // ====================== HELPERS ======================
    private fun tv(id: Int): TextView? = findViewById<TextView?>(id)

    private fun setupBtn(id: Int, text: String, action: () -> Unit) {
        findViewById<MaterialButton?>(id)?.setOnClickListener { v ->
            sound.play(R.raw.touch)
            pulseAnimation(v)
            toast.show(text)
            tts.speak(text)
            action()
        }
    }

    private fun pulseAnimation(view: View) {
        val sx = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f)
        val sy = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f)
        android.animation.AnimatorSet().apply {
            playTogether(sx, sy)
            duration = 180
            interpolator = android.view.animation.DecelerateInterpolator()
            start()
        }
    }

    // ====================== ORBE ======================
    private fun setOrbToListening() {
        orbState = "idle"
        try {
            orbView.clearAnimation()
            orbView.setImageResource(R.drawable.orb_listening)
            (orbView.drawable as? Animatable)?.start()
            orbView.startAnimation(orbBreatheAnim)
        } catch (e: Exception) {
            Log.w("MainActivity", "Orbe idle falló: ${e.message}")
        }
    }

    private fun setOrbToActive() {
        orbState = "active"
        try {
            orbView.clearAnimation()
            orbView.setImageResource(R.drawable.orb_active)
            (orbView.drawable as? Animatable)?.start()
            orbView.startAnimation(orbFastAnim)
        } catch (e: Exception) {
            Log.w("MainActivity", "Orbe active falló: ${e.message}")
        }
    }

    private fun resetOrbToIdle() {
        if (orbState == "active") setOrbToListening()
    }

    // ====================== COMANDOS DE VOZ ======================
    private fun handleVoiceCommand(command: String, params: Map<String, String>) {
        toast.show("Comando: $command")

        when (command) {
            "open_app" -> {
                val appName = params["app"] ?: "desconocido"
                if (appName != "desconocido") {
                    val ok = appLauncher.launchApp(appName)
                    tts.speak(if (ok) "Abriendo $appName" else "No encontré la aplicación $appName")
                } else {
                    tts.speak("¿Qué aplicación quieres abrir?")
                }
            }
            "call" -> {
                val contact = params["contact"].orEmpty()
                if (contact.isNotEmpty()) {
                    if (contact.any { it.isDigit() }) phoneActions.dialNumber(contact)
                    else {
                        tts.speak("Buscando a $contact")
                        phoneActions.searchContactAndDial(contact)
                    }
                } else tts.speak("¿A quién quieres llamar?")
            }
            "message" -> {
                val contact = params["contact"].orEmpty()
                if (contact.isNotEmpty()) phoneActions.openSmsToContact(contact)
                else phoneActions.openSms()
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

    // ====================== TEMAS ======================
    private fun applyTheme() {
        val theme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        Log.d("MainActivity", "Aplicando tema: $theme")

        try {
            // Fondo
            val bgRes = when (theme) {
                "caribe" -> R.color.caribe_background
                "noche"  -> R.color.oscuro_background
                else     -> R.color.amanecer_background
            }
            window.decorView.setBackgroundColor(ContextCompat.getColor(this, bgRes))

            // Status bar
            val statusRes = when (theme) {
                "caribe" -> R.color.status_bar_caribe
                "noche"  -> R.color.status_bar_oscuro
                else     -> R.color.status_bar_amanecer
            }
            window.statusBarColor = ContextCompat.getColor(this, statusRes)

            // Iconos status bar (claro/oscuro)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val light = theme != "noche"
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (light)
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
            }

            // Texto
            val textRes = when (theme) {
                "caribe" -> R.color.caribe_text
                "noche"  -> R.color.oscuro_text
                else     -> R.color.amanecer_text
            }
            val color = ContextCompat.getColor(this, textRes)
            tv(R.id.tv_time)?.setTextColor(color)
            tv(R.id.tv_ampm)?.setTextColor(color)
            tv(R.id.greeting_text)?.setTextColor(color)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error aplicando tema '$theme': ${e.message}")
        }
    }

    private fun applyClockVisibility() {
        val hide = prefs.getBoolean("hide_clock", false)
        val vis = if (hide) View.GONE else View.VISIBLE
        tv(R.id.tv_time)?.visibility = vis
        tv(R.id.tv_ampm)?.visibility = vis
    }

    // ====================== PERMISOS ======================
    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            val granted = grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED
            tts.speak(if (granted) "Permiso de micrófono concedido"
                      else "Necesito permiso de micrófono para escucharte")
        }
    }

    // ====================== CICLO DE VIDA ======================
    override fun onResume() {
        super.onResume()
        applyTheme()
        applyClockVisibility()
        tts.updateSpeechSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        sound.release()
        tts.shutdown()
        voice.destroy()
    }
}
