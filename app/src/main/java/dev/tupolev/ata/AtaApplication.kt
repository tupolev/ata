package dev.tupolev.ata

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class AtaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel("monitor", "ATA monitoring", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel("alarm", "ATA movement alarms", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}
