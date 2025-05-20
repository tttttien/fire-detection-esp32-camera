package com.example.camera.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.camera.R
object NotificationHelper {
    private const val CHANNEL_ID = "fire_alert_channel"
    private const val CHANNEL_NAME = "Fire Alert"
    private const val CHANNEL_DESC = "Notifications when fire is detected"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }
            val mgr = context.getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(chan)
        }
    }

    fun showFireDetectedNotification(context: Context, snapshot: Bitmap) {
        // Android 13+ cần permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_fire_notification)   // <-- icon của bạn
            .setContentTitle("🔥 Fire Detected!")
            .setContentText("Tap to view snapshot.")
            .setStyle(NotificationCompat.BigPictureStyle().bigPicture(snapshot))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(1001, builder.build())
    }
}
