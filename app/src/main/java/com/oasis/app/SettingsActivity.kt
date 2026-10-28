package com.oasis.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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

    // Launcher moderno (reemplaza startActivityForResult)
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Al volver de la pantalla de permiso de superposición
        val enabled = prefs.getBoolean("overlay_enabled", false)
        if (enabled && hasOverlayPermission()) {
            toggleOverlayService(true)
        } else if (enabled && !hasOverlayPermission()) {
            // El usuario activó el switch pero denegó el permiso: revertir
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

        // Configurar cada ajuste
        setupSounds()
        setupTtsEnabled()
        setupTheme()
        setupOverlay()
        setupTtsSpeed()
        setupTtsPitch()
        setupHideClock()
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
        // Ya no es necesario reiniciar: MainActivity.onResume() aplica el tema
        Toast.makeText(this, "Tema cambiado", Toast.LENGTH_SHORT).show()
        sound.play(R.raw.check_on)
    }

    private fun updateThemeUI(themeKey: String, indicators: Map<String, ImageView>) {
        indicators.values.forEach { it.visibility = View.GONE }
        indicators[themeKey]?.visibility = View.VISIBLE
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
        // Sincronización idempotente: no dependemos de un flag local
        val overlayEnabled = prefs.getBoolean("overlay_enabled", false)
        if (overlayEnabled && hasOverlayPermission()) {
            toggleOverlayService(true)
        } else if (!overlayEnabled) {
            toggleOverlayService(false)
        }
        updateOverlayUI(overlayEnabled)
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsModule.shutdown()
        sound.release()
    }
}
