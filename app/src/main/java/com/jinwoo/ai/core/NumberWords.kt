package com.jinwoo.ai.core

/** Converts spoken number words ("twenty five", "ten") into digits. Pure Kotlin, no Android deps. */
object NumberWords {
    private val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    fun toDigits(input: String): String {
        val tokens = input.split(" ").filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val tenVal = tens[t]
            if (tenVal != null) {
                val next = tokens.getOrNull(i + 1)
                val nextUnit = if (next != null) units[next] else null
                if (nextUnit != null && nextUnit in 1..9) {
                    out.add((tenVal + nextUnit).toString())
                    i += 2
                    continue
                }
                out.add(tenVal.toString())
            } else if (units[t] != null) {
                // keep the word "one" when used as a pronoun ("which one") - only convert next to a unit/time word
                out.add(units[t].toString())
            } else {
                out.add(t)
            }
            i++
        }
        return out.joinToString(" ")
    }
}
