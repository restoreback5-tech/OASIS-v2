package com.oasis.app

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.widget.ImageViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var ttsModule: TTSModule
    private lateinit var sound: SoundModule

    // Switches
    private lateinit var switchSounds: SwitchMaterial
    private lateinit var switchTtsEnabled: SwitchMaterial
    private lateinit var switchOverlay: SwitchMaterial
    private lateinit var switchHideClock: SwitchMaterial

    // Sliders
    private lateinit var sliderSpeed: Slider
    private lateinit var sliderPitch: Slider

    // Textos
    private lateinit var soundsText: TextView
    private lateinit var ttsEnabledText: TextView
    private lateinit var overlayText: TextView
    private lateinit var hideClockText: TextView
    private lateinit var speedValueText: TextView
    private lateinit var pitchValueText: TextView
    private lateinit var themeText: TextView

    // Tema
    private lateinit var themeSol: View
    private lateinit var themeLuna: View
    private lateinit var themeNubes: View
    private lateinit var indicatorSol: ImageView
    private lateinit var indicatorLuna: ImageView
    private lateinit var indicatorNubes: ImageView

    // Launcher predeterminado
    private lateinit var textLauncher: TextView
    private lateinit var btnSetLauncher: MaterialButton
    private var palette: ThemePalette? = null
    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { updateLauncherUI() }

    // Launcher moderno
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val enabled = prefs.getBoolean("overlay_enabled", false)
        if (enabled && hasOverlayPermission()) {
            toggleOverlayService(true)
        } else if (enabled && !hasOverlayPermission()) {
            prefs.edit().putBoolean("overlay_enabled", false).apply()
            switchOverlay.isChecked = false
            updateOverlayUI(false)
            Toast.makeText(this, "Permiso de superposición necesario para el flotante", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)
        ttsModule = TTSModule(this)
        sound = SoundModule(this)

        // Switches
        switchSounds = findViewById(R.id.led_sounds)
        switchTtsEnabled = findViewById(R.id.led_tts_enabled)
        switchOverlay = findViewById(R.id.led_overlay)
        switchHideClock = findViewById(R.id.led_hide_clock)

        // Sliders
        sliderSpeed = findViewById(R.id.seekbar_tts_speed)
        sliderPitch = findViewById(R.id.seekbar_tts_pitch)

        // Textos
        soundsText = findViewById(R.id.text_sounds)
        ttsEnabledText = findViewById(R.id.text_tts_enabled)
        overlayText = findViewById(R.id.text_overlay)
        hideClockText = findViewById(R.id.text_hide_clock)
        speedValueText = findViewById(R.id.text_tts_speed_value)
        pitchValueText = findViewById(R.id.text_tts_pitch_value)
        themeText = findViewById(R.id.text_theme)

        // Tema
        themeSol = findViewById(R.id.theme_sol)
        themeLuna = findViewById(R.id.theme_luna)
        themeNubes = findViewById(R.id.theme_nubes)
        indicatorSol = findViewById(R.id.indicator_sol)
        indicatorLuna = findViewById(R.id.indicator_luna)
        indicatorNubes = findViewById(R.id.indicator_nubes)

        // Botón atrás
        findViewById<ImageView?>(R.id.btn_back)?.setOnClickListener { finish() }

        // Launcher predeterminado
        textLauncher = findViewById(R.id.text_launcher)
        btnSetLauncher = findViewById(R.id.btn_set_launcher)
        btnSetLauncher.setOnClickListener {
            sound.play(R.raw.touch)
            LauncherHelper.requestDefaultLauncher(this, roleLauncher)
        }

        // Configurar ajustes
        setupSounds()
        setupTtsEnabled()
        setupTheme()
        setupOverlay()
        setupTtsSpeed()
        setupTtsPitch()
        setupHideClock()
        applyPalette()
        updateLauncherUI()
    }

    // ====================== LAUNCHER PREDETERMINADO ======================
    private fun updateLauncherUI() {
        val isDefault = LauncherHelper.isDefaultLauncher(this)
        textLauncher.text = if (isDefault) "OASIS es tu pantalla de inicio"
                            else "Usar OASIS como pantalla de inicio"
        btnSetLauncher.visibility = if (isDefault) View.GONE else View.VISIBLE
    }

    // ====================== PALETA DEL TEMA ======================
    /** Pinta fondo, tarjetas, textos, iconos, sliders e interruptores con el tema elegido. */
    private fun applyPalette() {
        val p = ThemePalette.from(this, prefs.getString("selected_theme", "amanecer"))
        palette = p

        findViewById<View>(R.id.settings_root).setBackgroundColor(p.background)
        window.statusBarColor = p.background
        window.navigationBarColor = p.background
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = p.lightSystemBarIcons
            isAppearanceLightNavigationBars = p.lightSystemBarIcons
        }

        paintTree(findViewById(R.id.settings_root), p)

        listOf(R.id.btn_back, R.id.icon_sol, R.id.icon_nubes, R.id.icon_luna).forEach {
            ImageViewCompat.setImageTintList(findViewById<ImageView>(it), ColorStateList.valueOf(p.text))
        }
        btnSetLauncher.backgroundTintList = ColorStateList.valueOf(p.accent)

        val accent = ColorStateList.valueOf(p.accent)
        val trackOff = ColorStateList.valueOf(0x338E8E93)
        listOf(sliderSpeed, sliderPitch).forEach {
            it.thumbTintList = accent
            it.trackActiveTintList = accent
            it.trackInactiveTintList = trackOff
            it.haloTintList = ColorStateList.valueOf((p.accent and 0x00FFFFFF) or 0x33000000)
        }
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val thumb = ColorStateList(states, intArrayOf(0xFFFFFFFF.toInt(), 0xFFF4F4F5.toInt()))
        val track = ColorStateList(states, intArrayOf(p.accent, 0xFFB0B0B5.toInt()))
        listOf(switchSounds, switchTtsEnabled, switchOverlay, switchHideClock).forEach {
            it.thumbTintList = thumb
            it.trackTintList = track
        }
    }

    private fun paintTree(v: View, p: ThemePalette) {
        when {
            v is MaterialCardView -> v.setCardBackgroundColor(p.card)
            v is MaterialButton -> {}
            v is TextView -> v.setTextColor(if (v.tag == "secondary") p.textSecondary else p.text)
        }
        if (v.tag == "divider") v.setBackgroundColor(if (p.key == "noche") 0x33FFFFFF else 0x1F000000)
        if (v is ViewGroup) for (i in 0 until v.childCount) paintTree(v.getChildAt(i), p)
    }

    // ====================== SONIDOS ======================
    private fun setupSounds() {
        val enabled = prefs.getBoolean("sounds_enabled", true)
        switchSounds.isChecked = enabled
        updateSoundsUI(enabled)

        switchSounds.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("sounds_enabled", isChecked).apply()
            updateSoundsUI(isChecked)
            sound.play(R.raw.check_on)
            if (isChecked) sound.play(R.raw.touch)
        }
    }

    private fun updateSoundsUI(enabled: Boolean) {
        soundsText.text = if (enabled) "Sonidos UI: Activados" else "Sonidos UI: Desactivados"
    }

    // ====================== TTS ON/OFF ======================
    private fun setupTtsEnabled() {
        val enabled = prefs.getBoolean("tts_enabled", true)
        switchTtsEnabled.isChecked = enabled
        updateTtsEnabledUI(enabled)

        switchTtsEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("tts_enabled", isChecked).apply()
            updateTtsEnabledUI(isChecked)
            sound.play(R.raw.check_on)
            if (isChecked) ttsModule.speak("Voz activada")
        }
    }

    private fun updateTtsEnabledUI(enabled: Boolean) {
        ttsEnabledText.text = if (enabled) "Voz asistente: Activada" else "Voz asistente: Desactivada"
    }

    // ====================== TEMA ======================
    private fun setupTheme() {
        val indicators = mapOf(
            "amanecer" to indicatorSol,
            "caribe" to indicatorNubes,
            "noche" to indicatorLuna
        )
        val current = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        updateThemeUI(current, indicators)
        themeText.text = labelForTheme(current)

        themeSol.setOnClickListener { setTheme("amanecer", indicators) }
        themeNubes.setOnClickListener { setTheme("caribe", indicators) }
        themeLuna.setOnClickListener { setTheme("noche", indicators) }
    }

    private fun setTheme(themeKey: String, indicators: Map<String, ImageView>) {
        prefs.edit().putString("selected_theme", themeKey).apply()
        updateThemeUI(themeKey, indicators)
        themeText.text = labelForTheme(themeKey)
        applyPalette() // se ve el cambio al instante
        sound.play(R.raw.check_on)
    }

    private fun updateThemeUI(themeKey: String, indicators: Map<String, ImageView>) {
        // Un solo origen de verdad: "selected" controla el fondo y el check de cada opción
        val options = mapOf("amanecer" to themeSol, "caribe" to themeNubes, "noche" to themeLuna)
        options.forEach { (key, view) -> view.isSelected = key == themeKey }
        indicators.forEach { (key, check) ->
            check.visibility = if (key == themeKey) View.VISIBLE else View.GONE
        }
    }

    private fun labelForTheme(key: String): String = when (key) {
        "amanecer" -> "Tema: Amanecer"
        "caribe" -> "Tema: Caribe"
        else -> "Tema: Noche"
    }

    // ====================== OVERLAY ======================
    private fun setupOverlay() {
        val enabled = prefs.getBoolean("overlay_enabled", false)
        switchOverlay.isChecked = enabled
        updateOverlayUI(enabled)

        switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("overlay_enabled", isChecked).apply()
            updateOverlayUI(isChecked)
            if (isChecked) {
                if (hasOverlayPermission()) toggleOverlayService(true)
                else requestOverlayPermission()
            } else {
                toggleOverlayService(false)
            }
            sound.play(R.raw.check_on)
        }
    }

    private fun toggleOverlayService(start: Boolean) {
        val intent = Intent(this, OverlayService::class.java)
        try {
            if (start) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
            } else {
                stopService(intent)
            }
        } catch (e: Exception) {
            Log.w("SettingsActivity", "toggleOverlayService falló: ${e.message}")
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun updateOverlayUI(enabled: Boolean) {
        overlayText.text = if (enabled) "Asistente flotante: Activado" else "Asistente flotante: Desactivado"
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    }

    // ====================== VELOCIDAD ======================
    private fun setupTtsSpeed() {
        val saved = prefs.getFloat("voice_speed", 1.0f)
        sliderSpeed.value = saved
        updateSpeedText(saved)

        sliderSpeed.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.edit().putFloat("voice_speed", value).apply()
                updateSpeedText(value)
                ttsModule.updateSpeechSettings()
                if (prefs.getBoolean("tts_enabled", true)) ttsModule.speak("Velocidad ajustada")
                sound.play(R.raw.deslizar)
            }
        }
    }

    private fun updateSpeedText(speed: Float) {
        val label = when {
            speed < 0.8f -> "Lenta"
            speed < 1.2f -> "Normal"
            else -> "Rápida"
        }
        speedValueText.text = "$label (${String.format("%.1f", speed)}x)"
    }

    // ====================== TONO ======================
    private fun setupTtsPitch() {
        val saved = prefs.getFloat("voice_pitch", 1.0f)
        sliderPitch.value = saved
        updatePitchText(saved)

        sliderPitch.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.edit().putFloat("voice_pitch", value).apply()
                updatePitchText(value)
                ttsModule.updateSpeechSettings()
                if (prefs.getBoolean("tts_enabled", true)) ttsModule.speak("Tono ajustado")
                sound.play(R.raw.deslizar)
            }
        }
    }

    private fun updatePitchText(pitch: Float) {
        val label = when {
            pitch < 0.8f -> "Grave"
            pitch < 1.2f -> "Normal"
            else -> "Agudo"
        }
        pitchValueText.text = "$label (${String.format("%.1f", pitch)}x)"
    }

    // ====================== OCULTAR RELOJ ======================
    private fun setupHideClock() {
        val hide = prefs.getBoolean("hide_clock", false)
        switchHideClock.isChecked = hide
        updateHideClockUI(hide)

        switchHideClock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("hide_clock", isChecked).apply()
            updateHideClockUI(isChecked)
            sound.play(R.raw.check_on)
        }
    }

    private fun updateHideClockUI(hideClock: Boolean) {
        hideClockText.text = if (hideClock) "Ocultar reloj: Sí" else "Ocultar reloj: No"
    }

    // ====================== CICLO DE VIDA ======================
    override fun onResume() {
        super.onResume()
        val overlayEnabled = prefs.getBoolean("overlay_enabled", false)
        if (overlayEnabled && hasOverlayPermission()) {
            toggleOverlayService(true)
        } else if (!overlayEnabled) {
            toggleOverlayService(false)
        }
        updateOverlayUI(overlayEnabled)
        updateLauncherUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsModule.shutdown()
        sound.release()
    }
}
