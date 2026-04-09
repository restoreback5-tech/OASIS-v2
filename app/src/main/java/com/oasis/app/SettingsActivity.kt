package com.oasis.app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var ttsModule: TTSModule

    // LEDs
    private lateinit var ledClock: ImageView
    private lateinit var ledSounds: ImageView
    private lateinit var ledAnimations: ImageView
    private lateinit var ledTheme: ImageView
    private lateinit var ledOverlay: ImageView
    private lateinit var ledTtsSpeed: ImageView
    private lateinit var ledTtsPitch: ImageView
    private lateinit var ledHideClock: ImageView
    private lateinit var hideClockText: TextView

    // Textos (usando los TextView que ya existen en el layout)
    private lateinit var clockText: TextView
    private lateinit var soundsText: TextView
    private lateinit var animationsText: TextView
    private lateinit var themeText: TextView
    private lateinit var overlayText: TextView

    // SeekBars y valores
    private lateinit var seekSpeed: SeekBar
    private lateinit var seekPitch: SeekBar
    private lateinit var speedValueText: TextView
    private lateinit var pitchValueText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)
        ttsModule = TTSModule(this)

        // Inicializar LEDs
        ledClock = findViewById(R.id.led_clock_format)
        ledSounds = findViewById(R.id.led_sounds)
        ledAnimations = findViewById(R.id.led_animations)
        ledTheme = findViewById(R.id.led_theme)
        ledOverlay = findViewById(R.id.led_overlay)
        ledTtsSpeed = findViewById(R.id.led_tts_speed)
        ledTtsPitch = findViewById(R.id.led_tts_pitch)

        // Inicializar Textos (los que ya existen en el layout)
        clockText = findViewById(R.id.text_clock_format)
        soundsText = findViewById(R.id.text_sounds)
        animationsText = findViewById(R.id.text_animations)
        themeText = findViewById(R.id.text_theme)
        overlayText = findViewById(R.id.text_overlay)

        // Inicializar SeekBars y textos de valor
        seekSpeed = findViewById(R.id.seekbar_tts_speed)
        seekPitch = findViewById(R.id.seekbar_tts_pitch)
        speedValueText = findViewById(R.id.text_tts_speed_value)
        pitchValueText = findViewById(R.id.text_tts_pitch_value)

        // Botón de regreso
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Configurar cada ajuste
        setupClockFormat()
        setupSounds()
        setupAnimations()
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
        }
    }

    private fun updateClockUI(is24Hour: Boolean) {
        val text = if (is24Hour) "Formato de reloj: 24h" else "Formato de reloj: 12h"
        clockText.text = text
        ledClock.setColorFilter(if (is24Hour) 0xFF39FF14.toInt() else 0xFF888888.toInt())
    }

    private fun setupSounds() {
        val soundsEnabled = prefs.getBoolean("sounds_enabled", true)
        updateSoundsUI(soundsEnabled)
        ledSounds.setOnClickListener {
            val newValue = !prefs.getBoolean("sounds_enabled", true)
            prefs.edit().putBoolean("sounds_enabled", newValue).apply()
            updateSoundsUI(newValue)
            if (newValue) {
                val sound = SoundModule(this)
                sound.play(R.raw.touch)
                sound.release()
            }
        }
    }

    private fun updateSoundsUI(enabled: Boolean) {
        val text = if (enabled) "Sonidos: Activados" else "Sonidos: Desactivados"
        soundsText.text = text
        ledSounds.setColorFilter(if (enabled) 0xFF39FF14.toInt() else 0xFF888888.toInt())
    }

    private fun setupAnimations() {
        val animationsEnabled = prefs.getBoolean("animations_enabled", true)
        updateAnimationsUI(animationsEnabled)
        ledAnimations.setOnClickListener {
            val newValue = !prefs.getBoolean("animations_enabled", true)
            prefs.edit().putBoolean("animations_enabled", newValue).apply()
            updateAnimationsUI(newValue)
        }
    }

    private fun updateAnimationsUI(enabled: Boolean) {
        val text = if (enabled) "Animaciones: Activadas" else "Animaciones: Desactivadas"
        animationsText.text = text
        ledAnimations.setColorFilter(if (enabled) 0xFF39FF14.toInt() else 0xFF888888.toInt())
    }

    private fun setupTheme() {
        val themes = arrayOf("Amanecer", "Caribe", "Oscuro")
        val themeKeys = arrayOf("amanecer", "caribe", "oscuro")
        val currentTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val currentIndex = themeKeys.indexOf(currentTheme).coerceAtLeast(0)
        themeText.text = "Tema: ${themes[currentIndex]}"
        ledTheme.setColorFilter(0xFF39FF14.toInt())
        
        ledTheme.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Seleccionar tema")
                .setItems(themes) { _, which ->
                    val newTheme = themeKeys[which]
                    prefs.edit().putString("selected_theme", newTheme).apply()
                    themeText.text = "Tema: ${themes[which]}"
                    Toast.makeText(this, "Tema cambiado a ${themes[which]}. Reinicia la app para ver los cambios.", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun setupOverlay() {
        val overlayEnabled = hasOverlayPermission()
        updateOverlayUI(overlayEnabled)
        ledOverlay.setOnClickListener {
            if (hasOverlayPermission()) {
                Toast.makeText(this, "Desactiva el permiso de superposición desde Ajustes del sistema", Toast.LENGTH_LONG).show()
            } else {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))
                startActivity(intent)
            }
        }
    }

    private fun updateOverlayUI(enabled: Boolean) {
        val text = if (enabled) "Asistente flotante: Activado" else "Asistente flotante: Desactivado"
        overlayText.text = text
        ledOverlay.setColorFilter(if (enabled) 0xFF39FF14.toInt() else 0xFF888888.toInt())
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
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
                    ttsModule.speak("Velocidad ajustada")
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
        ledTtsSpeed.setColorFilter(0xFF39FF14.toInt())
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
                    ttsModule.speak("Tono ajustado")
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
        ledTtsPitch.setColorFilter(0xFF39FF14.toInt())
    }

   private fun setupHideClock() {
    val hideClock = prefs.getBoolean("hide_clock", false)
    updateHideClockUI(hideClock)
    ledHideClock.setOnClickListener {
        val newValue = !prefs.getBoolean("hide_clock", false)
        prefs.edit().putBoolean("hide_clock", newValue).apply()
        updateHideClockUI(newValue)
    }
}

private fun updateHideClockUI(hideClock: Boolean) {
    val text = if (hideClock) "Ocultar reloj: Sí" else "Ocultar reloj: No"
    hideClockText.text = text
    ledHideClock.setColorFilter(if (hideClock) 0xFF39FF14.toInt() else 0xFF888888.toInt())
}

    override fun onResume() {
        super.onResume()
        updateOverlayUI(hasOverlayPermission())
    }

    override fun onDestroy() {
        super.onDestroy()
        ttsModule.shutdown()
    }
}
