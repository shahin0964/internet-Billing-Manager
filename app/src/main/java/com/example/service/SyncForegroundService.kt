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

        // Remove any lingering notification if present, while running silently in background
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (e: Throwable) {
            Log.w(TAG, "Notification cleanup note: ${e.message}")
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
            Log.i(TAG, "Periodic background sync loop started in Service")
            while (isActive) {
                try {
                    val appCtx = applicationContext
                    if (IspApplication.isLoggedIn(appCtx)) {
                        val uid = IspApplication.getUserId(appCtx)
                        if (!uid.isNullOrBlank() && HostingSyncManager.isSessionValid(appCtx, uid)) {
                            Log.d(TAG, "Background Service sync tick executing...")
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
                context.startService(intent)
            } catch (e: Throwable) {
                Log.w(TAG, "Background startService note: ${e.message}")
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
