package com.oasis.app

import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ToastModule(private val activity: AppCompatActivity) {
    
    fun show(text: String) {
        if (!Config.ENABLE_TOASTS || Config.SAFE_MODE) return
        try {
            activity.findViewById<TextView?>(R.id.status_text)?.text = text
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.w("ToastModule", "No se pudo mostrar texto: ${e.message}")
        }
    }
}
