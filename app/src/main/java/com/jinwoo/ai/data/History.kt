package com.jinwoo.ai.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Optional conversation history, stored only on this phone in the app's private storage. */
class History(context: Context) {
    data class Entry(val time: Long, val role: String, val text: String)

    private val file = File(context.applicationContext.filesDir, "history.json")
    private val lock = Any()

    fun all(): List<Entry> = synchronized(lock) {
        try {
            if (!file.exists()) return emptyList()
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Entry(o.optLong("t"), o.optString("r"), o.optString("x"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(role: String, text: String) = synchronized(lock) {
        try {
            val list = all().toMutableList()
            list.add(Entry(System.currentTimeMillis(), role, text))
            val trimmed = if (list.size > 300) list.takeLast(300) else list
            val arr = JSONArray()
            trimmed.forEach { arr.put(JSONObject().put("t", it.time).put("r", it.role).put("x", it.text)) }
            file.writeText(arr.toString())
        } catch (e: Exception) {
            // history is best-effort
        }
    }

    fun clear() = synchronized(lock) { file.delete(); Unit }
}
