package jovannedeljkovic.gps_tracker_pro.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import jovannedeljkovic.gps_tracker_pro.R
import jovannedeljkovic.gps_tracker_pro.ui.main.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "gps_tracker_channel"
        const val TRACKING_NOTIFICATION_ID = 1
        const val POINT_NOTIFICATION_ID = 2
        const val EXPORT_NOTIFICATION_ID = 3
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "GPS Tracker Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Obaveštenja za praćenje lokacije i GPS funkcionalnosti"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                enableLights(true)
                lightColor = context.getColor(R.color.blue_primary)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun createTrackingNotification(distance: String, speed: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("GPS Tracker - Aktivno praćenje")
            .setContentText("Udaljenost: $distance • Brzina: $speed")
            .setSmallIcon(R.drawable.ic_notification_gps)
            .setColor(context.getColor(R.color.blue_primary))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(100, 200, 100))
            .build()
    }

    fun createPointAddedNotification(pointName: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Nova tačka dodata")
            .setContentText("Tačka: $pointName")
            .setSmallIcon(R.drawable.ic_notification_point)
            .setColor(context.getColor(R.color.blue_primary))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(100, 100))
            .build()
    }

    fun createExportSuccessNotification(fileName: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Eksport uspešan")
            .setContentText("Fajl: $fileName")
            .setSmallIcon(R.drawable.ic_notification_export)
            .setColor(context.getColor(R.color.green_active))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(100, 100, 100))
            .build()
    }

    // OSNOVNE METODE
    fun showTrackingNotification(distance: String, speed: String) {
        val notification = createTrackingNotification(distance, speed)
        notificationManager.notify(TRACKING_NOTIFICATION_ID, notification)
    }
// Dodaj ove metode u NotificationHelper klasu

    fun createPointRenamedNotification(oldName: String, newName: String): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            4, // Novi request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Tačka preimenovana")
            .setContentText("$oldName → $newName")
            .setSmallIcon(R.drawable.ic_notification_point)
            .setColor(context.getColor(R.color.blue_primary))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(100, 100))
            .build()
    }

    fun showPointRenamed(oldName: String, newName: String) {
        val notification = createPointRenamedNotification(oldName, newName)
        notificationManager.notify(POINT_NOTIFICATION_ID, notification)
    }
    fun updateTrackingNotification(distance: String, speed: String) {
        showTrackingNotification(distance, speed)
    }

    fun showPointAdded(pointName: String) {
        val notification = createPointAddedNotification(pointName)
        notificationManager.notify(POINT_NOTIFICATION_ID, notification)
    }

    fun showExportSuccess(fileName: String) {
        val notification = createExportSuccessNotification(fileName)
        notificationManager.notify(EXPORT_NOTIFICATION_ID, notification)
    }

    fun cancelTrackingNotification() {
        notificationManager.cancel(TRACKING_NOTIFICATION_ID)
    }

    fun cancelAllNotifications() {
        notificationManager.cancel(TRACKING_NOTIFICATION_ID)
        notificationManager.cancel(POINT_NOTIFICATION_ID)
        notificationManager.cancel(EXPORT_NOTIFICATION_ID)
    }
    // Dodaj ove metode u NotificationHelper klasu
    fun createBulkDeleteNotification(count: Int): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            5,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Rute obrisane")
            .setContentText("Obrisano $count ruta")
            .setSmallIcon(R.drawable.ic_notification_point)
            .setColor(context.getColor(R.color.accent_red))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    fun createAllRoutesDeletedNotification(count: Int): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            6,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Sve rute obrisane")
            .setContentText("Obrisano svih $count ruta")
            .setSmallIcon(R.drawable.ic_notification_point)
            .setColor(context.getColor(R.color.accent_red))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun showBulkDeleteSuccess(count: Int) {
        val notification = createBulkDeleteNotification(count)
        notificationManager.notify(EXPORT_NOTIFICATION_ID, notification)
    }

    fun showAllRoutesDeleted(count: Int) {
        val notification = createAllRoutesDeletedNotification(count)
        notificationManager.notify(EXPORT_NOTIFICATION_ID, notification)
    }
}