package com.jinwoo.ai.ai

import com.jinwoo.ai.data.KeyVault
import com.jinwoo.ai.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException

class AiError(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NO_KEY, BAD_KEY, RATE_LIMIT, QUOTA, NOT_FOUND, SERVER, NETWORK, BAD_RESPONSE, BAD_URL, BLOCKED }
}

data class ChatMsg(val role: String, val content: String) // role: "user" or "assistant"

data class ModelInfo(val id: String, val label: String, val free: Boolean?)

data class AiConfig(val spec: ProviderSpec, val baseUrl: String, val model: String, val key: String)

/** One small HTTP client for every provider. No third-party networking libraries (keeps the app light). */
class AiClient(private val prefs: Prefs, private val vault: KeyVault) {

    fun currentConfig(): AiConfig {
        val spec = Providers.byId(prefs.providerId)
        return AiConfig(
            spec = spec,
            baseUrl = prefs.baseUrl(spec.id, spec.defaultBase).trim().trimEnd('/'),
            model = prefs.model(spec.id, spec.defaultModel).trim(),
            key = vault.get(spec.id)
        )
    }

    suspend fun chat(system: String, history: List<ChatMsg>, user: String): String =
        withContext(Dispatchers.IO) { chatBlocking(currentConfig(), system, history, user) }

    suspend fun listModels(cfg: AiConfig = currentConfig()): List<ModelInfo> =
        withContext(Dispatchers.IO) { listModelsBlocking(cfg) }

    /** Sends a tiny real request so the user finds out about bad keys / wrong model ids immediately. */
    suspend fun testConnection(cfg: AiConfig = currentConfig()): String = withContext(Dispatchers.IO) {
        val reply = chatBlocking(cfg, "Reply with the single word: online", emptyList(), "ping")
        "Connected to ${cfg.spec.label} using model ${cfg.model}. Reply: ${reply.take(60)}"
    }

    // ------------------------------------------------------------------ chat

    private fun chatBlocking(cfg: AiConfig, system: String, history: List<ChatMsg>, user: String): String {
        validate(cfg, needModel = true)
        return when (cfg.spec.kind) {
            ApiKind.GEMINI -> geminiChat(cfg, system, history, user)
            ApiKind.OPENAI_COMPAT -> openAiChat(cfg, system, history, user)
        }
    }

