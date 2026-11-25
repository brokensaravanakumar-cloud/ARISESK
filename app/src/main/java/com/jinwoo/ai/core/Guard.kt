package com.jinwoo.ai.core

/**
 * Safety rules for screen control. Pure Kotlin so it can be unit tested.
 * Payments, purchases, credentials and destructive actions are always blocked, whatever the user says.
 */
object Guard {
    val allowedPackages = setOf(
        "com.android.vending",        // Google Play Store
        "com.android.chrome",         // Chrome
        "com.google.android.gm",      // Gmail
        "com.bt.bms",                 // BookMyShow
        "com.whatsapp",               // WhatsApp (send button only, after your spoken confirmation)
        "com.google.android.youtube"  // YouTube (play a result)
    )

    private val blockedWords = Regex(
        "\\b(pay|pays|payment|payments|paying|place order|buy|purchase|checkout|check out|subscribe|" +
            "upi|pin|cvv|otp|password|passcode|card number|net banking|netbanking|wallet|transfer|" +
            "send money|donate|delete|uninstall|remove|factory reset|sign out|log out|logout)\\b",
        RegexOption.IGNORE_CASE
    )

    // Play Store paid-app buttons start with a price such as "$4.99" or "₹ 149"
    private val priceLike = Regex("^\\s*(?:[$€£¥₹]|rs\\.?|inr|usd)\\s*\\d", RegexOption.IGNORE_CASE)

    fun isBlockedTarget(text: String): Boolean {
        if (text.isBlank()) return false
        return blockedWords.containsMatchIn(text) || priceLike.containsMatchIn(text)
    }

    private val sensitiveField = Regex("password|passcode|\\bpin\\b|\\botp\\b|cvv|card number|security code", RegexOption.IGNORE_CASE)

    fun isSensitiveField(hintOrDescription: String?): Boolean {
        if (hintOrDescription.isNullOrBlank()) return false
        return sensitiveField.containsMatchIn(hintOrDescription)
    }
}
