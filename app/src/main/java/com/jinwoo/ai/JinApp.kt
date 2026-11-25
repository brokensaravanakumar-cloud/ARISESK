package com.jinwoo.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class JinApp : Application() {
    companion object {
        const val CH_SERVICE = "jin_service"
        const val CH_ACTION = "jin_action"
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val service = NotificationChannel(CH_SERVICE, "ARISE listening", NotificationManager.IMPORTANCE_LOW)
        service.description = "Persistent notification while ARISE listens for its name"
        service.setShowBadge(false)
        nm.createNotificationChannel(service)

        val action = NotificationChannel(CH_ACTION, "System actions", NotificationManager.IMPORTANCE_HIGH)
        action.description = "Tap-to-continue prompts when Android blocks opening an app from the background"
        nm.createNotificationChannel(action)
    }
}
