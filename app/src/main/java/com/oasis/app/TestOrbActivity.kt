package com.oasis.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class TestOrbActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test_orb)
        // Hacer la barra de estado transparente para inmersión total
        window.statusBarColor = 0xFF050510.toInt()
        window.navigationBarColor = 0xFF050510.toInt()
    }
}
