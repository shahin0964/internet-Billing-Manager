package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SmsQueueWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SmsQueueWorker"
    }

    override suspend fun doWork(): Result {
        val context = applicationContext

        // 1. If Automatic SMS feature is disabled globally, do not send
        if (!AutomaticSmsManager.isSmsEnabled(context)) {
            Log.d(TAG, "SmsQueueWorker stopped: Automatic SMS feature is disabled")
            return Result.success()
        }

        // 2. Check if SEND_SMS permission is granted
        if (!AutomaticSmsManager.isSmsPermissionGranted(context)) {
            Log.w(TAG, "SmsQueueWorker stopped: SEND_SMS permission is not granted")
            return Result.failure()
        }

        try {
            SafeSmsSenderEngine.processQueue(context)
            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in SmsQueueWorker doWork: ${e.message}", e)
            return Result.retry()
        }
    }
}
