package com.oasis.app

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ceil

/**
 * Cerebro del orbe. Dos niveles:
 *  1) Local (sin internet, instantáneo): hora, fecha, batería, RAM, almacenamiento, red, clima, agenda,
 *     notas, temporizadores, volumen, temas, cuentas, saludos.
 *  2) Opcional: IA local con Ollama (ollama serve) para preguntas libres.
 */
class AssistantEngine(private val ctx: Context) {

    sealed class Act {
        data class OpenApp(val app: String) : Act()
        data class Call(val contact: String) : Act()
        data class Message(val contact: String) : Act()
        data class Timer(val minutes: Int) : Act()
        object Alarms : Act()
        object Restyle : Act()
        data class AlarmAt(val hour: Int, val minute: Int, val label: String) : Act()
        data class System(val action: String) : Act()
        data class Routine(val night: Boolean) : Act()
        object ReadAloud : Act()
    }

    data class Reply(val text: String, val act: Act? = null)

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val history = ArrayList<Pair<String, String>>()   // ("user"|"assistant") a texto

    private fun prefs() = ctx.getSharedPreferences("oasis_settings", Context.MODE_PRIVATE)

    private fun serverUrl() = (prefs().getString("ollama_url", DEFAULT_URL)?.trim()?.ifBlank { DEFAULT_URL } ?: DEFAULT_URL).trimEnd('/')
    private fun model() = prefs().getString("ollama_model", DEFAULT_MODEL)?.trim()?.ifBlank { DEFAULT_MODEL } ?: DEFAULT_MODEL

    /** La IA conversacional es opcional y se activa en Ajustes. */
    fun cloudEnabled(): Boolean = prefs().getBoolean("ai_enabled", false)

    /** Responde en el hilo principal. */
    fun handle(raw: String, done: (Reply) -> Unit) {
        io.execute {
            val reply: Reply = try {
                local(raw) ?: if (cloudEnabled()) cloud(raw) else Reply(FALLBACK)
            } catch (e: Exception) {
                Reply("No pude conectarme a internet para pensar eso. Intenta de nuevo.")
            }
            main.post { done(reply) }
        }
    }

    fun shutdown() { io.shutdownNow() }

    // ====================== CEREBRO LOCAL ======================
    private fun has(n: String, vararg words: String) = words.any { TextMatch.containsWord(n, it) }
    private fun hasPhrase(n: String, vararg phrases: String) = phrases.any { n.contains(it) }

