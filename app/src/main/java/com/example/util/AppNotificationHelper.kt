package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity

object AppNotificationHelper {
    private const val TAG = "AppNotificationHelper"
    private const val CHANNEL_ID = "app_update_channel"
    private const val CHANNEL_NAME = "App Updates"
    private const val NOTIFICATION_ID = 9912

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = "Notifications for application updates"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showUpdateNotification(context: Context, versionName: String) {
        createNotificationChannel(context)

        // Check permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                "android.permission.POST_NOTIFICATIONS"
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Notification permission not granted, skipping notification")
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_update_dialog", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Determine language settings to support Bangla/English
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val lang = prefs.getString("app_lang", "en") ?: "en"
        val title = if (lang == "bn") "নতুন আপডেট পাওয়া গেছে" else "New Update Available"
        val message = if (lang == "bn") {
            "ইন্টারনেট বিলিং ম্যানেজমেন্ট ভার্সন $versionName এখন ডাউনলোডের জন্য প্রস্তুত!"
        } else {
            "Internet Billing Management version $versionName is ready to download!"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // fallback standard download icon
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build())
            Log.d(TAG, "Sent update notification for version $versionName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post notification", e)
        }
    }
}
