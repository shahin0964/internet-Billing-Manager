package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED ||
            intent?.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent?.action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            Log.d("BootCompletedReceiver", "Device boot completed. Re-scheduling ISP background workers & alarms...")

            try {
                // 1. Reschedule 11:30 PM daily auto-backup
                AutoBackupWorker.scheduleDailyAutoBackup(context)

                // 2. Reschedule 1st of month auto-billing
                MonthlyAutoBillingWorker.scheduleMonthlyAutoBilling(context)

                // 3. Reschedule periodic sync and automatic SMS workers
                SyncWorker.schedulePeriodicSync(context)
                AutomaticSmsManager.schedulePeriodicSmsWorker(context)

                // 4. If logged in, trigger live sync check and start Foreground Service
                if (com.example.IspApplication.isLoggedIn(context)) {
                    SyncWorker.enqueueSync(context, forceExpedited = false)
                    com.example.service.SyncForegroundService.startService(context)
                }
            } catch (e: Throwable) {
                Log.w("BootCompletedReceiver", "Failed to re-schedule workers on boot: ${e.message}")
            }
        }
    }
}
