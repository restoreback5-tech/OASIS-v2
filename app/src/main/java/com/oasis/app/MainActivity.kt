package com.oasis.app

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
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
    private lateinit var stt: STTModule
    private lateinit var prefs: SharedPreferences

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
        stt = STTModule(this)
        prefs = getSharedPreferences("oasis_settings", MODE_PRIVATE)

        // 3. Tema y Permisos
        applyTheme()
        checkMicPermission()

        // 4. Listener de comandos de voz
        stt.setOnCommandListener { command ->
            runOnUiThread { processCommand(command) }
        }

        // 5. Secuencia de inicio
        sound.play(R.raw.inicio)
        orbView.postDelayed({ tts.speak("Bienvenido a OASIS") }, 1000)
        anim.startRippleAnimation()

        // 6. Botón Ajustes
        findViewById<ImageView>(R.id.btn_settings).setOnClickListener {
            sound.play(R.raw.touch)
            tts.speak("Ajustes")
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 7. Reloj en tiempo real
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

        // 8. Click en el Orbe
        orbView.setOnClickListener {
            sound.play(R.raw.touch)
            toast.show("Escuchando...")
            setOrbToActive()
            stt.startListening()
        }

        // 9. Botones principales
        setupBtn(R.id.btn_call, "Llamar") { openDialer() }
        setupBtn(R.id.btn_message, "Enviar mensaje") { openSms() }
        setupBtn(R.id.btn_contacts, "Contactos") { openContacts() }
        setupBtn(R.id.btn_apps, "Apps") { openLauncher() }
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
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.94f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.94f, 1f)
        AnimatorSet().apply {
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

    // === TEMAS ===
    private fun applyTheme() {
        val selectedTheme = prefs.getString("selected_theme", "amanecer") ?: "amanecer"
        val bgRes = when (selectedTheme) {
            "caribe" -> R.color.caribe_background
            "oscuro" -> R.color.oscuro_background
            else -> R.color.amanecer_background
        }
        window.setBackgroundDrawableResource(bgRes)

        val textColor = when (selectedTheme) {
            "caribe" -> ContextCompat.getColor(this, R.color.caribe_text)
            "oscuro" -> ContextCompat.getColor(this, R.color.oscuro_text)
            else -> ContextCompat.getColor(this, R.color.amanecer_text)
        }
        findViewById<TextView>(R.id.clock_text).setTextColor(textColor)
        findViewById<TextView>(R.id.greeting_text)?.setTextColor(textColor)
    }

    // === ACCIONES SEGURAS ===
    private fun openDialer() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply { data = android.net.Uri.parse("tel:") }
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
            else tts.speak("No se encontró aplicación de teléfono")
        } catch (e: Exception) {
            tts.speak("No pude abrir el marcador")
        }
    }

    private fun openSms() {
        try {
            val whatsappIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (whatsappIntent != null) {
                startActivity(whatsappIntent)
                tts.speak("Abriendo WhatsApp")
                return
            }
            val smsIntent = Intent(Intent.ACTION_SENDTO).apply { data = android.net.Uri.parse("smsto:") }
            if (smsIntent.resolveActivity(packageManager) != null) {
                startActivity(smsIntent)
                tts.speak("Abriendo mensajes")
            } else {
                tts.speak("No hay aplicación de mensajes disponible")
            }
        } catch (e: Exception) {
            tts.speak("No pude abrir los mensajes")
        }
    }

    private fun openSmsToContact(contactName: String) {
        try {
            if (contactName.lowercase().contains("whatsapp") || contactName.lowercase().contains("wasap")) {
                val intent = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (intent != null) {
                    startActivity(intent)
                    tts.speak("Abriendo WhatsApp")
                    return
                }
            }
            val intent = Intent(Intent.ACTION_SENDTO).apply { data = android.net.Uri.parse("smsto:") }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                tts.speak("Mensaje para $contactName")
            } else {
                tts.speak("No hay aplicación de mensajes")
            }
        } catch (e: Exception) {
            tts.speak("No pude abrir mensajes")
        }
    }

    private fun openContacts() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = android.provider.ContactsContract.Contacts.CONTENT_URI
            }
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun openLauncher() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        } catch (e: Exception) {}
    }

    private fun dialNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = android.net.Uri.parse("tel:$number")
            }
            startActivity(intent)
            tts.speak("Iniciando llamada")
        } catch (e: Exception) {
            tts.speak("No se pudo iniciar la llamada")
        }
    }

    // === PROCESAR COMANDOS CON RESPUESTAS VARIADAS ===
