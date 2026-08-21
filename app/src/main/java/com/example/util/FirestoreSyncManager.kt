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

    private data class SyncOperation(
        val writeOp: (WriteBatch) -> Unit,
        val onSuccess: suspend (IspDatabase) -> Unit
    )

    /**
     * Helper to execute WriteBatch in chunks (up to 450 operations per batch, well below Firestore 500 limit).
     * Immediately marks only the successfully committed chunk's records as synced in Room local DB.
     */
    private suspend fun commitSyncOperationsInChunks(
        firestore: FirebaseFirestore,
        db: IspDatabase,
        operations: List<SyncOperation>
    ): Boolean {
        if (operations.isEmpty()) return true
        val chunkSize = 450
        var allChunksSuccessful = true

        for (chunk in operations.chunked(chunkSize)) {
            try {
                val batch = firestore.batch()
                for (op in chunk) {
                    op.writeOp(batch)
                }
                batch.commit().await()

                // Mark only the successfully committed chunk's records as synced locally in Room
                db.withTransaction {
                    for (op in chunk) {
                        op.onSuccess(db)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chunk commit failed: ${e.message}", e)
                allChunksSuccessful = false
                break
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

            val uid = getCurrentUid(context)
            if (!uid.isNullOrBlank()) {
                val firestore = FirebaseFirestore.getInstance()
                val docId = "${collectionName}_$id"
                firestore.collection("users").document(uid)
                    .collection("deleted_records").document(docId)
                    .set(mapOf(
                        "collection" to collectionName,
                        "recordId" to id,
                        "deletedAt" to System.currentTimeMillis()
                    ))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error marking record as deleted: ${e.message}")
        }
    }

    suspend fun syncAndGetDeletedRecords(context: Context, userRef: com.google.firebase.firestore.DocumentReference): Set<String> {
        val prefs = context.getSharedPreferences("isp_deleted_records", Context.MODE_PRIVATE)
        val localDeleted = prefs.getStringSet("deleted_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val combinedDeleted = mutableSetOf<String>()
        combinedDeleted.addAll(localDeleted)
        
        try {
            val remoteDeletedDocs = withTimeoutOrNull(5000L) {
                userRef.collection("deleted_records").get().await()
            }
            remoteDeletedDocs?.documents?.forEach { doc ->
                val collection = doc.getString("collection") ?: ""
                val recordId = doc.getString("recordId") ?: ""
                if (collection.isNotBlank() && recordId.isNotBlank()) {
                    combinedDeleted.add("$collection:$recordId")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching remote deleted records: ${e.message}")
        }
        
        // Sync local deleted ones to remote
        localDeleted.forEach { key ->
            val parts = key.split(":")
            if (parts.size == 2) {
                val col = parts[0]
                val id = parts[1]
                try {
                    userRef.collection("deleted_records").document("${col}_$id").set(
                        mapOf(
                            "collection" to col,
                            "recordId" to id,
                            "deletedAt" to System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error uploading deleted record tombstone: ${e.message}")
                }
            }
        }
        
        // Save the merged list back locally to stay updated
        prefs.edit().putStringSet("deleted_ids", combinedDeleted).apply()
        
        return combinedDeleted
    }

    /**
     * Returns the Firebase Authentication UID if the user is authenticated.
     * Returns null for unauthenticated or guest users.
     */
    fun getCurrentUid(context: Context? = null): String? {
        if (context != null && !com.example.IspApplication.isLoggedIn(context)) {
            return null
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
     * Schedules periodic background sync using WorkManager (every 15 mins when connected).
     */
    fun scheduleBackgroundSync(context: Context) {
        try {
            val uid = getCurrentUid() ?: return
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
            val uid = getCurrentUid() ?: return
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
        com.example.IspApplication.ensureFirebaseInitialized(context)
        val uid = getCurrentUid(context) ?: return@withContext
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.collection("users").document(uid)
                .collection(collectionName).document(docId)
                .delete()
                .await()
            Log.d(TAG, "Deleted doc $docId from cloud collection $collectionName")
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(TAG, "Firestore delete permission denied for doc $docId: ${e.message}")
            } else {
                Log.w(TAG, "Firestore error deleting doc $docId: ${e.message}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error deleting doc $docId from cloud: ${e.message}")
        }
    }

    /**
     * Performs Delta / Dirty Cloud Sync from local Room database to authenticated user's Firestore path.
     * Uploads ONLY modified (syncStatus = 1) records and flushes pending deletions via Firestore WriteBatch.
     * Path structure: users/{uid}/{collection}/{id}
     */
    suspend fun syncLocalToCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        com.example.IspApplication.ensureFirebaseInitialized(context)
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "Sync failed: User is guest or unauthenticated.")
            return@withContext false
        }

        try {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_syncing", true).apply()

            val db = IspDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()
            val userRef = firestore.collection("users").document(uid)

            // Step 1: Collect dirty entities only
            val dirtyCustomers = db.customerDao().getDirtyCustomers()
            val dirtyPackages = db.packageDao().getDirtyPackages()
            val dirtyBills = db.billDao().getDirtyBills()
            val dirtyPayments = db.paymentDao().getDirtyPayments()
            val dirtyExpenses = db.expenseDao().getDirtyExpenses()
            val dirtyCategories = db.expenseDao().getDirtyCategories()
            val dirtySettings = db.settingsDao().getDirtySettings()
            val dirtyDiagrams = db.networkDiagramDao().getDirtyDiagrams()
            val dirtyNodes = db.networkDiagramDao().getDirtyNodes()
            val dirtyConnections = db.networkDiagramDao().getDirtyConnections()
            val dirtyAuditLogs = db.auditLogDao().getDirtyAuditLogs()
            val dirtyBandwidthBills = db.bandwidthBillDao().getDirtyBandwidthBills()
            val dirtySpecificAdvances = db.specificAdvanceDao().getDirtySpecificAdvances()
            val pendingDeletions = db.pendingDeletionDao().getAllPendingDeletions()

            val totalDirtyCount = dirtyCustomers.size + dirtyPackages.size + dirtyBills.size +
                    dirtyPayments.size + dirtyExpenses.size + dirtyCategories.size +
                    (if (dirtySettings != null) 1 else 0) + dirtyDiagrams.size +
                    dirtyNodes.size + dirtyConnections.size + dirtyAuditLogs.size +
                    dirtyBandwidthBills.size + dirtySpecificAdvances.size +
                    pendingDeletions.size

            if (totalDirtyCount == 0) {
                Log.d(TAG, "Delta Sync: No dirty records or pending deletions to upload. Quota preserved.")
                prefs.edit()
                    .putLong("last_cloud_sync_time", System.currentTimeMillis())
                    .putInt("pending_sync_count", 0)
                    .putBoolean("is_syncing", false)
                    .apply()
                return@withContext true
            }

            Log.i(TAG, "Delta Sync: Processing $totalDirtyCount modified records with conflict protection for UID: $uid")

            // Multi-Device Conflict Protection:
            // Fetch remote metadata ONLY for dirty document IDs (never full collections) to verify updatedAt.
            val remoteTimestamps = mutableMapOf<String, Long>()
            val remoteDocsToFetch = mutableListOf<Pair<String, com.google.firebase.firestore.DocumentReference>>()

            for (c in dirtyCustomers) remoteDocsToFetch.add("customers:${c.id}" to userRef.collection("customers").document(c.id.toString()))
            for (p in dirtyPackages) remoteDocsToFetch.add("packages:${p.id}" to userRef.collection("packages").document(p.id.toString()))
            for (b in dirtyBills) remoteDocsToFetch.add("bills:${b.id}" to userRef.collection("bills").document(b.id.toString()))
            for (pm in dirtyPayments) remoteDocsToFetch.add("payments:${pm.id}" to userRef.collection("payments").document(pm.id.toString()))
            for (e in dirtyExpenses) remoteDocsToFetch.add("expenses:${e.id}" to userRef.collection("expenses").document(e.id.toString()))
            for (ec in dirtyCategories) remoteDocsToFetch.add("expense_categories:${ec.id}" to userRef.collection("expense_categories").document(ec.id.toString()))
            if (dirtySettings != null) remoteDocsToFetch.add("settings:business_settings" to userRef.collection("settings").document("business_settings"))
            for (d in dirtyDiagrams) remoteDocsToFetch.add("network_diagrams:${d.id}" to userRef.collection("network_diagrams").document(d.id.toString()))
            for (n in dirtyNodes) remoteDocsToFetch.add("network_nodes:${n.id}" to userRef.collection("network_nodes").document(n.id))
            for (cn in dirtyConnections) remoteDocsToFetch.add("network_connections:${cn.id}" to userRef.collection("network_connections").document(cn.id))
            for (b in dirtyBandwidthBills) remoteDocsToFetch.add("bandwidth_bills:${b.billingMonth}" to userRef.collection("bandwidth_bills").document(b.billingMonth))
            for (sa in dirtySpecificAdvances) remoteDocsToFetch.add("specific_advances:${sa.id}" to userRef.collection("specific_advances").document(sa.id.toString()))

            // Fetch targeted remote snapshots in parallel/chunks without scanning full collections
            for (item in remoteDocsToFetch) {
                try {
                    val snap = item.second.get().await()
                    if (snap.exists()) {
                        val rUpdatedAt = snap.getLong("updatedAt") ?: snap.getLong("timestamp") ?: 0L
                        remoteTimestamps[item.first] = rUpdatedAt
                    }
                } catch (e: Exception) {
                    // In offline/transient failure, allow delta batch merge as fallback
                    Log.d(TAG, "Conflict check non-blocking note for ${item.first}: ${e.message}")
                }
            }

            val syncOperations = mutableListOf<SyncOperation>()

            // 1. Pending Deletions
            for (del in pendingDeletions) {
                val docRef = userRef.collection(del.collectionName).document(del.documentId)
                val delId = del.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.delete(docRef) },
                        onSuccess = { database -> database.pendingDeletionDao().deletePendingDeletionsByIds(listOf(delId)) }
                    )
                )
            }

            // 2. Customers
            for (customer in dirtyCustomers) {
                val rTime = remoteTimestamps["customers:${customer.id}"]
                if (rTime != null && rTime > customer.updatedAt) {
                    // Conflict: Remote document is NEWER than local dirty version.
                    // Server wins: do NOT overwrite remote. Mark locally synced to avoid infinite conflict loops.
                    Log.w(TAG, "Conflict detected for Customer ${customer.id}: remote ($rTime) is newer than local (${customer.updatedAt}). Preserving remote.")
                    db.customerDao().markCustomersSynced(listOf(customer.id))
                } else {
                    val map = mapOf(
                        "id" to customer.id,
                        "customerCode" to customer.customerCode,
                        "name" to customer.name,
                        "phone" to customer.phone,
                        "address" to customer.address,
                        "pppoeUsername" to customer.pppoeUsername,
                        "ipAddress" to customer.ipAddress,
                        "packageId" to customer.packageId,
                        "packageName" to customer.packageName,
                        "monthlyFee" to customer.monthlyFee,
                        "status" to customer.status,
                        "joiningDate" to customer.joiningDate,
                        "notes" to customer.notes,
                        "area" to customer.area,
                        "zone" to customer.zone,
                        "latitude" to customer.latitude,
                        "longitude" to customer.longitude,
                        "oltName" to customer.oltName,
                        "ponPort" to customer.ponPort,
                        "onuSerial" to customer.onuSerial,
                        "routerName" to customer.routerName,
                        "advanceBalance" to customer.advanceBalance,
                        "updatedAt" to customer.updatedAt
                    )
                    val docRef = userRef.collection("customers").document(customer.id.toString())
                    val cid = customer.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { database -> database.customerDao().markCustomersSynced(listOf(cid)) }
                        )
                    )
                }
            }

            // 3. Packages
            for (pkg in dirtyPackages) {
                val rTime = remoteTimestamps["packages:${pkg.id}"]
                if (rTime != null && rTime > pkg.updatedAt) {
                    Log.w(TAG, "Conflict detected for Package ${pkg.id}: remote ($rTime) > local (${pkg.updatedAt}). Preserving remote.")
                    db.packageDao().markPackagesSynced(listOf(pkg.id))
                } else {
                    val map = mapOf(
                        "id" to pkg.id,
                        "name" to pkg.name,
                        "speedMbps" to pkg.speedMbps,
                        "monthlyPrice" to pkg.monthlyPrice,
                        "description" to pkg.description,
                        "updatedAt" to pkg.updatedAt
                    )
                    val docRef = userRef.collection("packages").document(pkg.id.toString())
                    val pid = pkg.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { database -> database.packageDao().markPackagesSynced(listOf(pid)) }
                        )
                    )
                }
            }

            // 4. Bills
            for (bill in dirtyBills) {
                val rTime = remoteTimestamps["bills:${bill.id}"]
                if (rTime != null && rTime > bill.updatedAt) {
                    Log.w(TAG, "Conflict detected for Bill ${bill.id}: remote ($rTime) > local (${bill.updatedAt}). Preserving remote.")
                    db.billDao().markBillsSynced(listOf(bill.id))
                } else {
                    val map = mapOf(
                        "id" to bill.id,
                        "billNumber" to bill.billNumber,
                        "customerId" to bill.customerId,
                        "customerName" to bill.customerName,
                        "customerCode" to bill.customerCode,
                        "billingMonth" to bill.billingMonth,
                        "amount" to bill.amount,
                        "paidAmount" to bill.paidAmount,
                        "dueAmount" to bill.dueAmount,
                        "status" to bill.status,
                        "generatedDate" to bill.generatedDate,
                        "dueDate" to bill.dueDate,
                        "updatedAt" to bill.updatedAt
                    )
                    val docRef = userRef.collection("bills").document(bill.id.toString())
                    val bid = bill.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { database -> database.billDao().markBillsSynced(listOf(bid)) }
                        )
                    )
                }
            }

            // 5. Payments
            for (payment in dirtyPayments) {
                val rTime = remoteTimestamps["payments:${payment.id}"]
                if (rTime != null && rTime > payment.updatedAt) {
                    Log.w(TAG, "Conflict detected for Payment ${payment.id}: remote ($rTime) > local (${payment.updatedAt}). Preserving remote.")
                    db.paymentDao().markPaymentsSynced(listOf(payment.id))
                } else {
                    val map = mapOf(
                        "id" to payment.id,
                        "paymentReceiptNo" to payment.paymentReceiptNo,
                        "billId" to payment.billId,
                        "customerId" to payment.customerId,
                        "customerName" to payment.customerName,
                        "amount" to payment.amount,
                        "paymentDate" to payment.paymentDate,
                        "paymentMethod" to payment.paymentMethod,
                        "notes" to payment.notes,
                        "updatedAt" to payment.updatedAt
                    )
                    val docRef = userRef.collection("payments").document(payment.id.toString())
                    val payId = payment.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { database -> database.paymentDao().markPaymentsSynced(listOf(payId)) }
                        )
                    )
                }
            }

            // 6. Expenses
            for (expense in dirtyExpenses) {
                val rTime = remoteTimestamps["expenses:${expense.id}"]
                if (rTime != null && rTime > expense.updatedAt) {
                    Log.w(TAG, "Conflict detected for Expense ${expense.id}: remote ($rTime) > local (${expense.updatedAt}). Preserving remote.")
                    db.expenseDao().markExpensesSynced(listOf(expense.id))
                } else {
                    val map = mapOf(
                        "id" to expense.id,
                        "title" to expense.title,
                        "amount" to expense.amount,
                        "category" to expense.category,
                        "date" to expense.date,
                        "paymentMethod" to expense.paymentMethod,
                        "note" to expense.note,
                        "receiptPath" to (expense.receiptPath ?: ""),
                        "createdAt" to expense.createdAt,
                        "updatedAt" to expense.updatedAt
                    )
                    val docRef = userRef.collection("expenses").document(expense.id.toString())
                    val expId = expense.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { database -> database.expenseDao().markExpensesSynced(listOf(expId)) }
                        )
                    )
                }
            }

            // 7. Categories
            for (cat in dirtyCategories) {
                val rTime = remoteTimestamps["expense_categories:${cat.id}"]
                if (rTime != null && rTime > cat.updatedAt) {
                    Log.w(TAG, "Conflict detected for Category ${cat.id}: remote ($rTime) > local (${cat.updatedAt}). Preserving remote.")
                    db.expenseDao().markCategoriesSynced(listOf(cat.id))
                } else {
                    val map = mapOf(
                        "id" to cat.id,
                        "name" to cat.name,
                        "updatedAt" to cat.updatedAt
                    )
                    val docRef = userRef.collection("expense_categories").document(cat.id.toString())
                    val catId = cat.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { database -> database.expenseDao().markCategoriesSynced(listOf(catId)) }
                        )
                    )
                }
            }

            // 8. Settings
            if (dirtySettings != null) {
                val rTime = remoteTimestamps["settings:business_settings"]
                if (rTime != null && rTime > dirtySettings.updatedAt) {
                    Log.w(TAG, "Conflict detected for Business Settings: remote ($rTime) > local (${dirtySettings.updatedAt}). Preserving remote.")
                    db.settingsDao().markSettingsSynced()
                } else {
                    val map = mapOf(
                        "id" to dirtySettings.id,
                        "ispName" to dirtySettings.ispName,
                        "hotline" to dirtySettings.hotline,
                        "address" to dirtySettings.address,
                        "currencySymbol" to dirtySettings.currencySymbol,
                        "networkStatus" to dirtySettings.networkStatus,
                        "themeMode" to dirtySettings.themeMode,
                        "logoUri" to (dirtySettings.logoUri ?: ""),
                        "email" to (dirtySettings.email ?: ""),
                        "updatedAt" to dirtySettings.updatedAt
                    )
                    val docRef = userRef.collection("settings").document("business_settings")
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { database -> database.settingsDao().markSettingsSynced() }
                        )
                    )
                }
            }

            // 9. Network Diagrams
            for (diag in dirtyDiagrams) {
                val rTime = remoteTimestamps["network_diagrams:${diag.id}"]
                if (rTime != null && rTime > diag.updatedAt) {
                    Log.w(TAG, "Conflict detected for Diagram ${diag.id}: remote ($rTime) > local (${diag.updatedAt}). Preserving remote.")
                    db.networkDiagramDao().markDiagramsSynced(listOf(diag.id))
                } else {
                    val map = mapOf(
                        "id" to diag.id,
                        "name" to diag.name,
                        "isDefault" to diag.isDefault,
                        "createdAt" to diag.createdAt,
                        "updatedAt" to diag.updatedAt
                    )
                    val docRef = userRef.collection("network_diagrams").document(diag.id.toString())
                    val diagId = diag.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { database -> database.networkDiagramDao().markDiagramsSynced(listOf(diagId)) }
                        )
                    )
                }
            }

            // 10. Network Nodes
            for (node in dirtyNodes) {
                val rTime = remoteTimestamps["network_nodes:${node.id}"]
                if (rTime != null && rTime > node.updatedAt) {
                    Log.w(TAG, "Conflict detected for Node ${node.id}: remote ($rTime) > local (${node.updatedAt}). Preserving remote.")
                    db.networkDiagramDao().markNodesSynced(listOf(node.id))
                } else {
                    val nodeMap = mapOf(
                        "id" to node.id,
                        "diagramId" to node.diagramId,
                        "name" to node.name,
                        "type" to node.type,
                        "ipAddress" to node.ipAddress,
                        "location" to node.location,
                        "areaZone" to node.areaZone,
                        "portInfo" to node.portInfo,
                        "customerRef" to node.customerRef,
                        "customerId" to node.customerId,
                        "notes" to node.notes,
                        "positionX" to node.positionX,
                        "positionY" to node.positionY,
                        "updatedAt" to node.updatedAt
                    )
                    val docRef = userRef.collection("network_nodes").document(node.id)
                    val nodeId = node.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, nodeMap, SetOptions.merge()) },
                            onSuccess = { database -> database.networkDiagramDao().markNodesSynced(listOf(nodeId)) }
                        )
                    )
                }
            }

            // 11. Network Connections
            for (conn in dirtyConnections) {
                val rTime = remoteTimestamps["network_connections:${conn.id}"]
                if (rTime != null && rTime > conn.updatedAt) {
                    Log.w(TAG, "Conflict detected for Connection ${conn.id}: remote ($rTime) > local (${conn.updatedAt}). Preserving remote.")
                    db.networkDiagramDao().markConnectionsSynced(listOf(conn.id))
                } else {
                    val connMap = mapOf(
                        "id" to conn.id,
                        "diagramId" to conn.diagramId,
                        "fromNodeId" to conn.fromNodeId,
                        "toNodeId" to conn.toNodeId,
                        "label" to conn.label,
                        "notes" to conn.notes,
                        "updatedAt" to conn.updatedAt
                    )
                    val docRef = userRef.collection("network_connections").document(conn.id)
                    val connId = conn.id
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, connMap, SetOptions.merge()) },
                            onSuccess = { database -> database.networkDiagramDao().markConnectionsSynced(listOf(connId)) }
                        )
                    )
                }
            }

            // 12. Audit Logs
            for (log in dirtyAuditLogs) {
                val logMap = mapOf(
                    "id" to log.id,
                    "action" to log.action,
                    "actionType" to log.actionType,
                    "details" to log.details,
                    "userEmail" to log.userEmail,
                    "userRole" to log.userRole,
                    "targetEntity" to log.targetEntity,
                    "targetId" to log.targetId,
                    "previousState" to log.previousState,
                    "newState" to log.newState,
                    "status" to log.status,
                    "timestamp" to log.timestamp
                )
                val docRef = userRef.collection("audit_logs").document(log.id.toString())
                val logId = log.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, logMap, SetOptions.merge()) },
                        onSuccess = { database -> database.auditLogDao().markAuditLogsSynced(listOf(logId)) }
                    )
                )
            }

            // 13. Bandwidth Bills
            for (b in dirtyBandwidthBills) {
                val remoteTs = remoteTimestamps["bandwidth_bills:${b.billingMonth}"]
                if (remoteTs != null && remoteTs > b.updatedAt) {
                    Log.w(TAG, "Conflict detected for bandwidth_bill ${b.billingMonth}: Server ($remoteTs) > Local (${b.updatedAt}). Preserving server copy.")
                    db.bandwidthBillDao().markBandwidthBillsSynced(listOf(b.billingMonth))
                    continue
                }
                val bMap = mapOf(
                    "billingMonth" to b.billingMonth,
                    "amount" to b.amount,
                    "updatedAt" to b.updatedAt
                )
                val docRef = userRef.collection("bandwidth_bills").document(b.billingMonth)
                val bMonth = b.billingMonth
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, bMap, SetOptions.merge()) },
                        onSuccess = { database -> database.bandwidthBillDao().markBandwidthBillsSynced(listOf(bMonth)) }
                    )
                )
            }

            // 14. Specific Advances
            for (sa in dirtySpecificAdvances) {
                val remoteTs = remoteTimestamps["specific_advances:${sa.id}"]
                if (remoteTs != null && remoteTs > sa.updatedAt) {
                    Log.w(TAG, "Conflict detected for specific_advance ${sa.id}: Server ($remoteTs) > Local (${sa.updatedAt}). Preserving server copy.")
                    db.specificAdvanceDao().markSpecificAdvancesSynced(listOf(sa.id))
                    continue
                }
                val saMap = mapOf(
                    "id" to sa.id,
                    "customerId" to sa.customerId,
                    "billingMonth" to sa.billingMonth,
                    "amount" to sa.amount,
                    "isConsumed" to sa.isConsumed,
                    "updatedAt" to sa.updatedAt
                )
                val docRef = userRef.collection("specific_advances").document(sa.id.toString())
                val saId = sa.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, saMap, SetOptions.merge()) },
                        onSuccess = { database -> database.specificAdvanceDao().markSpecificAdvancesSynced(listOf(saId)) }
                    )
                )
            }

            // Commit write operations in chunks and mark each chunk synced upon success
            val syncSuccess = commitSyncOperationsInChunks(firestore, db, syncOperations)

            if (syncSuccess) {
                userRef.collection("sync_meta").document("status").set(
                    mapOf(
                        "lastSyncTimestamp" to System.currentTimeMillis(),
                        "lastBatchSize" to totalDirtyCount
                    ),
                    SetOptions.merge()
                ).await()

                prefs.edit()
                    .putLong("last_cloud_sync_time", System.currentTimeMillis())
                    .putInt("pending_sync_count", 0)
                    .putBoolean("is_syncing", false)
                    .apply()

                Log.i(TAG, "Successfully committed Delta Sync batch of $totalDirtyCount records for UID: $uid")
                true
            } else {
                prefs.edit().putBoolean("is_syncing", false).apply()
                Log.w(TAG, "Delta Sync partially completed: Successful chunks were marked synced; failed chunks remain dirty.")
                false
            }
        } catch (e: FirebaseFirestoreException) {
            context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_syncing", false).apply()
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(TAG, "Firestore sync skipped (Permission Denied): Check Firestore security rules or authentication status.")
            } else {
                Log.w(TAG, "Firestore error syncing local data: ${e.message}")
            }
            false
        } catch (e: Exception) {
            context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_syncing", false).apply()
            Log.w(TAG, "Error syncing local data to Firestore: ${e.message}")
            false
        }
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
        com.example.IspApplication.ensureFirebaseInitialized(context)
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "Backup to Cloud failed: User is guest or unauthenticated.")
            return@withContext false
        }

        try {
            val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("is_syncing", true).apply()

            val db = IspDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()
            val userRef = firestore.collection("users").document(uid)

            // Step 1: Collect ALL local entities regardless of syncStatus (0 or 1)
            val allCustomers = db.customerDao().getAllCustomersList()
            val allPackages = db.packageDao().getAllPackagesList()
            val allBills = db.billDao().getAllBillsList()
            val allPayments = db.paymentDao().getAllPaymentsList()
            val allExpenses = db.expenseDao().getAllExpensesList()
            val allCategories = db.expenseDao().getAllCategoriesList()
            val settings = db.settingsDao().getSettingsSingle()
            val allDiagrams = db.networkDiagramDao().getAllDiagramsList()
            val allNodes = db.networkDiagramDao().getAllNodesList()
            val allConnections = db.networkDiagramDao().getAllConnectionsList()
            val allAuditLogs = db.auditLogDao().getAllAuditLogsList()
            val allBwBills = db.bandwidthBillDao().getAllBandwidthBillsList()
            val allSpecificAdvances = db.specificAdvanceDao().getAllSpecificAdvancesList()
            val pendingDeletions = db.pendingDeletionDao().getAllPendingDeletions()

            val syncOperations = mutableListOf<SyncOperation>()

            // 1. Pending Deletions (Delta deleted records queue)
            for (del in pendingDeletions) {
                val docRef = userRef.collection(del.collectionName).document(del.documentId)
                val delId = del.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.delete(docRef) },
                        onSuccess = { database -> database.pendingDeletionDao().deletePendingDeletionsByIds(listOf(delId)) }
                    )
                )
            }

            // 1.5. deleted_records (Tombstones from SharedPreferences)
            val deletedPrefs = context.getSharedPreferences("isp_deleted_records", Context.MODE_PRIVATE)
            val localDeletedSet = deletedPrefs.getStringSet("deleted_ids", emptySet()) ?: emptySet()
            for (key in localDeletedSet) {
                val parts = key.split(":")
                if (parts.size == 2) {
                    val col = parts[0]
                    val id = parts[1]
                    val docId = "${col}_$id"
                    val docRef = userRef.collection("deleted_records").document(docId)
                    val map = mapOf(
                        "collection" to col,
                        "recordId" to id,
                        "deletedAt" to System.currentTimeMillis()
                    )
                    syncOperations.add(
                        SyncOperation(
                            writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                            onSuccess = { /* No Room status change required for preferences tombstone */ }
                        )
                    )
                }
            }

            // 2. ALL Local Customers -> users/{uid}/customers/{customerId} (Safe Merge)
            for (customer in allCustomers) {
                val map = mapOf(
                    "id" to customer.id,
                    "customerCode" to customer.customerCode,
                    "name" to customer.name,
                    "phone" to customer.phone,
                    "address" to customer.address,
                    "pppoeUsername" to customer.pppoeUsername,
                    "ipAddress" to customer.ipAddress,
                    "packageId" to customer.packageId,
                    "packageName" to customer.packageName,
                    "monthlyFee" to customer.monthlyFee,
                    "status" to customer.status,
                    "joiningDate" to customer.joiningDate,
                    "notes" to customer.notes,
                    "area" to customer.area,
                    "zone" to customer.zone,
                    "latitude" to customer.latitude,
                    "longitude" to customer.longitude,
                    "oltName" to customer.oltName,
                    "ponPort" to customer.ponPort,
                    "onuSerial" to customer.onuSerial,
                    "routerName" to customer.routerName,
                    "advanceBalance" to customer.advanceBalance,
                    "updatedAt" to customer.updatedAt
                )
                val docRef = userRef.collection("customers").document(customer.id.toString())
                val cid = customer.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                        onSuccess = { database -> database.customerDao().markCustomersSynced(listOf(cid)) }
                    )
                )
            }

            // 3. Packages
            for (pkg in allPackages) {
                val map = mapOf(
                    "id" to pkg.id,
                    "name" to pkg.name,
                    "speedMbps" to pkg.speedMbps,
                    "monthlyPrice" to pkg.monthlyPrice,
                    "description" to pkg.description,
                    "updatedAt" to pkg.updatedAt
                )
                val docRef = userRef.collection("packages").document(pkg.id.toString())
                val pid = pkg.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                        onSuccess = { database -> database.packageDao().markPackagesSynced(listOf(pid)) }
                    )
                )
            }

            // 4. Bills
            for (bill in allBills) {
                val map = mapOf(
                    "id" to bill.id,
                    "billNumber" to bill.billNumber,
                    "customerId" to bill.customerId,
                    "customerName" to bill.customerName,
                    "customerCode" to bill.customerCode,
                    "billingMonth" to bill.billingMonth,
                    "amount" to bill.amount,
                    "paidAmount" to bill.paidAmount,
                    "dueAmount" to bill.dueAmount,
                    "status" to bill.status,
                    "generatedDate" to bill.generatedDate,
                    "dueDate" to bill.dueDate,
                    "updatedAt" to bill.updatedAt
                )
                val docRef = userRef.collection("bills").document(bill.id.toString())
                val bid = bill.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                        onSuccess = { database -> database.billDao().markBillsSynced(listOf(bid)) }
                    )
                )
            }

            // 5. Payments
            for (payment in allPayments) {
                val map = mapOf(
                    "id" to payment.id,
                    "paymentReceiptNo" to payment.paymentReceiptNo,
                    "billId" to payment.billId,
                    "customerId" to payment.customerId,
                    "customerName" to payment.customerName,
                    "amount" to payment.amount,
                    "paymentDate" to payment.paymentDate,
                    "paymentMethod" to payment.paymentMethod,
                    "notes" to payment.notes,
                    "updatedAt" to payment.updatedAt
                )
                val docRef = userRef.collection("payments").document(payment.id.toString())
                val payId = payment.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                        onSuccess = { database -> database.paymentDao().markPaymentsSynced(listOf(payId)) }
                    )
                )
            }

            // 6. Expenses
            for (expense in allExpenses) {
                val map = mapOf(
                    "id" to expense.id,
                    "title" to expense.title,
                    "amount" to expense.amount,
                    "category" to expense.category,
                    "date" to expense.date,
                    "paymentMethod" to expense.paymentMethod,
                    "note" to expense.note,
                    "receiptPath" to (expense.receiptPath ?: ""),
                    "createdAt" to expense.createdAt,
                    "updatedAt" to expense.updatedAt
                )
                val docRef = userRef.collection("expenses").document(expense.id.toString())
                val expId = expense.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                        onSuccess = { database -> database.expenseDao().markExpensesSynced(listOf(expId)) }
                    )
                )
            }

            // 7. Categories
            for (cat in allCategories) {
                val map = mapOf(
                    "id" to cat.id,
                    "name" to cat.name,
                    "updatedAt" to cat.updatedAt
                )
                val docRef = userRef.collection("expense_categories").document(cat.id.toString())
                val catId = cat.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                        onSuccess = { database -> database.expenseDao().markCategoriesSynced(listOf(catId)) }
                    )
                )
            }

            // 8. Settings
            if (settings != null) {
                val map = mapOf(
                    "id" to settings.id,
                    "ispName" to settings.ispName,
                    "hotline" to settings.hotline,
                    "address" to settings.address,
                    "currencySymbol" to settings.currencySymbol,
                    "networkStatus" to settings.networkStatus,
                    "themeMode" to settings.themeMode,
                    "logoUri" to (settings.logoUri ?: ""),
                    "email" to (settings.email ?: ""),
                    "updatedAt" to settings.updatedAt
                )
                val docRef = userRef.collection("settings").document("business_settings")
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                        onSuccess = { database -> database.settingsDao().markSettingsSynced() }
                    )
                )
            }

            // 9. Network Diagrams
            for (diag in allDiagrams) {
                val map = mapOf(
                    "id" to diag.id,
                    "name" to diag.name,
                    "isDefault" to diag.isDefault,
                    "createdAt" to diag.createdAt,
                    "updatedAt" to diag.updatedAt
                )
                val docRef = userRef.collection("network_diagrams").document(diag.id.toString())
                val diagId = diag.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, map, SetOptions.merge()) },
                        onSuccess = { database -> database.networkDiagramDao().markDiagramsSynced(listOf(diagId)) }
                    )
                )
            }

            // 10. Network Nodes
            for (node in allNodes) {
                val nodeMap = mapOf(
                    "id" to node.id,
                    "diagramId" to node.diagramId,
                    "name" to node.name,
                    "type" to node.type,
                    "ipAddress" to node.ipAddress,
                    "location" to node.location,
                    "areaZone" to node.areaZone,
                    "portInfo" to node.portInfo,
                    "customerRef" to node.customerRef,
                    "customerId" to node.customerId,
                    "notes" to node.notes,
                    "positionX" to node.positionX,
                    "positionY" to node.positionY,
                    "updatedAt" to node.updatedAt
                )
                val docRef = userRef.collection("network_nodes").document(node.id)
                val nodeId = node.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, nodeMap, SetOptions.merge()) },
                        onSuccess = { database -> database.networkDiagramDao().markNodesSynced(listOf(nodeId)) }
                    )
                )
            }

            // 11. Network Connections
            for (conn in allConnections) {
                val connMap = mapOf(
                    "id" to conn.id,
                    "diagramId" to conn.diagramId,
                    "fromNodeId" to conn.fromNodeId,
                    "toNodeId" to conn.toNodeId,
                    "label" to conn.label,
                    "notes" to conn.notes,
                    "updatedAt" to conn.updatedAt
                )
                val docRef = userRef.collection("network_connections").document(conn.id)
                val connId = conn.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, connMap, SetOptions.merge()) },
                        onSuccess = { database -> database.networkDiagramDao().markConnectionsSynced(listOf(connId)) }
                    )
                )
            }

            // 12. Audit Logs
            for (log in allAuditLogs) {
                val logMap = mapOf(
                    "id" to log.id,
                    "action" to log.action,
                    "actionType" to log.actionType,
                    "details" to log.details,
                    "userEmail" to log.userEmail,
                    "userRole" to log.userRole,
                    "targetEntity" to log.targetEntity,
                    "targetId" to log.targetId,
                    "previousState" to log.previousState,
                    "newState" to log.newState,
                    "status" to log.status,
                    "timestamp" to log.timestamp
                )
                val docRef = userRef.collection("audit_logs").document(log.id.toString())
                val logId = log.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, logMap, SetOptions.merge()) },
                        onSuccess = { database -> database.auditLogDao().markAuditLogsSynced(listOf(logId)) }
                    )
                )
            }

            // 13. Bandwidth Bills
            for (b in allBwBills) {
                val bMap = mapOf(
                    "billingMonth" to b.billingMonth,
                    "amount" to b.amount,
                    "updatedAt" to b.updatedAt
                )
                val docRef = userRef.collection("bandwidth_bills").document(b.billingMonth)
                val bMonth = b.billingMonth
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, bMap, SetOptions.merge()) },
                        onSuccess = { database -> database.bandwidthBillDao().markBandwidthBillsSynced(listOf(bMonth)) }
                    )
                )
            }

            // 14. Specific Advances
            for (sa in allSpecificAdvances) {
                val saMap = mapOf(
                    "id" to sa.id,
                    "customerId" to sa.customerId,
                    "billingMonth" to sa.billingMonth,
                    "amount" to sa.amount,
                    "isConsumed" to sa.isConsumed,
                    "updatedAt" to sa.updatedAt
                )
                val docRef = userRef.collection("specific_advances").document(sa.id.toString())
                val saId = sa.id
                syncOperations.add(
                    SyncOperation(
                        writeOp = { batch -> batch.set(docRef, saMap, SetOptions.merge()) },
                        onSuccess = { database -> database.specificAdvanceDao().markSpecificAdvancesSynced(listOf(saId)) }
                    )
                )
            }

            if (syncOperations.isEmpty()) {
                Log.d(TAG, "Backup to Cloud: No local records found to write.")
                prefs.edit().putBoolean("is_syncing", false).apply()
                return@withContext true
            }

            Log.i(TAG, "Backup to Cloud: Safely uploading all local datasets (${syncOperations.size} operations total) to Firestore path users/$uid/...")
            
            // Execute batches safely in chunks and mark each chunk synced upon success
            val syncSuccess = commitSyncOperationsInChunks(firestore, db, syncOperations)

            if (syncSuccess) {
                userRef.collection("sync_meta").document("status").set(
                    mapOf(
                        "lastSyncTimestamp" to System.currentTimeMillis(),
                        "lastBatchSize" to syncOperations.size
                    ),
                    SetOptions.merge()
                ).await()

                prefs.edit()
                    .putLong("last_cloud_sync_time", System.currentTimeMillis())
                    .putInt("pending_sync_count", 0)
                    .putBoolean("is_syncing", false)
                    .apply()

                Log.i(TAG, "Backup to Cloud: Successfully completed full backup to Firestore!")
                true
            } else {
                prefs.edit().putBoolean("is_syncing", false).apply()
                Log.w(TAG, "Backup to Cloud partially completed: Successful chunks were marked synced; failed chunks remain dirty.")
                false
            }
        } catch (e: FirebaseFirestoreException) {
            context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_syncing", false).apply()
            Log.e(TAG, "Backup to Cloud failed with FirestoreException: ${e.message}", e)
            false
        } catch (e: Exception) {
            context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_syncing", false).apply()
            Log.e(TAG, "Backup to Cloud failed with exception: ${e.message}", e)
            false
        }
    }

    /**
     * Performs Automatic Incremental Delta Pull from Firestore to Room.
     * Fetches only records with updatedAt > lastPullTimestamp, skips locally dirty records (to avoid overwriting unsynced local mutations),
     * and inserts/updates them in Room with syncStatus = 0 (loop prevention).
     */
    suspend fun pullDeltaFromCloud(context: Context): Boolean = withContext(Dispatchers.IO) {
        com.example.IspApplication.ensureFirebaseInitialized(context)
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "Delta Pull skipped: User is guest or unauthenticated.")
            return@withContext false
        }

        val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
        val lastPullKey = "last_delta_pull_time_$uid"
        val lastPullTime = prefs.getLong(lastPullKey, 0L)
        val pullStartTime = System.currentTimeMillis()

        try {
            val firestore = FirebaseFirestore.getInstance()
            val userRef = firestore.collection("users").document(uid)
            val db = IspDatabase.getDatabase(context)

            // Retrieve all deleted records (from local tombstones, pending deletions, and remote tombstones)
            val pendingDeletions = db.pendingDeletionDao().getAllPendingDeletions()
            val pendingDeletedKeys = pendingDeletions.map { "${it.collectionName}:${it.documentId}" }.toSet()
            val deletedRecords = syncAndGetDeletedRecords(context, userRef) + pendingDeletedKeys

            // Step 1: Query only records modified after lastPullTime
            var pulledCount = 0

            // 1. Customers
            val custQuery = if (lastPullTime > 0L) {
                userRef.collection("customers").whereGreaterThan("updatedAt", lastPullTime)
            } else {
                userRef.collection("customers")
            }
            val custDocs = custQuery.get().await()
            val dirtyCustIds = db.customerDao().getDirtyCustomers().map { it.id }.toSet()
            val customersToApply = custDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtyCustIds.contains(id)) return@mapNotNull null // Protect local dirty mutation
                    if (deletedRecords.contains("customers:$id")) return@mapNotNull null // Protect local/pending deletion
                    CustomerEntity(
                        id = id,
                        customerCode = doc.getString("customerCode") ?: "CUST-$id",
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
            pulledCount += customersToApply.size

            // 2. Packages
            val pkgQuery = if (lastPullTime > 0L) {
                userRef.collection("packages").whereGreaterThan("updatedAt", lastPullTime)
            } else {
                userRef.collection("packages")
            }
            val pkgDocs = pkgQuery.get().await()
            val dirtyPkgIds = db.packageDao().getDirtyPackages().map { it.id }.toSet()
            val packagesToApply = pkgDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtyPkgIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("packages:$id")) return@mapNotNull null
                    IspPackageEntity(
                        id = id,
                        name = doc.getString("name") ?: "",
                        speedMbps = doc.safeInt("speedMbps", 0),
                        monthlyPrice = doc.safeDouble("monthlyPrice", 0.0),
                        description = doc.getString("description") ?: "",
                        updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                } catch (e: Exception) { null }
            }
            pulledCount += packagesToApply.size

            // 3. Bills
            val billQuery = if (lastPullTime > 0L) {
                userRef.collection("bills").whereGreaterThan("updatedAt", lastPullTime)
            } else {
                userRef.collection("bills")
            }
            val billDocs = billQuery.get().await()
            val dirtyBillIds = db.billDao().getDirtyBills().map { it.id }.toSet()
            val billsToApply = billDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtyBillIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("bills:$id")) return@mapNotNull null
                    BillEntity(
                        id = id,
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
            pulledCount += billsToApply.size

            // 4. Payments
            val payQuery = if (lastPullTime > 0L) {
                userRef.collection("payments").whereGreaterThan("updatedAt", lastPullTime)
            } else {
                userRef.collection("payments")
            }
            val payDocs = payQuery.get().await()
            val dirtyPayIds = db.paymentDao().getDirtyPayments().map { it.id }.toSet()
            val paymentsToApply = payDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtyPayIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("payments:$id")) return@mapNotNull null
                    PaymentEntity(
                        id = id,
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
            pulledCount += paymentsToApply.size

            // 5. Expenses
            val expQuery = if (lastPullTime > 0L) {
                userRef.collection("expenses").whereGreaterThan("updatedAt", lastPullTime)
            } else {
                userRef.collection("expenses")
            }
            val expDocs = expQuery.get().await()
            val dirtyExpIds = db.expenseDao().getDirtyExpenses().map { it.id }.toSet()
            val expensesToApply = expDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtyExpIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("expenses:$id")) return@mapNotNull null
                    ExpenseEntity(
                        id = id,
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
            pulledCount += expensesToApply.size

            // 6. Expense Categories
            val catQuery = if (lastPullTime > 0L) {
                userRef.collection("expense_categories").whereGreaterThan("updatedAt", lastPullTime)
            } else {
                userRef.collection("expense_categories")
            }
            val catDocs = catQuery.get().await()
            val dirtyCatIds = db.expenseDao().getDirtyCategories().map { it.id }.toSet()
            val categoriesToApply = catDocs.documents.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtyCatIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("expense_categories:$id")) return@mapNotNull null
                    ExpenseCategoryEntity(
                        id = id,
                        name = doc.getString("name") ?: "",
                        updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                } catch (e: Exception) { null }
            }
            pulledCount += categoriesToApply.size

            // 7. Business Settings
            val settingsDoc = try {
                userRef.collection("settings").document("business_settings").get().await()
            } catch (e: Exception) { null }
            val dirtySettings = db.settingsDao().getDirtySettings()
            val settingsToApply = if (settingsDoc != null && settingsDoc.exists() && dirtySettings == null) {
                val remoteUpdatedAt = settingsDoc.safeLong("updatedAt", 0L)
                if (remoteUpdatedAt > lastPullTime) {
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
                        updatedAt = remoteUpdatedAt,
                        syncStatus = 0
                    )
                } else null
            } else null
            if (settingsToApply != null) pulledCount += 1

            // 8. Network Diagrams
            val diagDocs = try {
                val q = if (lastPullTime > 0L) userRef.collection("network_diagrams").whereGreaterThan("updatedAt", lastPullTime)
                        else userRef.collection("network_diagrams")
                q.get().await()
            } catch (e: Exception) { null }
            val dirtyDiagIds = db.networkDiagramDao().getDirtyDiagrams().map { it.id }.toSet()
            val diagramsToApply = diagDocs?.documents?.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtyDiagIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("network_diagrams:$id")) return@mapNotNull null
                    NetworkDiagramEntity(
                        id = id,
                        name = doc.getString("name") ?: "",
                        isDefault = doc.getBoolean("isDefault") ?: false,
                        createdAt = doc.safeLong("createdAt", System.currentTimeMillis()),
                        updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()
            pulledCount += diagramsToApply.size

            // 9. Network Nodes
            val nodeDocs = try {
                val q = if (lastPullTime > 0L) userRef.collection("network_nodes").whereGreaterThan("updatedAt", lastPullTime)
                        else userRef.collection("network_nodes")
                q.get().await()
            } catch (e: Exception) { null }
            val dirtyNodeIds = db.networkDiagramDao().getDirtyNodes().map { it.id }.toSet()
            val nodesToApply = nodeDocs?.documents?.mapNotNull { doc ->
                try {
                    val id = doc.getString("id") ?: doc.id
                    if (dirtyNodeIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("network_nodes:$id")) return@mapNotNull null
                    NetworkNodeEntity(
                        id = id,
                        diagramId = doc.safeLong("diagramId", 0L),
                        name = doc.getString("name") ?: "",
                        type = doc.getString("type") ?: "MIKROTIK",
                        ipAddress = doc.getString("ipAddress") ?: "",
                        location = doc.getString("location") ?: "",
                        areaZone = doc.getString("areaZone") ?: "",
                        portInfo = doc.getString("portInfo") ?: "",
                        customerRef = doc.getString("customerRef") ?: "",
                        customerId = doc.getString("customerId") ?: "",
                        notes = doc.getString("notes") ?: "",
                        positionX = doc.safeDouble("positionX", 0.0).toFloat(),
                        positionY = doc.safeDouble("positionY", 0.0).toFloat(),
                        updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()
            pulledCount += nodesToApply.size

            // 10. Network Connections
            val connDocs = try {
                val q = if (lastPullTime > 0L) userRef.collection("network_connections").whereGreaterThan("updatedAt", lastPullTime)
                        else userRef.collection("network_connections")
                q.get().await()
            } catch (e: Exception) { null }
            val dirtyConnIds = db.networkDiagramDao().getDirtyConnections().map { it.id }.toSet()
            val connectionsToApply = connDocs?.documents?.mapNotNull { doc ->
                try {
                    val id = doc.getString("id") ?: doc.id
                    if (dirtyConnIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("network_connections:$id")) return@mapNotNull null
                    NetworkConnectionEntity(
                        id = id,
                        diagramId = doc.safeLong("diagramId", 0L),
                        fromNodeId = doc.getString("fromNodeId") ?: "",
                        toNodeId = doc.getString("toNodeId") ?: "",
                        label = doc.getString("label") ?: "",
                        notes = doc.getString("notes") ?: "",
                        updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()
            pulledCount += connectionsToApply.size

            // 11. Audit Logs
            val auditDocs = try {
                val q = if (lastPullTime > 0L) userRef.collection("audit_logs").whereGreaterThan("timestamp", lastPullTime)
                        else userRef.collection("audit_logs")
                q.get().await()
            } catch (e: Exception) { null }
            val dirtyLogIds = db.auditLogDao().getDirtyAuditLogs().map { it.id }.toSet()
            val logsToApply = auditDocs?.documents?.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtyLogIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("audit_logs:$id")) return@mapNotNull null
                    AuditLogEntity(
                        id = id,
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
            pulledCount += logsToApply.size

            // 12. Bandwidth Bills
            val bwDocs = try {
                val q = if (lastPullTime > 0L) userRef.collection("bandwidth_bills").whereGreaterThan("updatedAt", lastPullTime)
                        else userRef.collection("bandwidth_bills")
                q.get().await()
            } catch (e: Exception) { null }
            val dirtyBwMonths = db.bandwidthBillDao().getDirtyBandwidthBills().map { it.billingMonth }.toSet()
            val bwToApply = bwDocs?.documents?.mapNotNull { doc ->
                try {
                    val month = doc.getString("billingMonth") ?: doc.id
                    if (dirtyBwMonths.contains(month)) return@mapNotNull null
                    if (deletedRecords.contains("bandwidth_bills:$month")) return@mapNotNull null
                    val amount = doc.safeDouble("amount", 0.0)
                    val updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis())
                    if (month.isNotBlank()) BandwidthBillEntity(billingMonth = month, amount = amount, updatedAt = updatedAt, syncStatus = 0) else null
                } catch (e: Exception) { null }
            } ?: emptyList()
            pulledCount += bwToApply.size

            // 13. Specific Advances
            val saQuery = if (lastPullTime > 0L) {
                userRef.collection("specific_advances").whereGreaterThan("updatedAt", lastPullTime)
            } else {
                userRef.collection("specific_advances")
            }
            val saDocs = try {
                saQuery.get().await()
            } catch (e: Exception) { null }
            val dirtySaIds = db.specificAdvanceDao().getDirtySpecificAdvances().map { it.id }.toSet()
            val saToApply = saDocs?.documents?.mapNotNull { doc ->
                try {
                    val id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L)
                    if (dirtySaIds.contains(id)) return@mapNotNull null
                    if (deletedRecords.contains("specific_advances:$id")) return@mapNotNull null
                    SpecificAdvanceEntity(
                        id = id,
                        customerId = doc.safeLong("customerId", 0L),
                        billingMonth = doc.getString("billingMonth") ?: "",
                        amount = doc.safeDouble("amount", 0.0),
                        isConsumed = doc.getBoolean("isConsumed") ?: false,
                        updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                } catch (e: Exception) { null }
            } ?: emptyList()
            pulledCount += saToApply.size

            // Step 2: Apply pulled delta records transactionally to Room
            if (pulledCount > 0) {
                db.withTransaction {
                    if (customersToApply.isNotEmpty()) db.customerDao().insertCustomers(customersToApply)
                    if (packagesToApply.isNotEmpty()) db.packageDao().insertPackages(packagesToApply)
                    if (billsToApply.isNotEmpty()) db.billDao().insertBills(billsToApply)
                    if (paymentsToApply.isNotEmpty()) db.paymentDao().insertPayments(paymentsToApply)
                    if (expensesToApply.isNotEmpty()) db.expenseDao().insertExpenses(expensesToApply)
                    if (categoriesToApply.isNotEmpty()) db.expenseDao().insertCategories(categoriesToApply)
                    if (settingsToApply != null) db.settingsDao().insertOrUpdateSettings(settingsToApply)
                    if (diagramsToApply.isNotEmpty()) diagramsToApply.forEach { db.networkDiagramDao().insertDiagram(it) }
                    if (nodesToApply.isNotEmpty()) db.networkDiagramDao().insertNodes(nodesToApply)
                    if (connectionsToApply.isNotEmpty()) db.networkDiagramDao().insertConnections(connectionsToApply)
                    if (logsToApply.isNotEmpty()) db.auditLogDao().insertLogs(logsToApply)
                    if (bwToApply.isNotEmpty()) db.bandwidthBillDao().insertOrUpdateBandwidthBills(bwToApply)
                    if (saToApply.isNotEmpty()) db.specificAdvanceDao().insertSpecificAdvances(saToApply)
                }
                Log.i(TAG, "Delta Pull: Successfully applied $pulledCount changed records into Room")
            } else {
                Log.d(TAG, "Delta Pull: No remote changes found since $lastPullTime")
            }

            // Step 3: Advance last successful pull position only upon successful completion
            prefs.edit().putLong(lastPullKey, pullStartTime).apply()
            true
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                Log.w(TAG, "Delta Pull skipped (Permission Denied): Check Firestore security rules or authentication status.")
            } else {
                Log.w(TAG, "Firestore error pulling delta data: ${e.message}")
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error pulling delta data from Firestore: ${e.message}")
            false
        }
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

        try {
            // Stage 1: Safely fetch all cloud collections in memory under a strict timeout
            val (restoredData, hasAnyData) = withTimeout(25000L) {
                val firestore = FirebaseFirestore.getInstance()
                val userRef = firestore.collection("users").document(uid)
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
                    "${it.customerId}_${it.billingMonth.trim().lowercase(java.util.Locale.ROOT)}"
                }.map { (_, group) ->
                    if (group.size == 1) group.first()
                    else {
                        group.maxByOrNull { it.paidAmount > 0 } ?: group.maxByOrNull { it.id } ?: group.first()
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

                // 8. Restore Network Diagrams, Nodes & Connections
                val diagDocs = try {
                    userRef.collection("network_diagrams").get().await()
                } catch (e: Exception) { null }
                val restoredDiagrams = diagDocs?.documents?.mapNotNull { doc ->
                    try {
                        NetworkDiagramEntity(
                            id = doc.safeLong("id", doc.id.toLongOrNull() ?: 0L),
                            name = doc.getString("name") ?: "",
                            isDefault = doc.getBoolean("isDefault") ?: false,
                            createdAt = doc.safeLong("createdAt", System.currentTimeMillis()),
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()

                val nodeDocs = try {
                    userRef.collection("network_nodes").get().await()
                } catch (e: Exception) { null }
                val restoredNodes = nodeDocs?.documents?.mapNotNull { doc ->
                    try {
                        NetworkNodeEntity(
                            id = doc.getString("id") ?: doc.id,
                            diagramId = doc.safeLong("diagramId", 0L),
                            name = doc.getString("name") ?: "",
                            type = doc.getString("type") ?: "MIKROTIK",
                            ipAddress = doc.getString("ipAddress") ?: "",
                            location = doc.getString("location") ?: "",
                            areaZone = doc.getString("areaZone") ?: "",
                            portInfo = doc.getString("portInfo") ?: "",
                            customerRef = doc.getString("customerRef") ?: "",
                            customerId = doc.getString("customerId") ?: "",
                            notes = doc.getString("notes") ?: "",
                            positionX = doc.safeDouble("positionX", 0.0).toFloat(),
                            positionY = doc.safeDouble("positionY", 0.0).toFloat(),
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()

                val connDocs = try {
                    userRef.collection("network_connections").get().await()
                } catch (e: Exception) { null }
                val restoredConnections = connDocs?.documents?.mapNotNull { doc ->
                    try {
                        NetworkConnectionEntity(
                            id = doc.getString("id") ?: doc.id,
                            diagramId = doc.safeLong("diagramId", 0L),
                            fromNodeId = doc.getString("fromNodeId") ?: "",
                            toNodeId = doc.getString("toNodeId") ?: "",
                            label = doc.getString("label") ?: "",
                            notes = doc.getString("notes") ?: "",
                            updatedAt = doc.safeLong("updatedAt", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()

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
                db.networkDiagramDao().deleteAllDiagrams()
                db.networkDiagramDao().deleteAllNodes()
                db.networkDiagramDao().deleteAllConnections()
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
                if (restoredData.diagrams.isNotEmpty()) {
                    restoredData.diagrams.forEach { db.networkDiagramDao().insertDiagram(it) }
                }
                if (restoredData.nodes.isNotEmpty()) {
                    db.networkDiagramDao().insertNodes(restoredData.nodes)
                }
                if (restoredData.connections.isNotEmpty()) {
                    db.networkDiagramDao().insertConnections(restoredData.connections)
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
        Log.d("CloudSyncWorker", "Executing scheduled background cloud sync...")
        val uploadSuccess = FirestoreSyncManager.syncLocalToCloud(context)
        val pullSuccess = FirestoreSyncManager.pullDeltaFromCloud(context)
        return if (uploadSuccess || pullSuccess) {
            Result.success()
        } else {
            Result.failure()
        }
    }
}