    private fun local(raw: String): Reply? {
        val lower = raw.lowercase(Locale.getDefault()).trim()
        val n = TextMatch.normalize(raw)

        // ---- memoria: "recuerda que mi hijo se llama Luis"
        MEM_SAVE.find(lower)?.let { m ->
            val fact = m.groupValues[2].trim()
            if (fact.isNotEmpty()) { MemoryStore.add(ctx, fact); return Reply("Está bien, lo recordaré: $fact.") }
        }
        if (hasPhrase(n, "que recuerdas", "que sabes de mi", "que te dije", "que te he dicho")) {
            val l = MemoryStore.list(ctx)
            return Reply(if (l.isEmpty()) "Todavía no me has pedido recordar nada. Dime: recuerda que, y lo que quieras." else "Me pediste recordar: " + l.takeLast(5).joinToString(". "))
        }
        if (hasPhrase(n, "olvida todo", "borra tu memoria", "olvida lo que te dije", "olvidalo todo")) {
            MemoryStore.clear(ctx); return Reply("Listo, olvidé todo lo que me pediste recordar.")
        }

        // ---- recordatorio / alarma a una hora: "recuérdame a las 5 tomar la pastilla"
        parseAlarmAt(n)?.let { a ->
            val h12 = if (a.hour % 12 == 0) 12 else a.hour % 12
            val part = when { a.hour < 12 -> "de la mañana"; a.hour < 19 -> "de la tarde"; else -> "de la noche" }
            val mm = if (a.minute == 0) "" else ":" + a.minute.toString().padStart(2, '0')
            return Reply("Listo, te aviso a las $h12$mm $part" + (if (a.label.isNotBlank()) ": ${a.label}." else "."), a)
        }

        // ---- notas
        NOTE.find(lower)?.let { m ->
            val text = m.groupValues[2].trim()
            if (text.isNotEmpty()) {
                NotesStore.add(ctx, text.replaceFirstChar { it.uppercase() })
                return Reply("Listo, anoté: $text")
            }
        }
        if (has(n, "notas", "pendientes") && has(n, "mis", "lee", "leeme", "dime", "cuales", "que")) {
            val open = NotesStore.list(ctx).filter { !it.done }.take(3)
            return Reply(if (open.isEmpty()) "No tienes notas pendientes." else "Tus pendientes son: " + open.joinToString(". ") { it.text })
        }

        // ---- cuentas
        mathAnswer(lower)?.let { return Reply(it) }

        // ---- leer mensajes recibidos
        if (hasPhrase(n, "lee mis mensajes", "leeme los mensajes", "lee los mensajes", "mensajes nuevos", "tengo mensajes", "que mensajes", "leeme mis mensajes")) {
            if (!OasisNotificationListener.isEnabled(ctx)) return Reply("Para leer tus mensajes, permite a OASIS el acceso a notificaciones en Ajustes.")
            val m = Inbox.recent(3)
            return Reply(if (m.isEmpty()) "No tienes mensajes nuevos." else m.joinToString(". ") { "${it.who} dice: ${it.text.take(160)}" })
        }

        // ---- leer en voz alta con la cámara
        if (hasPhrase(n, "lee esto", "leer en voz alta", "lee el texto", "lee este papel", "leeme esto", "lee lo que dice", "que dice aqui"))
            return Reply("Apunta la cámara al texto.", Act.ReadAloud)

        // ---- linterna
        if (has(n, "linterna", "lampara")) return Reply("Listo.", Act.OpenApp("linterna"))

        // ---- ajustes del teléfono por voz
        if (has(n, "enciende", "apaga", "activa", "desactiva", "prende", "conecta", "desconecta", "abre", "pon", "cambia", "ajusta")) {
            val setting: Pair<String, String>? = when {
                has(n, "wifi") -> (if (android.os.Build.VERSION.SDK_INT >= 29) android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY else android.provider.Settings.ACTION_WIFI_SETTINGS) to "los controles de Wi-Fi"
                has(n, "bluetooth") -> android.provider.Settings.ACTION_BLUETOOTH_SETTINGS to "el Bluetooth"
                hasPhrase(n, "modo avion") -> android.provider.Settings.ACTION_AIRPLANE_MODE_SETTINGS to "el modo avión"
                hasPhrase(n, "no molestar", "silencio del telefono") -> android.provider.Settings.ACTION_SOUND_SETTINGS to "los sonidos"
                has(n, "brillo", "pantalla") && !has(n, "tiempo") -> android.provider.Settings.ACTION_DISPLAY_SETTINGS to "la pantalla"
                has(n, "ubicacion", "gps") -> android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS to "la ubicación"
                else -> null
            }
            if (setting != null) return Reply("Abriendo ${setting.second}.", Act.System(setting.first))
        }

        // ---- número de un contacto
        NUMBER_OF.find(lower)?.let { m ->
            val who = m.groupValues[1].trim()
            val num = contactNumber(who) ?: return Reply("No encontré el número de $who. Revisa el permiso de contactos.")
            return Reply("El número de $who es " + num.filter { it.isDigit() }.toList().joinToString(" "))
        }

        // ---- rutinas
        if (hasPhrase(n, "rutina de la manana", "rutina de la noche") || (n.split(' ').size <= 3 && hasPhrase(n, "buenos dias", "buenas noches")))
            return Reply("Un momento.", Act.Routine(night = n.contains("noche")))

        // ---- temporizador / alarma
        if (hasPhrase(n, "temporizador", "cronometro", "cuenta regresiva", "avisame en", "despiertame en", "pon una alarma en")) {
            val minutes = parseMinutes(n)
            return if (minutes > 0) Reply("Listo, temporizador de $minutes ${if (minutes == 1) "minuto" else "minutos"}.", Act.Timer(minutes))
            else Reply("¿De cuántos minutos?")
        }
        if (has(n, "alarma", "alarmas", "despertador")) return Reply("Abriendo tus alarmas.", Act.Alarms)

        // ---- estado del teléfono
        if (hasPhrase(n, "que hora", "dime la hora", "la hora")) return Reply(timeSpeech())
        if (hasPhrase(n, "que dia", "que fecha", "fecha de hoy", "dia es hoy", "a cuantos estamos"))
            return Reply("Hoy es " + java.text.SimpleDateFormat("EEEE d 'de' MMMM", Locale.getDefault()).format(Date()) + ".")
        if (has(n, "bateria", "pila", "carga") && !has(n, "cargador")) {
            val b = SystemStats.battery(ctx)
            return Reply("Tienes ${b.pct}% de batería" + if (b.charging) " y se está cargando." else if (b.pct <= 15) ". Conviene cargarlo pronto." else ".")
        }
        if (has(n, "ram") || hasPhrase(n, "memoria ram")) {
            val r = SystemStats.ram(ctx)
            return Reply("Estás usando ${SystemStats.fmtGb(r.used)} de ${SystemStats.fmtGb(r.total)} de memoria RAM.")
        }
        if (has(n, "almacenamiento", "espacio") || hasPhrase(n, "memoria interna")) {
            val s = SystemStats.storage()
            return Reply("Te quedan ${SystemStats.fmtGb(s.total - s.used)} libres de ${SystemStats.fmtGb(s.total)}.")
        }
        if (has(n, "wifi", "internet", "conexion", "red")) {
            val net = SystemStats.network(ctx)
            return Reply(when {
                net.type == "Sin conexión" -> "No tienes conexión a internet ahora mismo."
                net.type == "Wi-Fi" && net.linkMbps > 0 -> "Estás en Wi-Fi a ${net.linkMbps} megabits por segundo."
                else -> "Estás conectado por ${net.type.lowercase(Locale.getDefault())}."
            })
        }

        // ---- clima y agenda
        if (has(n, "clima", "temperatura", "llover", "lluvia", "tiempo") && !hasPhrase(n, "cuanto tiempo", "tiempo de pantalla")) {
            if (WeatherRepo.city(ctx).isBlank()) return Reply("Aún no sé tu ciudad. En la página Hoy toca la tarjeta del clima para elegirla.")
            val w = WeatherRepo.cached(ctx) ?: return Reply("Todavía no tengo el clima. Abre la página Hoy para actualizarlo.")
            val desc = WeatherRepo.describe(w.code).second.lowercase(Locale.getDefault())
            return Reply("En ${WeatherRepo.displayName(ctx)} hay ${w.temp} grados y está $desc. Máxima ${w.max}, mínima ${w.min}.")
        }
        if (has(n, "agenda", "calendario", "citas", "eventos", "cita") || hasPhrase(n, "que tengo hoy", "que tengo manana")) {
            if (!TodayData.has(ctx, android.Manifest.permission.READ_CALENDAR))
                return Reply("Necesito permiso para ver tu calendario. En la página Hoy toca la tarjeta Agenda.")
            val ev = TodayData.events(ctx, 2, 3)
            val tf = DateFormat.getTimeFormat(ctx)
            return Reply(if (ev.isEmpty()) "No tienes nada programado para hoy ni mañana."
            else "Tienes: " + ev.joinToString(". ") { (if (it.allDay) "Todo el día" else "A las " + tf.format(Date(it.begin))) + ", " + it.title })
        }

        // ---- volumen
        if (has(n, "volumen") || has(n, "silencio", "mute")) {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            return try {
                when {
                    has(n, "silencio", "mute", "silencia") -> { am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI); Reply("Silenciado.") }
                    has(n, "maximo", "todo") -> { am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC), AudioManager.FLAG_SHOW_UI); Reply("Volumen al máximo.") }
                    has(n, "sube", "subir", "aumenta", "mas", "alto") -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI); Reply("Subí el volumen.") }
                    has(n, "baja", "bajar", "menos", "reduce", "bajo") -> { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); Reply("Bajé el volumen.") }
                    else -> null
                }
            } catch (e: Exception) { Reply("No pude cambiar el volumen ahora.") }
        }

        // ---- temas
        if (has(n, "tema", "modo", "colores")) {
            val key = when {
                has(n, "oscuro", "noche", "nocturno") -> "noche"
                has(n, "claro", "dia", "amanecer") -> "amanecer"
                else -> ThemePalette.KEYS.firstOrNull { n.contains(TextMatch.normalize(ThemePalette.nameOf(it)).substringBefore(' ')) }
            }
            if (key != null) {
                prefs().edit().putString("selected_theme", key).apply()
                return Reply("Listo, puse el tema ${ThemePalette.nameOf(key)}.", Act.Restyle)
            }
        }

        // ---- conversación básica
        if (hasPhrase(n, "quien eres", "como te llamas", "tu nombre")) return Reply("Soy OASIS, tu asistente. Puedo abrir apps, llamar, decirte la hora, la batería, el clima y mucho más.")
        if (hasPhrase(n, "que puedes hacer", "que sabes hacer", "ayudame") || n == "ayuda")
            return Reply("Puedo abrir aplicaciones, llamar, mandar mensajes, leerte tus mensajes y los textos que me muestres con la cámara, decirte la hora, la batería, el clima y tu agenda, poner alarmas y recordatorios, encender la linterna, recordar datos que me pidas y hacer tu rutina de buenos días.")
        if (has(n, "gracias")) return Reply("¡Con gusto!")
        if (has(n, "hola", "buenas", "buenos", "hey")) return Reply(greeting())
        if (has(n, "chiste")) return Reply(JOKES.random())

        // ---- preguntas sobre lo que me pidieron recordar
        if (Regex("^(cual|como|donde|quien|que|cuando|cuanto)\\b").containsMatchIn(n)) {
            MemoryStore.lookup(ctx, raw)?.let { return Reply("Me dijiste: $it.") }
        }
        return null
    }

    /** "recuérdame a las 5 y media tomar la pastilla" -> AlarmAt(17, 30, "tomar la pastilla"). Elige la próxima ocurrencia. */
    private fun parseAlarmAt(n: String): Act.AlarmAt? {
        val m = ALARM_AT.find(n) ?: return null
        var h = m.groupValues[1].toIntOrNull() ?: return null
        if (h > 23) return null
        var min = m.groupValues[2].toIntOrNull() ?: 0
        when (m.groupValues[3]) { "media" -> min = 30; "cuarto" -> min = 15 }
        m.groupValues[4].toIntOrNull()?.let { min = it }
        if (min > 59) return null
        val part = m.groupValues[5]
        val label = m.groupValues[6].trim().removePrefix("para ").removePrefix("que ").trim()
        if (h < 12 && (part.contains("tarde") || part.contains("noche") || part == "pm")) h += 12
        else if (h == 12 && part.contains("manana")) h = 0
        else if (h in 1..12 && part.isEmpty()) {
            // sin "de la mañana/tarde": la próxima vez que ocurra esa hora
            val now = Calendar.getInstance()
            val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val a = (h % 12) * 60 + min
            val p = a + 720
            h = if (a > nowMin) h % 12 else if (p > nowMin) (h % 12) + 12 else h % 12
        }
        return Act.AlarmAt(h % 24, min, label.replaceFirstChar { it.uppercase() })
    }

    private fun contactNumber(name: String): String? {
        if (!TodayData.has(ctx, android.Manifest.permission.READ_CONTACTS)) return null
        return try {
            ctx.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$name%"), null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) { null }
    }

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return (if (h < 12) "¡Buenos días!" else if (h < 19) "¡Buenas tardes!" else "¡Buenas noches!") + " ¿En qué te ayudo?"
    }

    private fun timeSpeech(): String {
        val c = Calendar.getInstance()
        val h24 = c.get(Calendar.HOUR_OF_DAY)
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val m = c.get(Calendar.MINUTE)
        val part = when { h24 < 12 -> "de la mañana"; h24 < 19 -> "de la tarde"; else -> "de la noche" }
        val minutes = if (m == 0) "en punto" else if (m < 10) "con 0$m" else "con $m"
        return (if (h12 == 1) "Es la una" else "Son las $h12") + " $minutes $part."
    }

    /** "cuánto es 12 por 3" -> "12 por 3 son 36". */
    private fun mathAnswer(lower: String): String? {
        var t = lower
            .replace("cuánto es", "").replace("cuanto es", "").replace("cuánto son", "").replace("cuanto son", "")
            .replace("cuánto da", "").replace("cuanto da", "")
            .replace(" por ", "*").replace(" multiplicado por ", "*")
            .replace(" más ", "+").replace(" mas ", "+")
            .replace(" menos ", "-")
            .replace(" entre ", "/").replace(" dividido entre ", "/").replace(" dividido por ", "/")
            .replace(" elevado a ", "^")
        t = t.replace("?", "").replace("¿", "").trim()
        if (t.none { it.isDigit() }) return null
        val v = Calc.eval(t) ?: return null
        return "El resultado es " + Calc.format(v)
    }

    private fun parseMinutes(n: String): Int {
        if (n.contains("media hora")) return 30
        val words = mapOf("un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6,
            "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "quince" to 15, "veinte" to 20, "treinta" to 30)
        val m = Regex("(\\d+|un|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|quince|veinte|treinta)\\s*(segundos?|minutos?|horas?)").find(n)
            ?: return 0
        val qty = m.groupValues[1].toIntOrNull() ?: words[m.groupValues[1]] ?: return 0
        return when {
            m.groupValues[2].startsWith("seg") -> ceil(qty / 60.0).toInt().coerceAtLeast(1)
            m.groupValues[2].startsWith("hor") -> qty * 60
            else -> qty
        }
    }

    // ====================== IA LOCAL (Ollama) ======================
    /**
     * POST {URL}/api/chat con {"model", "stream": false, "messages": [...]}.
     * Se ejecuta siempre en el hilo secundario de [io]; nunca toca la interfaz.
     */
    private fun cloud(raw: String): Reply {
        val base = serverUrl()
        val model = model()
        val now = java.text.SimpleDateFormat("EEEE d 'de' MMMM, HH:mm", Locale.getDefault()).format(Date())

        history.add("user" to raw)
        while (history.size > 8) history.removeAt(0)
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM + memoryNote() + "\nAhora: $now."))
        history.forEach { (role, text) -> msgs.put(JSONObject().put("role", role).put("content", text)) }
        val body = JSONObject()
            .put("model", model)
            .put("stream", false)
            .put("messages", msgs)
            .put("options", JSONObject().put("num_predict", 200))

        val conn = try { URL("$base/api/chat").openConnection() as HttpURLConnection } catch (e: Exception) {
            history.removeLastOrNull(); return Reply("La URL del servidor no es válida. Revísala en Ajustes.")
        }
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 60_000
            conn.readTimeout = 120_000        // la primera carga del modelo local tarda
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code == 404) { history.removeLastOrNull(); return Reply("El servidor no tiene el modelo $model. En la computadora ejecuta: ollama pull $model") }
            if (code !in 200..299) { history.removeLastOrNull(); return Reply("El servidor respondió con error $code.") }
            val text = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .getJSONObject("message").getString("content").trim()
            val reply = parseCloud(text)
            history.add("assistant" to reply.text)
            return reply
        } catch (e: java.io.IOException) {
            history.removeLastOrNull()
            return Reply("No encontré el servidor en $base. Revisa que ollama serve esté corriendo.")
        } finally { conn.disconnect() }
    }

    private fun memoryNote(): String {
        val l = MemoryStore.list(ctx)
        return if (l.isEmpty()) "" else "\nDatos que el usuario te pidió recordar: " + l.takeLast(15).joinToString("; ") + "."
    }

    /** Separa la frase hablada de una posible línea "ACCION: {json}". */
    private fun parseCloud(text: String): Reply {
        var act: Act? = null
        val spoken = text.lines().filter { line ->
            val l = line.trim()
            if (l.startsWith("ACCION:")) {
                try {
                    val j = JSONObject(l.removePrefix("ACCION:").trim())
                    act = when (j.optString("t")) {
                        "abrir" -> Act.OpenApp(j.optString("app"))
                        "llamar" -> Act.Call(j.optString("contacto"))
                        "mensaje" -> Act.Message(j.optString("contacto"))
                        "temporizador" -> Act.Timer(j.optInt("min", 0)).takeIf { it.minutes > 0 }
                        "linterna" -> Act.OpenApp("linterna")
                        "leer" -> Act.ReadAloud
                        "alarma" -> Act.AlarmAt(j.optInt("hora", 0).coerceIn(0, 23), j.optInt("min", 0).coerceIn(0, 59), j.optString("texto"))
                        else -> null
                    }
                } catch (_: Exception) {}
                false
            } else true
        }.joinToString(" ").trim()
        return Reply(spoken.ifBlank { "Listo." }, act)
    }

    companion object {
        const val DEFAULT_URL = "http://127.0.0.1:11434"
        const val DEFAULT_MODEL = "qwen2.5:3b"
        const val FALLBACK = "No estoy segura de cómo ayudarte con eso. Prueba: abre WhatsApp, llama a mamá, ¿qué hora es?, ¿cuánta batería tengo?"

        private val MEM_SAVE = Regex("^(recuerda|acuérdate|acuerdate|ten en cuenta|no olvides)(?: que)?\\s+(.+)$")
        private val NUMBER_OF = Regex("n[uú]mero (?:de|tiene)\\s+(.+?)\\??$")
        private val ALARM_AT = Regex("(?:recuerdame|avisame|ponme una alarma|pon una alarma|despiertame|alarma)\\s+(?:para\\s+)?a las (\\d{1,2})(?::(\\d{2}))?(?: y (media|cuarto)| con (\\d{1,2}))?\\s*(de la manana|de la tarde|de la noche|am|pm)?\\s*(.*)$")
        private val NOTE = Regex("^(anota|apunta|anótame|apúntame|recuérdame|recuerdame|nueva nota|crea una nota|agrega una nota|añade una nota)(?: que| de| para)?\\s+(.+)$")

        private val JOKES = listOf(
            "¿Qué le dice un jardinero a otro? Nos vemos en el rosal.",
            "¿Por qué los pájaros vuelan hacia el sur? Porque caminando tardarían mucho.",
            "¿Qué hace una abeja en el gimnasio? ¡Zum-ba!"
        )

        private const val SYSTEM = "Eres OASIS, el asistente de voz de un teléfono Android pensado para personas con poca experiencia con la tecnología. " +
            "Responde SIEMPRE en español, con lenguaje sencillo y máximo 2 frases cortas, sin listas, sin markdown y sin emojis. " +
            "Si el usuario te pide hacer algo en el teléfono, responde con una frase y añade al final UNA línea exacta con el formato " +
            "ACCION: {\"t\":\"abrir\",\"app\":\"nombre\"} o ACCION: {\"t\":\"llamar\",\"contacto\":\"nombre\"} o " +
            "ACCION: {\"t\":\"mensaje\",\"contacto\":\"nombre\"} o ACCION: {\"t\":\"temporizador\",\"min\":5} o ACCION: {\"t\":\"linterna\"} o ACCION: {\"t\":\"leer\"} (leer un texto con la cámara) o " +
            "ACCION: {\"t\":\"alarma\",\"hora\":17,\"min\":30,\"texto\":\"tomar la pastilla\"}. " +
            "No inventes datos del teléfono (batería, clima, agenda): esos los responde la app por su cuenta. " +
            "Si no sabes algo, dilo con honestidad."
    }
}
