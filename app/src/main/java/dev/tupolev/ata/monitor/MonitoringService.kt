package dev.tupolev.ata.monitor

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.tupolev.ata.MainActivity
import dev.tupolev.ata.api.DeviceRepository
import kotlinx.coroutines.*

class MonitoringService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: MonitorStore
    private lateinit var repository: DeviceRepository

    override fun onCreate() {
        super.onCreate()
        store = MonitorStore(this)
        repository = DeviceRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1001, ongoingNotification("ATA monitoring active"))
        scope.coroutineContext.cancelChildren()

        scope.launch {
            while (isActive) {
                val armed = store.armed()
                if (armed.isEmpty()) {
                    stopSelf()
                    break
                }

                armed.forEach { config ->
                    runCatching {
                        val result = repository.requestLocationOnce(config.id)
                        val entry = result?.locations
                            ?.filter { it.latitude != null && it.longitude != null && it.timestamp > 0 }
                            ?.maxByOrNull { it.timestamp }

                        if (entry != null) {
                            val updated = AlarmEngine.evaluate(
                                config,
                                LocationSample(
                                    lat = entry.latitude!!,
                                    lon = entry.longitude!!,
                                    timestamp = entry.timestamp,
                                )
                            )
                            store.save(updated)
                            store.appendHistory(
                                config.id,
                                HistoryEntry(
                                    timestamp = entry.timestamp,
                                    lat = entry.latitude!!,
                                    lon = entry.longitude!!,
                                    distanceM = updated.lastDistanceM,
                                    status = updated.status,
                                )
                            )

                            if (updated.status == MonitorStatus.ALARM && config.status != MonitorStatus.ALARM) {
                                showAlarm(updated)
                            }
                        }
                    }
                }

                val interval = store.armed().minOfOrNull { it.intervalMinutes } ?: 5
                delay(interval.coerceAtLeast(1) * 60_000L)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun activityIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun ongoingNotification(text: String): Notification =
        NotificationCompat.Builder(this, "monitor")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("ATA")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(activityIntent())
            .build()

    private fun showAlarm(config: TrackerConfig) {
        val distance = config.lastDistanceM?.toInt() ?: 0
        val notification = NotificationCompat.Builder(this, "alarm")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Movement detected: " + config.name)
            .setContentText("Tracker is about " + distance + " m from its armed position.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(activityIntent())
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(config.id.hashCode(), notification)
    }
}
