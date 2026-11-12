package com.oasis.app

/** Calculadora para el buscador: + - * / ^ % y paréntesis. Devuelve null si no es una cuenta. */
object Calc {
    fun eval(raw: String): Double? {
        val s = raw.lowercase().replace(" ", "").replace(',', '.').replace('x', '*').replace('×', '*').replace('÷', '/')
        if (s.isEmpty() || s.none { it in "+-*/^%" } || s.any { !(it.isDigit() || it in "+-*/^%().") }) return null
        return try {
            val p = Parser(s)
            val v = p.parseExpr()
            if (p.pos != s.length || v.isNaN() || v.isInfinite()) null else v
        } catch (e: Exception) { null }
    }

    fun format(v: Double): String =
        if (v == Math.floor(v) && Math.abs(v) < 1e12) v.toLong().toString()
        else String.format(java.util.Locale.getDefault(), "%.6f", v).trimEnd('0').trimEnd('.', ',')

    private class Parser(val s: String) {
        var pos = 0
        fun parseExpr(): Double {
            var v = parseTerm()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]; val r = parseTerm()
                v = if (op == '+') v + r else v - r
            }
            return v
        }
        fun parseTerm(): Double {
            var v = parsePow()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/' || s[pos] == '%')) {
                val op = s[pos++]; val r = parsePow()
                v = when (op) { '*' -> v * r; '/' -> v / r; else -> v % r }
            }
            return v
        }
        fun parsePow(): Double {
            val b = parseUnary()
            return if (pos < s.length && s[pos] == '^') { pos++; Math.pow(b, parsePow()) } else b
        }
        fun parseUnary(): Double {
            if (pos < s.length && s[pos] == '-') { pos++; return -parseUnary() }
            if (pos < s.length && s[pos] == '+') { pos++; return parseUnary() }
            return parseAtom()
        }
        fun parseAtom(): Double {
            if (pos < s.length && s[pos] == '(') {
                pos++; val v = parseExpr()
                if (pos >= s.length || s[pos] != ')') throw IllegalArgumentException()
                pos++; return v
            }
            val st = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (st == pos) throw IllegalArgumentException()
            return s.substring(st, pos).toDouble()
        }
    }
}
