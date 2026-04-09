package com.oasis.app

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var ttsModule: TTSModule
    private lateinit var sound: SoundModule
    private var overlayServiceRunning = false

    // LEDs
    private lateinit var ledClock: ImageView
    private lateinit var ledSounds: ImageView
    private lateinit var ledTtsEnabled: ImageView
    private lateinit var ledOverlay: ImageView
    private lateinit var ledTtsSpeed: ImageView
    private lateinit var ledTtsPitch: ImageView
    private lateinit var ledHideClock: ImageView
    private lateinit var hideClockText: TextView

    // Tema
    private lateinit var themeSol: View
    private lateinit var themeLuna: View
    private lateinit var themeNubes: View
    private lateinit var indicatorSol: ImageView
    private lateinit var indicatorLuna: ImageView
    private lateinit var indicatorNubes: ImageView
    private lateinit var themeText: TextView

    // Textos
    private lateinit var clockText: TextView
    private lateinit var soundsText: TextView
    private lateinit var ttsEnabledText: TextView
    private lateinit var overlayText: TextView

    // SeekBars
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekPitch: SeekBar
    private lateinit var speedValueText: TextView
    private lateinit var pitchValueText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)
        ttsModule = TTSModule(this)
        sound = SoundModule(this)

        // Inicializar vistas
        ledClock = findViewById(R.id.led_clock_format)
        ledSounds = findViewById(R.id.led_sounds)
        ledTtsEnabled = findViewById(R.id.led_tts_enabled)
        ledOverlay = findViewById(R.id.led_overlay)
        ledTtsSpeed = findViewById(R.id.led_tts_speed)
        ledTtsPitch = findViewById(R.id.led_tts_pitch)
        ledHideClock = findViewById(R.id.led_hide_clock)
        hideClockText = findViewById(R.id.text_hide_clock)

        clockText = findViewById(R.id.text_clock_format)
        soundsText = findViewById(R.id.text_sounds)
        ttsEnabledText = findViewById(R.id.text_tts_enabled)
        overlayText = findViewById(R.id.text_overlay)

        seekSpeed = findViewById(R.id.seekbar_tts_speed)
        seekPitch = findViewById(R.id.seekbar_tts_pitch)
        speedValueText = findViewById(R.id.text_tts_speed_value)
        pitchValueText = findViewById(R.id.text_tts_pitch_value)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener { finish() }

        setupClockFormat()
        setupSounds()
        setupTtsEnabled()
        setupTheme()
        setupOverlay()
        setupTtsSpeed()
        setupTtsPitch()
        setupHideClock()
    }

    private fun setupClockFormat() {
        val is24Hour = prefs.getBoolean("clock_24h", true)
        updateClockUI(is24Hour)
        ledClock.setOnClickListener {
            val newValue = !prefs.getBoolean("clock_24h", true)
            prefs.edit().putBoolean("clock_24h", newValue).apply()
            updateClockUI(newValue)
            sound.play(R.raw.check_on)
        }
    }

    private fun updateClockUI(is24Hour: Boolean) {
        val text = if (is24Hour) "Formato de reloj: 24h" else "Formato de reloj: 12h"
        clockText.text = text
        ledClock.setColorFilter(if (is24Hour) ContextCompat.getColor(this, R.color.settings_accent_blue) else 0xFF888888.toInt())
    }

    private fun setupSounds() {
        val soundsEnabled = prefs.getBoolean("sounds_enabled", true)
        updateSoundsUI(soundsEnabled)
        ledSounds.setOnClickListener {
            val newValue = !prefs.getBoolean("sounds_enabled", true)
            prefs.edit().putBoolean("sounds_enabled", newValue).apply()
            updateSoundsUI(newValue)
            sound.play(R.raw.check_on)
            if (newValue) {
                // Probar sonido
                sound.play(R.raw.touch)
            }
        }
    }

    private fun updateSoundsUI(enabled: Boolean) {
        val text = if (enabled) "Sonidos: Activados" else "Sonidos: Desactivados"
        soundsText.text = text
        ledSounds.setColorFilter(if (enabled) ContextCompat.getColor(this, R.color.settings_accent_blue) else 0xFF888888.toInt())
    }

    private fun setupTtsEnabled() {
        val ttsEnabled = prefs.getBoolean("tts_enabled", true)
        updateTtsEnabledUI(ttsEnabled)
        ledTtsEnabled.setOnClickListener {
            val newValue = !prefs.getBoolean("tts_enabled", true)
            prefs.edit().putBoolean("tts_enabled", newValue).apply()
            updateTtsEnabledUI(newValue)
            sound.play(R.raw.check_on)
            if (newValue) ttsModule.speak("Voz activada")
        }
    }

    private fun updateTtsEnabledUI(enabled: Boolean) {
        val text = if (enabled) "Voz asistente: Activada" else "Voz asistente: Desactivada"
        ttsEnabledText.text = text
        ledTtsEnabled.setColorFilter(if (enabled) ContextCompat.getColor(this, R.color.settings_accent_blue) else 0xFF888888.toInt())
    }

    private fun setupTheme() {
        themeSol = findViewById(R.id.theme_sol)
        themeLuna = findViewById(R.id.theme_luna)
        themeNubes = findViewById(R.id.theme_nubes)
        indicatorSol = findViewById(R.id.indicator_sol)
        indicatorLuna = findViewById(R.id.indicator_luna)
        indicatorNubes = findViewById(R.id.indicator_nubes)
        themeText = findViewById(R.id.text_theme)

        val themes = mapOf("amanecer" to themeSol, "caribe" to themeLuna, "oscuro" to themeNubes)
        val indicators = mapOf("amanecer" to indicatorSol, "caribe" to indicatorLuna, "oscuro" to indicatorNubes)

        val currentTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        updateThemeUI(currentTheme, themes, indicators)

        themeSol.setOnClickListener { setTheme("amanecer", themes, indicators) }
        themeLuna.setOnClickListener { setTheme("caribe", themes, indicators) }
        themeNubes.setOnClickListener { setTheme("oscuro", themes, indicators) }
    }

    private fun setTheme(themeKey: String, themes: Map<String, View>, indicators: Map<String, ImageView>) {
        prefs.edit().putString("selected_theme", themeKey).apply()
        updateThemeUI(themeKey, themes, indicators)
        themeText.text = when (themeKey) {
            "amanecer" -> "Tema: Amanecer"
            "caribe" -> "Tema: Caribe"
            else -> "Tema: Oscuro"
        }
        Toast.makeText(this, "Tema cambiado a ${themeText.text}. Reinicia la app para ver los cambios.", Toast.LENGTH_SHORT).show()
        sound.play(R.raw.check_on) // opcional: sonido al cambiar tema
    }

    private fun updateThemeUI(themeKey: String, themes: Map<String, View>, indicators: Map<String, ImageView>) {
        indicators.values.forEach { it.visibility = View.GONE }
        indicators[themeKey]?.visibility = View.VISIBLE
    }

    private fun setupOverlay() {
        val overlayEnabled = prefs.getBoolean("overlay_enabled", false)
        updateOverlayUI(overlayEnabled)
        ledOverlay.setOnClickListener {
            val newState = !prefs.getBoolean("overlay_enabled", false)
            prefs.edit().putBoolean("overlay_enabled", newState).apply()
            updateOverlayUI(newState)
            toggleOverlayService(newState)
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
        val text = if (enabled) "Asistente flotante: Activado" else "Asistente flotante: Desactivado"
        overlayText.text = text
        ledOverlay.setColorFilter(if (enabled) ContextCompat.getColor(this, R.color.settings_accent_blue) else 0xFF888888.toInt())
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
        val progress = ((savedSpeed - 0.5f) / 1.5f * 100).toInt().coerceIn(0, 100)
        seekSpeed.progress = progress
        updateSpeedText(progress)

        seekSpeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = 0.5f + (progress / 100f) * 1.5f
                    prefs.edit().putFloat("voice_speed", speed).apply()
                    updateSpeedText(progress)
                    ttsModule.updateSpeechSettings()
                    if (prefs.getBoolean("tts_enabled", true)) ttsModule.speak("Velocidad ajustada")
                    sound.play(R.raw.deslizar)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updateSpeedText(progress: Int) {
        val speed = 0.5f + (progress / 100f) * 1.5f
        val speedText = when {
            speed < 0.8f -> "Lenta"
            speed < 1.2f -> "Normal"
            else -> "Rápida"
        }
        speedValueText.text = "$speedText (${String.format("%.1f", speed)}x)"
        ledTtsSpeed.setColorFilter(ContextCompat.getColor(this, R.color.settings_accent_blue))
    }

    private fun setupTtsPitch() {
        val savedPitch = prefs.getFloat("voice_pitch", 1.0f)
        val progress = ((savedPitch - 0.5f) / 1.0f * 100).toInt().coerceIn(0, 100)
        seekPitch.progress = progress
        updatePitchText(progress)

        seekPitch.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val pitch = 0.5f + (progress / 100f) * 1.0f
                    prefs.edit().putFloat("voice_pitch", pitch).apply()
                    updatePitchText(progress)
                    ttsModule.updateSpeechSettings()
                    if (prefs.getBoolean("tts_enabled", true)) ttsModule.speak("Tono ajustado")
                    sound.play(R.raw.deslizar)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun updatePitchText(progress: Int) {
        val pitch = 0.5f + (progress / 100f) * 1.0f
        val pitchText = when {
            pitch < 0.8f -> "Grave"
            pitch < 1.2f -> "Normal"
            else -> "Agudo"
        }
        pitchValueText.text = "$pitchText (${String.format("%.1f", pitch)}x)"
        ledTtsPitch.setColorFilter(ContextCompat.getColor(this, R.color.settings_accent_blue))
    }

    private fun setupHideClock() {
        val hideClock = prefs.getBoolean("hide_clock", false)
        updateHideClockUI(hideClock)
        ledHideClock.setOnClickListener {
            val newValue = !prefs.getBoolean("hide_clock", false)
            prefs.edit().putBoolean("hide_clock", newValue).apply()
            updateHideClockUI(newValue)
            sound.play(R.raw.check_on)
        }
    }

    private fun updateHideClockUI(hideClock: Boolean) {
        val text = if (hideClock) "Ocultar reloj: Sí" else "Ocultar reloj: No"
        hideClockText.text = text
        ledHideClock.setColorFilter(if (hideClock) ContextCompat.getColor(this, R.color.settings_accent_blue) else 0xFF888888.toInt())
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
