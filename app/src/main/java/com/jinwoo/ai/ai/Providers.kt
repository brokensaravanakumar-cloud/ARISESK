package com.jinwoo.ai.ai

enum class ApiKind { OPENAI_COMPAT, GEMINI }

data class ProviderSpec(
    val id: String,
    val label: String,
    val kind: ApiKind,
    val defaultBase: String,
    val defaultModel: String,
    /** Honest pricing note shown in the UI. Provider terms change - always check their site. */
    val pricingNote: String,
    val keyHint: String,
    val keyUrl: String
)

object Providers {
    val all: List<ProviderSpec> = listOf(
        ProviderSpec(
            id = "gemini",
            label = "Google Gemini",
            kind = ApiKind.GEMINI,
            defaultBase = "https://generativelanguage.googleapis.com/v1beta",
            defaultModel = "gemini-2.5-flash",
            pricingNote = "Google offers a rate-limited free tier for some Gemini models through AI Studio. " +
                "Which models are free, and the limits, are set by Google and can change. Heavier use needs a paid plan.",
            keyHint = "AIza...",
            keyUrl = "https://aistudio.google.com/apikey"
        ),
        ProviderSpec(
            id = "openai",
            label = "OpenAI",
            kind = ApiKind.OPENAI_COMPAT,
            defaultBase = "https://api.openai.com/v1",
            defaultModel = "gpt-4o-mini",
            pricingNote = "PAID. The OpenAI API has no free tier - it needs prepaid billing credit on your OpenAI account. " +
                "A ChatGPT subscription does not include API access.",
            keyHint = "sk-...",
            keyUrl = "https://platform.openai.com/api-keys"
        ),
        ProviderSpec(
            id = "openrouter",
            label = "OpenRouter",
            kind = ApiKind.OPENAI_COMPAT,
            defaultBase = "https://openrouter.ai/api/v1",
            defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
            pricingNote = "OpenRouter lists some models with a \":free\" tag (rate-limited, with daily caps; availability changes). " +
                "All other models are paid per use.",
            keyHint = "sk-or-...",
            keyUrl = "https://openrouter.ai/keys"
        ),
        ProviderSpec(
            id = "groq",
            label = "Groq",
            kind = ApiKind.OPENAI_COMPAT,
            defaultBase = "https://api.groq.com/openai/v1",
            defaultModel = "llama-3.3-70b-versatile",
            pricingNote = "Groq offers a rate-limited free tier (no card needed at the time of writing). " +
                "Limits are set by Groq and can change; higher limits are paid.",
            keyHint = "gsk_...",
            keyUrl = "https://console.groq.com/keys"
        ),
        ProviderSpec(
            id = "custom",
            label = "Custom (OpenAI-compatible)",
            kind = ApiKind.OPENAI_COMPAT,
            defaultBase = "https://",
            defaultModel = "",
            pricingNote = "Any service that speaks the OpenAI chat-completions format (for example Together, Mistral, DeepSeek, " +
                "a local Ollama or LM Studio server). Cost depends on that service.",
            keyHint = "your key (can be blank for a local server)",
            keyUrl = ""
        )
    )

    fun byId(id: String): ProviderSpec = all.firstOrNull { it.id == id } ?: all.first()
}
