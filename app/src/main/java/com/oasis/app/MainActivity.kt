package com.oasis.app

import android.Manifest
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.widget.LinearLayout
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
    private lateinit var dashboard: DashboardController
    private lateinit var orbFx: OrbFx
    private lateinit var assistant: AssistantEngine
    private lateinit var liveBg: LiveBackground
    private lateinit var dock: DockEditor
    private lateinit var readAloud: ReadAloud
    private var wake: WakeWord? = null
    private var fall: FallDetector? = null
    private var convoActive = false
    private var convoPending = false
    private var convoTurns = 0
    @Volatile private var speaking = false
    private var tilt: TiltParallax? = null
    private var packageReceiver: BroadcastReceiver? = null

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
        liveBg = findViewById(R.id.live_bg)
        orbView = findViewById(R.id.orb_view)
        orbFx = OrbFx(orbView, findViewById(R.id.orb_ring_1), findViewById(R.id.orb_ring_2))
        orbFx.alt = findViewById(R.id.voice_fx)
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
        dashboard = DashboardController(
            act = this,
            phone = phoneActions,
            onTalk = { sound.play(R.raw.touch); startVoice() },
            onReplay = { command, params -> handleVoiceCommand(command, params) },
            onFlash = { appLauncher.launchApp("linterna") }
        )
        dashboard.bind()
        dock = DockEditor(this, appLauncher)
        readAloud = ReadAloud(
            act = this,
            onStatus = { msg -> runOnUiThread { findViewById<TextView>(R.id.status_text).text = msg; tts.speak(msg) } },
            onText = { text -> runOnUiThread { findViewById<TextView>(R.id.status_text).text = text; tts.speak(text) } }
        )

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
                convoActive = false
                runOnUiThread {
                    toast.show(error)
                    tts.speak(error)
                    resetOrbToIdle()
                }
            },
            onFreeText = { text -> runOnUiThread { askAssistant(text) } },
            onLevel = { l -> runOnUiThread { orbFx.level(l) } }
        )

        // El orbe se modula con la voz del asistente: late en cada palabra
        assistant = AssistantEngine(this)
        tts.callbacks = object : TTSModule.Callbacks {
            override fun onSpeakStart() { speaking = true; runOnUiThread { orbFx.thinking(false); orbFx.start(gentle = true) } }
            override fun onSpeakWord() { runOnUiThread { orbFx.kick() } }
            override fun onSpeakDone() {
                speaking = false
                runOnUiThread {
                    if (orbState != "active") orbFx.stop()
                    // Modo conversación: tras contestar, vuelve a escuchar sin tocar el orbe
                    if (convoPending && convoActive) {
                        convoPending = false
                        wake?.pauseFor(15000)
                        orbView.postDelayed({ if (convoActive && !speaking && orbState != "active") voice.startListening() }, 800)
                    }
                }
            }
        }

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
            if (Fx.enabled(this)) it.animate().rotationBy(120f).setDuration(350).start()
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 6. Reloj en tiempo real
        clockHandler.post(clockRunnable)

        // 7. Click en el Orbe
        orbView.setOnClickListener {
            sound.play(R.raw.touch)
            startVoice()
        }

        // 7b. Comportamiento de launcher
        setupLauncherBehavior()

        // 8. Botones principales
        setupBtn(R.id.btn_call, "Llamar") { dock.launchOr(R.id.btn_call) { phoneActions.openDialer() } }
        setupBtn(R.id.btn_message, "Enviar mensaje") { dock.launchOr(R.id.btn_message) { phoneActions.openSms() } }
        setupBtn(R.id.btn_contacts, "Contactos") { dock.launchOr(R.id.btn_contacts) { phoneActions.openContacts() } }
        // Dock editable: mantén presionado un botón para elegir otra app
        listOf(R.id.btn_call, R.id.btn_message, R.id.btn_contacts).forEach { id ->
            findViewById<View>(id).setOnLongClickListener { sound.play(R.raw.touch); dock.edit(id); true }
        }
        dock.refresh()
        setupBtn(R.id.btn_apps, "Apps") { appLauncher.showAllApps() }

        // 8b. Rebote al tocar
        listOf(R.id.btn_call, R.id.btn_message, R.id.btn_contacts, R.id.btn_apps)
            .forEach { Fx.pressable(findViewById(it), 0.9f) }
        Fx.pressable(orbView, 0.92f)
        Fx.pressable(findViewById(R.id.btn_settings), 0.85f)
        findViewById<RecyclerView>(R.id.rv_pinned).layoutAnimation =
            android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_cascade)

        // 9. Instalar/desinstalar apps: refresca inicio y dashboard
        registerPackageReceiver()
    }

    // ====================== HELPERS ======================
    private fun tv(id: Int): TextView? = findViewById<TextView?>(id)

    private fun setupBtn(id: Int, text: String, action: () -> Unit) {
        findViewById<View?>(id)?.setOnClickListener { v ->
            sound.play(R.raw.touch)
            pulseAnimation(v)
            val say = (v.contentDescription ?: text).toString()
            toast.show(say)
            tts.speak(say)
            action()
        }
    }

    private fun pulseAnimation(view: View) {
        if (Fx.enabled(this)) return // con efectos, el rebote lo hace Fx.pressable
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
        orbFx.stop()
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
        orbFx.start()
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
        VoiceHistory.add(this, command, params)
        if (::dashboard.isInitialized) dashboard.refreshVoice()

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
            "ask" -> askAssistant(params["q"].orEmpty())
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

    // ====================== ASISTENTE ======================
    private fun askAssistant(q: String) {
        if (q.isBlank()) return
        if (convoActive) {
            convoTurns++
            val n = TextMatch.normalize(q)
            val bye = listOf("adios", "hasta luego", "gracias", "terminar", "basta", "nada mas", "ya no", "silencio").any { n.contains(it) }
            if (bye || convoTurns > 8) {
                convoActive = false
                val msg = "Aquí estaré cuando me necesites."
                findViewById<android.widget.TextView>(R.id.status_text).text = msg
                tts.speak(msg)
                return
            }
        }
        VoiceHistory.add(this, "ask", mapOf("q" to q))
        dashboard.refreshVoice()
        val status = findViewById<android.widget.TextView>(R.id.status_text)
        status.text = "“$q”"
        orbFx.thinking(true)
        assistant.handle(q) { reply ->
            orbFx.thinking(false)
            status.text = reply.text
            convoPending = convoActive
            tts.speak(reply.text)
            runAssistantAct(reply.act)
        }
    }

    private fun runAssistantAct(a: AssistantEngine.Act?) {
        try {
            when (a) {
                is AssistantEngine.Act.OpenApp -> appLauncher.launchApp(AppRegistry.canonicalAliasFor(a.app))
                is AssistantEngine.Act.Call -> if (a.contact.any { it.isDigit() }) phoneActions.dialNumber(a.contact) else phoneActions.searchContactAndDial(a.contact)
                is AssistantEngine.Act.Message -> if (a.contact.isNotBlank()) phoneActions.openSmsToContact(a.contact) else phoneActions.openSms()
                is AssistantEngine.Act.Timer -> startActivity(
                    Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                        .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, a.minutes * 60)
                        .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "OASIS")
                        .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                )
                is AssistantEngine.Act.Alarms -> startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
                is AssistantEngine.Act.Restyle -> applyTheme()
                is AssistantEngine.Act.AlarmAt -> startActivity(
                    Intent(android.provider.AlarmClock.ACTION_SET_ALARM)
                        .putExtra(android.provider.AlarmClock.EXTRA_HOUR, a.hour)
                        .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, a.minute)
                        .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, a.label.ifBlank { "OASIS" })
                        .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                )
                is AssistantEngine.Act.System -> startActivity(Intent(a.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                is AssistantEngine.Act.Routine -> runRoutine(a.night)
                is AssistantEngine.Act.ReadAloud -> startReadAloud()
                is AssistantEngine.Act.SendSms -> {
                    if (Safety.ensureSms(this)) {
                        val ok = Safety.sendSms(this, a.number, a.body)
                        val msg = if (ok) "Mensaje enviado a ${a.name}." else "No pude enviar el mensaje."
                        findViewById<android.widget.TextView>(R.id.status_text).text = msg
                        tts.speak(msg)
                    } else tts.speak("Permite enviar mensajes y vuelve a pedírmelo.")
                }
                is AssistantEngine.Act.Sos -> SafetyUi.runSos(this) { dial(it) }
                is AssistantEngine.Act.ShareLocation -> SafetyUi.shareLocation(this)
                is AssistantEngine.Act.GoHome -> SafetyUi.goHome(this)
                is AssistantEngine.Act.Breathe -> Breathing.show(this) { tts.speak(it) }
                is AssistantEngine.Act.Medical -> SafetyUi.showMedical(this)
                null -> {}
            }
        } catch (e: Exception) {
            toast.show("No pude hacer eso en este teléfono")
        }
    }

    // ====================== RUTINAS, CÁMARA, "HOLA OASIS" ======================
    /** Buenos días / buenas noches: el orbe resume el día (o la noche) en voz alta. */
    fun runRoutine(night: Boolean) {
        val status = findViewById<TextView>(R.id.status_text)
        orbFx.thinking(true)
        Routines.run(this, night) { text ->
            orbFx.thinking(false)
            status.text = text
            tts.speak(text)
        }
    }

    /** Foto de un texto (cartel, receta, medicina) y el orbe lo lee en voz alta. */
    fun startReadAloud() {
        sound.play(R.raw.touch)
        wake?.pauseFor(20000)
        readAloud.start()
    }

    // Pequeñas puertas para otras pantallas (tarjetas de Widgets)
    fun assistantEngine() = assistant
    fun say(text: String) = tts.speak(text)
    fun dial(number: String) = phoneActions.dialNumber(number)

    /** Un solo punto para empezar a escuchar: pausa «Hola Oasis» para que no compitan por el micrófono. */
    private fun startVoice() {
        wake?.pauseFor(9000)
        convoActive = UiPrefs.convoMode(this)
        convoTurns = 0
        voice.startListening()
    }

    /** Detector de caídas (solo con OASIS abierta) y aviso de batería baja al cuidador. */
    private fun syncSafety() {
        if (UiPrefs.fallDetect(this)) {
            if (fall == null) fall = FallDetector(this) { runOnUiThread { SafetyUi.fallCountdown(this) { dial(it) } } }
            fall?.start()
        } else fall?.stop()
        Safety.checkBattery(this)
    }

    private fun syncWakeWord() {
        val micOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (UiPrefs.wakeWord(this) && micOk) {
            if (wake == null) {
                wake = WakeWord(
                    ctx = this,
                    isBusy = { orbState == "active" || speaking },
                    onWake = { sound.play(R.raw.touch); voice.startListening() }
                )
            }
            wake?.start()
        } else {
            wake?.stop()
        }
    }

    // ====================== MODO SIMPLE ======================
    /** Modo simple: contactos favoritos con foto, grandes, directo en la pantalla de inicio. */
    private fun refreshHomeFavs() {
        val box = findViewById<LinearLayout>(R.id.home_favs)
        if (!UiPrefs.simpleMode(this)) { box.visibility = View.GONE; return }
        if (!TodayData.has(this, Manifest.permission.READ_CONTACTS)) {
            box.visibility = View.GONE
            if (!prefs.getBoolean("asked_contacts_simple", false)) {
                prefs.edit().putBoolean("asked_contacts_simple", true).apply()
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), 203)
            }
            return
        }
        Thread {
            val favs = TodayData.favorites(this, 4)
            runOnUiThread {
                val pal = ThemePalette.current(this)
                box.removeAllViews()
                favs.forEach { f ->
                    val t = ContactTiles.build(this, box, f, 72, pal.accent, phoneActions) { tts.speak(it) }
                    t.findViewById<TextView>(R.id.tile_initial).setTextColor(pal.text)
                    t.findViewById<TextView>(R.id.tile_name).setTextColor(pal.text)
                    FontManager.apply(this, t)
                    box.addView(t)
                }
                box.visibility = if (favs.isEmpty()) View.GONE else View.VISIBLE
            }
        }.start()
    }

    // ====================== LAUNCHER ======================
    private fun setupLauncherBehavior() {
        // Deslizar hacia arriba abre el cajón de apps
        swipeDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!dashboard.atHome || isOverControl(e.rawX, e.rawY)) return false
                runGesture(UiPrefs.gestureDouble(this@MainActivity))
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && dashboard.atHome && e2.y - e1.y > 150 && velocityY > 800 &&
                    (e2.y - e1.y) > abs(e2.x - e1.x) && UiPrefs.gestureDown(this@MainActivity) != 0) {
                    runGesture(UiPrefs.gestureDown(this@MainActivity))
                    return true
                }
                if (e1 == null || !UiPrefs.swipeUp(this@MainActivity)) return false
                // En las páginas con lista (Hoy / Mi teléfono) deslizar es para desplazarse
                if (::dashboard.isInitialized && !dashboard.atHome) return false
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
                if (::dashboard.isInitialized && !dashboard.atHome) dashboard.goHome()
                else if (!LauncherHelper.isDefaultLauncher(this@MainActivity)) finish()
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

    /** ¿El toque cae sobre algo interactivo (dock, orbe, apps fijadas, ajustes)? */
    private fun isOverControl(x: Float, y: Float): Boolean {
        val r = android.graphics.Rect()
        return listOf(R.id.dock, R.id.orb_holder, R.id.rv_pinned, R.id.btn_settings).any { id ->
            val v = findViewById<View?>(id)
            v != null && v.getGlobalVisibleRect(r) && r.contains(x.toInt(), y.toInt())
        }
    }

    private fun runGesture(action: Int) {
        when (action) {
            1 -> if (!expandNotifications()) toast.show("No pude abrir las notificaciones en este teléfono")
            2 -> appLauncher.showAllApps()
            3 -> appLauncher.launchApp("linterna")
            4 -> startActivity(Intent(this, SettingsActivity::class.java))
            5 -> { sound.play(R.raw.touch); voice.startListening() }
            6 -> if (!OasisAccessibilityService.lockScreen()) {
                toast.show("Activa «OASIS» en Accesibilidad para poder bloquear la pantalla")
                try { startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) {}
            }
        }
    }

    private fun expandNotifications(): Boolean = try {
        val sb = getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        true
    } catch (e: Exception) { false }

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
            dashboard.goHome()
        }
    }

    // ====================== PAQUETES ======================
    private fun registerPackageReceiver() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                refreshPinned()
                dashboard.refreshNetApps()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        packageReceiver = receiver
    }

    // ====================== APPS FIJADAS ======================
    private fun setupPinnedApps() {
        pinnedAdapter = PinnedAdapter(
            context = this,
            onFolder = { name ->
                sound.play(R.raw.touch)
                FolderUi.open(this, name, { cn -> appLauncher.launchComponent(cn) }, { refreshPinned() })
            },
            onLaunch = { cn: ComponentName ->
                sound.play(R.raw.touch)
                if (UiPrefs.simpleMode(this)) {
                    try { tts.speak(packageManager.getActivityInfo(cn, 0).loadLabel(packageManager).toString()) } catch (_: Exception) {}
                }
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
        // Elegir las apps de inicio con una lista: toca el aviso o mantén presionada esa zona
        val pick = { sound.play(R.raw.touch); HomePicker.show(this) { refreshPinned() } }
        findViewById<View>(R.id.tv_pinned_hint).setOnClickListener { pick() }
        findViewById<View>(R.id.pinned_area).setOnLongClickListener { pick(); true }
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
        val p = ThemePalette.current(this)
        Log.d("MainActivity", "Aplicando tema: ${p.key}")
        try {
            // Texto oscuro u claro según el fondo de pantalla real; "noche" siempre claro
            // Temas oscuros: texto claro del propio tema. Claros: depende del fondo de pantalla real.
            val darkText = !p.dark && wallpaperPrefersDarkText()
            val fg = if (darkText || p.dark) p.text else 0xFFFFFFFF.toInt()
            val fg2 = if (darkText || p.dark) p.textSecondary else 0xCCFFFFFF.toInt()

            // Velo y dock casi transparentes: dejan ver el fondo de pantalla
            val baseScrim = if (darkText || p.dark) p.scrim else 0x33000000
            val custom = UiPrefs.scrimPercent(this)
            val scrim = if (custom >= 0) ((custom * 255 / 100) shl 24) or (baseScrim and 0x00FFFFFF) else baseScrim
            val dock = if (darkText || p.dark) p.dock else 0x73000000
            findViewById<View>(R.id.scrim).setBackgroundColor(scrim)
            val dockView = findViewById<View>(R.id.dock)
            if (Fx.enabled(this)) {
                dockView.backgroundTintList = null
                dockView.background = Fx.glass(this, dock, 30f, !darkText)
            } else {
                dockView.setBackgroundResource(R.drawable.dock_bg)
                dockView.backgroundTintList = ColorStateList.valueOf(dock)
            }
            val ringTint = ColorStateList.valueOf(p.accent)
            findViewById<ImageView>(R.id.orb_ring_1).imageTintList = ringTint
            findViewById<ImageView>(R.id.orb_ring_2).imageTintList = ringTint
            findViewById<VoiceFxView>(R.id.voice_fx).color = p.accent

            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = darkText
                isAppearanceLightNavigationBars = darkText
            }

            listOf(R.id.tv_time, R.id.tv_ampm, R.id.greeting_text,
                R.id.lbl_call, R.id.lbl_message, R.id.lbl_contacts, R.id.lbl_apps
            ).forEach { id ->
                tv(id)?.apply {
                    setTextColor(fg)
                    if (darkText || p.dark) setShadowLayer(0f, 0f, 0f, 0)
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
            val cardColor = when {
                p.dark -> (p.card and 0x00FFFFFF) or 0xA6000000.toInt()
                darkText -> 0x8CFFFFFF.toInt()
                else -> 0x59000000
            }
            if (::dashboard.isInitialized) dashboard.restyle(fg, fg2, cardColor, p.accent, !darkText && !p.dark)
            liveBg.setColors(p.accent, p.accent2, ColorUtils.blendARGB(p.accent, p.accent2, 0.5f))
            liveBg.mode = UiPrefs.bgEffect(this)
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
        applyVoiceStyle(px)
        val labels = if (UiPrefs.showLabels(this)) View.VISIBLE else View.GONE
        listOf(R.id.lbl_call, R.id.lbl_message, R.id.lbl_contacts, R.id.lbl_apps)
            .forEach { tv(it)?.visibility = labels }
        findViewById<View>(R.id.swipe_handle).visibility =
            if (UiPrefs.swipeUp(this)) View.VISIBLE else View.INVISIBLE
    }

    /** Orbe, ondas, barras o anillo según el ajuste. El orbe sigue ahí (invisible) como botón. */
    private fun applyVoiceStyle(orbPx: Int) {
        val s = UiPrefs.voiceStyle(this)
        val fx = findViewById<VoiceFxView>(R.id.voice_fx)
        fx.style = s
        val wide = s == VoiceFxView.STYLE_WAVES || s == VoiceFxView.STYLE_BARS
        val w = if (wide) (orbPx * 1.4f).toInt() else orbPx
        if (fx.layoutParams.width != w || fx.layoutParams.height != orbPx) {
            fx.layoutParams = fx.layoutParams.also { it.width = w; it.height = orbPx }
        }
        fx.visibility = if (s == 0) View.GONE else View.VISIBLE
        orbView.imageAlpha = if (s == 0) 255 else 0
        val rv = if (s == 0) View.VISIBLE else View.GONE
        findViewById<View>(R.id.orb_ring_1).visibility = rv
        findViewById<View>(R.id.orb_ring_2).visibility = rv
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

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (::dashboard.isInitialized) dashboard.widgets.onActivityResult(requestCode, resultCode, data)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        dashboard.onPermissionResult(requestCode)
        if (requestCode == 203) refreshHomeFavs()
        if (requestCode in 204..207) dashboard.widgets.wellness.onPermissionResult(requestCode)
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
        dashboard.applyPagePrefs()
        dashboard.onResume()
        dock.refresh()
        refreshHomeFavs()
        syncWakeWord()
        syncSafety()
        OasisNotificationListener.onChange = { runOnUiThread { if (::pinnedAdapter.isInitialized) pinnedAdapter.notifyDataSetChanged() } }
        liveBg.start()
        if (UiPrefs.tilt(this) && Fx.enabled(this)) {
            val d = resources.displayMetrics.density
            tilt = TiltParallax(this) { x, y ->
                liveBg.translationX = -x * 26f * d
                liveBg.translationY = -y * 26f * d
                findViewById<View>(R.id.orb_holder).apply { translationX = x * 10f * d; translationY = y * 8f * d }
            }.also { it.start() }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::dashboard.isInitialized) dashboard.onPause()
        wake?.stop()
        fall?.stop()
        OasisNotificationListener.onChange = null
        liveBg.stop()
        tilt?.stop(); tilt = null
    }

    override fun onDestroy() {
        super.onDestroy()
        clockHandler.removeCallbacks(clockRunnable)
        if (::assistant.isInitialized) assistant.shutdown()
        packageReceiver?.let { try { unregisterReceiver(it) } catch (_: Exception) {} }
        dashboard.destroy()
        if (::readAloud.isInitialized) readAloud.destroy()
        sound.release()
        tts.shutdown()
        voice.destroy()
    }
}
