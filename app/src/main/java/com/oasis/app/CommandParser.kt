package com.oasis.app

/**
 * Interpreta la frase del usuario ("abre claude", "llama a maría", "manda mensaje a ana").
 * Lógica pura (sin Android) para poder probarla con tests unitarios.
 *
 * Trabaja con PALABRAS completas, nunca con trozos: así "claude" no pierde su "la",
 * ni "telegram" su "a". Conserva los acentos del nombre (contactos como "María").
 */
object CommandParser {

    data class Result(val command: String, val params: Map<String, String>)

    private val OPEN = setOf("abrir", "abre", "abra", "lanza", "lanzar", "inicia", "iniciar", "ejecuta", "ejecutar")
    private val CALL = setOf("llamar", "llama", "llamale", "telefonear", "telefonea", "marcar", "marca")
    private val MESSAGE = setOf(
        "mensaje", "mensajes", "mandar", "manda", "mandale",
        "enviar", "envia", "enviale", "escribir", "escribe", "escribele"
    )
    private val CANCEL = setOf("cancelar", "cancela", "detener", "detente", "parar", "basta")
    private val HELP_WORDS = setOf("ayuda", "ayudame")
    private val HELP_PHRASES = listOf("que puedes hacer", "que haces", "que sabes hacer")

    /** Palabras de relleno que se descartan al extraer el nombre. */
    private val FILLER = setOf(
        "el", "la", "los", "las", "un", "una", "unos", "unas",
        "a", "al", "de", "del", "para", "por", "con", "sin", "sobre", "tras", "hacia", "hasta",
        "mi", "mis", "favor", "app", "apps", "aplicacion", "aplicaciones", "contacto", "numero"
    )

    fun parse(raw: String): Result? {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) return null
        val norm = tokens.map { TextMatch.normalize(it) }
        val joined = norm.joinToString(" ")

        fun has(words: Set<String>) = norm.any { it in words }

        return when {
            has(OPEN) -> {
                val app = subject(tokens, norm, OPEN)
                Result("open_app", mapOf("app" to app.ifBlank { "desconocido" }))
            }
            has(CALL) -> Result("call", mapOf("contact" to contact(tokens, norm, CALL)))
            has(MESSAGE) -> Result("message", mapOf("contact" to contact(tokens, norm, MESSAGE)))
            has(CANCEL) -> Result("cancel", emptyMap())
            has(HELP_WORDS) || HELP_PHRASES.any { joined.contains(it) } -> Result("help", emptyMap())
            else -> null
        }
    }

    private fun tokenize(raw: String): List<String> =
        raw.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}+\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    /** Palabras que quedan al quitar los verbos del comando y el relleno. */
    private fun subject(tokens: List<String>, norm: List<String>, verbs: Set<String>): String =
        tokens.filterIndexed { i, _ -> norm[i] !in verbs && norm[i] !in FILLER }
            .joinToString(" ")

    /** Como [subject], pero si es un teléfono dictado ("55 12 34 56 78") lo junta: "5512345678". */
    private fun contact(tokens: List<String>, norm: List<String>, verbs: Set<String>): String {
        val s = subject(tokens, norm, verbs)
        val looksLikeNumber = s.count { it.isDigit() } >= 3 && s.all { it.isDigit() || it == ' ' || it == '+' }
        return if (looksLikeNumber) s.replace(" ", "") else s
    }
}
