package de.myweather

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class AlertPollWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return Result.success()
        }
        return try {
            val prefs = applicationContext.getSharedPreferences("alerts", Context.MODE_PRIVATE)
            val lat = prefs.getFloat("lat", 50.9375f).toDouble()
            val lon = prefs.getFloat("lon", 6.9603f).toDouble()
            createChannels()

            val dwd = WeatherEngine.alerts(lat, lon)
            val old = prefs.getStringSet("dwd_seen", emptySet()) ?: emptySet()
            val newIds = dwd.map { it.event }.toSet()
            dwd.filter { !old.contains(it.event) }.take(4).forEach {
                notify("DWD-Warnung", it.event, "Amtliche Warnung des Deutschen Wetterdienstes.", "dwd")
            }
            prefs.edit().putStringSet("dwd_seen", newIds).apply()

            val tornadoEnabled = prefs.getBoolean("tornado", true)
            if (tornadoEnabled) {
                val meso = runCatching { WeatherEngine.nearbyMeso(lat, lon, 35.0) }.getOrDefault(false)
                val was = prefs.getBoolean("meso_seen", false)
                if (meso && !was) {
                    notify("Tornadobildung möglich", "Rotierendes Gewitter in Standortnähe erkannt.", "Nicht amtliche warnung · DWD-Mesocyclone Detection", "tornado")
                }
                prefs.edit().putBoolean("meso_seen", meso).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun createChannels() {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("dwd", "DWD-Warnungen", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel("tornado", "Nicht amtliche Rotationswarnungen", NotificationManager.IMPORTANCE_HIGH))
    }

    private fun notify(title: String, text: String, detail: String, channel: String) {
        val n = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(de.myweather.R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$detail"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((title + text).hashCode(), n)
    }
}
