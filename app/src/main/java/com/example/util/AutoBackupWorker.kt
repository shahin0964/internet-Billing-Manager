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
        if (userId.isNullOrBlank() || !com.example.IspApplication.isLoggedIn(context) || !HostingSyncManager.isSessionValid(context, userId)) {
            Log.d(TAG, "Skipping auto backup: User is not logged in or session is invalid.")
            return Result.success()
        }

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
        val backupSuccess = withTimeoutOrNull(120000L) {
            if (!HostingSyncManager.isSessionValid(context, userId)) return@withTimeoutOrNull false
            repository.backupToHosting(context, userId).first
        } ?: false

        if (!HostingSyncManager.isSessionValid(context, userId)) {
            Log.d(TAG, "Auto backup worker: Session invalidated during backup. Aborting.")
            return Result.success()
        }

        return if (backupSuccess) {
            Log.d(TAG, "Auto backup sync completed successfully.")
            Result.success()
        } else {
            Log.w(TAG, "Auto backup sync failed to process completely. Retrying...")
            Result.retry()
        }
    }
}
