package com.jinwoo.ai.core

import kotlin.math.roundToInt

/** Parses spoken clock times and durations. Pure Kotlin, no Android deps. */
object TimeParse {

    /** Returns (hour24, minute) or null. nowH/nowM are used to pick AM/PM when the speaker did not say it. */
    fun parseClock(input: String, nowH: Int, nowM: Int): Pair<Int, Int>? {
        var s = input.lowercase()
            .replace("a.m.", "am").replace("p.m.", "pm")
            .replace("a. m.", "am").replace("p. m.", "pm")
            .replace("o'clock", "").replace("o clock", "")
        s = NumberWords.toDigits(s)

        var ampm: String? = null
        if (Regex("\\bam\\b|\\bin the morning\\b").containsMatchIn(s)) ampm = "am"
        if (Regex("\\bpm\\b|\\bin the evening\\b|\\bin the afternoon\\b|\\bat night\\b|\\btonight\\b").containsMatchIn(s)) ampm = "pm"
        s = s.replace(Regex("\\b(am|pm|in the morning|in the evening|in the afternoon|at night|tonight)\\b"), " ").trim()

        if (s.contains("noon")) return Pair(12, 0)
        if (s.contains("midnight")) return Pair(0, 0)

        // "half past 6", "quarter past 6", "quarter to 7"
        val rel = Regex("(half past|quarter past|quarter to)\\s*(\\d{1,2})").find(s)
        if (rel != null) {
            val kind = rel.groupValues[1]
            var h = rel.groupValues[2].toInt()
            val m: Int
            when (kind) {
                "half past" -> m = 30
                "quarter past" -> m = 15
                else -> { m = 45; h = if (h == 1) 12 else h - 1 }
            }
            return resolve(h, m, ampm, nowH, nowM)
        }

        val abs = Regex("(\\d{1,2})(?:[:.\\s]\\s*(\\d{2}))?").find(s) ?: return null
        val h = abs.groupValues[1].toInt()
        val m = if (abs.groupValues[2].isEmpty()) 0 else abs.groupValues[2].toInt()
        return resolve(h, m, ampm, nowH, nowM)
    }

    private fun resolve(h: Int, m: Int, ampm: String?, nowH: Int, nowM: Int): Pair<Int, Int>? {
        if (m !in 0..59) return null
        if (ampm == "am") {
            if (h !in 1..12) return null
            return Pair(h % 12, m)
        }
        if (ampm == "pm") {
            if (h !in 1..12) return null
            return Pair(h % 12 + 12, m)
        }
        if (h == 0 || h in 13..23) return Pair(h, m)
        if (h == 24) return Pair(0, m)
        if (h !in 1..12) return null
        val a = Pair(h % 12, m)
        val b = Pair(h % 12 + 12, m)
        val nowMin = nowH * 60 + nowM
        fun delta(p: Pair<Int, Int>): Int {
            val d = ((p.first * 60 + p.second - nowMin) + 1440) % 1440
            return if (d == 0) 1440 else d
        }
        return if (delta(a) <= delta(b)) a else b
    }

    /** "10 minutes", "1 hour 30 minutes", "half an hour", "two and a half hours" -> seconds */
    fun parseDurationSeconds(input: String): Int? {
        var s = NumberWords.toDigits(input.lowercase())
        s = s.replace("an hour and a half", "90 minutes")
            .replace("half an hour", "30 minutes")
            .replace("half hour", "30 minutes")
            .replace(Regex("(\\d+)\\s*and a half\\s*hours?")) { "${it.groupValues[1].toInt() * 60 + 30} minutes" }
            .replace(Regex("(\\d+)\\s*and a half\\s*minutes?")) { "${it.groupValues[1].toInt() * 60 + 30} seconds" }
            .replace(Regex("\\ban? hour\\b"), "1 hour")
            .replace(Regex("\\ban? minute\\b"), "1 minute")
            .replace(Regex("\\ban? second\\b"), "1 second")
        val re = Regex("(\\d+(?:\\.\\d+)?)\\s*(hours?|hrs?|h|minutes?|mins?|m|seconds?|secs?|s)\\b")
        var total = 0.0
        var found = false
        for (m in re.findAll(s)) {
            val n = m.groupValues[1].toDouble()
            val u = m.groupValues[2]
            total += when {
                u.startsWith("h") -> n * 3600
                u.startsWith("m") -> n * 60
                else -> n
            }
            found = true
        }
        return if (found && total > 0) total.roundToInt() else null
    }

    fun formatClock(h24: Int, m: Int): String {
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val suffix = if (h24 < 12) "AM" else "PM"
        return "%d:%02d %s".format(h12, m, suffix)
    }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val parts = ArrayList<String>()
        if (h > 0) parts.add("$h hour" + if (h == 1) "" else "s")
        if (m > 0) parts.add("$m minute" + if (m == 1) "" else "s")
        if (s > 0) parts.add("$s second" + if (s == 1) "" else "s")
        return parts.joinToString(" ")
    }
}
