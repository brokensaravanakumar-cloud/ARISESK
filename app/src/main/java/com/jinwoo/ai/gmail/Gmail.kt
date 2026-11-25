package com.jinwoo.ai.gmail

import android.app.PendingIntent
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume

sealed class GmailToken {
    data class Ok(val token: String) : GmailToken()
    data class NeedsConsent(val pending: PendingIntent) : GmailToken()
    data class Fail(val message: String) : GmailToken()
}

/**
 * Read-only Gmail access through Google's official OAuth consent screen.
 * JIN WOO never sees your Google password. Sending mail is NOT done through the API:
 * a draft is opened in the Gmail app and you (or your "tap send" command) send it.
 */
object GmailAuth {
    const val SCOPE_READONLY = "https://www.googleapis.com/auth/gmail.readonly"

    fun request(): AuthorizationRequest =
        AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE_READONLY))).build()

    suspend fun token(ctx: Context): GmailToken = suspendCancellableCoroutine { cont ->
        Identity.getAuthorizationClient(ctx.applicationContext).authorize(request())
            .addOnSuccessListener { r ->
                val pi = r.pendingIntent
                if (r.hasResolution() && pi != null) {
                    cont.resume(GmailToken.NeedsConsent(pi))
                } else {
                    val t = r.accessToken
                    if (t != null) cont.resume(GmailToken.Ok(t))
                    else cont.resume(GmailToken.Fail("Google did not return an access token."))
                }
            }
            .addOnFailureListener { e ->
                val msg = e.message ?: "Google sign-in failed"
                val hint = if (msg.contains("10:") || msg.contains("DEVELOPER_ERROR", true))
                    " - the Google Cloud OAuth client for this app is not set up yet (see README, section Gmail)."
                else ""
                cont.resume(GmailToken.Fail(msg + hint))
            }
    }
}

object GmailClient {
    data class Summary(val unreadCount: Int, val items: List<String>)

    suspend fun unread(token: String, max: Int = 3): Summary = withContext(Dispatchers.IO) {
        val label = JSONObject(get(token, "https://gmail.googleapis.com/gmail/v1/users/me/labels/INBOX"))
        val count = label.optInt("messagesUnread", 0)
        val items = ArrayList<String>()
        if (count > 0) {
            val list = JSONObject(
                get(token, "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=is%3Aunread%20in%3Ainbox&maxResults=$max")
            )
            val msgs = list.optJSONArray("messages")
            if (msgs != null) {
                for (i in 0 until msgs.length()) {
                    val id = msgs.getJSONObject(i).optString("id")
                    val m = JSONObject(
                        get(
                            token,
                            "https://gmail.googleapis.com/gmail/v1/users/me/messages/$id" +
                                "?format=metadata&metadataHeaders=From&metadataHeaders=Subject"
                        )
                    )
                    val headers = m.optJSONObject("payload")?.optJSONArray("headers")
                    var from = ""
                    var subject = ""
                    if (headers != null) {
                        for (k in 0 until headers.length()) {
                            val h = headers.getJSONObject(k)
                            when (h.optString("name").lowercase()) {
                                "from" -> from = cleanSender(h.optString("value"))
                                "subject" -> subject = h.optString("value")
                            }
                        }
                    }
                    items.add("from $from: ${subject.ifBlank { "no subject" }}")
                }
            }
        }
        Summary(count, items)
    }

    private fun cleanSender(raw: String): String {
        val name = raw.substringBefore("<").trim().trim('"')
        return name.ifBlank { raw.substringAfter("<").substringBefore(">") }
    }

    private fun get(token: String, url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code == 401) throw IllegalStateException("Gmail access expired. Reconnect Gmail in Settings, Phone control.")
            if (code == 403) throw IllegalStateException("Gmail refused access (403). Make sure the Gmail API is enabled in your Google Cloud project and your account is a test user.")
            if (code !in 200..299) throw IllegalStateException("Gmail returned error $code.")
            return body
        } finally {
            conn.disconnect()
        }
    }
}
