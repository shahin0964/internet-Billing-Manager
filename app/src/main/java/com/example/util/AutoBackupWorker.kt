package com.example.util

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.withTimeoutOrNull

class AutoBackupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "AutoBackupWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting automatic periodic hosting backup...")
        
        if (!HostingSyncManager.isNetworkAvailable(context)) {
            Log.d(TAG, "Skipping auto backup: No network connection.")
            return Result.retry()
        }

        // 1. Sync local data to hosting
        val hostingUploadSuccess = withTimeoutOrNull(60000L) {
            HostingSyncManager.syncLocalToHosting(context)
        } ?: false

        // 2. Pull delta from hosting to keep local database fresh
        val hostingPullSuccess = withTimeoutOrNull(45000L) {
            HostingSyncManager.pullDeltaFromHosting(context)
        } ?: false

        // 3. Optional fallback/sync to Firestore
        val firestoreUploadSuccess = withTimeoutOrNull(45000L) {
            try {
                FirestoreSyncManager.syncLocalToCloud(context)
            } catch (e: Exception) {
                Log.e(TAG, "Firestore sync error during auto backup: ${e.message}")
                false
            }
        } ?: false

        return if (hostingUploadSuccess || hostingPullSuccess || firestoreUploadSuccess) {
            Log.d(TAG, "Auto backup sync completed successfully.")
            Result.success()
        } else {
            Log.w(TAG, "Auto backup sync failed to process completely. Retrying...")
            Result.retry()
        }
    }
}
