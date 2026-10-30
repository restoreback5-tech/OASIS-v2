package com.oasis.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {

    @Test fun normalizaAcentosYMayusculas() {
        assertEquals("galeria", TextMatch.normalize("Galería"))
        assertEquals("maria", TextMatch.normalize("  MARÍA "))
    }

    @Test fun palabraCompleta() {
        assertTrue(TextMatch.containsWord("abre x", "x"))
        assertFalse(TextMatch.containsWord("taxi", "x"))
        assertFalse(TextMatch.containsWord("excel", "x"))
    }

    @Test fun distanciaDeEdicion() {
        assertEquals(0, TextMatch.levenshtein("netflix", "netflix"))
        assertEquals(1, TextMatch.levenshtein("netflis", "netflix"))
        assertEquals(3, TextMatch.levenshtein("abc", ""))
    }

    @Test fun aliasCortoNoCoincideDentroDePalabras() {
        assertEquals(null, AppRegistry.packageFor("taxi"))
        assertEquals("com.twitter.android", AppRegistry.packageFor("abre x"))
        assertEquals("com.anthropic.claude", AppRegistry.packageFor("claude"))
        assertEquals("com.google.android.apps.maps", AppRegistry.packageFor("google maps"))
    }
}
