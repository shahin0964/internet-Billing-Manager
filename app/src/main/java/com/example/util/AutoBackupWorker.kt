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

        val userId = com.example.IspApplication.getUserId(context)
        val backupSuccess = if (!userId.isNullOrBlank()) {
            val db = com.example.data.database.IspDatabase.getDatabase(context)
            val repository = com.example.data.repository.IspRepository(
                db.customerDao(),
                db.packageDao(),
                db.billDao(),
                db.paymentDao(),
                db.settingsDao(),
                db.expenseDao(),
                db.networkDiagramDao(),
                db.auditLogDao(),
                db,
                context
            )
            withTimeoutOrNull(120000L) {
                repository.backupToHosting(context, userId).first
            } ?: false
        } else {
            // 1. Sync local data to hosting
            withTimeoutOrNull(60000L) {
                HostingSyncManager.syncLocalToHosting(context)
            } ?: false
        }

        // 2. Pull delta from hosting to keep local database fresh
        val hostingPullSuccess = withTimeoutOrNull(45000L) {
            HostingSyncManager.pullDeltaFromHosting(context)
        } ?: false

        return if (backupSuccess) {
            Log.d(TAG, "Auto backup sync completed successfully.")
            Result.success()
        } else {
            Log.w(TAG, "Auto backup sync failed to process completely. Retrying...")
            Result.retry()
        }
    }
}
