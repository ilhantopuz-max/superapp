package com.ceoasistanim.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class CeoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val ch = NotificationChannel(CHANNEL, "Hatırlatmalar", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Görev, toplantı ve randevu hatırlatmaları"
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    companion object {
        const val CHANNEL = "reminders"
    }
}
