package com.oasis.app

import java.text.Normalizer

/** Utilidades de texto puras (sin Android) para comparar lo que dice el usuario con nombres de apps y contactos. */
object TextMatch {

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** Minúsculas y sin acentos: "Galería" -> "galeria". */
    fun normalize(s: String): String =
        DIACRITICS.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "").trim()

    /** ¿[word] aparece en [text] como palabra completa? ("x" no aparece en "taxi"). */
    fun containsWord(text: String, word: String): Boolean =
        Regex("(^|\\s)" + Regex.escape(word) + "(\\s|$)").containsMatchIn(text)

    /** Distancia de edición entre dos textos. */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[b.length]
    }

    /** Errores tolerados al comparar un nombre de [length] letras (reconocimiento de voz imperfecto). */
    fun fuzzyLimit(length: Int): Int = when {
        length >= 7 -> 2
        length >= 4 -> 1
        else -> 0
    }
}
