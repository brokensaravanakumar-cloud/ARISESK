package com.jinwoo.ai.core

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.jinwoo.ai.JinApp
import com.jinwoo.ai.R

/**
 * Android 10+ blocks apps from opening other apps from the background. JIN WOO works around this
 * the legitimate way: if you granted "Display over other apps" (or the app is on screen) it opens
 * the app directly; otherwise it shows a tap-to-continue notification instead of failing silently.
 */
object Launch {
    fun canStartDirectly(ctx: Context): Boolean = Visibility.appVisible || Settings.canDrawOverlays(ctx)

    /** @return true if the activity was started, false if a "tap to continue" notification was posted instead. */
    fun start(ctx: Context, intent: Intent, label: String): Boolean {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (canStartDirectly(ctx)) {
            try {
                ctx.startActivity(intent)
                return true
            } catch (e: Exception) {
                // fall through to notification
            }
        }
        postTapNotification(ctx, intent, label)
        return false
    }

    private fun postTapNotification(ctx: Context, intent: Intent, label: String) {
        try {
            val pi = PendingIntent.getActivity(
                ctx, (System.currentTimeMillis() % 100000).toInt(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(ctx, JinApp.CH_ACTION)
                .setSmallIcon(R.drawable.ic_stat_jin)
                .setContentTitle("[ SYSTEM ] $label")
                .setContentText("Tap to continue, Master")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setColor(0xFF19D3FF.toInt())
                .build()
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(9001, n)
        } catch (e: Exception) {
            // notification permission missing - nothing else we can do
        }
    }
}

/**
 * A 1x1 invisible overlay window. Holding one (with the "Display over other apps" permission)
 * is what lets the listening service open apps for you while the phone is in your pocket.
 */
object OverlayAnchor {
    private var view: View? = null

    fun show(ctx: Context) {
        if (view != null || !Settings.canDrawOverlays(ctx)) return
        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = View(ctx)
            val lp = WindowManager.LayoutParams(
                1, 1,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            )
            lp.gravity = Gravity.TOP or Gravity.START
            wm.addView(v, lp)
            view = v
        } catch (e: Exception) {
            view = null
        }
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        try {
            (ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v)
        } catch (e: Exception) {
            // already gone
        }
        view = null
    }
}
