package com.jinwoo.ai.core

/** Detects "Hey Jin Woo" / "Jin Woo" in recognizer text. Pure Kotlin, no Android deps. */
object WakeMatcher {
    data class Match(val remaining: String)

    // Speech engines often mishear the name, so a few close spellings are accepted.
    private val core = "(?:sung\\s+)?(?:jin|gin|jinn|jean|gym|chin)\\s?(?:woo|wu|wo|wuh|who|u)\\b"
    private val withHey = Regex("\\b(?:hey|hi|hello|okay|ok|yo)\\s+$core")
    private val bare = Regex("\\b$core")
    private val arise = "(?:arise|a rise|arize|a rice|erise|uh rise)\\b"
    private val merged = Regex("\\b(?:jinwoo|ginwoo|jinu|jinwu)\\b")

    fun match(raw: String, requireHey: Boolean, extraPhrases: List<String> = emptyList()): Match? {
        val t = raw.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null

        for (p in extraPhrases) {
            val phrase = p.lowercase().trim()
            if (phrase.isNotEmpty()) {
                val i = t.indexOf(phrase)
                if (i >= 0) return Match(t.substring(i + phrase.length).trim())
            }
        }

        Regex("\\b(?:hey|hi|hello|okay|ok|yo)\\s+$arise").find(t)?.let { return Match(t.substring(it.range.last + 1).trim()) }
        if (!requireHey) {
            Regex("\\b$arise").find(t)?.let { return Match(t.substring(it.range.last + 1).trim()) }
        }
        withHey.find(t)?.let { return Match(t.substring(it.range.last + 1).trim()) }
        if (!requireHey) {
            bare.find(t)?.let { return Match(t.substring(it.range.last + 1).trim()) }
            merged.find(t)?.let { return Match(t.substring(it.range.last + 1).trim()) }
        } else {
            Regex("\\b(?:hey|hi|hello|okay|ok|yo)\\s+(?:jinwoo|ginwoo|jinu)\\b").find(t)
                ?.let { return Match(t.substring(it.range.last + 1).trim()) }
        }
        return null
    }

    private val yes = setOf(
        "yes", "yeah", "yep", "yup", "sure", "ok", "okay", "confirm", "confirmed", "proceed",
        "do it", "go ahead", "affirmative", "correct", "yes please", "please do"
    )
    private val no = setOf(
        "no", "nope", "nah", "cancel", "stop", "dont", "don't", "do not", "negative", "abort", "never mind", "nevermind"
    )

    /** true = yes, false = no, null = unclear */
    fun yesNo(raw: String): Boolean? {
        val t = raw.lowercase().replace(Regex("[^a-z' ]"), " ").replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return null
        if (t in no || no.any { t.startsWith("$it ") }) return false
        if (t in yes || yes.any { t.startsWith("$it ") }) return true
        return null
    }
}
