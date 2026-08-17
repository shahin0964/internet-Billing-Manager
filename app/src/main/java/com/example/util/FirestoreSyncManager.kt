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
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"
    private const val WORK_NAME_PERIODIC = "cloud_sync_periodic"
    private const val WORK_NAME_ONE_TIME = "cloud_sync_one_time"

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
     * Performs cloud sync from local Room database to authenticated user's Firestore path.
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

            val customers = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.customerDao().getAllCustomers().first() } ?: emptyList()
            val packages = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.packageDao().getAllPackages().first() } ?: emptyList()
            val bills = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.billDao().getAllBills().first() } ?: emptyList()
            val payments = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.paymentDao().getAllPayments().first() } ?: emptyList()
            val settings = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.settingsDao().getSettings().first() }
            val expenses = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.expenseDao().getAllExpenses().first() } ?: emptyList()
            val categories = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.expenseDao().getAllCategories().first() } ?: emptyList()

            val userRef = firestore.collection("users").document(uid)
            val deletedRecords = syncAndGetDeletedRecords(context, userRef)

            // 1. Sync Customers
            val localCustIds = customers.map { it.id.toString() }.toSet()
            val missingRemoteCusts = mutableListOf<CustomerEntity>()
            try {
                val remoteCusts = userRef.collection("customers").get().await()
                remoteCusts.documents.forEach { doc ->
                    if (deletedRecords.contains("customers:${doc.id}")) {
                        userRef.collection("customers").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted customer ${doc.id} from cloud because it is tombstoned.")
                    } else if (!localCustIds.contains(doc.id)) {
                        try {
                            val custId = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                            missingRemoteCusts.add(
                                CustomerEntity(
                                    id = custId,
                                    customerCode = doc.getString("customerCode") ?: "CUST-$custId",
                                    name = doc.getString("name") ?: "",
                                    phone = doc.getString("phone") ?: "",
                                    address = doc.getString("address") ?: "",
                                    pppoeUsername = doc.getString("pppoeUsername") ?: "",
                                    ipAddress = doc.getString("ipAddress") ?: "",
                                    packageId = doc.getLong("packageId") ?: 0L,
                                    packageName = doc.getString("packageName") ?: "",
                                    monthlyFee = doc.getDouble("monthlyFee") ?: 0.0,
                                    status = doc.getString("status") ?: "ACTIVE",
                                    joiningDate = doc.getString("joiningDate") ?: "",
                                    notes = doc.getString("notes") ?: "",
                                    area = doc.getString("area") ?: "",
                                    zone = doc.getString("zone") ?: "",
                                    latitude = doc.getDouble("latitude") ?: 0.0,
                                    longitude = doc.getDouble("longitude") ?: 0.0,
                                    oltName = doc.getString("oltName") ?: "",
                                    ponPort = doc.getString("ponPort") ?: "",
                                    onuSerial = doc.getString("onuSerial") ?: "",
                                    routerName = doc.getString("routerName") ?: ""
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing remote customer ${doc.id}: ${e.message}")
                        }
                    }
                }
                if (missingRemoteCusts.isNotEmpty()) {
                    db.customerDao().insertCustomers(missingRemoteCusts)
                    Log.d(TAG, "Sync: Ingested ${missingRemoteCusts.size} remote customers into local DB.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error processing remote customers: ${e.message}")
            }
            customers.forEach { customer ->
                if (deletedRecords.contains("customers:${customer.id}")) return@forEach
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
                    "updatedAt" to System.currentTimeMillis()
                )
                userRef.collection("customers").document(customer.id.toString())
                    .set(map, SetOptions.merge()).await()
            }

            // 2. Sync Packages
            val localPkgIds = packages.map { it.id.toString() }.toSet()
            val missingRemotePkgs = mutableListOf<IspPackageEntity>()
            try {
                val remotePkgs = userRef.collection("packages").get().await()
                remotePkgs.documents.forEach { doc ->
                    if (deletedRecords.contains("packages:${doc.id}")) {
                        userRef.collection("packages").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted package ${doc.id} from cloud because it is tombstoned.")
                    } else if (!localPkgIds.contains(doc.id)) {
                        try {
                            val pkgId = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                            missingRemotePkgs.add(
                                IspPackageEntity(
                                    id = pkgId,
                                    name = doc.getString("name") ?: "",
                                    speedMbps = doc.getLong("speedMbps")?.toInt() ?: 0,
                                    monthlyPrice = doc.getDouble("monthlyPrice") ?: 0.0,
                                    description = doc.getString("description") ?: ""
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing remote package ${doc.id}: ${e.message}")
                        }
                    }
                }
                if (missingRemotePkgs.isNotEmpty()) {
                    db.packageDao().insertPackages(missingRemotePkgs)
                    Log.d(TAG, "Sync: Ingested ${missingRemotePkgs.size} remote packages into local DB.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error processing remote packages: ${e.message}")
            }
            packages.forEach { pkg ->
                if (deletedRecords.contains("packages:${pkg.id}")) return@forEach
                val map = mapOf(
                    "id" to pkg.id,
                    "name" to pkg.name,
                    "speedMbps" to pkg.speedMbps,
                    "monthlyPrice" to pkg.monthlyPrice,
                    "description" to pkg.description,
                    "updatedAt" to System.currentTimeMillis()
                )
                userRef.collection("packages").document(pkg.id.toString())
                    .set(map, SetOptions.merge()).await()
            }

            // 3. Sync Bills
            val localBillIds = bills.map { it.id.toString() }.toSet()
            val existingLocalCustomerMonthKeys = bills.map {
                "${it.customerId}_${it.billingMonth.trim().lowercase(java.util.Locale.ROOT)}"
            }.toSet()
            val missingRemoteBills = mutableListOf<BillEntity>()
            val newlyAddedRemoteKeys = mutableSetOf<String>()
            try {
                val remoteBills = userRef.collection("bills").get().await()
                remoteBills.documents.forEach { doc ->
                    if (deletedRecords.contains("bills:${doc.id}")) {
                        userRef.collection("bills").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted bill ${doc.id} from cloud because it is tombstoned.")
                    } else if (!localBillIds.contains(doc.id)) {
                        try {
                            val billId = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                            val custId = doc.getLong("customerId") ?: 0L
                            val bMonth = doc.getString("billingMonth") ?: ""
                            val monthKey = "${custId}_${bMonth.trim().lowercase(java.util.Locale.ROOT)}"

                            if (!existingLocalCustomerMonthKeys.contains(monthKey) && !newlyAddedRemoteKeys.contains(monthKey)) {
                                missingRemoteBills.add(
                                    BillEntity(
                                        id = billId,
                                        billNumber = doc.getString("billNumber") ?: "",
                                        customerId = custId,
                                        customerName = doc.getString("customerName") ?: "",
                                        customerCode = doc.getString("customerCode") ?: "",
                                        billingMonth = bMonth,
                                        amount = doc.getDouble("amount") ?: 0.0,
                                        paidAmount = doc.getDouble("paidAmount") ?: 0.0,
                                        dueAmount = doc.getDouble("dueAmount") ?: 0.0,
                                        status = doc.getString("status") ?: "UNPAID",
                                        generatedDate = doc.getString("generatedDate") ?: "",
                                        dueDate = doc.getString("dueDate") ?: ""
                                    )
                                )
                                newlyAddedRemoteKeys.add(monthKey)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing remote bill ${doc.id}: ${e.message}")
                        }
                    }
                }
                if (missingRemoteBills.isNotEmpty()) {
                    db.billDao().insertBills(missingRemoteBills)
                    Log.d(TAG, "Sync: Ingested ${missingRemoteBills.size} remote bills into local DB.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error processing remote bills: ${e.message}")
            }
            bills.forEach { bill ->
                if (deletedRecords.contains("bills:${bill.id}")) return@forEach
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
                    "updatedAt" to System.currentTimeMillis()
                )
                userRef.collection("bills").document(bill.id.toString())
                    .set(map, SetOptions.merge()).await()
            }

            // 4. Sync Payments
            val localPaymentIds = payments.map { it.id.toString() }.toSet()
            val missingRemotePayments = mutableListOf<PaymentEntity>()
            try {
                val remotePayments = userRef.collection("payments").get().await()
                remotePayments.documents.forEach { doc ->
                    if (deletedRecords.contains("payments:${doc.id}")) {
                        userRef.collection("payments").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted payment ${doc.id} from cloud because it is tombstoned.")
                    } else if (!localPaymentIds.contains(doc.id)) {
                        try {
                            val paymentId = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                            missingRemotePayments.add(
                                PaymentEntity(
                                    id = paymentId,
                                    paymentReceiptNo = doc.getString("paymentReceiptNo") ?: "",
                                    billId = doc.getLong("billId") ?: 0L,
                                    customerId = doc.getLong("customerId") ?: 0L,
                                    customerName = doc.getString("customerName") ?: "",
                                    amount = doc.getDouble("amount") ?: 0.0,
                                    paymentDate = doc.getString("paymentDate") ?: "",
                                    paymentMethod = doc.getString("paymentMethod") ?: "Cash",
                                    notes = doc.getString("notes") ?: ""
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing remote payment ${doc.id}: ${e.message}")
                        }
                    }
                }
                if (missingRemotePayments.isNotEmpty()) {
                    db.paymentDao().insertPayments(missingRemotePayments)
                    Log.d(TAG, "Sync: Ingested ${missingRemotePayments.size} remote payments into local DB.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error processing remote payments: ${e.message}")
            }
            payments.forEach { payment ->
                if (deletedRecords.contains("payments:${payment.id}")) return@forEach
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
                    "updatedAt" to System.currentTimeMillis()
                )
                userRef.collection("payments").document(payment.id.toString())
                    .set(map, SetOptions.merge()).await()
            }

            // 5. Sync Expenses
            val localExpenseIds = expenses.map { it.id.toString() }.toSet()
            val missingRemoteExpenses = mutableListOf<ExpenseEntity>()
            try {
                val remoteExpenses = userRef.collection("expenses").get().await()
                remoteExpenses.documents.forEach { doc ->
                    if (deletedRecords.contains("expenses:${doc.id}")) {
                        userRef.collection("expenses").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted expense ${doc.id} from cloud because it is tombstoned.")
                    } else if (!localExpenseIds.contains(doc.id)) {
                        try {
                            val expId = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                            missingRemoteExpenses.add(
                                ExpenseEntity(
                                    id = expId,
                                    title = doc.getString("title") ?: "",
                                    amount = doc.getDouble("amount") ?: 0.0,
                                    category = doc.getString("category") ?: "",
                                    date = doc.getString("date") ?: "",
                                    paymentMethod = doc.getString("paymentMethod") ?: "Cash",
                                    note = doc.getString("note") ?: "",
                                    receiptPath = doc.getString("receiptPath")?.ifEmpty { null },
                                    createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                                    updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing remote expense ${doc.id}: ${e.message}")
                        }
                    }
                }
                if (missingRemoteExpenses.isNotEmpty()) {
                    db.expenseDao().insertExpenses(missingRemoteExpenses)
                    Log.d(TAG, "Sync: Ingested ${missingRemoteExpenses.size} remote expenses into local DB.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error processing remote expenses: ${e.message}")
            }
            expenses.forEach { expense ->
                if (deletedRecords.contains("expenses:${expense.id}")) return@forEach
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
                userRef.collection("expenses").document(expense.id.toString())
                    .set(map, SetOptions.merge()).await()
            }

            // 6. Sync Categories
            val localCategoryIds = categories.map { it.id.toString() }.toSet()
            val missingRemoteCategories = mutableListOf<ExpenseCategoryEntity>()
            try {
                val remoteCategories = userRef.collection("expense_categories").get().await()
                remoteCategories.documents.forEach { doc ->
                    if (deletedRecords.contains("expense_categories:${doc.id}")) {
                        userRef.collection("expense_categories").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted category ${doc.id} from cloud because it is tombstoned.")
                    } else if (!localCategoryIds.contains(doc.id)) {
                        try {
                            val catId = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                            missingRemoteCategories.add(
                                ExpenseCategoryEntity(
                                    id = catId,
                                    name = doc.getString("name") ?: ""
                                )
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Error parsing remote category ${doc.id}: ${e.message}")
                        }
                    }
                }
                if (missingRemoteCategories.isNotEmpty()) {
                    db.expenseDao().insertCategories(missingRemoteCategories)
                    Log.d(TAG, "Sync: Ingested ${missingRemoteCategories.size} remote categories into local DB.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error processing remote categories: ${e.message}")
            }
            categories.forEach { cat ->
                if (deletedRecords.contains("expense_categories:${cat.id}")) return@forEach
                val map = mapOf(
                    "id" to cat.id,
                    "name" to cat.name,
                    "updatedAt" to System.currentTimeMillis()
                )
                userRef.collection("expense_categories").document(cat.id.toString())
                    .set(map, SetOptions.merge()).await()
            }

            // 7. Sync Settings
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
                    "updatedAt" to System.currentTimeMillis()
                )
                userRef.collection("settings").document("business_settings")
                    .set(map, SetOptions.merge()).await()
            }

            // 8. Sync Network Diagrams, Nodes & Connections
            try {
                val diagrams = db.networkDiagramDao().getAllDiagramsList()
                diagrams.forEach { diag ->
                    val map = mapOf(
                        "id" to diag.id,
                        "name" to diag.name,
                        "isDefault" to diag.isDefault,
                        "createdAt" to diag.createdAt,
                        "updatedAt" to diag.updatedAt
                    )
                    userRef.collection("network_diagrams").document(diag.id.toString())
                        .set(map, SetOptions.merge()).await()

                    val nodes = db.networkDiagramDao().getNodesListForDiagram(diag.id)
                    nodes.forEach { node ->
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
                            "positionY" to node.positionY
                        )
                        userRef.collection("network_nodes").document(node.id)
                            .set(nodeMap, SetOptions.merge()).await()
                    }

                    val connections = db.networkDiagramDao().getConnectionsListForDiagram(diag.id)
                    connections.forEach { conn ->
                        val connMap = mapOf(
                            "id" to conn.id,
                            "diagramId" to conn.diagramId,
                            "fromNodeId" to conn.fromNodeId,
                            "toNodeId" to conn.toNodeId,
                            "label" to conn.label,
                            "notes" to conn.notes
                        )
                        userRef.collection("network_connections").document(conn.id)
                            .set(connMap, SetOptions.merge()).await()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Network diagram sync warning: ${e.message}")
            }

            try {
                val auditLogs = db.auditLogDao().getAllAuditLogs().first()
                for (log in auditLogs) {
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
                    userRef.collection("audit_logs").document(log.id.toString())
                        .set(logMap, SetOptions.merge()).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Audit logs sync warning: ${e.message}")
            }

            // Status record
            userRef.collection("sync_meta").document("status").set(
                mapOf(
                    "lastSyncTimestamp" to System.currentTimeMillis(),
                    "customerCount" to customers.size,
                    "billCount" to bills.size,
                    "paymentCount" to payments.size
                )
            ).await()

            prefs.edit()
                .putLong("last_cloud_sync_time", System.currentTimeMillis())
                .putInt("pending_sync_count", 0)
                .putBoolean("is_syncing", false)
                .apply()

            Log.i(TAG, "Successfully synced all local data to Firestore for UID: $uid")
            true
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
        val auditLogs: List<AuditLogEntity>
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
                    val idStr = doc.getLong("id")?.toString() ?: doc.id
                    if (deletedRecords.contains("customers:$idStr")) return@mapNotNull null
                    try {
                        val custId = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L
                        CustomerEntity(
                            id = custId,
                            customerCode = doc.getString("customerCode") ?: "CUST-$custId",
                            name = doc.getString("name") ?: "",
                            phone = doc.getString("phone") ?: "",
                            address = doc.getString("address") ?: "",
                            pppoeUsername = doc.getString("pppoeUsername") ?: "",
                            ipAddress = doc.getString("ipAddress") ?: "",
                            packageId = doc.getLong("packageId") ?: 0L,
                            packageName = doc.getString("packageName") ?: "",
                            monthlyFee = doc.getDouble("monthlyFee") ?: 0.0,
                            status = doc.getString("status") ?: "ACTIVE",
                            joiningDate = doc.getString("joiningDate") ?: "",
                            notes = doc.getString("notes") ?: "",
                            area = doc.getString("area") ?: "",
                            zone = doc.getString("zone") ?: "",
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            oltName = doc.getString("oltName") ?: "",
                            ponPort = doc.getString("ponPort") ?: "",
                            onuSerial = doc.getString("onuSerial") ?: "",
                            routerName = doc.getString("routerName") ?: ""
                        )
                    } catch (e: Exception) { null }
                }

                // 2. Restore Packages
                val pkgDocs = userRef.collection("packages").get().await()
                val restoredPackages = pkgDocs.documents.mapNotNull { doc ->
                    val idStr = doc.getLong("id")?.toString() ?: doc.id
                    if (deletedRecords.contains("packages:$idStr")) return@mapNotNull null
                    try {
                        IspPackageEntity(
                            id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
                            name = doc.getString("name") ?: "",
                            speedMbps = doc.getLong("speedMbps")?.toInt() ?: 0,
                            monthlyPrice = doc.getDouble("monthlyPrice") ?: 0.0,
                            description = doc.getString("description") ?: ""
                        )
                    } catch (e: Exception) { null }
                }

                // 3. Restore Bills
                val billDocs = userRef.collection("bills").get().await()
                val restoredBillsRaw = billDocs.documents.mapNotNull { doc ->
                    val idStr = doc.getLong("id")?.toString() ?: doc.id
                    if (deletedRecords.contains("bills:$idStr")) return@mapNotNull null
                    try {
                        BillEntity(
                            id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
                            billNumber = doc.getString("billNumber") ?: "",
                            customerId = doc.getLong("customerId") ?: 0L,
                            customerName = doc.getString("customerName") ?: "",
                            customerCode = doc.getString("customerCode") ?: "",
                            billingMonth = doc.getString("billingMonth") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            paidAmount = doc.getDouble("paidAmount") ?: 0.0,
                            dueAmount = doc.getDouble("dueAmount") ?: 0.0,
                            status = doc.getString("status") ?: "UNPAID",
                            generatedDate = doc.getString("generatedDate") ?: "",
                            dueDate = doc.getString("dueDate") ?: ""
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
                    val idStr = doc.getLong("id")?.toString() ?: doc.id
                    if (deletedRecords.contains("payments:$idStr")) return@mapNotNull null
                    try {
                        PaymentEntity(
                            id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
                            paymentReceiptNo = doc.getString("paymentReceiptNo") ?: "",
                            billId = doc.getLong("billId") ?: 0L,
                            customerId = doc.getLong("customerId") ?: 0L,
                            customerName = doc.getString("customerName") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            paymentDate = doc.getString("paymentDate") ?: "",
                            paymentMethod = doc.getString("paymentMethod") ?: "Cash",
                            notes = doc.getString("notes") ?: ""
                        )
                    } catch (e: Exception) { null }
                }

                // 5. Restore Expenses
                val expDocs = userRef.collection("expenses").get().await()
                val restoredExpenses = expDocs.documents.mapNotNull { doc ->
                    val idStr = doc.getLong("id")?.toString() ?: doc.id
                    if (deletedRecords.contains("expenses:$idStr")) return@mapNotNull null
                    try {
                        ExpenseEntity(
                            id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
                            title = doc.getString("title") ?: "",
                            amount = doc.getDouble("amount") ?: 0.0,
                            category = doc.getString("category") ?: "",
                            date = doc.getString("date") ?: "",
                            paymentMethod = doc.getString("paymentMethod") ?: "Cash",
                            note = doc.getString("note") ?: "",
                            receiptPath = doc.getString("receiptPath")?.ifEmpty { null },
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) { null }
                }

                // 6. Restore Categories
                val catDocs = userRef.collection("expense_categories").get().await()
                val restoredCategories = catDocs.documents.mapNotNull { doc ->
                    val idStr = doc.getLong("id")?.toString() ?: doc.id
                    if (deletedRecords.contains("expense_categories:$idStr")) return@mapNotNull null
                    try {
                        ExpenseCategoryEntity(
                            id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
                            name = doc.getString("name") ?: ""
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
                        email = settingsDoc.getString("email") ?: ""
                    )
                } else null

                // 8. Restore Network Diagrams, Nodes & Connections
                val diagDocs = try {
                    userRef.collection("network_diagrams").get().await()
                } catch (e: Exception) { null }
                val restoredDiagrams = diagDocs?.documents?.mapNotNull { doc ->
                    try {
                        NetworkDiagramEntity(
                            id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
                            name = doc.getString("name") ?: "",
                            isDefault = doc.getBoolean("isDefault") ?: false,
                            createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis(),
                            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
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
                            diagramId = doc.getLong("diagramId") ?: 0L,
                            name = doc.getString("name") ?: "",
                            type = doc.getString("type") ?: "MIKROTIK",
                            ipAddress = doc.getString("ipAddress") ?: "",
                            location = doc.getString("location") ?: "",
                            areaZone = doc.getString("areaZone") ?: "",
                            portInfo = doc.getString("portInfo") ?: "",
                            customerRef = doc.getString("customerRef") ?: "",
                            customerId = doc.getString("customerId") ?: "",
                            notes = doc.getString("notes") ?: "",
                            positionX = (doc.getDouble("positionX") ?: 0.0).toFloat(),
                            positionY = (doc.getDouble("positionY") ?: 0.0).toFloat()
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
                            diagramId = doc.getLong("diagramId") ?: 0L,
                            fromNodeId = doc.getString("fromNodeId") ?: "",
                            toNodeId = doc.getString("toNodeId") ?: "",
                            label = doc.getString("label") ?: "",
                            notes = doc.getString("notes") ?: ""
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
                            id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
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
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()

                val hasData = restoredCustomers.isNotEmpty() || restoredPackages.isNotEmpty() ||
                        restoredBills.isNotEmpty() || restoredPayments.isNotEmpty() ||
                        restoredExpenses.isNotEmpty() || restoredCategories.isNotEmpty() ||
                        restoredSettings != null || restoredDiagrams.isNotEmpty() ||
                        restoredNodes.isNotEmpty() || restoredConnections.isNotEmpty() ||
                        restoredLogs.isNotEmpty()

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
                    auditLogs = restoredLogs
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
                db.settingsDao().deleteSettings()
                db.networkDiagramDao().deleteAllDiagrams()
                db.networkDiagramDao().deleteAllNodes()
                db.networkDiagramDao().deleteAllConnections()
                db.auditLogDao().deleteAllLogs()

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
        val success = FirestoreSyncManager.syncLocalToCloud(context)
        return if (success) {
            Result.success()
        } else {
            Result.failure()
        }
    }
}
