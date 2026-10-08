package com.example.util

import android.content.Context
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
import com.example.data.remote.ApiClient
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val UNIQUE_ONE_TIME_SYNC_WORK_NAME = "isp_live_sync_work"
        private const val UNIQUE_PERIODIC_SYNC_WORK_NAME = "isp_periodic_sync_work"

        /**
         * Enqueues a OneTime sync work request when data changes locally
         * or when internet connectivity is re-established.
         */
        fun enqueueSync(context: Context, forceExpedited: Boolean = false) {
            try {
                val userId = IspApplication.getUserId(context)
                val isLoggedIn = IspApplication.isLoggedIn(context)
                if (userId.isNullOrBlank() || !isLoggedIn) {
                    Log.d(TAG, "Skipping sync work enqueue: User not logged in.")
                    return
                }

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequestBuilder = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .addTag("isp_sync")

                if (forceExpedited) {
                    try {
                        workRequestBuilder.setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    } catch (e: Throwable) {
                        Log.d(TAG, "Expedited quota policy fallback: ${e.message}")
                    }
                }

                val workRequest = workRequestBuilder.build()

                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_ONE_TIME_SYNC_WORK_NAME,
                    if (forceExpedited) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    workRequest
                )
                Log.d(TAG, "Live sync work enqueued successfully (forceExpedited=$forceExpedited).")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to enqueue sync work: ${e.message}")
            }
        }

        /**
         * Schedules periodic background sync every 15 minutes to guarantee
         * background synchronization even if network callbacks are throttled by OS Doze mode.
         */
        fun schedulePeriodicSync(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                    15, TimeUnit.MINUTES
                ).setConstraints(constraints)
                    .addTag("isp_periodic_sync")
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_SYNC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
                Log.d(TAG, "Periodic sync work scheduled (15 min interval).")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to schedule periodic sync work: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background live sync execution...")

        // Step 1: Ensure ApiClient is properly initialized with context and auth token
        ApiClient.init(context)

        val userId = IspApplication.getUserId(context)
        val token = IspApplication.getAuthToken(context)

        if (userId.isNullOrBlank() || token.isNullOrBlank() || !IspApplication.isLoggedIn(context)) {
            Log.d(TAG, "SyncWorker aborted: User not logged in or missing auth token.")
            return Result.success()
        }

        if (!HostingSyncManager.isNetworkAvailable(context)) {
            Log.d(TAG, "SyncWorker retry: No active network connection.")
            return Result.retry()
        }

        // Step 2: Perform atomic full sync to Hosting
        val syncSuccess = withTimeoutOrNull(180000L) { // 3 minute timeout
            HostingSyncManager.syncLocalToHosting(context)
        } ?: false

        return if (syncSuccess) {
            Log.d(TAG, "SyncWorker completed live sync successfully.")
            Result.success()
        } else {
            val hasPending = HostingSyncManager.getActualPendingDirtyCount(context) > 0
            if (hasPending && HostingSyncManager.isNetworkAvailable(context)) {
                Log.w(TAG, "SyncWorker failed to sync all dirty records, retrying later.")
                Result.retry()
            } else {
                Log.d(TAG, "SyncWorker finished without remaining dirty rows.")
                Result.success()
            }
        }
    }
}
