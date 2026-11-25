package com.jinwoo.ai.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Looks up a phone number by the name you speak. Needs the Contacts permission; data never leaves the phone. */
object Contacts {
    data class Hit(val name: String, val number: String)

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun find(ctx: Context, spoken: String): Hit? {
        val digits = spoken.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 5 && spoken.none { it.isLetter() }) return Hit(digits, digits)
        if (!hasPermission(ctx)) return null
        val q = spoken.trim().lowercase()
        if (q.isEmpty()) return null
        val hits = ArrayList<Hit>()
        try {
            ctx.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                arrayOf("%$q%"),
                null
            )?.use { c ->
                while (c.moveToNext() && hits.size < 50) {
                    hits.add(Hit(c.getString(0) ?: "", c.getString(1) ?: ""))
                }
            }
        } catch (e: Exception) {
            return null
        }
        return hits.filter { it.number.isNotBlank() }
            .minWithOrNull(compareBy<Hit> { it.name.lowercase() != q }.thenBy { !it.name.lowercase().startsWith(q) }.thenBy { it.name.length })
    }

    /** wa.me wants digits only with country code. A bare 10-digit number is assumed to be Indian (+91). */
    fun whatsappNumber(raw: String): String {
        var d = raw.filter { it.isDigit() }.trimStart('0')
        if (d.length == 10) d = "91$d"
        return d
    }
}
