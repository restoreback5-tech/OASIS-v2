package com.oasis.app

import android.Manifest
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.Animatable
import android.os.Build
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
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
    private lateinit var pinnedAdapter: PinnedAdapter

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
            val h24 = UiPrefs.clock24(this@MainActivity)
            tv(R.id.tv_time)?.text = SimpleDateFormat(if (h24) "HH:mm" else "hh:mm", Locale.getDefault()).format(cal.time)
            tv(R.id.tv_ampm)?.text = if (h24) "" else SimpleDateFormat("a", Locale.getDefault()).format(cal.time)
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

        // 4b. Apps fijadas en inicio
        setupPinnedApps()

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
                if (e1 == null || !UiPrefs.swipeUp(this@MainActivity)) return false
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

    // ====================== APPS FIJADAS ======================
    private fun setupPinnedApps() {
        pinnedAdapter = PinnedAdapter(
            context = this,
            onLaunch = { cn: ComponentName ->
                sound.play(R.raw.touch)
                if (!appLauncher.launchComponent(cn)) {
                    tts.speak("No pude abrir la aplicación")
                    refreshPinned()
                }
            },
            onRemove = { cn: ComponentName ->
                PinnedApps.unpin(this, cn)
                refreshPinned()
            }
        )
        findViewById<RecyclerView>(R.id.rv_pinned).apply {
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = pinnedAdapter
        }
        appLauncher.onPinsChanged = { refreshPinned() }
        refreshPinned()
    }

    private fun refreshPinned() {
        val count = pinnedAdapter.reload()
        findViewById<View>(R.id.rv_pinned).visibility = if (count == 0) View.GONE else View.VISIBLE
        findViewById<View>(R.id.tv_pinned_hint).visibility = if (count == 0) View.VISIBLE else View.GONE
    }

    // ====================== TEMAS ======================
    /** ¿El fondo de pantalla del teléfono es claro (conviene texto oscuro)? */
    private fun wallpaperPrefersDarkText(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return true
        return try {
            val colors = WallpaperManager.getInstance(this)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM) ?: return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: el sistema ya dice si el fondo admite texto oscuro
                (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
            } else {
                // Android 8.1–11: aproximamos por la luminosidad del color principal
                ColorUtils.calculateLuminance(colors.primaryColor.toArgb()) > 0.5
            }
        } catch (e: Exception) {
            true
        }
    }

    private fun applyTheme() {
        val p = ThemePalette.from(this, prefs.getString("selected_theme", "amanecer"))
        Log.d("MainActivity", "Aplicando tema: ${p.key}")
        try {
            // Texto oscuro u claro según el fondo de pantalla real; "noche" siempre claro
            val darkText = p.key != "noche" && wallpaperPrefersDarkText()
            val fg = if (darkText) p.text else 0xFFFFFFFF.toInt()
            val fg2 = if (darkText) p.textSecondary else 0xCCFFFFFF.toInt()

            // Velo y dock casi transparentes: dejan ver el fondo de pantalla
            val baseScrim = if (darkText || p.key == "noche") p.scrim else 0x33000000
            val custom = UiPrefs.scrimPercent(this)
            val scrim = if (custom >= 0) ((custom * 255 / 100) shl 24) or (baseScrim and 0x00FFFFFF) else baseScrim
            val dock = if (darkText) p.dock else 0x73000000
            findViewById<View>(R.id.scrim).setBackgroundColor(scrim)
            findViewById<View>(R.id.dock).backgroundTintList = ColorStateList.valueOf(dock)

            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = darkText
                isAppearanceLightNavigationBars = darkText
            }

            listOf(R.id.tv_time, R.id.tv_ampm, R.id.greeting_text,
                R.id.lbl_call, R.id.lbl_message, R.id.lbl_contacts, R.id.lbl_apps
            ).forEach { id ->
                tv(id)?.apply {
                    setTextColor(fg)
                    if (darkText) setShadowLayer(0f, 0f, 0f, 0)
                    else setShadowLayer(8f, 0f, 2f, 0x80000000.toInt())
                }
            }
            listOf(R.id.status_text, R.id.tv_pinned_hint).forEach { id ->
                tv(id)?.apply {
                    setTextColor(fg2)
                    if (darkText) setShadowLayer(0f, 0f, 0f, 0)
                    else setShadowLayer(8f, 0f, 2f, 0x80000000.toInt())
                }
            }
            findViewById<ImageView?>(R.id.btn_settings)?.imageTintList = ColorStateList.valueOf(fg)
            if (::pinnedAdapter.isInitialized) pinnedAdapter.restyle(fg, !darkText)
            FontManager.apply(this, findViewById(R.id.root))
        } catch (e: Exception) {
            Log.e("MainActivity", "Error aplicando tema '${p.key}': ${e.message}")
        }
    }

    /** Aplica los ajustes de inicio: tamaño del orbe, nombres de los botones y gesto. */
    private fun applyHomePrefs() {
        val px = (UiPrefs.orbDp(this) * resources.displayMetrics.density).toInt()
        orbView.layoutParams?.let { lp ->
            if (lp.width != px || lp.height != px) {
                lp.width = px
                lp.height = px
                orbView.layoutParams = lp
            }
        }
        val labels = if (UiPrefs.showLabels(this)) View.VISIBLE else View.GONE
        listOf(R.id.lbl_call, R.id.lbl_message, R.id.lbl_contacts, R.id.lbl_apps)
            .forEach { tv(it)?.visibility = labels }
        findViewById<View>(R.id.swipe_handle).visibility =
            if (UiPrefs.swipeUp(this)) View.VISIBLE else View.INVISIBLE
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
        applyHomePrefs()
        applyTheme()
        applyClockVisibility()
        refreshPinned()
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