    private fun openAiChat(cfg: AiConfig, system: String, history: List<ChatMsg>, user: String): String {
        val msgs = JSONArray()
        msgs.put(JSONObject().put("role", "system").put("content", system))
        history.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.content)) }
        msgs.put(JSONObject().put("role", "user").put("content", user))
        // No temperature / max_tokens on purpose: newer models reject some of those parameters.
        val body = JSONObject().put("model", cfg.model).put("messages", msgs)
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "application/json"
        if (cfg.key.isNotBlank()) headers["Authorization"] = "Bearer ${cfg.key}"
        if (cfg.spec.id == "openrouter") {
            headers["X-Title"] = "ARISE"
        }
        val resp = request("POST", cfg.baseUrl + "/chat/completions", headers, body.toString())
        return try {
            val j = JSONObject(resp)
            val choices = j.optJSONArray("choices")
            val text = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
            if (text.isBlank()) throw AiError(AiError.Kind.BAD_RESPONSE, "The model returned an empty answer. Try another model.")
            text.trim()
        } catch (e: AiError) {
            throw e
        } catch (e: Exception) {
            throw AiError(AiError.Kind.BAD_RESPONSE, "Unexpected response from ${cfg.spec.label}.")
        }
    }

    private fun geminiChat(cfg: AiConfig, system: String, history: List<ChatMsg>, user: String): String {
        val contents = JSONArray()
        history.forEach {
            val role = if (it.role == "assistant") "model" else "user"
            contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", it.content))))
        }
        contents.put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user))))
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
        val model = cfg.model.removePrefix("models/")
        val headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to cfg.key)
        val resp = request("POST", "${cfg.baseUrl}/models/$model:generateContent", headers, body.toString())
        return try {
            val j = JSONObject(resp)
            val block = j.optJSONObject("promptFeedback")?.optString("blockReason", "") ?: ""
            if (block.isNotBlank()) throw AiError(AiError.Kind.BLOCKED, "Gemini blocked that request ($block).")
            val parts = j.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val sb = StringBuilder()
            if (parts != null) for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text", "") ?: "")
            if (sb.isBlank()) throw AiError(AiError.Kind.BAD_RESPONSE, "Gemini returned an empty answer. Try again or pick another model.")
            sb.toString().trim()
        } catch (e: AiError) {
            throw e
        } catch (e: Exception) {
            throw AiError(AiError.Kind.BAD_RESPONSE, "Unexpected response from Gemini.")
        }
    }

    // ------------------------------------------------------------------ models

    private fun listModelsBlocking(cfg: AiConfig): List<ModelInfo> {
        validate(cfg, needModel = false)
        return when (cfg.spec.kind) {
            ApiKind.GEMINI -> {
                val resp = request("GET", "${cfg.baseUrl}/models?pageSize=200", mapOf("x-goog-api-key" to cfg.key), null)
                val arr = JSONObject(resp).optJSONArray("models") ?: JSONArray()
                val out = ArrayList<ModelInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val methods = o.optJSONArray("supportedGenerationMethods")
                    var canChat = false
                    if (methods != null) for (k in 0 until methods.length()) if (methods.optString(k) == "generateContent") canChat = true
                    if (!canChat) continue
                    val id = o.optString("name").removePrefix("models/")
                    if (id.isBlank()) continue
                    out.add(ModelInfo(id, o.optString("displayName", id), null))
                }
                out.sortedBy { it.id }
            }
            ApiKind.OPENAI_COMPAT -> {
                val headers = HashMap<String, String>()
                if (cfg.key.isNotBlank()) headers["Authorization"] = "Bearer ${cfg.key}"
                val resp = request("GET", cfg.baseUrl + "/models", headers, null)
                val arr = JSONObject(resp).optJSONArray("data") ?: JSONArray()
                val out = ArrayList<ModelInfo>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val lower = id.lowercase()
                    if (cfg.spec.id != "custom" &&
                        (lower.contains("whisper") || lower.contains("tts") || lower.contains("embed") ||
                            lower.contains("guard") || lower.contains("moderation") || lower.contains("dall-e") || lower.contains("image"))
                    ) continue
                    var free: Boolean? = null
                    if (cfg.spec.id == "openrouter") {
                        val pr = o.optJSONObject("pricing")
                        free = id.endsWith(":free") || (pr != null && pr.optString("prompt") == "0" && pr.optString("completion") == "0")
                    }
                    out.add(ModelInfo(id, o.optString("name", id), free))
                }
                out.sortedWith(compareByDescending<ModelInfo> { it.free == true }.thenBy { it.id })
            }
        }
    }

    // ------------------------------------------------------------------ http + errors

    private fun validate(cfg: AiConfig, needModel: Boolean) {
        if (cfg.key.isBlank() && cfg.spec.id != "custom") {
            throw AiError(AiError.Kind.NO_KEY, "No API key saved for ${cfg.spec.label}. Open Settings, then Provider and Keys.")
        }
        if (needModel && cfg.model.isBlank()) {
            throw AiError(AiError.Kind.NOT_FOUND, "No model selected. Open Settings, then Models.")
        }
        val url = cfg.baseUrl
        if (!(url.startsWith("https://") || url.startsWith("http://")) || url.length < 12) {
            throw AiError(AiError.Kind.BAD_URL, "The API base URL looks wrong: $url")
        }
        if (url.startsWith("http://") && cfg.key.isNotBlank() && !isLocalHost(url)) {
            throw AiError(AiError.Kind.BLOCKED, "Refusing to send your API key over plain http to a public server. Use https.")
        }
    }

    private fun isLocalHost(url: String): Boolean {
        val host = try { URL(url).host.lowercase() } catch (e: Exception) { return false }
        if (host == "localhost" || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) return true
        if (host.startsWith("172.")) {
            val second = host.split(".").getOrNull(1)?.toIntOrNull() ?: return false
            return second in 16..31
        }
        return host.endsWith(".local")
    }

    private fun request(method: String, url: String, headers: Map<String, String>, body: String?): String {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw AiError(AiError.Kind.BAD_URL, "The API base URL looks wrong: $url")
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 45000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) throw mapHttpError(code, text, conn.getHeaderField("Retry-After"))
            return text
        } catch (e: AiError) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AiError(AiError.Kind.NETWORK, "The AI server took too long to answer. Check your connection and try again.")
        } catch (e: UnknownHostException) {
            throw AiError(AiError.Kind.NETWORK, "No internet connection, or the server address is wrong.")
        } catch (e: IOException) {
            throw AiError(AiError.Kind.NETWORK, "Network problem: ${e.message ?: "connection failed"}")
        } finally {
            conn.disconnect()
        }
    }

    private fun mapHttpError(code: Int, body: String, retryAfter: String?): AiError {
        val apiMessage = try {
            val j = JSONObject(body)
            val e = j.opt("error")
            when (e) {
                is JSONObject -> e.optString("message", "")
                is String -> e
                else -> j.optString("message", "")
            }
        } catch (ex: Exception) {
            ""
        }
        val tail = if (apiMessage.isNotBlank()) " Details: ${apiMessage.take(160)}" else ""
        return when (code) {
            400 -> AiError(AiError.Kind.BAD_RESPONSE, "The provider rejected the request (400). The model id may be wrong.$tail")
            401 -> AiError(AiError.Kind.BAD_KEY, "The API key was rejected (401). Check that it is correct and for this provider.$tail")
            403 -> AiError(AiError.Kind.BAD_KEY, "Access denied (403). The key may lack permission, or the service is not enabled for it.$tail")
            404 -> AiError(AiError.Kind.NOT_FOUND, "Model or address not found (404). Pick a model from the Models screen.$tail")
            402 -> AiError(AiError.Kind.QUOTA, "This provider needs payment or credit for that model (402).$tail")
            429 -> {
                val wait = retryAfter?.let { " Try again in about $it seconds." } ?: " Try again in a minute."
                val quota = apiMessage.contains("quota", true) || apiMessage.contains("billing", true) || apiMessage.contains("credit", true)
                if (quota) AiError(AiError.Kind.QUOTA, "Your quota or credit for this provider is used up (429).$tail")
                else AiError(AiError.Kind.RATE_LIMIT, "Rate limit reached (429) - free tiers are limited.$wait")
            }
            in 500..599 -> AiError(AiError.Kind.SERVER, "The AI provider is having problems ($code). Try again shortly.")
            else -> AiError(AiError.Kind.BAD_RESPONSE, "Unexpected error ($code).$tail")
        }
    }

    @Suppress("unused")
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
