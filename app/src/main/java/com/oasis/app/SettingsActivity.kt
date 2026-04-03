package com.oasis.app

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.graphics.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AlertDialog
import android.widget.Button

class SettingsActivity : AppCompatActivity() {

    private lateinit var tts: TTSModule
    private lateinit var sound: SoundModule
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        // Aplicar fondo según tema seleccionado
        val selectedTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val bgRes = when (selectedTheme) {
            "caribe" -> R.color.caribe_background
            "oscuro" -> R.color.oscuro_background
            else -> R.color.amanecer_background
        }
        window.setBackgroundDrawableResource(bgRes)

      // ==========================================
        // LÓGICA DE LEDs BINARIOS
        // ==========================================
        
        // Función auxiliar para cambiar color del LED
        fun updateLedColor(id: Int, active: Boolean) {
            val color = if (active) 0xFF4CAF50.toInt() else 0xFF808080.toInt() // Verde o Gris
            findViewById<ImageView>(id).setColorFilter(color)
        }

        // Función para configurar cada botón LED
        fun setupLed(ledId: Int, prefKey: String, defaultVal: Boolean) {
            val led = findViewById<ImageView>(ledId)
            var state = prefs.getBoolean(prefKey, defaultVal)
            updateLedColor(ledId, state)

            led.setOnClickListener {
                // Inflar diálogo binario
                val dialogView = layoutInflater.inflate(R.layout.dialog_binary_confirm, null)
                val dialog = android.app.Dialog(this).apply {
                    setContentView(dialogView)
                    // Fondo transparente para que el borde redondeado del XML se vea bien
                    window?.setBackgroundDrawable(android.graphics.ColorDrawable(android.graphics.Color.TRANSPARENT))
                }

                // Configurar textos y botones
                dialogView.findViewById<TextView>(R.id.dialog_message).text = "¿Activar función?"
                
                // Botón SI (✓)
                dialogView.findViewById<Button>(R.id.btn_yes).setOnClickListener {
                    dialog.dismiss()
                    state = !state // Invertir estado
                    prefs.edit().putBoolean(prefKey, state).apply()
                    updateLedColor(ledId, state)
                    // Si es el reloj, avisar que se actualizó (opcional)
                    if(prefKey == "clock_24h") {
                         // Lógica extra si fuera necesaria
                    }
                }

                // Botón NO (✗)
                dialogView.findViewById<Button>(R.id.btn_no).setOnClickListener {
                    dialog.dismiss()
                }

                dialog.show()
            }
        }

        // --- INICIALIZAR LOS 5 LEDs ---
        setupLed(R.id.led_clock_format, "clock_24h", true)
        setupLed(R.id.led_tts_speed, "tts_speed_normal", true)
        setupLed(R.id.led_sounds, "sounds_enabled", true)
        setupLed(R.id.led_animations, "animations_enabled", true)
        setupLed(R.id.led_day_night, "dark_mode", false)

        tts = TTSModule(this)
        sound = SoundModule(this)

        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            sound.play(R.raw.touch)
            finish()
        }

        val title = findViewById<TextView>(R.id.settings_title)
        title.text = "Ajustes"
        tts.speak("Ajustes de OASIS")

        setupSetting(R.id.btn_clock_format, "Formato de reloj", getClockFormatText(), "Formato de reloj") { toggleClockFormat() }
        setupSetting(R.id.btn_tts_speed, "Velocidad de voz", getTtsSpeedText(), "Velocidad de voz") { toggleTtsSpeed() }
        setupSetting(R.id.btn_sounds, "Sonidos", getSoundsText(), "Sonidos de la app") { toggleSounds() }
        setupSetting(R.id.btn_animations, "Animaciones", getAnimationsText(), "Animaciones de la app") { toggleAnimations() }
        
        // SELECTOR DE TEMAS (todo inline, sin funciones separadas)
        val btnTheme = findViewById<SwitchCompat>(R.id.btn_day_night)
        val currentTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val themeLabel = when (currentTheme) {
            "caribe" -> "Mar Caribe"
            "oscuro" -> "Modo Oscuro"
            else -> "Amanecer Latino"
        }
        btnTheme.text = "Tema: " + themeLabel
        btnTheme.isEnabled = false
        btnTheme.setOnClickListener {
            sound.play(R.raw.touch)            
            val labels = arrayOf("Amanecer Latino", "Mar Caribe", "Modo Oscuro")
            val keys = arrayOf("amanecer", "caribe", "oscuro")
            val currentIndex = keys.indexOf(currentTheme)
            
            AlertDialog.Builder(this)
                .setTitle("Seleccionar Tema")
                .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                    val selectedKey = keys[which]
                    val selectedLabel = labels[which]
                    prefs.edit().putString("selected_theme", selectedKey).apply()
                    btnTheme.text = "Tema: " + selectedLabel
                    tts.speak("Tema cambiado a " + selectedLabel)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
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

    private fun getClockFormatText(): String = if (prefs.getBoolean("clock_24h", true)) "24h" else "12h"
    private fun toggleClockFormat() {
        val c = prefs.getBoolean("clock_24h", true)
        prefs.edit().putBoolean("clock_24h", !c).apply()
        tts.speak("Cambiado")
    }

    private fun getTtsSpeedText(): String {
        return when (prefs.getFloat("tts_speed", 1.0f)) {
            0.5f -> "Lenta"
            1.5f -> "Rápida"
            else -> "Normal"
        }
    }
    private fun toggleTtsSpeed() {
        val c = prefs.getFloat("tts_speed", 1.0f)
        val n = when (c) { 0.5f -> 1.0f; 1.0f -> 1.5f; else -> 0.5f }
        prefs.edit().putFloat("tts_speed", n).apply()
        tts.speak("Velocidad actualizada")
    }
    private fun getSoundsText(): String = if (prefs.getBoolean("enable_sounds", true)) "Activados" else "Desactivados"
    private fun toggleSounds() {
        val c = prefs.getBoolean("enable_sounds", true)
        prefs.edit().putBoolean("enable_sounds", !c).apply()
        tts.speak("Sonidos actualizados")
    }

    private fun getAnimationsText(): String = if (prefs.getBoolean("enable_animations", true)) "Activadas" else "Desactivadas"
    private fun toggleAnimations() {
        val c = prefs.getBoolean("enable_animations", true)
        prefs.edit().putBoolean("enable_animations", !c).apply()
        tts.speak("Animaciones actualizadas")
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        sound.release()
    }
}
