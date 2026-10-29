package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

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

    // === LAUNCHER ===
    private lateinit var swipeDetector: GestureDetector
    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* el sistema ya aplica la elección del usuario */ }

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
        // Pantalla completa: el fondo de pantalla del teléfono se ve detrás de las barras
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.content)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

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

        // 7b. Comportamiento de launcher
        setupLauncherBehavior()

        // 8. Botones principales
        setupBtn(R.id.btn_call, "Llamar") { phoneActions.openDialer() }
        setupBtn(R.id.btn_message, "Enviar mensaje") { phoneActions.openSms() }
        setupBtn(R.id.btn_contacts, "Contactos") { phoneActions.openContacts() }
        setupBtn(R.id.btn_apps, "Apps") { appLauncher.showAllApps() }
    }

    // ====================== HELPERS ======================
    private fun tv(id: Int): TextView? = findViewById<TextView?>(id)

    private fun setupBtn(id: Int, text: String, action: () -> Unit) {
        findViewById<View?>(id)?.setOnClickListener { v ->
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

    // ====================== LAUNCHER ======================
    private fun setupLauncherBehavior() {
        // Deslizar hacia arriba abre el cajón de apps
        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val dy = e1.y - e2.y
                val dx = e2.x - e1.x
                if (dy > 150 && velocityY < -800 && dy > abs(dx)) {
                    appLauncher.showAllApps()
                    return true
                }
                return false
            }
        })

        // Como launcher, "Atrás" no debe cerrar la pantalla de inicio
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!LauncherHelper.isDefaultLauncher(this@MainActivity)) finish()
            }
        })

        // Una sola vez: ofrecer ser el launcher predeterminado
        if (!LauncherHelper.isDefaultLauncher(this) && !prefs.getBoolean("launcher_prompt_done", false)) {
            prefs.edit().putBoolean("launcher_prompt_done", true).apply()
            orbView.postDelayed({
                if (!isFinishing) LauncherHelper.requestDefaultLauncher(this, roleLauncher)
            }, 2500)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::swipeDetector.isInitialized) swipeDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    /** El botón Inicio estando ya en OASIS: cierra el cajón y vuelve al estado base. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            appLauncher.dismissAllApps()
            resetOrbToIdle()
        }
    }

    // ====================== TEMAS ======================
    private fun applyTheme() {
        val p = ThemePalette.from(this, prefs.getString("selected_theme", "amanecer"))
        Log.d("MainActivity", "Aplicando tema: ${p.key}")
        try {
            findViewById<View>(R.id.scrim).setBackgroundColor(p.scrim)
            findViewById<View>(R.id.dock).backgroundTintList = ColorStateList.valueOf(p.dock)

            // Iconos de la barra de estado/navegación (oscuros sobre fondos claros)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = p.lightSystemBarIcons
                isAppearanceLightNavigationBars = p.lightSystemBarIcons
            }

            listOf(R.id.tv_time, R.id.tv_ampm, R.id.greeting_text,
                R.id.lbl_call, R.id.lbl_message, R.id.lbl_contacts, R.id.lbl_apps
            ).forEach { tv(it)?.setTextColor(p.text) }
            tv(R.id.status_text)?.setTextColor(p.textSecondary)
            findViewById<ImageView?>(R.id.btn_settings)?.imageTintList = ColorStateList.valueOf(p.text)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error aplicando tema '${p.key}': ${e.message}")
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
