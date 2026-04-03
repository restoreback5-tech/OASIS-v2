package com.oasis.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AlertDialog

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

        // Configuración del Reloj
        setupSetting(R.id.btn_clock_format, "Formato de reloj", getClockFormatText(), "Formato de reloj. Actual: " + getClockFormatText()) { toggleClockFormat() }
        
        // Configuración de Velocidad
        setupSetting(R.id.btn_tts_speed, "Velocidad de voz", getTtsSpeedText(), "Velocidad de voz. Actual: " + getTtsSpeedText()) { toggleTtsSpeed() }
        
        // Configuración de Sonidos
        setupSetting(R.id.btn_sounds, "Sonidos", getSoundsText(), "Sonidos de la app. " + getSoundsText()) { toggleSounds() }
        
        // Configuración de Animaciones
        setupSetting(R.id.btn_animations, "Animaciones", getAnimationsText(), "Animaciones de la app. " + getAnimationsText()) { toggleAnimations() }
        
        // --- MODIFICACIÓN: Selector de Temas (Reemplaza Día/Noche) ---
        // Reutilizamos el botón 'btn_day_night' existente pero cambiamos su comportamiento
        val btnDayNight = findViewById<SwitchCompat>(R.id.btn_day_night)
        val currentTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"        val themeDisplayText = when(currentTheme) {
            "caribe" -> "Mar Caribe"
            "oscuro" -> "Modo Oscuro"
            else -> "Amanecer Latino"
        }
        btnDayNight.text = "Tema Visual: $themeDisplayText"
        
        // Al hacer clic, abrimos el selector en lugar de cambiar un booleano
        btnDayNight.setOnClickListener {
            sound.play(R.raw.touch)
            openThemeSelector()
        }
        // Deshabilitamos el switch para que no se mueva, funcione solo como botón
        btnDayNight.isEnabled = false 
    }

    // --- LÓGICA DEL SELECTOR DE TEMAS ---
    private fun openThemeSelector() {
        val themes = arrayOf("Amanecer Latino", "Mar Caribe", "Modo Oscuro")
        val themeKeys = arrayOf("amanecer", "caribe", "oscuro")
        val currentKey = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val currentIndex = themeKeys.indexOf(currentKey)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Seleccionar Tema")
        builder.setSingleChoiceItems(themes, currentIndex) { dialog, which ->
            val selectedKey = themeKeys[which]
            val selectedLabel = themes[which]
            
            // Guardar preferencia
            prefs.edit().putString("selected_theme", selectedKey).apply()
            
            // Actualizar etiqueta en pantalla
            findViewById<SwitchCompat>(R.id.btn_day_night).text = "Tema Visual: $selectedLabel"
            
            // Feedback auditivo
            tts.speak("Tema cambiado a: $selectedLabel")
            
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    // --- Helpers existentes (sin cambios) ---
    private fun setupSetting(btnId: Int, label: String, value: String, ttsText: String, onClick: () -> Unit) {
        val switch = findViewById<SwitchCompat>(btnId)
        switch.isChecked = value == "24h" || value == "Rápida" || value == "Activados" || value == "Activadas"
        switch.setOnCheckedChangeListener { _, isChecked ->
            sound.play(R.raw.touch)            tts.speak(ttsText)
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

    override fun onDestroy() { super.onDestroy(); tts.shutdown(); sound.release() }
}
