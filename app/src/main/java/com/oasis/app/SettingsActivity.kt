package com.oasis.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var ttsModule: TTSModule
    private lateinit var sound: SoundModule
    private var overlayServiceRunning = false

    // Switches (antes LEDs)
    private lateinit var switchSounds: SwitchMaterial
    private lateinit var switchTtsEnabled: SwitchMaterial
    private lateinit var switchOverlay: SwitchMaterial
    private lateinit var switchHideClock: SwitchMaterial

    // Sliders (antes SeekBars)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)
        ttsModule = TTSModule(this)
        sound = SoundModule(this)

        // Inicializar Switches
        switchSounds = findViewById(R.id.led_sounds)
        switchTtsEnabled = findViewById(R.id.led_tts_enabled)
        switchOverlay = findViewById(R.id.led_overlay)
        switchHideClock = findViewById(R.id.led_hide_clock)

        // Inicializar Sliders
        sliderSpeed = findViewById(R.id.seekbar_tts_speed)
        sliderPitch = findViewById(R.id.seekbar_tts_pitch)

        // Inicializar Textos
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

        // Botón de regreso
        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        // Configurar cada ajuste
        setupSounds()
        setupTtsEnabled()
        setupTheme()
        setupOverlay()
        setupTtsSpeed()
        setupTtsPitch()
        setupHideClock()
    }

    private fun setupSounds() {
        val soundsEnabled = prefs.getBoolean("sounds_enabled", true)
        switchSounds.isChecked = soundsEnabled
        updateSoundsUI(soundsEnabled)

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

    private fun setupTtsEnabled() {
        val ttsEnabled = prefs.getBoolean("tts_enabled", true)
        switchTtsEnabled.isChecked = ttsEnabled
        updateTtsEnabledUI(ttsEnabled)

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

    private fun setupTheme() {
        val indicators = mapOf("amanecer" to indicatorSol, "caribe" to indicatorNubes, "noche" to indicatorLuna)
        val currentTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"

        updateThemeUI(currentTheme, indicators)
        themeText.text = when (currentTheme) {
            "amanecer" -> "Tema: Amanecer"
            "caribe" -> "Tema: Caribe"
            else -> "Tema: Noche"
        }

        themeSol.setOnClickListener { setTheme("amanecer", indicators) }
        themeNubes.setOnClickListener { setTheme("caribe", indicators) }
        themeLuna.setOnClickListener { setTheme("noche", indicators) }
    }

    private fun setTheme(themeKey: String, indicators: Map<String, ImageView>) {
        prefs.edit().putString("selected_theme", themeKey).apply()
        updateThemeUI(themeKey, indicators)
        themeText.text = when (themeKey) {
            "amanecer" -> "Tema: Amanecer"
            "caribe" -> "Tema: Caribe"
            else -> "Tema: Noche"
        }
        Toast.makeText(this, "Tema cambiado. Reinicia la app para ver los cambios.", Toast.LENGTH_SHORT).show()
        sound.play(R.raw.check_on)
    }

    private fun updateThemeUI(themeKey: String, indicators: Map<String, ImageView>) {
        indicators.values.forEach { it.visibility = View.GONE }
        indicators[themeKey]?.visibility = View.VISIBLE
    }

    private fun setupOverlay() {
        val overlayEnabled = prefs.getBoolean("overlay_enabled", false)
        switchOverlay.isChecked = overlayEnabled
        updateOverlayUI(overlayEnabled)

        switchOverlay.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("overlay_enabled", isChecked).apply()
            updateOverlayUI(isChecked)
            toggleOverlayService(isChecked)
            sound.play(R.raw.check_on)
        }
    }

    private fun toggleOverlayService(start: Boolean) {
        val intent = Intent(this, OverlayService::class.java)
        if (start) {
            if (hasOverlayPermission()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
                else startService(intent)
                overlayServiceRunning = true
            } else {
                requestOverlayPermission()
            }
        } else {
            stopService(intent)
            overlayServiceRunning = false
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
    }

    private fun updateOverlayUI(enabled: Boolean) {
        overlayText.text = if (enabled) "Asistente flotante: Activado" else "Asistente flotante: Desactivado"
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (hasOverlayPermission()) {
                val overlayEnabled = prefs.getBoolean("overlay_enabled", false)
                if (overlayEnabled) toggleOverlayService(true)
            } else {
                Toast.makeText(this, "Permiso de superposición necesario para el flotante", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupTtsSpeed() {
        val savedSpeed = prefs.getFloat("voice_speed", 1.0f)
        sliderSpeed.value = savedSpeed
        updateSpeedText(savedSpeed)

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
        val speedText = when {
            speed < 0.8f -> "Lenta"
            speed < 1.2f -> "Normal"
            else -> "Rápida"
        }
        speedValueText.text = "$speedText (${String.format("%.1f", speed)}x)"
    }

    private fun setupTtsPitch() {
        val savedPitch = prefs.getFloat("voice_pitch", 1.0f)
        sliderPitch.value = savedPitch
        updatePitchText(savedPitch)

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
        val pitchText = when {
            pitch < 0.8f -> "Grave"
            pitch < 1.2f -> "Normal"
            else -> "Agudo"
        }
        pitchValueText.text = "$pitchText (${String.format("%.1f", pitch)}x)"
    }

    private fun setupHideClock() {
        val hideClock = prefs.getBoolean("hide_clock", false)
        switchHideClock.isChecked = hideClock
        updateHideClockUI(hideClock)

        switchHideClock.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("hide_clock", isChecked).apply()
            updateHideClockUI(isChecked)
            sound.play(R.raw.check_on)
        }
    }

    private fun updateHideClockUI(hideClock: Boolean) {
        hideClockText.text = if (hideClock) "Ocultar reloj: Sí" else "Ocultar reloj: No"
    }

    override fun onResume() {
        super.onResume()
        val overlayEnabled = prefs.getBoolean("overlay_enabled", false)
        val hasPermission = hasOverlayPermission()
        updateOverlayUI(overlayEnabled)
        if (overlayEnabled && hasPermission && !overlayServiceRunning) {
            toggleOverlayService(true)
        } else if (!overlayEnabled && overlayServiceRunning) {
            toggleOverlayService(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsModule.shutdown()
        sound.release()
    }

    companion object {
        private const val REQUEST_OVERLAY_PERMISSION = 1001
    }
}
