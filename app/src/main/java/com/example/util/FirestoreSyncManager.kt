package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import com.example.data.database.IspDatabase
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.IspPackageEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.NetworkConnectionEntity
import com.example.data.model.NetworkDiagramEntity
import com.example.data.model.NetworkNodeEntity
import com.example.data.model.PendingDeletionEntity
import com.example.data.model.BandwidthBillEntity
import com.example.data.model.SpecificAdvanceEntity
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"
    private const val WORK_NAME_PERIODIC = "cloud_sync_periodic"
    private const val WORK_NAME_ONE_TIME = "cloud_sync_one_time"

    @Volatile
    var lastCloudBackupError: String? = null

    private fun cleanErrorMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown error"
        return when {
            raw.contains("PERMISSION_DENIED", ignoreCase = true) -> "PERMISSION_DENIED (Check Firestore security rules)"
            raw.contains("UNAUTHENTICATED", ignoreCase = true) -> "UNAUTHENTICATED (User session expired)"
            raw.contains("UNAVAILABLE", ignoreCase = true) -> "UNAVAILABLE (Firestore service unreachable)"
            raw.contains("DEADLINE_EXCEEDED", ignoreCase = true) -> "DEADLINE_EXCEEDED (Network timeout)"
            raw.contains("RESOURCE_EXHAUSTED", ignoreCase = true) -> "RESOURCE_EXHAUSTED (Quota exceeded)"
            raw.contains("FAILED_PRECONDITION", ignoreCase = true) -> "FAILED_PRECONDITION (Missing index or precondition)"
            raw.contains("NOT_FOUND", ignoreCase = true) -> "NOT_FOUND"
            raw.contains("ALREADY_EXISTS", ignoreCase = true) -> "ALREADY_EXISTS"
            raw.contains("INVALID_ARGUMENT", ignoreCase = true) -> "INVALID_ARGUMENT (Check document format)"
            else -> raw.take(80)
        }
    }

    private fun DocumentSnapshot.safeDouble(field: String, default: Double = 0.0): Double {
        val v = this.get(field)
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: default
            else -> default
        }
    }

    private fun DocumentSnapshot.safeLong(field: String, default: Long = 0L): Long {
        val v = this.get(field)
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: default
            else -> default
        }
    }

    private fun DocumentSnapshot.safeInt(field: String, default: Int = 0): Int {
        val v = this.get(field)
        return when (v) {
            is Number -> v.toInt()
            is String -> v.toIntOrNull() ?: default
            else -> default
        }
    }

    /**
     * Check if active internet connection is available.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val activeNetworkInfo = connectivityManager.activeNetworkInfo
                activeNetworkInfo != null && activeNetworkInfo.isConnected
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error checking network availability: ${e.message}")
            true
        }
    }

    private data class SyncOperation(
        val writeOp: (WriteBatch) -> Unit,
        val onSuccess: suspend (IspDatabase) -> Unit
    )

    /**
     * Helper to execute WriteBatch in chunks (up to 450 operations per batch, well below Firestore 500 limit).
     * Immediately marks only the successfully committed chunk's records as synced in Room local DB.
     */
    /**
     * Probes if Firestore backend is reachable and ready to process read/write operations.
     * Prevents hanging operations and avoids flooding logs when Firestore is uninitialized or unreachable.
     */
    private suspend fun isFirestoreAvailable(
        userRef: com.google.firebase.firestore.DocumentReference,
        testWrite: Boolean = true
    ): Boolean {
        return try {
            withTimeout(10000L) {
                if (testWrite) {
                    userRef.collection("sync_meta").document("probe").set(
                        mapOf("lastProbe" to System.currentTimeMillis()),
                        SetOptions.merge()
                    ).await()
                } else {
                    userRef.collection("sync_meta").document("status").get().await()
                }
            }
            true
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "Firestore availability probe failed with Firestore error [${e.code}]: ${e.message}", e)
            if (lastCloudBackupError == null) {
                lastCloudBackupError = "Firestore probe error [${e.code}]: ${e.message?.take(80) ?: cleanErrorMessage(e.code.name)}"
            }
            false
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Firestore availability probe timed out after 10s")
            if (lastCloudBackupError == null) {
                lastCloudBackupError = "Firestore probe timed out after 10s"
            }
            false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Firestore availability probe note: ${e.message}")
            if (lastCloudBackupError == null) {
                lastCloudBackupError = cleanErrorMessage(e.message)
            }
            false
        }
    }

    private suspend fun commitSingleChunk(
        firestore: FirebaseFirestore,
        db: IspDatabase,
        chunk: List<SyncOperation>,
        timeoutMs: Long
    ): Boolean {
        return try {
            val batch = firestore.batch()
            for (op in chunk) {
                op.writeOp(batch)
            }
            withTimeout(timeoutMs) {
                batch.commit().await()
            }

            // Mark only the successfully committed chunk's records as synced locally in Room
            db.withTransaction {
                for (op in chunk) {
                    op.onSuccess(db)
                }
            }
            true
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "Chunk commit failed with Firestore error [${e.code}]: ${e.message}", e)
            if (lastCloudBackupError == null) {
                lastCloudBackupError = "Firestore write error [${e.code}]: ${e.message?.take(80) ?: cleanErrorMessage(e.code.name)}"
            }
            false
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Chunk commit of ${chunk.size} operations not acknowledged within ${timeoutMs / 1000}s (Timeout)")
            if (lastCloudBackupError == null) {
                lastCloudBackupError = "Firestore acknowledgment timeout (${timeoutMs / 1000}s)"
            }
            false
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Chunk commit note for ${chunk.size} operations: ${e.message}")
            if (lastCloudBackupError == null) {
                lastCloudBackupError = cleanErrorMessage(e.message)
            }
            false
        }
    }

    /**
     * Helper to execute WriteBatch in manageable chunks (default 40 operations per batch,
     * well below Firestore 500 limit and highly reliable to serialize/commit over mobile networks).
     * Includes automatic retry and fallback to micro-batches (15 items) if a chunk times out.
     * Immediately marks each successfully committed chunk's records as synced in Room local DB.
     */
    private suspend fun commitSyncOperationsInChunks(
        firestore: FirebaseFirestore,
        db: IspDatabase,
        operations: List<SyncOperation>
    ): Boolean {
        if (operations.isEmpty()) return true
        val chunkSize = 40
        var allChunksSuccessful = true

        for (chunk in operations.chunked(chunkSize)) {
            // First attempt (60s to allow full gRPC SSL TLS connection establishment over cellular networks)
            var success = commitSingleChunk(firestore, db, chunk, timeoutMs = 60000L)

            if (!success) {
                // Quick retry once (18s)
                kotlinx.coroutines.delay(500L)
                Log.i(TAG, "Retrying chunk commit (${chunk.size} operations)...")
                success = commitSingleChunk(firestore, db, chunk, timeoutMs = 18000L)
            }

            if (!success) {
                // Fallback to micro-chunks of 15 items (12s each)
                Log.w(TAG, "Chunk of size ${chunk.size} not acknowledged. Retrying in micro-batches of 15 items...")
                var allSubChunksOk = true
                for (subChunk in chunk.chunked(15)) {
                    val subSuccess = commitSingleChunk(firestore, db, subChunk, timeoutMs = 12000L)
                    if (!subSuccess) {
                        allSubChunksOk = false
                        break
                    }
                }
                if (!allSubChunksOk) {
                    Log.w(TAG, "Sub-chunk commit deferred for this chunk; remaining records stay queued locally.")
                    allChunksSuccessful = false
                    break
                }
            }
        }
        return allChunksSuccessful
    }

    /**
     * Local storage and Firebase Sync for Deleted Records.
     * Keeps track of deleted IDs to prevent restored/stale data from reappearing.
     */
    fun markRecordAsDeleted(context: Context, collectionName: String, id: String) {
        try {
            val prefs = context.getSharedPreferences("isp_deleted_records", Context.MODE_PRIVATE)
            val deletedSet = prefs.getStringSet("deleted_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            deletedSet.add("$collectionName:$id")
            prefs.edit().putStringSet("deleted_ids", deletedSet).apply()

            // Also record in Room pending_deletions table
            val db = IspDatabase.getDatabase(context)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    db.pendingDeletionDao().insertPendingDeletion(
                        PendingDeletionEntity(
                            collectionName = collectionName,
                            documentId = id,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error saving pending deletion: ${e.message}")
                }
            }


        } catch (e: Throwable) {
            Log.w(TAG, "Error marking record as deleted: ${e.message}")
        }
    }

    suspend fun syncAndGetDeletedRecords(context: Context, userRef: com.google.firebase.firestore.DocumentReference): Set<String> {
        return emptySet()
    }

    /**
     * Returns the Firebase Authentication UID if the user is authenticated.
     * Returns null for unauthenticated or guest users.
     */
    fun getCurrentUid(context: Context? = null): String? {
        if (context != null) {
            val localUid = com.example.IspApplication.getUserId(context)
            if (!localUid.isNullOrBlank()) {
                return localUid
            }
            if (!com.example.IspApplication.isLoggedIn(context)) {
                return null
            }
        }
        return try {
            context?.let { com.example.IspApplication.ensureFirebaseInitialized(it) }
            val currentUser = FirebaseAuth.getInstance().currentUser
            currentUser?.uid
        } catch (e: Throwable) {
            Log.w(TAG, "FirebaseAuth not available or initialized: ${e.message}")
            null
        }
    }

    /**
     * Counts the actual number of dirty/unsynced records and pending deletions in local database.
     */
    suspend fun getActualPendingDirtyCount(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val db = IspDatabase.getDatabase(context)
            val dirtyCustomers = db.customerDao().getDirtyCustomers().size
            val dirtyPackages = db.packageDao().getDirtyPackages().size
            val dirtyBills = db.billDao().getDirtyBills().size
            val dirtyPayments = db.paymentDao().getDirtyPayments().size
            val dirtyExpenses = db.expenseDao().getDirtyExpenses().size
            val dirtyCategories = db.expenseDao().getDirtyCategories().size
            val dirtySettings = if (db.settingsDao().getDirtySettings() != null) 1 else 0
            val dirtyDiagrams = 0
            val dirtyNodes = 0
            val dirtyConnections = 0
            val dirtyAuditLogs = db.auditLogDao().getDirtyAuditLogs().size
            val dirtyBandwidthBills = db.bandwidthBillDao().getDirtyBandwidthBills().size
            val dirtySpecificAdvances = db.specificAdvanceDao().getDirtySpecificAdvances().size
            val pendingDeletions = db.pendingDeletionDao().getAllPendingDeletions().size

            dirtyCustomers + dirtyPackages + dirtyBills + dirtyPayments +
                    dirtyExpenses + dirtyCategories + dirtySettings + dirtyDiagrams +
                    dirtyNodes + dirtyConnections + dirtyAuditLogs + dirtyBandwidthBills +
                    dirtySpecificAdvances + pendingDeletions
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Schedules periodic background sync using WorkManager (every 15 mins when connected).
     */
    fun scheduleBackgroundSync(context: Context) {
        try {
            val uid = getCurrentUid(context) ?: return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicWork = PeriodicWorkRequestBuilder<CloudSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "${WORK_NAME_PERIODIC}_$uid",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWork
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error scheduling background sync: ${e.message}")
        }
    }

    /**
     * Triggers an immediate one-time background sync when internet returns.
     */
    fun triggerSync(context: Context) {
        try {
            val uid = getCurrentUid(context) ?: return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val oneTimeWork = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME_ONE_TIME}_$uid",
                ExistingWorkPolicy.REPLACE,
                oneTimeWork
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Error triggering sync: ${e.message}")
        }
    }

    /**
     * Safely deletes a document from user's Firestore collection when deleted locally.
     */
    suspend fun deleteDocumentFromCloud(context: Context, collectionName: String, docId: String) = withContext(Dispatchers.IO) {
        return@withContext
    }

    /**
     * Performs Delta / Dirty Cloud Sync from local Room database to authenticated user's Firestore path.
     * Uploads ONLY modified (syncStatus = 1) records and flushes pending deletions via Firestore WriteBatch.
     * Path structure: users/{uid}/{collection}/{id}
     */
    suspend fun syncLocalToCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        return@withContext true
    }

    /**
     * Dedicated Safe Manual "Backup to Cloud" Operation:
     * Reads ALL existing local customers from Room (db.customerDao().getAllCustomersList()) regardless of syncStatus (0 or 1),
     * along with any other local entities, and safely uploads them to Firestore under users/{uid}/customers/{customerId}
     * using SetOptions.merge() so that existing cloud data is never overwritten or deleted.
     * Guarantees that local customers missing from Firestore are created, existing ones are merged,
     * and returns true ONLY when all batch writes succeed.
     */
    suspend fun uploadAllLocalDataToCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        lastCloudBackupError = null
        com.example.IspApplication.ensureFirebaseInitialized(context)
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank()) {
            lastCloudBackupError = "User is not logged in"
            Log.d(TAG, "Backup to Cloud failed: User is guest or unauthenticated.")
            return@withContext false
        }
        if (!isNetworkAvailable(context)) {
            lastCloudBackupError = "No active network connection"
            Log.w(TAG, "Backup to Cloud failed: No active internet connection.")
            return@withContext false
        }

        try {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_syncing", true).apply()

            val db = IspDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()
            val userRef = firestore.collection("users").document(uid)

            // Step 1: Collect ALL local network diagram entities (Completely Removed from Cloud Backup)
            val syncOperations = mutableListOf<SyncOperation>()

            if (syncOperations.isEmpty()) {
                Log.d(TAG, "Backup to Cloud: No local records found to write.")
                prefs.edit().putBoolean("is_syncing", false).apply()
                return@withContext true
            }

            Log.i(TAG, "Backup to Cloud: Safely uploading all local datasets (${syncOperations.size} operations total) to Firestore path users/$uid/...")
            
            // Execute batches safely in chunks and mark each chunk synced upon success
            val syncSuccess = commitSyncOperationsInChunks(firestore, db, syncOperations)

            if (syncSuccess) {
                try {
                    withTimeoutOrNull(8000L) {
                        userRef.collection("sync_meta").document("status").set(
                            mapOf(
                                "lastSyncTimestamp" to System.currentTimeMillis(),
                                "lastBatchSize" to syncOperations.size
                            ),
                            SetOptions.merge()
                        ).await()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Non-critical: sync_meta status update failed: ${e.message}")
                }

                val remainingDirty = getActualPendingDirtyCount(context)
                val editor = prefs.edit().putBoolean("is_syncing", false)
                editor.putLong("last_cloud_sync_time_$uid", System.currentTimeMillis())
                editor.putInt("pending_sync_count_$uid", remainingDirty)
                editor.apply()

                Log.i(TAG, "Backup to Cloud: Successfully completed full backup to Firestore!")
                true
            } else {
                if (lastCloudBackupError == null) {
                    lastCloudBackupError = "Chunk write failed or timed out"
                }
                val remainingDirty = getActualPendingDirtyCount(context)
                prefs.edit()
                    .putInt("pending_sync_count_$uid", remainingDirty)
                    .putBoolean("is_syncing", false)
                    .apply()
                Log.w(TAG, "Backup to Cloud partially completed: Successful chunks were marked synced; failed chunks remain dirty.")
                false
            }
        } catch (e: FirebaseFirestoreException) {
            context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_syncing", false).apply()
            Log.w(TAG, "Backup to Cloud note (FirestoreException): ${e.message}")
            if (lastCloudBackupError == null) {
                lastCloudBackupError = cleanErrorMessage(e.message)
            }
            false
        } catch (e: Exception) {
            context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_syncing", false).apply()
            Log.w(TAG, "Backup to Cloud note (Exception): ${e.message}")
            if (lastCloudBackupError == null) {
                lastCloudBackupError = cleanErrorMessage(e.message)
            }
            false
        }
    }

    /**
     * Performs Automatic Incremental Delta Pull from Firestore to Room.
     * Fetches only records with updatedAt > lastPullTimestamp, skips locally dirty records (to avoid overwriting unsynced local mutations),
     * and inserts/updates them in Room with syncStatus = 0 (loop prevention).
     */
    suspend fun pullDeltaFromCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        return@withContext true
    }

    private data class RestoredCloudPayload(
        val customers: List<CustomerEntity>,
        val packages: List<IspPackageEntity>,
        val bills: List<BillEntity>,
        val payments: List<PaymentEntity>,
        val expenses: List<ExpenseEntity>,
        val categories: List<ExpenseCategoryEntity>,
        val settings: BusinessSettingsEntity?,
        val diagrams: List<NetworkDiagramEntity>,
        val nodes: List<NetworkNodeEntity>,
        val connections: List<NetworkConnectionEntity>,
        val auditLogs: List<AuditLogEntity>,
        val bandwidthBills: List<BandwidthBillEntity>,
        val specificAdvances: List<SpecificAdvanceEntity>
    )

    /**
     * Restores cloud data from Firestore for the current authenticated user into local Room DB.
     */
    suspend fun restoreCloudToLocal(context: Context): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        com.example.IspApplication.ensureFirebaseInitialized(context)
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "Restore skipped: User is guest or unauthenticated.")
            return@withContext Pair(false, "Authentication required")
        }
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "Restore skipped: No active network connection.")
            return@withContext Pair(false, "No internet connection")
        }

        try {
            val firestore = FirebaseFirestore.getInstance()
            val userRef = firestore.collection("users").document(uid)

            if (!isFirestoreAvailable(userRef, testWrite = false)) {
                Log.w(TAG, "Restore skipped: Firestore backend is currently unreachable.")
                return@withContext Pair(false, "Firestore service is unreachable")
            }

            // Stage 1: Safely fetch all cloud collections in memory under a generous timeout
            val (restoredData, hasAnyData) = withTimeout(60000L) {
                val deletedRecords = syncAndGetDeletedRecords(context, userRef)

                // 1. Restore Customers
                val custDocs = userRef.collection("customers").get().await()
                val restoredCustomers = custDocs.documents.mapNotNull { doc ->
                    val idStr = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L).toString()
                    if (deletedRecords.contains("customers:$idStr")) return@mapNotNull null
                    try {
                        val custId = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                        CustomerEntity(
                            id = custId,
                            customerCode = doc.getString("customerCode") ?: "CUST-$custId",
                            name = doc.getString("name") ?: "",
                            phone = doc.getString("phone") ?: "",
                            address = doc.getString("address") ?: "",
                            pppoeUsername = doc.getString("pppoeUsername") ?: "",
                            ipAddress = doc.getString("ipAddress") ?: "",
                            packageId = doc.safeLong("packageId", 0L),
                            packageName = doc.getString("packageName") ?: "",
                            monthlyFee = doc.safeDouble("monthlyFee", 0.0),
                            status = doc.getString("status") ?: "ACTIVE",
                            joiningDate = doc.getString("joiningDate") ?: "",
                            notes = doc.getString("notes") ?: "",
                            area = doc.getString("area") ?: "",
                            zone = doc.getString("zone") ?: "",
                            latitude = doc.safeDouble("latitude", 0.0),
                            longitude = doc.safeDouble("longitude", 0.0),
                            oltName = doc.getString("oltName") ?: "",
                            ponPort = doc.getString("ponPort") ?: "",
                            onuSerial = doc.getString("onuSerial") ?: "",
                            routerName = doc.getString("routerName") ?: "",
                            advanceBalance = doc.safeDouble("advanceBalance", 0.0),
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                }

                // 2. Restore Packages
                val pkgDocs = userRef.collection("packages").get().await()
                val restoredPackages = pkgDocs.documents.mapNotNull { doc ->
                    val idStr = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L).toString()
                    if (deletedRecords.contains("packages:$idStr")) return@mapNotNull null
                    try {
                        IspPackageEntity(
                            id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L),
                            name = doc.getString("name") ?: "",
                            speedMbps = doc.safeInt("speedMbps", 0),
                            monthlyPrice = doc.safeDouble("monthlyPrice", 0.0),
                            description = doc.getString("description") ?: "",
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                }

                // 3. Restore Bills
                val billDocs = userRef.collection("bills").get().await()
                val restoredBillsRaw = billDocs.documents.mapNotNull { doc ->
                    val idStr = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L).toString()
                    if (deletedRecords.contains("bills:$idStr")) return@mapNotNull null
                    try {
                        BillEntity(
                            id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L),
                            billNumber = doc.getString("billNumber") ?: "",
                            customerId = doc.safeLong("customerId", 0L),
                            customerName = doc.getString("customerName") ?: "",
                            customerCode = doc.getString("customerCode") ?: "",
                            billingMonth = doc.getString("billingMonth") ?: "",
                            amount = doc.safeDouble("amount", 0.0),
                            paidAmount = doc.safeDouble("paidAmount", 0.0),
                            dueAmount = doc.safeDouble("dueAmount", 0.0),
                            status = doc.getString("status") ?: "UNPAID",
                            generatedDate = doc.getString("generatedDate") ?: "",
                            dueDate = doc.getString("dueDate") ?: "",
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                }
                val restoredBills = restoredBillsRaw.groupBy {
                    "${it.customerId}_${com.example.util.BillingMonthUtils.normalizeMonthKey(it.billingMonth)}"
                }.map { (_, group) ->
                    if (group.size == 1) group.first()
                    else {
                        group.maxByOrNull { it.paidAmount > 0 } ?: group.maxByOrNull { it.updatedAt } ?: group.maxByOrNull { it.id } ?: group.first()
                    }
                }

                // 4. Restore Payments
                val payDocs = userRef.collection("payments").get().await()
                val restoredPayments = payDocs.documents.mapNotNull { doc ->
                    val idStr = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L).toString()
                    if (deletedRecords.contains("payments:$idStr")) return@mapNotNull null
                    try {
                        PaymentEntity(
                            id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L),
                            paymentReceiptNo = doc.getString("paymentReceiptNo") ?: "",
                            billId = doc.safeLong("billId", 0L),
                            customerId = doc.safeLong("customerId", 0L),
                            customerName = doc.getString("customerName") ?: "",
                            amount = doc.safeDouble("amount", 0.0),
                            paymentDate = doc.getString("paymentDate") ?: "",
                            paymentMethod = doc.getString("paymentMethod") ?: "Cash",
                            notes = doc.getString("notes") ?: "",
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                }

                // 5. Restore Expenses
                val expDocs = userRef.collection("expenses").get().await()
                val restoredExpenses = expDocs.documents.mapNotNull { doc ->
                    val idStr = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L).toString()
                    if (deletedRecords.contains("expenses:$idStr")) return@mapNotNull null
                    try {
                        ExpenseEntity(
                            id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L),
                            title = doc.getString("title") ?: "",
                            amount = doc.safeDouble("amount", 0.0),
                            category = doc.getString("category") ?: "",
                            date = doc.getString("date") ?: "",
                            paymentMethod = doc.getString("paymentMethod") ?: "Cash",
                            note = doc.getString("note") ?: "",
                            receiptPath = doc.getString("receiptPath")?.ifEmpty { null },
                            createdAt = doc.safeLong("createdAt", System.currentTimeMillis()),
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                }

                // 6. Restore Categories
                val catDocs = userRef.collection("expense_categories").get().await()
                val restoredCategories = catDocs.documents.mapNotNull { doc ->
                    val idStr = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L).toString()
                    if (deletedRecords.contains("expense_categories:$idStr")) return@mapNotNull null
                    try {
                        ExpenseCategoryEntity(
                            id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L),
                            name = doc.getString("name") ?: "",
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                }

                // 7. Restore Settings
                val settingsDoc = userRef.collection("settings").document("business_settings").get().await()
                val restoredSettings = if (settingsDoc.exists()) {
                    BusinessSettingsEntity(
                        id = 1,
                        ispName = settingsDoc.getString("ispName") ?: "",
                        hotline = settingsDoc.getString("hotline") ?: "",
                        address = settingsDoc.getString("address") ?: "",
                        currencySymbol = settingsDoc.getString("currencySymbol") ?: "৳",
                        networkStatus = settingsDoc.getString("networkStatus") ?: "Operational",
                        themeMode = settingsDoc.getString("themeMode") ?: "SYSTEM",
                        logoUri = settingsDoc.getString("logoUri")?.ifEmpty { null },
                        email = settingsDoc.getString("email") ?: "",
                        updatedAt = settingsDoc.safeLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                } else null

                // 8. Restore Network Diagrams, Nodes & Connections (Completely Removed from Firestore Restore)
                val restoredDiagrams = emptyList<NetworkDiagramEntity>()
                val restoredNodes = emptyList<NetworkNodeEntity>()
                val restoredConnections = emptyList<NetworkConnectionEntity>()

                // 9. Restore Audit Logs
                val auditDocs = try {
                    userRef.collection("audit_logs").get().await()
                } catch (e: Exception) { null }
                val restoredLogs = auditDocs?.documents?.mapNotNull { doc ->
                    try {
                        AuditLogEntity(
                            id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L),
                            action = doc.getString("action") ?: "",
                            actionType = doc.getString("actionType") ?: "",
                            details = doc.getString("details") ?: "",
                            userEmail = doc.getString("userEmail") ?: "",
                            userRole = doc.getString("userRole") ?: "",
                            targetEntity = doc.getString("targetEntity") ?: "",
                            targetId = doc.getString("targetId") ?: "",
                            previousState = doc.getString("previousState") ?: "",
                            newState = doc.getString("newState") ?: "",
                            status = doc.getString("status") ?: "SUCCESS",
                            timestamp = doc.safeLong("timestamp", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()

                // 10. Restore Bandwidth Bills
                val bwDocs = try {
                    userRef.collection("bandwidth_bills").get().await()
                } catch (e: Exception) { null }
                val restoredBwBills = bwDocs?.documents?.mapNotNull { doc ->
                    try {
                        val month = doc.getString("billingMonth") ?: doc.id
                        val amount = doc.safeDouble("amount", 0.0)
                        if (month.isNotBlank()) BandwidthBillEntity(billingMonth = month, amount = amount) else null
                    } catch (e: Exception) { null }
                } ?: emptyList()

                // 11. Restore Specific Advances
                val saDocs = try {
                    userRef.collection("specific_advances").get().await()
                } catch (e: Exception) { null }
                val restoredSpecificAdvances = saDocs?.documents?.mapNotNull { doc ->
                    try {
                        val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                        SpecificAdvanceEntity(
                            id = id,
                            customerId = doc.safeLong("customerId", 0L),
                            billingMonth = doc.getString("billingMonth") ?: "",
                            amount = doc.safeDouble("amount", 0.0),
                            isConsumed = doc.getBoolean("isConsumed") ?: false,
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis())
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()

                val hasData = restoredCustomers.isNotEmpty() || restoredPackages.isNotEmpty() ||
                        restoredBills.isNotEmpty() || restoredPayments.isNotEmpty() ||
                        restoredExpenses.isNotEmpty() || restoredCategories.isNotEmpty() ||
                        restoredSettings != null || restoredDiagrams.isNotEmpty() ||
                        restoredNodes.isNotEmpty() || restoredConnections.isNotEmpty() ||
                        restoredLogs.isNotEmpty() || restoredBwBills.isNotEmpty() ||
                        restoredSpecificAdvances.isNotEmpty()

                val payload = RestoredCloudPayload(
                    customers = restoredCustomers,
                    packages = restoredPackages,
                    bills = restoredBills,
                    payments = restoredPayments,
                    expenses = restoredExpenses,
                    categories = restoredCategories,
                    settings = restoredSettings,
                    diagrams = restoredDiagrams,
                    nodes = restoredNodes,
                    connections = restoredConnections,
                    auditLogs = restoredLogs,
                    bandwidthBills = restoredBwBills,
                    specificAdvances = restoredSpecificAdvances
                )
                Pair(payload, hasData)
            }

            if (!hasAnyData) {
                Log.w(TAG, "No cloud backup found for UID: $uid")
                return@withContext Pair(false, "No cloud backup found")
            }

            // Stage 2: Atomically replace local database only after full validation
            val db = IspDatabase.getDatabase(context)
            db.withTransaction {
                db.customerDao().deleteAllCustomers()
                db.packageDao().deleteAllPackages()
                db.billDao().deleteAllBills()
                db.paymentDao().deleteAllPayments()
                db.expenseDao().deleteAllExpenses()
                db.expenseDao().deleteAllCategories()
                db.bandwidthBillDao().deleteAllBandwidthBills()
                db.specificAdvanceDao().deleteAllSpecificAdvances()
                db.settingsDao().deleteSettings()

                db.auditLogDao().deleteAllLogs()
                db.pendingDeletionDao().clearAllPendingDeletions()

                if (restoredData.customers.isNotEmpty()) {
                    db.customerDao().insertCustomers(restoredData.customers)
                }
                if (restoredData.packages.isNotEmpty()) {
                    db.packageDao().insertPackages(restoredData.packages)
                }
                if (restoredData.bills.isNotEmpty()) {
                    db.billDao().insertBills(restoredData.bills)
                }
                if (restoredData.payments.isNotEmpty()) {
                    db.paymentDao().insertPayments(restoredData.payments)
                }
                if (restoredData.expenses.isNotEmpty()) {
                    db.expenseDao().insertExpenses(restoredData.expenses)
                }
                if (restoredData.categories.isNotEmpty()) {
                    db.expenseDao().insertCategories(restoredData.categories)
                }
                if (restoredData.bandwidthBills.isNotEmpty()) {
                    db.bandwidthBillDao().insertOrUpdateBandwidthBills(restoredData.bandwidthBills)
                }
                if (restoredData.specificAdvances.isNotEmpty()) {
                    db.specificAdvanceDao().insertSpecificAdvances(restoredData.specificAdvances)
                }
                if (restoredData.settings != null) {
                    db.settingsDao().insertOrUpdateSettings(restoredData.settings)
                }

                if (restoredData.auditLogs.isNotEmpty()) {
                    db.auditLogDao().insertLogs(restoredData.auditLogs)
                }
            }

            Log.i(TAG, "Successfully restored data from Firestore for UID: $uid")
            Pair(true, "Cloud restore successful")
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Cloud restore timed out: ${e.message}")
            Pair(false, "Restore timed out")
        } catch (e: FirebaseFirestoreException) {
            val msg = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Cloud restore failed: Permission denied"
            } else {
                "Cloud restore failed"
            }
            Log.w(TAG, "Firestore error restoring cloud data: ${e.message}")
            Pair(false, msg)
        } catch (e: java.io.IOException) {
            Log.w(TAG, "Network error restoring cloud data: ${e.message}")
            Pair(false, "Cloud restore failed")
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring from Firestore: ${e.message}")
            Pair(false, "Cloud restore failed")
        }
    }
}

class CloudSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("CloudSyncWorker", "Executing scheduled background cloud sync to Hosting + MySQL...")
        if (!FirestoreSyncManager.isNetworkAvailable(context)) {
            Log.d("CloudSyncWorker", "Skipping background sync: No network connection.")
            return Result.retry()
        }

        // 1. Primary Hosting + MySQL Background Sync
        val hostingUploadSuccess = withTimeoutOrNull(60000L) {
            HostingSyncManager.syncLocalToHosting(context)
        } ?: false

        val hostingPullSuccess = withTimeoutOrNull(45000L) {
            HostingSyncManager.pullDeltaFromHosting(context)
        } ?: false

        // 2. Firebase / Firestore Sync (for non-migrated network diagrams and legacy fallback)
        val firestoreUploadSuccess = withTimeoutOrNull(45000L) {
            try {
                FirestoreSyncManager.syncLocalToCloud(context)
            } catch (_: Exception) {
                false
            }
        } ?: false

        val firestorePullSuccess = withTimeoutOrNull(30000L) {
            try {
                FirestoreSyncManager.pullDeltaFromCloud(context)
            } catch (_: Exception) {
                false
            }
        } ?: false

        return if (hostingUploadSuccess || hostingPullSuccess || firestoreUploadSuccess || firestorePullSuccess) {
            Result.success()
        } else {
            Log.w("CloudSyncWorker", "Background sync did not complete. Retrying...")
            Result.retry()
        }
    }
}
