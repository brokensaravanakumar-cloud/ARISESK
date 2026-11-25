package com.jinwoo.ai.core

import android.content.Context
import android.content.Intent

object AppLauncher {
    data class AppEntry(val label: String, val pkg: String)

    private val aliases = mapOf(
        "chrome" to "com.android.chrome",
        "googlechrome" to "com.android.chrome",
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "playstore" to "com.android.vending",
        "googleplay" to "com.android.vending",
        "googleplaystore" to "com.android.vending",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "googlemaps" to "com.google.android.apps.maps",
        "bookmyshow" to "com.bt.bms"
    )

    /** Popular apps whose Play Store page can be opened directly. Unknown apps use a Play Store search. */
    private val knownPackages = mapOf(
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "facebook" to "com.facebook.katana",
        "messenger" to "com.facebook.orca",
        "snapchat" to "com.snapchat.android",
        "twitter" to "com.twitter.android",
        "youtube" to "com.google.android.youtube",
        "gmail" to "com.google.android.gm",
        "chrome" to "com.android.chrome",
        "googlemaps" to "com.google.android.apps.maps",
        "maps" to "com.google.android.apps.maps",
        "amazon" to "com.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "googlepay" to "com.google.android.apps.nbu.paisa.user",
        "zomato" to "com.application.zomato",
        "swiggy" to "in.swiggy.android",
        "bookmyshow" to "com.bt.bms",
        "uber" to "com.ubercab",
        "ola" to "com.olacabs.customer",
        "linkedin" to "com.linkedin.android",
        "discord" to "com.discord",
        "reddit" to "com.reddit.frontpage",
        "pinterest" to "com.pinterest",
        "truecaller" to "com.truecaller",
        "zoom" to "us.zoom.videomeetings",
        "duolingo" to "com.duolingo",
        "canva" to "com.canva.editor",
        "vlc" to "org.videolan.vlc",
        "shazam" to "com.shazam.android"
    )

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

    fun knownPackageFor(spokenName: String): String? = knownPackages[norm(spokenName)]

    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.pkg }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    fun find(ctx: Context, spoken: String): AppEntry? {
        val want = norm(spoken)
        if (want.isEmpty()) return null
        aliases[want]?.let { pkg ->
            if (isInstalled(ctx, pkg)) return AppEntry(spoken, pkg)
        }
        val apps = launchable(ctx)
        apps.firstOrNull { norm(it.label) == want }?.let { return it }
        apps.filter { norm(it.label).startsWith(want) }.minByOrNull { it.label.length }?.let { return it }
        apps.filter { norm(it.label).contains(want) }.minByOrNull { it.label.length }?.let { return it }
        return null
    }

    /** @return true if started directly, false if a tap-to-continue notification was posted instead */
    fun open(ctx: Context, entry: AppEntry): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(entry.pkg) ?: return false
        return Launch.start(ctx, intent, "Open ${entry.label}")
    }
}
