package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.database.SmsDatabase

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

        val db = SmsDatabase.getDatabase(context)
        val dao = db.smsQueueDao()

        // 3. Fetch all pending SMS
        val pendingList = dao.getSmsByStatus("PENDING")
        if (pendingList.isEmpty()) {
            Log.d(TAG, "SmsQueueWorker finished: No pending SMS in the queue")
            return Result.success()
        }

        Log.d(TAG, "SmsQueueWorker: Processing ${pendingList.size} pending SMS messages...")

        val retryEnabled = AutomaticSmsManager.isRetryFailedEnabled(context)
        val maxRetryCount = AutomaticSmsManager.getMaxRetryCount(context)

        for (sms in pendingList) {
            // Mark as SENDING so other workers won't touch it
            dao.updateSms(sms.copy(status = "SENDING"))

            val sendResult = try {
                AutomaticSmsManager.sendSingleSms(
                    context = context,
                    mobileNumber = sms.mobileNumber,
                    message = sms.message,
                    smsId = sms.id
                )
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }

            if (sendResult.isSuccess) {
                Log.d(TAG, "Successfully sent SMS ID ${sms.id} to ${sms.mobileNumber}")
                dao.updateSms(
                    sms.copy(
                        status = "SENT",
                        lastError = null
                    )
                )
            } else {
                val errorMsg = sendResult.exceptionOrNull()?.message ?: "Unknown SMS sending error"
                Log.e(TAG, "Failed to send SMS ID ${sms.id} to ${sms.mobileNumber}: $errorMsg")

                val currentRetry = sms.retryCount
                if (retryEnabled && currentRetry < maxRetryCount) {
                    val nextRetry = currentRetry + 1
                    Log.d(TAG, "Re-queuing SMS ID ${sms.id} (Retry count $nextRetry / $maxRetryCount)")
                    dao.updateSms(
                        sms.copy(
                            status = "PENDING",
                            retryCount = nextRetry,
                            lastError = errorMsg
                        )
                    )
                } else {
                    Log.d(TAG, "Sms ID ${sms.id} reached maximum retries or retry disabled. Marked as FAILED.")
                    dao.updateSms(
                        sms.copy(
                            status = "FAILED",
                            lastError = errorMsg
                        )
                    )
                }
            }
        }

        return Result.success()
    }
}
