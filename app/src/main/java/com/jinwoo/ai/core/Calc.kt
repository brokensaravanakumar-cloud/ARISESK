package com.jinwoo.ai.core

import kotlin.math.pow
import kotlin.math.sqrt

/** Safe arithmetic evaluator (no scripting engine). Pure Kotlin, no Android deps. */
object Calc {

    /** Turns spoken math ("12 times 5 plus 3", "20 percent of 200") into an expression string, or null. */
    fun spokenToExpr(spoken: String): String? {
        var s = NumberWords.toDigits(spoken.lowercase()).replace(",", "")
        s = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:percent|%)\\s*of\\s*").replace(s) { "(${it.groupValues[1]}/100)*" }
        s = s.replace("square root of", "sqrt")
            .replace(Regex("\\bsquared\\b"), "^2")
            .replace(Regex("\\bcubed\\b"), "^3")
            .replace(Regex("\\bto the power of\\b|\\braised to\\b|\\bpower\\b"), "^")
            .replace(Regex("\\bmultiplied by\\b|\\btimes\\b|\\bx\\b|×"), "*")
            .replace(Regex("\\bdivided by\\b|\\bover\\b|÷"), "/")
            .replace(Regex("\\bplus\\b"), "+")
            .replace(Regex("\\bminus\\b|\\bless\\b"), "-")
            .replace(Regex("\\bmod(?:ulo)?\\b"), "%")
        s = Regex("sqrt\\s*(\\d+(?:\\.\\d+)?)").replace(s) { "sqrt(${it.groupValues[1]})" }
        val cleaned = s.replace(" ", "")
        if (!cleaned.matches(Regex("[0-9+\\-*/^%().sqrt]+"))) return null
        if (!cleaned.any { it.isDigit() }) return null
        if (!cleaned.any { it in "+-*/^%" } && !cleaned.contains("sqrt")) return null
        return cleaned
    }

    /** Full pipeline: spoken text -> formatted answer, or null if it is not a math question. */
    fun answer(spoken: String): String? {
        val expr = spokenToExpr(spoken) ?: return null
        val v = evaluate(expr) ?: return null
        return format(v)
    }

    fun format(v: Double): String {
        if (v.isNaN() || v.isInfinite()) return "undefined"
        if (v == Math.rint(v) && kotlin.math.abs(v) < 1e15) return v.toLong().toString()
        var t = "%.6f".format(v).trimEnd('0').trimEnd('.')
        if (t.isEmpty() || t == "-") t = "0"
        return t
    }

    fun evaluate(expr: String): Double? {
        return try {
            val p = Parser(expr)
            val v = p.parseExpr()
            if (p.pos != expr.length) null else v
        } catch (e: Exception) {
            null
        }
    }

    private class Parser(val s: String) {
        var pos = 0

        private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'

        fun parseExpr(): Double {
            var v = parseTerm()
            while (true) {
                when (peek()) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> return v
                }
            }
        }

        private fun parseTerm(): Double {
            var v = parsePower()
            while (true) {
                when (peek()) {
                    '*' -> { pos++; v *= parsePower() }
                    '/' -> {
                        pos++
                        val d = parsePower()
                        if (d == 0.0) throw ArithmeticException("div0")
                        v /= d
                    }
                    '%' -> {
                        pos++
                        val d = parsePower()
                        if (d == 0.0) throw ArithmeticException("mod0")
                        v %= d
                    }
                    else -> return v
                }
            }
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            if (peek() == '^') {
                pos++
                val exp = parsePower()
                return base.pow(exp)
            }
            return base
        }

        private fun parseUnary(): Double {
            return when (peek()) {
                '-' -> { pos++; -parseUnary() }
                '+' -> { pos++; parseUnary() }
                else -> parsePrimary()
            }
        }

        private fun parsePrimary(): Double {
            if (s.startsWith("sqrt", pos)) {
                pos += 4
                if (peek() != '(') throw IllegalArgumentException("sqrt(")
                pos++
                val v = parseExpr()
                if (peek() != ')') throw IllegalArgumentException(")")
                pos++
                if (v < 0) throw ArithmeticException("neg sqrt")
                return sqrt(v)
            }
            if (peek() == '(') {
                pos++
                val v = parseExpr()
                if (peek() != ')') throw IllegalArgumentException(")")
                pos++
                return v
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("number expected")
            return s.substring(start, pos).toDouble()
        }
    }
}
