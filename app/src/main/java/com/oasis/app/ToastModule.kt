package com.oasis.app

import android.content.Context
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ToastModule(private val activity: AppCompatActivity) {
    
    fun show(text: String) {
        if (!Config.ENABLE_TOASTS || Config.SAFE_MODE) return
        try {
            val tv = activity.findViewById<TextView>(R.id.greeting_text)
            tv.text = text
            Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Config.SAFE_MODE = true }
    }
}
