package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
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
	sound.preload(R.raw.cancelar, R.raw.confirmar)
	toast = ToastModule(this)
	anim = AnimationModule(orbView)
	tts = TTSModule(this)

	// VoiceCommandModule con callbacks
	voice = VoiceCommandModule(
	 context = this,
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
	appLauncher = AppLauncherModule(this)

	prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

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

        // 6. Reloj en tiempo real
        val clockHandler = Handler(Looper.getMainLooper())
        val clockRunnable = object : Runnable {
            override fun run() {
                val is24Hour = prefs.getBoolean("clock_24h", true)
                val format = if (is24Hour) {
                    SimpleDateFormat("HH:mm", Locale.getDefault())
                } else {
                    SimpleDateFormat("hh:mm a", Locale.getDefault())
                }
                findViewById<TextView>(R.id.clock_text).text = format.format(System.currentTimeMillis())
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
        setupBtn(R.id.btn_call, "Llamar") { openDialer() }
        setupBtn(R.id.btn_message, "Enviar mensaje") { openSms() }
        setupBtn(R.id.btn_contacts, "Contactos") { openContacts() }
        setupBtn(R.id.btn_apps, "Apps") { openLauncher() }
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
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f)
        AnimatorSet().apply {
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
                    openSpecificApp(appName)
                } else {
                    tts.speak("¿Qué aplicación quieres abrir?")
                }
            }

            "call" -> {
                val contact = params["contact"] ?: ""
                if (contact.isNotEmpty()) {
                    if (contact.any { it.isDigit() }) {
                        dialNumber(contact)
                    } else {
                        tts.speak("Buscando a $contact")
                        openDialer()
                    }
                } else {
                    tts.speak("¿A quién quieres llamar?")
                }
            }

            "message" -> {
                val contact = params["contact"] ?: ""
                if (contact.isNotEmpty()) {
                    openSmsToContact(contact)
                } else {
                    openSms()
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
            "oscuro" -> R.color.oscuro_background
            else -> R.color.amanecer_background
        }
        window.setBackgroundDrawableResource(bgRes)

        val textColor = when (selectedTheme) {
            "caribe" -> ContextCompat.getColor(this, R.color.caribe_text)
            "oscuro" -> ContextCompat.getColor(this, R.color.oscuro_text)
            else -> ContextCompat.getColor(this, R.color.amanecer_text)
        }
        findViewById<TextView>(R.id.clock_text).setTextColor(textColor)
        findViewById<TextView>(R.id.greeting_text)?.setTextColor(textColor)
    }

    // === ACCIONES SEGURAS ===
    private fun openDialer() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:") }
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else tts.speak("No se encontró aplicación de teléfono")
        } catch (e: Exception) {
            tts.speak("No pude abrir el marcador")
        }
    }

    private fun openSms() {
        try {
            val whatsappIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (whatsappIntent != null) {
                startActivity(whatsappIntent)
                tts.speak("Abriendo WhatsApp")
                return
            }
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply { data = android.net.Uri.parse("smsto:") }
            if (smsIntent.resolveActivity(packageManager) != null) {
                startActivity(smsIntent)
                tts.speak("Abriendo mensajes")
            } else {
                tts.speak("No hay aplicación de mensajes disponible")
            }
        } catch (e: Exception) {
            tts.speak("No pude abrir los mensajes")
        }
    }

    private fun openSmsToContact(contactName: String) {
        try {
            if (contactName.lowercase().contains("whatsapp") || contactName.lowercase().contains("wasap")) {
                val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent != null) {
                    startActivity(intent)
                    tts.speak("Abriendo WhatsApp")
                    return
                }
            }
            val intent = Intent(Intent.ACTION_SENDTO).apply { data = android.net.Uri.parse("smsto:") }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                tts.speak("Mensaje para $contactName")
            } else {
                tts.speak("No hay aplicación de mensajes")
            }
        } catch (e: Exception) {
            tts.speak("No pude abrir mensajes")
        }
    }

    private fun openContacts() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.provider.ContactsContract.Contacts.CONTENT_URI
            }
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun openLauncher() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun dialNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:$number")
            }
            startActivity(intent)
            tts.speak("Iniciando llamada")
        } catch (e: Exception) {
            tts.speak("No se pudo iniciar la llamada")
        }
    }

  /*
        === ABRIR APPS === (Código antiguo - reemplazado por AppLauncherModule)
    private fun openSpecificApp(appName: String) {
        val pm = packageManager
        val normalized = appName.lowercase().trim()

        val knownPackage = when {
            normalized.contains("whatsapp") || normalized.contains("wasap") -> "com.whatsapp"
            normalized.contains("facebook") || normalized.contains("fb") -> "com.facebook.katana"
            normalized.contains("instagram") || normalized.contains("insta") -> "com.instagram.android"
            normalized.contains("youtube") || normalized.contains("tubo") -> "com.google.android.youtube"
            normalized.contains("chrome") || normalized.contains("navegador") -> "com.android.chrome"
            normalized.contains("spotify") -> "com.spotify.music"
            normalized.contains("tiktok") -> "com.zhiliaoapp.musically"
            normalized.contains("netflix") -> "com.netflix.mediaclient"
            normalized.contains("telegram") -> "org.telegram.messenger"
            normalized.contains("twitter") || normalized.contains("x") -> "com.twitter.android"
            normalized.contains("maps") || normalized.contains("mapas") -> "com.google.android.apps.maps"
            else -> null
        }

        if (knownPackage != null) {
            try {
                val intent = pm.getLaunchIntentForPackage(knownPackage)
                if (intent != null) {
                    startActivity(intent)
                    tts.speak("Abriendo $appName")
                    return
                }
            } catch (_: Exception) {}
        }

        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(intent, 0)
        val match = apps.firstOrNull {
            it.loadLabel(pm).toString().lowercase().contains(normalized)
        }

        if (match != null) {
            val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            if (launchIntent != null) {
                startActivity(launchIntent)
                tts.speak("Abriendo ${match.loadLabel(pm)}")
                return
            }
        }

        tts.speak("No encontré la aplicación $appName")
    }
*/

    // === PERMISOS ===
    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tts.speak("Permiso de micrófono concedido")
            } else {
                tts.speak("Necesito permiso de micrófono para escucharte")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
        tts.shutdown()
        voice.destroy()
    }
}
