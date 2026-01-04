package com.example.camera_fire.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.camera_fire.MainActivity
import com.example.camera_fire.R
import com.example.camera_fire.Supabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        // Gửi token này lên Server của bạn
        sendRegistrationToServer(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains notification
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "Fire Alert", it.body ?: "Fire detected!")
        }
    }

    private fun sendRegistrationToServer(token: String) {
        // Sử dụng RetrofitClient để gửi token
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get user_id from Supabase auth session
                val currentUser = Supabase.client.auth.currentUserOrNull()
                Log.d("FCM", "Current user: $currentUser")
                
                val userId = currentUser?.id
                Log.d("FCM", "User ID from auth: $userId")
                
                if (userId == null) {
                    Log.w("FCM", "User not logged in, skipping token registration")
                    return@launch
                }
                
                val apiService = RetrofitClient.apiService
                val requestData = mapOf(
                    "token" to token,
                    "user_id" to userId  // ✅ Include user_id as string
                )
                
                Log.d("FCM", "Sending registration request: $requestData")
                
                val response = apiService.registerToken(requestData)
                if (response.isSuccessful) {
                    Log.d("FCM", "Token registered successfully for user $userId")
                } else {
                    Log.e("FCM", "Failed to register token: ${response.code()} - ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("FCM", "Error registering token", e)
            }
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "fire_alerts"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Fire Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
