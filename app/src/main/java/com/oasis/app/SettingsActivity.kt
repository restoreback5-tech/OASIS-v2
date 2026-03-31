package com.oasis.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var tts: TTSModule
    private lateinit var sound: SoundModule
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tts = TTSModule(this)
        sound = SoundModule(this)
        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            sound.play(R.raw.touch)
            finish()
        }

        val title = findViewById<TextView>(R.id.settings_title)
        title.text = "Ajustes"
        tts.speak("Ajustes de OASIS")

        setupSetting(R.id.btn_clock_format, "Formato de reloj", getClockFormatText(), "Formato de reloj. Actual: " + getClockFormatText()) { toggleClockFormat() }
        setupSetting(R.id.btn_tts_speed, "Velocidad de voz", getTtsSpeedText(), "Velocidad de voz. Actual: " + getTtsSpeedText()) { toggleTtsSpeed() }
        setupSetting(R.id.btn_sounds, "Sonidos", getSoundsText(), "Sonidos de la app. " + getSoundsText()) { toggleSounds() }
        setupSetting(R.id.btn_animations, "Animaciones", getAnimationsText(), "Animaciones de la app. " + getAnimationsText()) { toggleAnimations() }
        setupSetting(R.id.btn_day_night, "Modo Día/Noche", getDayNightModeText(), "Modo de interfaz. Actual: " + getDayNightModeText()) { toggleDayNightMode() }
    }

    private fun setupSetting(btnId: Int, label: String, value: String, ttsText: String, onClick: () -> Unit) {
        val switch = findViewById<SwitchCompat>(btnId)
        switch.isChecked = value == "24h" || value == "Rápida" || value == "Activados" || value == "Activadas" || value == "Día"
        switch.setOnCheckedChangeListener { _, isChecked ->
            sound.play(R.raw.touch)
            tts.speak(ttsText)
            onClick()
        }
    }

    private fun getClockFormatText(): String = if (prefs.getBoolean("clock_24h", true)) "24h" else "12h (AM/PM)"
    private fun toggleClockFormat() { val c = prefs.getBoolean("clock_24h", true); prefs.edit().putBoolean("clock_24h", !c).apply(); tts.speak("Cambiado a " + getClockFormatText()) }

    private fun getTtsSpeedText(): String = when (prefs.getFloat("tts_speed", 1.0f)) { 0.5f -> "Lenta"; 1.5f -> "Rápida"; else -> "Normal" }
    private fun toggleTtsSpeed() { val c = prefs.getFloat("tts_speed", 1.0f); val n = when (c) { 0.5f -> 1.0f; 1.0f -> 1.5f; else -> 0.5f }; prefs.edit().putFloat("tts_speed", n).apply(); tts.speak("Velocidad " + getTtsSpeedText()) }

    private fun getSoundsText(): String = if (prefs.getBoolean("enable_sounds", true)) "Activados" else "Desactivados"
    private fun toggleSounds() { val c = prefs.getBoolean("enable_sounds", true); prefs.edit().putBoolean("enable_sounds", !c).apply(); tts.speak("Sonidos " + if (!c) "activados" else "desactivados") }

    private fun getAnimationsText(): String = if (prefs.getBoolean("enable_animations", true)) "Activadas" else "Desactivadas"
    private fun toggleAnimations() { val c = prefs.getBoolean("enable_animations", true); prefs.edit().putBoolean("enable_animations", !c).apply(); tts.speak("Animaciones " + if (!c) "activadas" else "desactivadas") }

    private fun getDayNightModeText(): String = if (prefs.getBoolean("day_mode", true)) "Día" else "Noche"
    private fun toggleDayNightMode() { val c = prefs.getBoolean("day_mode", true); prefs.edit().putBoolean("day_mode", !c).apply(); tts.speak("Modo " + if (!c) "Día" else "Noche") }

    override fun onDestroy() { super.onDestroy(); tts.shutdown(); sound.release() }
}
