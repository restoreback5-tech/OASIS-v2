package com.oasis.app

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var tts: TTSModule
    private lateinit var sound: SoundModule
    private lateinit var prefs: SharedPreferences

    // Colores LED: Neón verde cuando está activo
    private val colorLedOn = 0xFF39FF14.toInt()
    private val colorLedOff = 0xFF9E9E9E.toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        // ==========================================
        // FONDO DINÁMICO SEGÚN TEMA
        // ==========================================
        val selectedTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val bgRes = when (selectedTheme) {
            "caribe" -> R.color.caribe_background
            "oscuro" -> R.color.oscuro_background
            else -> R.color.amanecer_background
        }
        window.setBackgroundDrawableResource(bgRes)

        // ==========================================
        // INICIALIZACIÓN DE MÓDULOS
        // ==========================================
        tts = TTSModule(this)
        sound = SoundModule(this)

        // ==========================================
        // FUNCIÓN AUXILIAR: Actualizar color LED
        // ==========================================
        fun updateLedColor(id: Int, active: Boolean) {
            val color = if (active) colorLedOn else colorLedOff
            findViewById<ImageView>(id).setColorFilter(color)
        }
        // ==========================================
        // FUNCIÓN PRINCIPAL: Configurar LED con diálogo binario
        // ==========================================
        fun setupLed(ledId: Int, prefKey: String, defaultVal: Boolean, onToggle: ((Boolean) -> Unit)? = null) {
            val led = findViewById<ImageView>(ledId)
            var state = prefs.getBoolean(prefKey, defaultVal)
            updateLedColor(ledId, state)

            // El FrameLayout padre es el que recibe el click (para efecto ripple)
            val parent = led.parent as? FrameLayout
            parent?.setOnClickListener {
                sound.play(R.raw.touch)
                
                // Inflar diálogo binario moderno
                val dialogView = layoutInflater.inflate(R.layout.dialog_binary_confirm, null)
                val dialog = android.app.Dialog(this)
                dialog.setContentView(dialogView)
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                // Configurar mensaje según la preferencia
                val message = when (prefKey) {
                    "clock_24h" -> "¿Usar formato 24 horas?"
                    "sounds_enabled" -> "¿Activar sonidos de interfaz?"
                    "animations_enabled" -> "¿Activar animaciones?"
                    "theme_cycle" -> "¿Cambiar tema visual?"
                    else -> "¿Confirmar cambio?"
                }
                dialogView.findViewById<TextView>(R.id.dialog_message).text = message

                // Botón SÍ (✓)
                dialogView.findViewById<Button>(R.id.btn_yes).setOnClickListener {
                    sound.play(R.raw.confirmar)
		    dialog.dismiss()
                    state = !state
                    prefs.edit().putBoolean(prefKey, state).apply()
                    updateLedColor(ledId, state)
                    onToggle?.invoke(state)
                    
                    // Feedback de voz
                    val feedback = when (prefKey) {
                        "clock_24h" -> if (state) "Formato de veinticuatro horas activado" else "Formato de doce horas activado"
                        "sounds_enabled" -> if (state) "Sonidos activados" else "Sonidos desactivados"
                        "animations_enabled" -> if (state) "Animaciones activadas" else "Animaciones desactivadas"
                        else -> if (state) "Activado" else "Desactivado"
                    }
                    tts.speak(feedback)
                }

                // Botón NO (✗)
                dialogView.findViewById<Button>(R.id.btn_no).setOnClickListener {                    dialog.dismiss()
                    sound.play(R.raw.cancelar)
		    dialog.dismiss()
                }

                dialog.show()
            }
        }

        // ==========================================
        // CONFIGURAR LOS 4 LEDS BINARIOS (en nuevo orden)
        // ==========================================
        
        // 1. Formato de reloj
        setupLed(R.id.led_clock_format, "clock_24h", true)

        // 2. Sonidos
        setupLed(R.id.led_sounds, "sounds_enabled", true)

        // 3. Animaciones
        setupLed(R.id.led_animations, "animations_enabled", true)

        // 4. Tema (cíclico: amanecer → caribe → oscuro → amanecer)
        setupLed(R.id.led_theme, "theme_cycle", false) { newState ->
            if (newState) {
                cycleTheme()
            }
        }

       // 5. Asistente flotante
        setupLed(R.id.led_overlay, "overlay_enabled", false) { newState ->
            if (newState) {
                if (android.provider.Settings.canDrawOverlays(this)) {
                    startService(android.content.Intent(this, OverlayService::class.java))
                    tts.speak("Asistente flotante activado")
                } else {
                    val intent = android.content.Intent(this, OverlayPermissionActivity::class.java)
                    startActivity(intent)
                    tts.speak("Primero concede el permiso de superposición")
                }
            } else {
                stopService(android.content.Intent(this, OverlayService::class.java))
                tts.speak("Asistente flotante desactivado")
            }
        }

        // ==========================================
        // CONFIGURAR SEEK BAR: Velocidad de voz (AL FINAL)
        // ==========================================
        val seekBar = findViewById<SeekBar>(R.id.seekbar_tts_speed)
        val speedLabel = findViewById<TextView>(R.id.text_tts_speed_value)
        val ledTts = findViewById<ImageView>(R.id.led_tts_speed)

        // Cargar valor guardado
        val savedSpeed = prefs.getInt("tts_speed", 50)
        seekBar.progress = savedSpeed
        updateSpeedLabel(savedSpeed, speedLabel)
        updateLedColor(R.id.led_tts_speed, true) // LED siempre "encendido" (es un control, no toggle)

        // Listener del SeekBar
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateSpeedLabel(progress, speedLabel)
                    // Actualizar velocidad real del TTS (ajusta el factor según tu implementación)
                }            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                sound.play(R.raw.confirmar)
                tts.speak("Velocidad de voz ajustada a " + speedLabel.text)
            }
        })

        // Click en el LED de velocidad: scroll suave hacia el SeekBar
        (ledTts.parent as? FrameLayout)?.setOnClickListener {
            sound.play(R.raw.touch)
            seekBar.parent?.let { parent ->
                (parent.parent as? ScrollView)?.smoothScrollTo(0, seekBar.top - 100)
            }
        }

        // ==========================================
        // BOTÓN VOLVER + TTS INICIAL
        // ==========================================
        findViewById<ImageView>(R.id.btn_back).setOnClickListener {
            sound.play(R.raw.touch)
            finish()
        }

        val title = findViewById<TextView>(R.id.settings_title)
        title.text = "Ajustes"
        tts.speak("Ajustes de OASIS")
    }

    // ==========================================
    // FUNCIÓN AUXILIAR: Actualizar etiqueta de velocidad
    // ==========================================
    private fun updateSpeedLabel(progress: Int, label: TextView) {
        label.text = when {
            progress < 30 -> "Lenta"
            progress > 70 -> "Rápida"
            else -> "Normal"
        }
    }

    // ==========================================
    // FUNCIÓN: Ciclar entre 3 temas
    // ==========================================
    private fun cycleTheme() {
        val current = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val next = when (current) {
            "amanecer" -> "caribe"
            "caribe" -> "oscuro"
            else -> "amanecer"
        }
        prefs.edit().putString("selected_theme", next).apply()
        
        // Aplicar fondo inmediatamente
        val bgRes = when (next) {
            "caribe" -> R.color.caribe_background
            "oscuro" -> R.color.oscuro_background
            else -> R.color.amanecer_background
        }
        window.setBackgroundDrawableResource(bgRes)
        
        // Actualizar texto del LED de tema (busca el TextView hermano)
        val ledTheme = findViewById<ImageView>(R.id.led_theme)
        val parent = ledTheme.parent?.parent
        if (parent is LinearLayout) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is TextView && child.id != R.id.led_theme) {
                    val themeName = when (next) {
                        "caribe" -> "Mar Caribe"
                        "oscuro" -> "Modo Oscuro"
                        else -> "Amanecer Latino"
                    }
                    child.text = "Tema: $themeName"
                    break
                }
            }
        }
        
        // Feedback de voz
        val themeName = when (next) {
            "caribe" -> "Tema Mar Caribe aplicado"
            "oscuro" -> "Tema Modo Oscuro aplicado"
            else -> "Tema Amanecer Latino aplicado"
        }
        tts.speak(themeName)
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
        sound.release()
    }
}
