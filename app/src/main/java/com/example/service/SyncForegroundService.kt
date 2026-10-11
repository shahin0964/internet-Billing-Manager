package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.IspApplication
import com.example.MainActivity
import com.example.util.HostingSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight Foreground Service with sticky notification to ensure
 * live background sync and remote connectivity remain active at all times.
 */
class SyncForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var periodicSyncJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SyncForegroundService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "SyncForegroundService onStartCommand triggered")

        if (!IspApplication.isLoggedIn(this)) {
            Log.d(TAG, "User not logged in. Stopping SyncForegroundService.")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildNotification()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Immediately remove notification from notification bar while keeping background service/sync active
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }

        startPeriodicBackgroundSync()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "SyncForegroundService destroyed")
        periodicSyncJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPeriodicBackgroundSync() {
        if (periodicSyncJob?.isActive == true) return

        periodicSyncJob = serviceScope.launch {
            Log.i(TAG, "Periodic background sync loop started in Foreground Service")
            while (isActive) {
                try {
                    val appCtx = applicationContext
                    if (IspApplication.isLoggedIn(appCtx)) {
                        val uid = IspApplication.getUserId(appCtx)
                        if (!uid.isNullOrBlank() && HostingSyncManager.isSessionValid(appCtx, uid)) {
                            Log.d(TAG, "Foreground Service sync tick executing...")
                            HostingSyncManager.performFullRemoteDataPull(appCtx)
                        }
                    } else {
                        Log.d(TAG, "User logged out detected inside sync loop. Stopping service.")
                        stopSelf()
                        break
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Sync tick note: ${e.message}")
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "লাইভ কানেক্টিভিটি ও সিঙ্ক",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "অ্যাপের ব্যাকগ্রাউন্ড সিঙ্ক ও লাইভ কানেক্টিভিটি সর্বদা সচল রাখে"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("লাইভ সিঙ্ক চালু রয়েছে")
            .setContentText("ব্যাকগ্রাউন্ড ডেটা সিঙ্ক ও অটোমেটিক ব্যাকআপ সচল রয়েছে")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "SyncForegroundService"
        private const val CHANNEL_ID = "isp_live_sync_channel"
        private const val NOTIFICATION_ID = 9981
        private const val SYNC_INTERVAL_MS = 40_000L // 40 seconds periodic sync

        @JvmStatic
        fun startService(context: Context) {
            if (!IspApplication.isLoggedIn(context)) return
            try {
                val intent = Intent(context, SyncForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to start SyncForegroundService: ${e.message}", e)
            }
        }

        @JvmStatic
        fun stopService(context: Context) {
            try {
                val intent = Intent(context, SyncForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to stop SyncForegroundService: ${e.message}", e)
            }
        }
    }
}
