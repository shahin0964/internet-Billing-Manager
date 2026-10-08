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
import com.example.IspApplication
import com.example.data.repository.IspRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MonthlyAutoBillingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "MonthlyAutoBilling"
        private const val PERIODIC_WORK_NAME = "monthly_auto_billing_periodic"
        private const val ONE_TIME_WORK_NAME = "monthly_auto_billing_immediate"
        const val ALARM_ACTION_1ST_OF_MONTH_BILLING = "com.example.action.MONTHLY_1ST_BILLING"

        /**
         * Calculates timestamp for 00:05 AM on the 1st day of the next billing cycle.
         */
        fun getNext1stOfMonthTimestamp(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                if (get(Calendar.DAY_OF_MONTH) == 1 && (get(Calendar.HOUR_OF_DAY) == 0 && get(Calendar.MINUTE) < 5)) {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 5)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                } else {
                    add(Calendar.MONTH, 1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 5)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
            return target.timeInMillis
        }

        /**
         * Schedules periodic background checking and exact alarm for the 1st of every month.
         */
        fun scheduleMonthlyAutoBilling(context: Context) {
            schedulePeriodicCheck(context)
            scheduleExact1stOfMonthAlarm(context)
        }

        private fun schedulePeriodicCheck(context: Context) {
            try {
                // Check daily to guarantee that if the device was off on the 1st,
                // bills for the active month are generated automatically as soon as possible.
                val periodicRequest = PeriodicWorkRequestBuilder<MonthlyAutoBillingWorker>(
                    24, TimeUnit.HOURS,
                    1, TimeUnit.HOURS
                ).setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                ).addTag("monthly_auto_billing")
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
                Log.d(TAG, "Periodic monthly auto-billing work scheduled.")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to schedule monthly auto-billing work: ${e.message}")
            }
        }

        fun scheduleExact1stOfMonthAlarm(context: Context) {
            try {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
                val targetTimeMs = getNext1stOfMonthTimestamp()

                val intent = Intent(context, MonthlyBillingAlarmReceiver::class.java).apply {
                    action = ALARM_ACTION_1ST_OF_MONTH_BILLING
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    101,
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
                Log.d(TAG, "Exact AlarmManager scheduled for 1st of month at: $targetTimeMs")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to schedule exact 1st of month alarm: ${e.message}")
            }
        }

        fun enqueueImmediateAutoBilling(context: Context) {
            try {
                val request = OneTimeWorkRequestBuilder<MonthlyAutoBillingWorker>()
                    .addTag("monthly_auto_billing_immediate")
                    .build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    ONE_TIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    request
                )
                Log.d(TAG, "Immediate monthly auto-billing work enqueued.")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to enqueue immediate monthly auto-billing: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Running automated monthly billing engine...")

        val userId = IspApplication.getUserId(context)
        val isLoggedIn = IspApplication.isLoggedIn(context)
        if (userId.isNullOrBlank() || !isLoggedIn) {
            Log.d(TAG, "Skipping auto billing: User is not authenticated.")
            return Result.success()
        }

        try {
            val repository = IspRepository.create(context, userId)
            val currentMonth = BillingMonthUtils.formatStandardMonth()
            val dueDate = BillingMonthUtils.formatStandardDueDate()

            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            val lastGeneratedMonth = prefs.getString("last_auto_billing_month_$userId", null)

            // Generate recurring monthly bills for active customers
            val generatedCount = repository.generateMonthlyBills(
                billingMonth = currentMonth,
                dueDate = dueDate,
                isAutoGeneration = true
            )

            if (generatedCount > 0) {
                Log.d(TAG, "Successfully generated $generatedCount recurring monthly bills for $currentMonth.")
                prefs.edit().putString("last_auto_billing_month_$userId", currentMonth).apply()

                // Trigger instant live sync so new recurring bills are pushed to server
                if (HostingSyncManager.isNetworkAvailable(context)) {
                    try {
                        HostingSyncManager.syncLocalToHosting(context)
                    } catch (e: Throwable) {
                        Log.d(TAG, "Live sync after auto-billing note: ${e.message}")
                    }
                }
                SyncWorker.enqueueSync(context, forceExpedited = true)
            } else {
                Log.d(TAG, "Bills for $currentMonth are already up to date. No new bills generated.")
            }

            return Result.success()
        } catch (e: Throwable) {
            Log.e(TAG, "Error during automated monthly billing generation: ${e.message}", e)
            return Result.retry()
        }
    }
}

/**
 * BroadcastReceiver triggered by AlarmManager on the 1st day of every month.
 */
class MonthlyBillingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("MonthlyBillingAlarmReceiver", "Received 1st of month alarm broadcast.")
        // 1. Enqueue immediate auto billing generation
        MonthlyAutoBillingWorker.enqueueImmediateAutoBilling(context)
        // 2. Schedule the next month's alarm
        MonthlyAutoBillingWorker.scheduleExact1stOfMonthAlarm(context)
    }
}