private val random = java.util.Random()

private fun randomResponse(responses: Array<String>): String {
    return responses[random.nextInt(responses.size)]
}

private fun processCommand(cmd: String) {
    val cmdLower = cmd.lowercase().trim()
    toast.show("Comando: $cmd")
    
    when {
        // === ABRIR APPS ===
        cmdLower.contains("abrir") || cmdLower.contains("abre") || cmdLower.contains("lanza") -> {
            val appName = extractAppName(cmdLower)
            if (appName.isNotEmpty()) {
                openSpecificApp(appName)
            } else {
                val responses = arrayOf(
                    "¿Qué aplicación quieres abrir?",
                    "Dime el nombre de la app",
                    "¿Cuál app buscas?",
                    "Te escucho, ¿qué app necesitas?"
                )
                tts.speak(randomResponse(responses))
            }
        }
        
        // === LLAMADAS ===
        cmdLower.contains("llamar") || cmdLower.contains("llama a") -> {
            val contactName = extractContactName(cmdLower)
            if (contactName.isNotEmpty()) {
                if (contactName.any { it.isDigit() }) {
                    val responses = arrayOf(
                        "Llamando a $contactName",
                        "Marcando $contactName",
                        "Conectando con $contactName"
                    )
                    tts.speak(randomResponse(responses))
                    dialNumber(contactName)
                } else {
                    val responses = arrayOf(
                        "Buscando a $contactName",
                        "Déjame buscar a $contactName",
                        "Un momento, busco a $contactName"
                    )
                    tts.speak(randomResponse(responses))
                    openDialer()
                }
            } else {                val responses = arrayOf(
                    "¿A quién quieres llamar?",
                    "Dime el nombre del contacto",
                    "¿Con quién te comunicas?"
                )
                tts.speak(randomResponse(responses))
            }
        }
        
        // === MENSAJES ===
        cmdLower.contains("mensaje") || cmdLower.contains("mandar") || cmdLower.contains("enviar") -> {
            val contactName = extractContactName(cmdLower)
            if (contactName.isNotEmpty()) {
                val responses = arrayOf(
                    "Mensaje para $contactName",
                    "Preparando mensaje para $contactName",
                    "Abriendo chat con $contactName"
                )
                tts.speak(randomResponse(responses))
                openSmsToContact(contactName)
            } else {
                val responses = arrayOf(
                    "¿A quién le envías el mensaje?",
                    "Dime el contacto",
                    "¿Para quién es el mensaje?"
                )
                tts.speak(randomResponse(responses))
                openSms()
            }
        }
        
        // === CONTACTOS ===
        cmdLower.contains("contacto") || cmdLower.contains("contactos") -> {
            val responses = arrayOf(
                "Abriendo contactos",
                "Aquí están tus contactos",
                "Mostrando tu agenda"
            )
            tts.speak(randomResponse(responses))
            openContacts()
        }
        
        // === APPS / MENÚ ===
        cmdLower.contains("app") || cmdLower.contains("menú") -> {
            val responses = arrayOf(
                "Abriendo menú de apps",
                "Aquí tienes tus aplicaciones",
                "Mostrando todas las apps"
            )
            tts.speak(randomResponse(responses))            openLauncher()
        }
        
        // === SALUDOS ===
        cmdLower.contains("hola") || cmdLower.contains("buenos") || cmdLower.contains("buenas") -> {
            val responses = arrayOf(
                "¡Hola! Soy OASIS. ¿En qué puedo ayudarte?",
                "¡Hola! Estoy aquí para lo que necesites",
                "¡Hola! ¿Qué quieres hacer hoy?",
                "¡Hola! Soy OASIS, tu asistente personal",
                "¡Hola! ¿En qué te ayudo?"
            )
            tts.speak(randomResponse(responses))
        }
        
        // === AYUDA ===
        cmdLower.contains("ayuda") || cmdLower.contains("qué puedes hacer") || cmdLower.contains("comandos") -> {
            val responses = arrayOf(
                "Puedo abrir apps, hacer llamadas y enviar mensajes.",
                "Puedo ayudarte con apps, llamadas y mensajes.",
                "Soy capaz de abrir aplicaciones, llamar a contactos y enviar mensajes.",
                "Puedo abrir apps, hacer llamadas, enviar mensajes y mostrar contactos."
            )
            tts.speak(randomResponse(responses))
        }
        
        // === AGRADECIMIENTOS ===
        cmdLower.contains("gracias") || cmdLower.contains("thank") -> {
            val responses = arrayOf(
                "¡Con gusto!",
                "¡Para eso estoy!",
                "¡Cuando quieras!",
                "¡Aquí para servirte!"
            )
            tts.speak(randomResponse(responses))
        }
        
        // === DESPEDIDAS ===
        cmdLower.contains("adiós") || cmdLower.contains("nos vemos") || cmdLower.contains("hasta luego") -> {
            val responses = arrayOf(
                "¡Hasta pronto!",
                "¡Nos vemos!",
                "¡Que tengas buen día!",
                "¡Aquí estaré cuando me necesites!"
            )
            tts.speak(randomResponse(responses))
        }
        
        // === NO ENTIENDE ===
        else -> {            val responses = arrayOf(
                "No entendí. Intenta de nuevo.",
                "¿Puedes repetirlo?",
                "No capté eso. ¿Otra vez?",
                "Disculpa, ¿puedes decirlo diferente?",
                "No estoy seguro de entender. Intenta otra vez."
            )
            tts.speak(randomResponse(responses))
        }
    }
    
    resetOrbToIdle()
}

    // === EXTRACCIÓN (pega aquí tus funciones originales si son diferentes) ===
    private fun extractAppName(cmd: String): String {
    return cmd
        .replace(Regex("abrir|abre|lanza|inicia|app|aplicación"), "")
        .replace(Regex("\\b(el|la|los|las|un|una|de|del|para|por)\\b"), "")
        .replace(Regex("[^a-zA-Záéíóúñ\\s]"), "")
        .trim()
}

    private fun extractContactName(cmd: String): String {
    var result = cmd
    listOf("llamar", "llama a", "mensaje", "mandar", "enviar", "a")
        .forEach { result = result.replace(it, "").trim() }
    result = result.replace(
        Regex("\\b(el|la|los|las|un|una|de|del|para|por)\\b"),
        ""
    ).trim()
    return result.replace(Regex("[^a-zA-Záéíóúñ\\s]"), "").trim()
}

    private fun openSpecificApp(appName: String) {
    val pm = packageManager
    val normalized = appName.lowercase().trim()
    
    // 1. Intento rápido (hardcode)
    val knownPackage = when {
        normalized.contains("whatsapp") || normalized.contains("wasap") -> "com.whatsapp"
        normalized.contains("facebook") -> "com.facebook.katana"
        normalized.contains("instagram") -> "com.instagram.android"
        normalized.contains("youtube") -> "com.google.android.youtube"
        normalized.contains("chrome") -> "com.android.chrome"
        normalized.contains("spotify") -> "com.spotify.music"
        normalized.contains("tiktok") -> "com.zhiliaoapp.musically"
        normalized.contains("netflix") -> "com.netflix.mediaclient"
        normalized.contains("telegram") -> "org.telegram.messenger"
        normalized.contains("twitter") || normalized.contains("x") -> "com.twitter.android"
        else -> null
    }
    
    // 👉 Intento directo
    if (knownPackage != null) {
        try {
            val intent = pm.getLaunchIntentForPackage(knownPackage)
            if (intent != null) {
                startActivity(intent)
                tts.speak("Abriendo $appName")
                return
            }
        } catch (_: Exception) {}
    }
    
    // 2. 🔥 FALLBACK REAL (busca en TODAS las apps instaladas)
    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)
    val apps = pm.queryIntentActivities(intent, 0)
    val match = apps.firstOrNull {
        it.loadLabel(pm).toString().lowercase().contains(normalized)
    }
    
    if (match != null) {
        val launchIntent = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            tts.speak("Abriendo ${match.loadLabel(pm)}")
            return
        }
    }
    
    // 3. Fallback final
    tts.speak("No encontré la aplicación $appName")
}

    // === PERMISOS ===
    private fun checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                tts.speak("Permiso de micrófono concedido")
            } else {
                tts.speak("Necesito permiso de micrófono para escucharte")
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
        stt.destroy()
    }
}
