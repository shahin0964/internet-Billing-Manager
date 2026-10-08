package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
        private const val PERIODIC_WORK_NAME = "auto_hosting_backup_1130pm"
        private const val ONE_TIME_WORK_NAME = "auto_hosting_backup_immediate"
        const val ALARM_ACTION_1130PM_BACKUP = "com.example.action.DAILY_1130PM_BACKUP"

        /**
         * Calculates the delay in milliseconds from now until 11:30 PM today (or tomorrow if already past 11:30 PM).
         */
        fun calculateInitialDelayTo1130Pm(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (now.after(target)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return (target.timeInMillis - now.timeInMillis).coerceAtLeast(1000L)
        }

        /**
         * Calculates the exact epoch timestamp for the next 11:30 PM.
         */
        fun getNext1130PmTimestamp(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (now.after(target)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        /**
         * Schedules the daily 11:30 PM auto-backup using both WorkManager and AlarmManager
         * to guarantee exact execution even under aggressive OS battery optimization and Doze mode.
         */
        fun scheduleDailyAutoBackup(context: Context) {
            scheduleWorkManagerBackup(context)
            scheduleExactAlarmBackup(context)
        }

        @Deprecated("Use scheduleDailyAutoBackup instead", ReplaceWith("scheduleDailyAutoBackup(context)"))
        fun schedulePeriodicBackup(context: Context) {
            scheduleDailyAutoBackup(context)
        }

        private fun scheduleWorkManagerBackup(context: Context) {
            try {
                val initialDelayMs = calculateInitialDelayTo1130Pm()
                Log.d(TAG, "Scheduling WorkManager backup with initial delay of ${initialDelayMs / 1000 / 60} minutes (at 11:30 PM)")

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val backupRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(
                    24, TimeUnit.HOURS,
                    30, TimeUnit.MINUTES // 30-min flex window
                ).setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints)
                    .addTag("auto_backup_1130pm")
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    backupRequest
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to schedule WorkManager auto backup: ${e.message}")
            }
        }

        fun scheduleExactAlarmBackup(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val targetTimeMs = getNext1130PmTimestamp()

                val intent = Intent(context, AutoBackupAlarmReceiver::class.java).apply {
                    action = ALARM_ACTION_1130PM_BACKUP
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    1130,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            targetTimeMs,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            targetTimeMs,
                            pendingIntent
                        )
                    }
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        targetTimeMs,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        targetTimeMs,
                        pendingIntent
                    )
                }
                Log.d(TAG, "Exact AlarmManager set for 11:30 PM at: $targetTimeMs")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to set AlarmManager exact alarm: ${e.message}")
            }
        }

        /**
         * Enqueues an immediate one-time backup task (e.g. triggered by exact alarm or manual retry).
         */
        fun enqueueImmediateOneTimeBackup(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                    .setConstraints(constraints)
                    .addTag("auto_backup_immediate")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.d(TAG, "Immediate auto backup worker enqueued.")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to enqueue immediate auto backup: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing scheduled Daily Auto-Backup at 11:30 PM...")

        if (!HostingSyncManager.isNetworkAvailable(context)) {
            Log.d(TAG, "Skipping auto backup: No network connection. Will retry when connected.")
            return Result.retry()
        }

        val userId = com.example.IspApplication.getUserId(context)
        if (userId.isNullOrBlank() || !com.example.IspApplication.isLoggedIn(context) || !HostingSyncManager.isSessionValid(context, userId)) {
            Log.d(TAG, "Skipping auto backup: User is not logged in or session is invalid.")
            return Result.success()
        }

        val repository = com.example.data.repository.IspRepository.create(context, userId)
        val backupSuccess = withTimeoutOrNull(180000L) { // 3 min timeout
            if (!HostingSyncManager.isSessionValid(context, userId)) return@withTimeoutOrNull false
            repository.backupToHosting(context, userId).first
        } ?: false

        if (!HostingSyncManager.isSessionValid(context, userId)) {
            Log.w(TAG, "Auto backup worker: Session invalidated during backup. Aborting.")
            return Result.success()
        }

        return if (backupSuccess) {
            val now = System.currentTimeMillis()
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putLong("last_cloud_sync_time_$userId", now)
                .putLong("last_cloud_sync_time", now)
                .putLong("last_daily_auto_backup_1130pm_$userId", now)
                .apply()
            Log.d(TAG, "Daily Auto-Backup at 11:30 PM completed successfully.")
            Result.success()
        } else {
            Log.w(TAG, "Daily Auto-Backup failed to complete. WorkManager will retry.")
            Result.retry()
        }
    }
}

/**
 * BroadcastReceiver triggered by AlarmManager at exact 11:30 PM daily.
 */
class AutoBackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("AutoBackupAlarmReceiver", "Received 11:30 PM alarm broadcast.")
        // 1. Enqueue immediate auto backup worker with network constraint
        AutoBackupWorker.enqueueImmediateOneTimeBackup(context)
        // 2. Schedule the next day's 11:30 PM alarm to maintain continuous schedule
        AutoBackupWorker.scheduleExactAlarmBackup(context)
    }
}
