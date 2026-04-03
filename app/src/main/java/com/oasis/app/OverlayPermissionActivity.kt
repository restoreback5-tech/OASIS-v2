package com.oasis.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class OverlayPermissionActivity : AppCompatActivity() {

    private lateinit var sound: SoundModule

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overlay_permission)
        
        sound = SoundModule(this)

        // Botón: Abrir configuración de permisos
        findViewById<Button>(R.id.btn_grant_permission).setOnClickListener {
            sound.play(R.raw.touch)
            openOverlayPermissionSettings()
        }

        // Botón: Volver (sin activar)
        findViewById<Button>(R.id.btn_later).setOnClickListener {
            sound.play(R.raw.cancelar)
            finish()
        }

        // Explicación
        findViewById<TextView>(R.id.text_explanation).text = 
            "Para que OASIS pueda ayudarte en cualquier momento, " +
            "necesita permiso para mostrarse sobre otras aplicaciones.\n\n" +
            "Activa el interruptor 'Mostrar sobre otras apps' y regresa."
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
        
        // Esperar 3 segundos y verificar si se concedió
        findViewById<Button>(R.id.btn_grant_permission).postDelayed({
            if (Settings.canDrawOverlays(this)) {
                sound.play(R.raw.confirmar)
                finish() // Regresar automáticamente si se concedió
            }
        }, 3000)
    }

    override fun onResume() {
        super.onResume()
        // Verificar si el usuario ya concedió el permiso
        if (Settings.canDrawOverlays(this)) {
            sound.play(R.raw.confirmar)
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
    }
}
