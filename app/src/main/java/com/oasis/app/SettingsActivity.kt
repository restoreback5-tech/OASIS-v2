package com.oasis.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var tts: TTSModule
    private lateinit var sound: SoundModule
    private lateinit var prefs: SharedPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Inicializar módulos
        tts = TTSModule(this)
        sound = SoundModule(this)
        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        // Botón volver
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            sound.play(R.raw.touch)
            finish()
        }

        // Título
        val title = findViewById<TextView>(R.id.settings_title)
        title.text = "Ajustes"
        tts.speak("Ajustes de OASIS")

        // Ajuste 1: Formato de reloj
        setupSetting(
            R.id.btn_clock_format,
            "Formato de reloj",
            getClockFormatText(),
            "Formato de reloj. Actual: " + getClockFormatText()
        ) { toggleClockFormat() }

        // Ajuste 2: Velocidad de voz
        setupSetting(
            R.id.btn_tts_speed,
            "Velocidad de voz",
            getTtsSpeedText(),            "Velocidad de voz. Actual: " + getTtsSpeedText()
        ) { toggleTtsSpeed() }

        // Ajuste 3: Sonidos
        setupSetting(
            R.id.btn_sounds,
            "Sonidos",
            getSoundsText(),
            "Sonidos de la app. " + getSoundsText()
        ) { toggleSounds() }

        // Ajuste 4: Animaciones
        setupSetting(
            R.id.btn_animations,
            "Animaciones",
            getAnimationsText(),
            "Animaciones de la app. " + getAnimationsText()

        // Ajuste 5: Modo Día/Noche
        setupSetting(
            R.id.btn_day_night,
            "Modo Día/Noche",
            getDayNightModeText(),
            "Modo de interfaz. Actual: " + getDayNightModeText()
        ) { toggleDayNightMode() }
        ) { toggleAnimations() }
    }

    private fun setupSetting(btnId: Int, label: String, value: String, ttsText: String, onClick: () -> Unit) {
    val switch = findViewById<SwitchCompat>(btnId)
    switch.isChecked = value == "24h" || value == "Rápida" || value == "Activados" || value == "Activadas"
    switch.setOnCheckedChangeListener { _, isChecked ->
        sound.play(R.raw.touch)
        tts.speak(ttsText)
        onClick()
    }
}

    // === Formato de Reloj ===
    private fun getClockFormatText(): String {
        return if (prefs.getBoolean("clock_24h", true)) "24h" else "12h (AM/PM)"
    }

    private fun toggleClockFormat() {
        val current = prefs.getBoolean("clock_24h", true)
        prefs.edit().putBoolean("clock_24h", !current).apply()
        
        tts.speak("Cambiado a " + getClockFormatText())
    }

    // === Velocidad TTS ===
    private fun getTtsSpeedText(): String {
        return when (prefs.getFloat("tts_speed", 1.0f)) {
            0.5f -> "Lenta"
            1.5f -> "Rápida"
            else -> "Normal"
        }
    }
    private fun toggleTtsSpeed() {
        val current = prefs.getFloat("tts_speed", 1.0f)
        val newSpeed = when (current) {
            0.5f -> 1.0f
            1.0f -> 1.5f
            else -> 0.5f
        }
        prefs.edit().putFloat("tts_speed", newSpeed).apply()
        
        tts.speak("Velocidad " + getTtsSpeedText())
    }

    // === Sonidos ===
    private fun getSoundsText(): String {
        return if (prefs.getBoolean("enable_sounds", true)) "Activados" else "Desactivados"
    }

    private fun toggleSounds() {
        val current = prefs.getBoolean("enable_sounds", true)
        prefs.edit().putBoolean("enable_sounds", !current).apply()
        
        tts.speak("Sonidos " + if (!current) "activados" else "desactivados")
    }

    // === Animaciones ===
    private fun getAnimationsText(): String {
        return if (prefs.getBoolean("enable_animations", true)) "Activadas" else "Desactivadas"
    }

    private fun toggleAnimations() {

    // === Modo Día/Noche ===
    private fun getDayNightModeText(): String {
        return if (prefs.getBoolean("day_mode", true)) "Día" else "Noche"
    }

    private fun toggleDayNightMode() {
        val current = prefs.getBoolean("day_mode", true)
        prefs.edit().putBoolean("day_mode", !current).apply()
        tts.speak("Modo " + if (!current) "Día" else "Noche")
    }
        val current = prefs.getBoolean("enable_animations", true)

    // === Modo Día/Noche ===
    private fun getDayNightModeText(): String {
        return if (prefs.getBoolean("day_mode", true)) "Día" else "Noche"
    }

    private fun toggleDayNightMode() {
        val current = prefs.getBoolean("day_mode", true)
        prefs.edit().putBoolean("day_mode", !current).apply()
        tts.speak("Modo " + if (!current) "Día" else "Noche")
    }
        prefs.edit().putBoolean("enable_animations", !current).apply()

    // === Modo Día/Noche ===
    private fun getDayNightModeText(): String {
        return if (prefs.getBoolean("day_mode", true)) "Día" else "Noche"
    }

    private fun toggleDayNightMode() {
        val current = prefs.getBoolean("day_mode", true)
        prefs.edit().putBoolean("day_mode", !current).apply()
        tts.speak("Modo " + if (!current) "Día" else "Noche")
    }
       

    // === Modo Día/Noche ===
    private fun getDayNightModeText(): String {
        return if (prefs.getBoolean("day_mode", true)) "Día" else "Noche"
    }

    private fun toggleDayNightMode() {
        val current = prefs.getBoolean("day_mode", true)
        prefs.edit().putBoolean("day_mode", !current).apply()
        tts.speak("Modo " + if (!current) "Día" else "Noche")
    }
        tts.speak("Animaciones " + if (!current) "activadas" else "desactivadas")

    // === Modo Día/Noche ===
    private fun getDayNightModeText(): String {
        return if (prefs.getBoolean("day_mode", true)) "Día" else "Noche"
    }

    private fun toggleDayNightMode() {
        val current = prefs.getBoolean("day_mode", true)
        prefs.edit().putBoolean("day_mode", !current).apply()
        tts.speak("Modo " + if (!current) "Día" else "Noche")
    }
    }

    // === Modo Día/Noche ===
    private fun getDayNightModeText(): String {
        return if (prefs.getBoolean("day_mode", true)) "Día" else "Noche"
    }

    private fun toggleDayNightMode() {
        val current = prefs.getBoolean("day_mode", true)
        prefs.edit().putBoolean("day_mode", !current).apply()
        tts.speak("Modo " + if (!current) "Día" else "Noche")
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        sound.release()
    }
}
