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
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object FirestoreSyncManager {
    private const val TAG = "FirestoreSyncManager"
    private const val WORK_NAME_PERIODIC = "cloud_sync_periodic"
    private const val WORK_NAME_ONE_TIME = "cloud_sync_one_time"

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

            val customers = db.customerDao().getAllCustomers().first()
            val packages = db.packageDao().getAllPackages().first()
            val bills = db.billDao().getAllBills().first()
            val payments = db.paymentDao().getAllPayments().first()
            val settings = db.settingsDao().getSettings().first()
            val expenses = db.expenseDao().getAllExpenses().first()
            val categories = db.expenseDao().getAllCategories().first()

            val userRef = firestore.collection("users").document(uid)

            // 1. Sync Customers
            val localCustIds = customers.map { it.id.toString() }.toSet()
            try {
                val remoteCusts = userRef.collection("customers").get().await()
                remoteCusts.documents.forEach { doc ->
                    if (!localCustIds.contains(doc.id)) {
                        userRef.collection("customers").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted customer ${doc.id} from cloud because it does not exist locally.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error cleaning deleted customers from cloud: ${e.message}")
            }
            customers.forEach { customer ->
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
                    "updatedAt" to System.currentTimeMillis()
                )
                userRef.collection("customers").document(customer.id.toString())
                    .set(map, SetOptions.merge()).await()
            }

            // 2. Sync Packages
            val localPkgIds = packages.map { it.id.toString() }.toSet()
            try {
                val remotePkgs = userRef.collection("packages").get().await()
                remotePkgs.documents.forEach { doc ->
                    if (!localPkgIds.contains(doc.id)) {
                        userRef.collection("packages").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted package ${doc.id} from cloud because it does not exist locally.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error cleaning deleted packages from cloud: ${e.message}")
            }
            packages.forEach { pkg ->
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
            try {
                val remoteBills = userRef.collection("bills").get().await()
                remoteBills.documents.forEach { doc ->
                    if (!localBillIds.contains(doc.id)) {
                        userRef.collection("bills").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted bill ${doc.id} from cloud because it does not exist locally.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error cleaning deleted bills from cloud: ${e.message}")
            }
            bills.forEach { bill ->
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
            try {
                val remotePayments = userRef.collection("payments").get().await()
                remotePayments.documents.forEach { doc ->
                    if (!localPaymentIds.contains(doc.id)) {
                        userRef.collection("payments").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted payment ${doc.id} from cloud because it does not exist locally.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error cleaning deleted payments from cloud: ${e.message}")
            }
            payments.forEach { payment ->
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
            try {
                val remoteExpenses = userRef.collection("expenses").get().await()
                remoteExpenses.documents.forEach { doc ->
                    if (!localExpenseIds.contains(doc.id)) {
                        userRef.collection("expenses").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted expense ${doc.id} from cloud because it does not exist locally.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error cleaning deleted expenses from cloud: ${e.message}")
            }
            expenses.forEach { expense ->
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
            try {
                val remoteCategories = userRef.collection("expense_categories").get().await()
                remoteCategories.documents.forEach { doc ->
                    if (!localCategoryIds.contains(doc.id)) {
                        userRef.collection("expense_categories").document(doc.id).delete().await()
                        Log.d(TAG, "Sync: Deleted category ${doc.id} from cloud because it does not exist locally.")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync: Error cleaning deleted categories from cloud: ${e.message}")
            }
            categories.forEach { cat ->
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
            val db = IspDatabase.getDatabase(context)
            val firestore = FirebaseFirestore.getInstance()
            val userRef = firestore.collection("users").document(uid)

            // Restore Customers
            val custDocs = userRef.collection("customers").get().await()
            val restoredCustomers = custDocs.documents.mapNotNull { doc ->
                try {
                    CustomerEntity(
                        id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
                        customerCode = doc.getString("customerCode") ?: "",
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
                        notes = doc.getString("notes") ?: ""
                    )
                } catch (e: Exception) { null }
            }

            // Restore Packages
            val pkgDocs = userRef.collection("packages").get().await()
            val restoredPackages = pkgDocs.documents.mapNotNull { doc ->
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

            // Restore Bills
            val billDocs = userRef.collection("bills").get().await()
            val restoredBills = billDocs.documents.mapNotNull { doc ->
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

            // Restore Payments
            val payDocs = userRef.collection("payments").get().await()
            val restoredPayments = payDocs.documents.mapNotNull { doc ->
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

            // Restore Expenses
            val expDocs = userRef.collection("expenses").get().await()
            val restoredExpenses = expDocs.documents.mapNotNull { doc ->
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

            // Restore Categories
            val catDocs = userRef.collection("expense_categories").get().await()
            val restoredCategories = catDocs.documents.mapNotNull { doc ->
                try {
                    ExpenseCategoryEntity(
                        id = doc.getLong("id") ?: doc.id.toLongOrNull() ?: 0L,
                        name = doc.getString("name") ?: ""
                    )
                } catch (e: Exception) { null }
            }

            // Restore Settings
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
                    logoUri = settingsDoc.getString("logoUri")?.ifEmpty { null }
                )
            } else null

            // Restore Audit Logs
            val auditDocs = try {
                userRef.collection("audit_logs").get().await()
            } catch (e: Exception) { null }
            val restoredLogs = auditDocs?.documents?.mapNotNull { doc ->
                try {
                    com.example.data.model.AuditLogEntity(
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

            if (restoredCustomers.isEmpty() && restoredPackages.isEmpty() &&
                restoredBills.isEmpty() && restoredPayments.isEmpty() &&
                restoredExpenses.isEmpty() && restoredCategories.isEmpty() &&
                restoredSettings == null && restoredLogs.isEmpty()) {
                Log.w(TAG, "No cloud backup found for UID: $uid")
                return@withContext Pair(false, "No cloud backup found")
            }

            db.withTransaction {
                db.customerDao().deleteAllCustomers()
                db.packageDao().deleteAllPackages()
                db.billDao().deleteAllBills()
                db.paymentDao().deleteAllPayments()
                db.expenseDao().deleteAllExpenses()
                db.expenseDao().deleteAllCategories()
                db.settingsDao().deleteSettings()
                db.auditLogDao().deleteAllLogs()

                if (restoredCustomers.isNotEmpty()) {
                    db.customerDao().insertCustomers(restoredCustomers)
                }
                if (restoredPackages.isNotEmpty()) {
                    db.packageDao().insertPackages(restoredPackages)
                }
                if (restoredBills.isNotEmpty()) {
                    db.billDao().insertBills(restoredBills)
                }
                if (restoredPayments.isNotEmpty()) {
                    db.paymentDao().insertPayments(restoredPayments)
                }
                if (restoredExpenses.isNotEmpty()) {
                    db.expenseDao().insertExpenses(restoredExpenses)
                }
                if (restoredCategories.isNotEmpty()) {
                    db.expenseDao().insertCategories(restoredCategories)
                }
                if (restoredSettings != null) {
                    db.settingsDao().insertOrUpdateSettings(restoredSettings)
                }
                if (restoredLogs.isNotEmpty()) {
                    db.auditLogDao().insertLogs(restoredLogs)
                }
            }

            Log.i(TAG, "Successfully restored data from Firestore for UID: $uid")
            Pair(true, "Cloud restore successful")
        } catch (e: FirebaseFirestoreException) {
            val msg = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Cloud restore failed: Permission denied"
            } else {
                "Cloud restore failed"
            }
            Log.w(TAG, "Firestore error restoring cloud data: ${e.message}")
            Pair(false, msg)
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
