package com.oasis.app

import android.content.SharedPreferences
import android.graphics.ColorDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

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
                val dialog = android.app.Dialog(this)
                dialog.setContentView(dialogView)
                // Fondo transparente para que el borde redondeado del XML se vea bien
                dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

                // Configurar textos y botones
                dialogView.findViewById<TextView>(R.id.dialog_message).text = "¿Activar función?"

                // Botón SI (✓)
                dialogView.findViewById<Button>(R.id.btn_yes).setOnClickListener {
                    dialog.dismiss()
                    state = !state // Invertir estado
                    prefs.edit().putBoolean(prefKey, state).apply()
                    updateLedColor(ledId, state)
                }

                // Botón NO (✗)
                dialogView.findViewById<Button>(R.id.btn_no).setOnClickListener {
                    dialog.dismiss()
                }

                dialog.show()
            }
        }

        // --- INICIALIZAR LOS 5 LEDs BINARIOS ---
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
        // SELECTOR DE TEMAS - COMENTADO TEMPORALMENTE
        // Se implementará después con LED + diálogo de 3 opciones
        
        /*
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
        */

    } // ← CIERRE CORRECTO DE onCreate

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        sound.release()
    }
}
