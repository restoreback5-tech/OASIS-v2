package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // === DECLARACIÓN DE MÓDULOS ===
    private lateinit var sound: SoundModule
    private lateinit var toast: ToastModule
    private lateinit var anim: AnimationModule
    private lateinit var tts: TTSModule
    private lateinit var voice: VoiceCommandModule
    private lateinit var appLauncher: AppLauncherModule
    private lateinit var prefs: SharedPreferences
    
    private val REQUEST_READ_CONTACTS = 102

    // === ORBE VIVO - VARIABLES ===
    private var orbState = "idle"
    private lateinit var orbView: ImageView
    private lateinit var orbBreatheAnim: android.view.animation.Animation
    private lateinit var orbFastAnim: android.view.animation.Animation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Configuración del Orbe Vivo
        orbView = findViewById(R.id.orb_view)
        orbBreatheAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.orb_breathe)
        orbFastAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.orb_fast_pulse)
        setOrbToListening()

        // 2. Inicialización de Módulos
        sound = SoundModule(this)
        sound.preload(R.raw.cancelar, R.raw.confirmar)
        toast = ToastModule(this)
        anim = AnimationModule(orbView)
        tts = TTSModule(this)

        // VoiceCommandModule con callbacks
        voice = VoiceCommandModule(
            context = this,
            onCommandDetected = { command, params ->
                runOnUiThread { handleVoiceCommand(command, params) }
            },
            onListening = { isListening ->
                runOnUiThread {
                    if (isListening) {
                        setOrbToActive()
                        toast.show("Escuchando...")
                    } else {
                        setOrbToListening()
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    toast.show(error)
                    tts.speak(error)
                    resetOrbToIdle()
                }
            }
        )

        // AppLauncherModule - Lanzador de aplicaciones
        appLauncher = AppLauncherModule(this)

        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        // 3. Tema y Permisos
        applyTheme()
        checkMicPermission()

        // 4. Secuencia de inicio
        sound.play(R.raw.inicio)
        orbView.postDelayed({ tts.speak("Bienvenido a OASIS") }, 1000)
        anim.startRippleAnimation()

        // 5. Botón Ajustes
        findViewById<ImageView>(R.id.btn_settings).setOnClickListener {
            sound.play(R.raw.touch)
            tts.speak("Ajustes")
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 6. Reloj en tiempo real
        val clockHandler = Handler(Looper.getMainLooper())
        val clockRunnable = object : Runnable {
            override fun run() {
                val is24Hour = prefs.getBoolean("clock_24h", true)
                val format = if (is24Hour) {
                    SimpleDateFormat("HH:mm", Locale.getDefault())
                } else {
                    SimpleDateFormat("hh:mm a", Locale.getDefault())
                }
                findViewById<TextView>(R.id.clock_text).text = format.format(System.currentTimeMillis())
                clockHandler.postDelayed(this, 1000)
            }
        }
        clockHandler.post(clockRunnable)

        // 7. Click en el Orbe - Usa VoiceModule
        orbView.setOnClickListener {
            sound.play(R.raw.touch)
            voice.startListening()
        }

        // 8. Botones principales
        setupBtn(R.id.btn_call, "Llamar") { openDialer() }
        setupBtn(R.id.btn_message, "Enviar mensaje") { openSms() }
        setupBtn(R.id.btn_contacts, "Contactos") { openContacts() }
        setupBtn(R.id.btn_apps, "Apps") { openAppDrawer() }
    }

    private fun setupBtn(id: Int, text: String, action: () -> Unit) {
        findViewById<MaterialButton>(id).setOnClickListener {
            sound.play(R.raw.touch)
            pulseAnimation(findViewById(id))
            toast.show(text)
            tts.speak(text)
            action()
        }
    }

    private fun pulseAnimation(view: View) {
        val scaleX = android.animation.ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f)
        val scaleY = android.animation.ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f)
        android.animation.AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 180
            interpolator = android.view.animation.DecelerateInterpolator()
            start()
        }
    }

    // ====================== ORBE VIVO ======================
    private fun setOrbToListening() {
        orbState = "idle"
        orbView.clearAnimation()
        orbView.setImageResource(R.drawable.orb_listening)
        (orbView.drawable as? android.graphics.drawable.Animatable)?.start()
        orbView.startAnimation(orbBreatheAnim)
    }

    private fun setOrbToActive() {
        orbState = "active"
        orbView.clearAnimation()
        orbView.setImageResource(R.drawable.orb_active)
        (orbView.drawable as? android.graphics.drawable.Animatable)?.start()
        orbView.startAnimation(orbFastAnim)
    }

    private fun resetOrbToIdle() {
        if (orbState == "active") {
            setOrbToListening()
        }
    }

    // ====================== MANEJO DE COMANDOS (VoiceModule) ======================
    private fun handleVoiceCommand(command: String, params: Map<String, String>) {
        toast.show("Comando: $command")

        when (command) {
            "open_app" -> {
                val appName = params["app"] ?: "desconocido"
                if (appName != "desconocido") {
                    val success = appLauncher.launchApp(appName)
                    if (success) {
                        tts.speak("Abriendo $appName")
                    } else {
                        tts.speak("No encontré la aplicación $appName")
                    }
                } else {
                    tts.speak("¿Qué aplicación quieres abrir?")
                }
            }

            "call" -> {
                val contact = params["contact"] ?: ""
                if (contact.isNotEmpty()) {
                    if (contact.any { it.isDigit() }) {
                        dialNumber(contact)
                    } else {
                        tts.speak("Buscando a $contact")
                        searchContactAndDial(contact)
                    }
                } else {
                    tts.speak("¿A quién quieres llamar?")
                }
            }

            "message" -> {
                val contact = params["contact"] ?: ""
                if (contact.isNotEmpty()) {
                    openSmsToContact(contact)
                } else {
                    openSms()
                }
            }

            "cancel" -> {
                sound.play(R.raw.cancelar)
                tts.speak("Cancelando")
                resetOrbToIdle()
            }

            "help" -> {
                tts.speak("Puedo abrir apps, hacer llamadas y enviar mensajes")
                resetOrbToIdle()
            }

            else -> {
                tts.speak("No entendí. Intenta de nuevo")
                resetOrbToIdle()
            }
        }
    }

    // === TEMAS ===
    private fun applyTheme() {
        val selectedTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val bgRes = when (selectedTheme) {
            "caribe" -> R.color.caribe_background
            "oscuro" -> R.color.oscuro_background
            else -> R.color.amanecer_background
        }
        window.setBackgroundDrawableResource(bgRes)

        val statusBarColor = when (selectedTheme) {
            "caribe" -> R.color.status_bar_caribe
            "oscuro" -> R.color.status_bar_oscuro
            else -> R.color.status_bar_amanecer
        }
        window.statusBarColor = ContextCompat.getColor(this, statusBarColor)

        val isLightStatusBar = selectedTheme != "oscuro"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = if (isLightStatusBar) {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else {
                0
            }
        }

        val textColor = when (selectedTheme) {
            "caribe" -> ContextCompat.getColor(this, R.color.caribe_text)
            "oscuro" -> ContextCompat.getColor(this, R.color.oscuro_text)
            else -> ContextCompat.getColor(this, R.color.amanecer_text)
        }
        findViewById<TextView>(R.id.clock_text).setTextColor(textColor)
        findViewById<TextView>(R.id.greeting_text)?.setTextColor(textColor)
    }

    // === ACCIONES DE TELÉFONO ===
    
    /**
     * Abre el marcador de teléfono (NO requiere permiso)
     */
    private fun openDialer() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply { 
                data = Uri.parse("tel:") 
            }
            startActivitySafe(intent, "No se encontró aplicación de teléfono")
        } catch (e: Exception) {
            tts.speak("No pude abrir el marcador")
        }
    }

    /**
     * Marca un número específico (NO requiere permiso, solo abre el dialer con el número)
     */
    private fun dialNumber(number: String) {
        try {
            val cleanNumber = number.replace(Regex("[^0-9+#*]"), "")
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
            }
            startActivitySafe(intent, "No se pudo iniciar la llamada")
            tts.speak("Marcando $number")
        } catch (e: Exception) {
            tts.speak("No se pudo marcar el número")
        }
    }

    /**
     * Busca contacto y abre dialer (requiere permiso READ_CONTACTS para búsqueda avanzada)
     */
    private fun searchContactAndDial(contactName: String) {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            requestContactsPermission()
            return
        }
        
        try {
            // Intentar abrir contactos con búsqueda
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = ContactsContract.Contacts.CONTENT_URI
                putExtra(ContactsContract.Intents.Insert.NAME, contactName)
            }
            startActivitySafe(intent, "No se encontró la aplicación de contactos")
        } catch (e: Exception) {
            // Fallback: abrir dialer directamente
            openDialer()
        }
    }

    // === MENSAJES Y SMS ===
    
    /**
     * Abre la app de mensajes predeterminada o WhatsApp
     */
    private fun openSms() {
        // Primero intentar con la app de SMS predeterminada
        try {
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply { 
                data = Uri.parse("smsto:") 
            }
            if (smsIntent.resolveActivity(packageManager) != null) {
                startActivity(smsIntent)
                return
            }
        } catch (e: Exception) {
            // Continuar con fallback
        }
        
        // Fallback: Intentar abrir cualquier app de mensajería
        val messagingApps = listOf(
            "com.google.android.apps.messaging", // Google Messages
            "com.samsung.android.messaging",     // Samsung Messages
            "com.android.mms",                   // MMS genérico
            "com.whatsapp"                       // WhatsApp como último recurso
        )
        
        for (packageName in messagingApps) {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
                tts.speak("Abriendo mensajes")
                return
            }
        }
        
        tts.speak("No hay aplicación de mensajes disponible")
    }

    /**
     * Abre SMS con contacto específico
     */
    private fun openSmsToContact(contactName: String) {
        // Detectar si quiere WhatsApp específicamente
        val lowerContact = contactName.lowercase()
        if (lowerContact.contains("whatsapp") || lowerContact.contains("wasap")) {
            openWhatsApp()
            return
        }
        
        // Intentar abrir SMS con el contacto/número
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply { 
                data = Uri.parse("smsto:$contactName") 
            }
            startActivitySafe(intent, "No hay aplicación de mensajes")
            tts.speak("Mensaje para $contactName")
        } catch (e: Exception) {
            tts.speak("No pude abrir mensajes")
        }
    }

    /**
     * Abre WhatsApp específicamente
     */
    private fun openWhatsApp() {
        val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (intent != null) {
            startActivity(intent)
            tts.speak("Abriendo WhatsApp")
        } else {
            tts.speak("WhatsApp no está instalado")
            // Abrir Play Store para instalar WhatsApp
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")))
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")))
            }
        }
    }

    // === CONTACTOS ===
    
    /**
     * Abre la aplicación de contactos
     */
    private fun openContacts() {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            requestContactsPermission()
            return
        }
        
        try {
            // Intent 1: Abrir lista de contactos completa
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = ContactsContract.Contacts.CONTENT_URI
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
            
            // Intent 2: Abrir app de contactos genérica
            val fallbackIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CONTACTS)
            }
            startActivitySafe(fallbackIntent, "No se encontró aplicación de contactos")
            
        } catch (e: Exception) {
            tts.speak("No pude abrir contactos")
        }
    }

    // === LANZADOR DE APPS ===
    
    /**
     * Abre el cajón de aplicaciones del sistema
     */
    private fun openAppDrawer() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            startActivitySafe(intent, "No se encontró el lanzador de aplicaciones")
        } catch (e: Exception) {
            tts.speak("No pude abrir las aplicaciones")
        }
    }

    // === UTILIDADES ===
    
    /**
     * Verifica si tenemos un permiso específico
     */
    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Inicia una actividad de forma segura con mensaje de error si falla
     */
    private fun startActivitySafe(intent: Intent, errorMessage: String) {
        try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                tts.speak(errorMessage)
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            tts.speak(errorMessage)
        }
    }

    // === PERMISOS ===
    
    private fun checkMicPermission() {
        if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    private fun requestContactsPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
            // Explicar por qué necesitamos el permiso
            AlertDialog.Builder(this)
                .setTitle("Permiso necesario")
                .setMessage("Necesitamos acceder a tus contactos para mostrarlos")
                .setPositiveButton("Conceder") { _, _ ->
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ_CONTACTS)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ_CONTACTS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            100 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    tts.speak("Permiso de micrófono concedido")
                } else {
                    tts.speak("Necesito permiso de micrófono para escucharte")
                }
            }
            REQUEST_READ_CONTACTS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    tts.speak("Permiso concedido, abriendo contactos")
                    openContacts()
                } else {
                    tts.speak("Sin permiso no puedo mostrar contactos")
                    // Opcional: Abrir configuración de la app
                    if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_CONTACTS)) {
                        // Permiso denegado permanentemente, ofrecer ir a ajustes
                        AlertDialog.Builder(this)
                            .setTitle("Permiso necesario")
                            .setMessage("El permiso de contactos fue denegado. ¿Deseas abrir la configuración de la app?")
                            .setPositiveButton("Abrir ajustes") { _, _ ->
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                startActivity(intent)
                            }
                            .setNegativeButton("Cancelar", null)
                            .show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
    }

    override fun onDestroy() {
        super.onDestroy()
        sound.release()
        tts.shutdown()
        voice.destroy()
    }
}

