package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.IspApplication
import com.example.data.database.IspDatabase
import com.example.data.model.*
import com.example.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

object HostingSyncManager {

    private const val TAG = "HostingSyncManager"

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow: StateFlow<Boolean> = _isSyncingFlow.asStateFlow()

    private val syncMutex = Mutex()

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentUid(context: Context): String? {
        val uid = IspApplication.getUserId(context)
        if (!uid.isNullOrBlank()) return uid
        return null
    }

    fun isSessionValid(context: Context, operationUserId: String): Boolean {
        if (operationUserId.isBlank()) return false
        val currentUid = IspApplication.getUserId(context)
        val loggedIn = IspApplication.isLoggedIn(context)
        return loggedIn && currentUid == operationUserId
    }

    /**
     * Performs Background Sync from local Room database to Hosting API & MySQL.
     * Uploads only modified (syncStatus = 1) records and processes pending deletions.
     * Pulls latest remote delta and applies to Room.
     */
    suspend fun syncLocalToHosting(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank()) {
            Log.d(TAG, "Sync skipped: User is guest or unauthenticated.")
            return@withContext false
        }
        if (!isSessionValid(context, uid)) {
            Log.w(TAG, "Sync skipped: Active session does not match initiating user $uid.")
            return@withContext false
        }
        if (!isNetworkAvailable(context)) {
            Log.d(TAG, "Sync skipped: No active network connection.")
            return@withContext false
        }

        if (!syncMutex.tryLock()) {
            Log.d(TAG, "Sync already in progress. Skipping concurrent invocation.")
            return@withContext false
        }

        _isSyncingFlow.value = true
        val appPrefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
        appPrefs.edit().putBoolean("is_syncing", true).apply()

        try {
            val db = IspDatabase.getDatabase(context, uid)
            val prefs = context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time_$uid", 0L)

            // Step 1: Collect dirty entities only
            val dirtyCustomers: List<CustomerEntity> = db.customerDao().getDirtyCustomers()
            val dirtyPackages: List<IspPackageEntity> = db.packageDao().getDirtyPackages()
            val dirtyBills: List<BillEntity> = db.billDao().getDirtyBills()
            val dirtyPayments: List<PaymentEntity> = db.paymentDao().getDirtyPayments()
            val dirtyExpenses: List<ExpenseEntity> = db.expenseDao().getDirtyExpenses()
            val dirtyCategories: List<ExpenseCategoryEntity> = db.expenseDao().getDirtyCategories()
            val dirtySettings: BusinessSettingsEntity? = db.settingsDao().getDirtySettings()
            val dirtyAuditLogs: List<AuditLogEntity> = db.auditLogDao().getDirtyAuditLogs()
            val dirtyBandwidthBills: List<BandwidthBillEntity> = db.bandwidthBillDao().getDirtyBandwidthBills()
            val dirtySpecificAdvances: List<SpecificAdvanceEntity> = db.specificAdvanceDao().getDirtySpecificAdvances()
            val pendingDeletions: List<PendingDeletionEntity> = db.pendingDeletionDao().getAllPendingDeletions()

            val customerPayloads = dirtyCustomers.map { c: CustomerEntity ->
                SyncCustomerPayload(
                    id = c.id.toString(),
                    name = c.name,
                    phone = c.phone,
                    address = c.address,
                    ipAddress = c.ipAddress,
                    packageId = c.packageId.toString(),
                    billingCycleDate = 1,
                    status = c.status,
                    pppoeUsername = c.pppoeUsername,
                    customerCode = c.customerCode,
                    joiningDate = c.joiningDate,
                    updatedAt = c.updatedAt
                )
            }

            val packagePayloads = dirtyPackages.map { p: IspPackageEntity ->
                SyncPackagePayload(
                    id = p.id.toString(),
                    name = p.name,
                    price = p.monthlyPrice,
                    speed = "${p.speedMbps} Mbps",
                    updatedAt = p.updatedAt
                )
            }

            val billPayloads = dirtyBills.map { b: BillEntity ->
                SyncBillPayload(
                    id = b.id,
                    customerId = b.customerId,
                    billNumber = b.billNumber,
                    customerName = b.customerName,
                    customerCode = b.customerCode,
                    month = b.billingMonth,
                    amount = b.amount,
                    paidAmount = b.paidAmount,
                    dueAmount = b.dueAmount,
                    status = b.status,
                    dueDate = b.dueDate,
                    generatedDate = b.generatedDate,
                    updatedAt = b.updatedAt
                )
            }

            val paymentPayloads = dirtyPayments.map { pm: PaymentEntity ->
                SyncPaymentPayload(
                    id = pm.id,
                    paymentReceiptNo = pm.paymentReceiptNo,
                    billId = pm.billId,
                    customerId = pm.customerId,
                    customerName = pm.customerName,
                    amount = pm.amount,
                    paymentDate = pm.paymentDate,
                    paymentMethod = pm.paymentMethod,
                    notes = pm.notes,
                    updatedAt = pm.updatedAt
                )
            }

            val expensePayloads = dirtyExpenses.map { e: ExpenseEntity ->
                SyncExpensePayload(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    category = e.category,
                    date = e.date,
                    paymentMethod = e.paymentMethod,
                    note = e.note,
                    receiptPath = e.receiptPath,
                    createdAt = e.createdAt,
                    updatedAt = e.updatedAt
                )
            }

            val categoryPayloads = dirtyCategories.map { ec: ExpenseCategoryEntity ->
                SyncExpenseCategoryPayload(
                    id = ec.id,
                    name = ec.name,
                    color = "#6750A4",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = ec.updatedAt
                )
            }

            val settingsPayload = dirtySettings?.let { s: BusinessSettingsEntity ->
                SyncSettingsPayload(
                    ispName = s.ispName,
                    hotline = s.hotline,
                    address = s.address,
                    currencySymbol = s.currencySymbol,
                    networkStatus = s.networkStatus,
                    themeMode = s.themeMode,
                    logoUri = s.logoUri,
                    email = s.email,
                    updatedAt = s.updatedAt
                )
            }

            val auditLogPayloads = dirtyAuditLogs.map { al: AuditLogEntity ->
                SyncAuditLogPayload(
                    id = al.id,
                    action = al.action,
                    actionType = al.actionType,
                    details = al.details,
                    userEmail = al.userEmail,
                    userRole = al.userRole,
                    targetEntity = al.targetEntity,
                    targetId = al.targetId,
                    previousState = al.previousState,
                    newState = al.newState,
                    status = al.status,
                    timestamp = al.timestamp
                )
            }

            val bandwidthPayloads = dirtyBandwidthBills.map { bb: BandwidthBillEntity ->
                SyncBandwidthBillPayload(
                    billingMonth = bb.billingMonth,
                    amount = bb.amount,
                    updatedAt = bb.updatedAt
                )
            }

            val advancePayloads = dirtySpecificAdvances.map { sa: SpecificAdvanceEntity ->
                SyncSpecificAdvancePayload(
                    id = sa.id,
                    customerId = sa.customerId,
                    billingMonth = sa.billingMonth,
                    amount = sa.amount,
                    isConsumed = sa.isConsumed,
                    updatedAt = sa.updatedAt
                )
            }

            val deletedPayloads = pendingDeletions.map { del: PendingDeletionEntity ->
                SyncDeletedRecordPayload(
                    collectionName = del.collectionName,
                    recordId = del.documentId,
                    deletedAt = del.timestamp
                )
            }

            val pushRequest = SyncPushRequest(
                userId = uid,
                lastSyncTimestamp = lastSyncTime,
                customers = customerPayloads,
                packages = packagePayloads,
                bills = billPayloads,
                payments = paymentPayloads,
                expenses = expensePayloads,
                expenseCategories = categoryPayloads,
                settings = settingsPayload,
                auditLogs = auditLogPayloads,
                bandwidthBills = bandwidthPayloads,
                specificAdvances = advancePayloads,
                deletedRecords = deletedPayloads
            )

            // Before network transmission: verify session
            if (!isSessionValid(context, uid)) {
                Log.w(TAG, "Sync aborted before push: session invalidated for user $uid")
                return@withContext false
            }

            // Step 2: Push to Hosting API
            val response = ApiClient.apiService.sync(pushRequest)

            // After network response: verify session
            if (!isSessionValid(context, uid)) {
                Log.w(TAG, "Sync response discarded: session invalidated for user $uid")
                return@withContext false
            }

            if (response.status) {
                Log.d(TAG, "Sync to Hosting succeeded for user $uid")

                // Immediately before marking dirty rows clean: verify session
                if (!isSessionValid(context, uid)) {
                    Log.w(TAG, "Dirty-flag clearance aborted: session invalidated for user $uid")
                    return@withContext false
                }

                // Step 3: Clear synced dirty flags based on confirmed synced IDs
                val synced = response.syncedIds
                if (synced != null) {
                    val custIds = synced.customers?.mapNotNull { it.toLongOrNull() }
                    if (!custIds.isNullOrEmpty()) {
                        db.customerDao().markCustomersSynced(custIds)
                    }
                    val pkgIds = synced.packages?.mapNotNull { it.toLongOrNull() }
                    if (!pkgIds.isNullOrEmpty()) {
                        db.packageDao().markPackagesSynced(pkgIds)
                    }
                    if (!synced.bills.isNullOrEmpty()) {
                        db.billDao().markBillsSynced(synced.bills)
                    }
                    if (!synced.payments.isNullOrEmpty()) {
                        db.paymentDao().markPaymentsSynced(synced.payments)
                    }
                    if (!synced.expenses.isNullOrEmpty()) {
                        db.expenseDao().markExpensesSynced(synced.expenses)
                    }
                    if (!synced.expenseCategories.isNullOrEmpty()) {
                        db.expenseDao().markCategoriesSynced(synced.expenseCategories)
                    }
                    if (synced.settings != null) {
                        db.settingsDao().markSettingsSynced()
                    }
                    if (!synced.auditLogs.isNullOrEmpty()) {
                        db.auditLogDao().markAuditLogsSynced(synced.auditLogs)
                    }
                    if (!synced.bandwidthBills.isNullOrEmpty()) {
                        db.bandwidthBillDao().markBandwidthBillsSynced(synced.bandwidthBills)
                    }
                    if (!synced.specificAdvances.isNullOrEmpty()) {
                        db.specificAdvanceDao().markSpecificAdvancesSynced(synced.specificAdvances)
                    }
                    if (!synced.deletedRecords.isNullOrEmpty()) {
                        db.pendingDeletionDao().deletePendingDeletionsByIds(pendingDeletions.map { it.id })
                    }
                } else {
                    // Fallback mark all uploaded as synced
                    if (dirtyCustomers.isNotEmpty()) db.customerDao().markCustomersSynced(dirtyCustomers.map { it.id })
                    if (dirtyPackages.isNotEmpty()) db.packageDao().markPackagesSynced(dirtyPackages.map { it.id })
                    if (dirtyBills.isNotEmpty()) db.billDao().markBillsSynced(dirtyBills.map { it.id })
                    if (dirtyPayments.isNotEmpty()) db.paymentDao().markPaymentsSynced(dirtyPayments.map { it.id })
                    if (dirtyExpenses.isNotEmpty()) db.expenseDao().markExpensesSynced(dirtyExpenses.map { it.id })
                    if (dirtyCategories.isNotEmpty()) db.expenseDao().markCategoriesSynced(dirtyCategories.map { it.id })
                    if (dirtySettings != null) db.settingsDao().markSettingsSynced()
                    if (dirtyAuditLogs.isNotEmpty()) db.auditLogDao().markAuditLogsSynced(dirtyAuditLogs.map { it.id })
                    if (dirtyBandwidthBills.isNotEmpty()) db.bandwidthBillDao().markBandwidthBillsSynced(dirtyBandwidthBills.map { it.billingMonth })
                    if (dirtySpecificAdvances.isNotEmpty()) db.specificAdvanceDao().markSpecificAdvancesSynced(dirtySpecificAdvances.map { it.id })
                    if (pendingDeletions.isNotEmpty()) db.pendingDeletionDao().deletePendingDeletionsByIds(pendingDeletions.map { it.id })
                }

                // Immediately before applyDeltaToRoom: verify session
                if (!isSessionValid(context, uid)) {
                    Log.w(TAG, "Delta application aborted: session invalidated for user $uid")
                    return@withContext false
                }

                // Step 4: Apply remote delta to Room safely
                if (response.data != null) {
                    applyDeltaToRoom(context, db, response.data, uid)
                }

                // Immediately before preference writes: verify session
                if (!isSessionValid(context, uid)) {
                    Log.w(TAG, "Preference updates aborted: session invalidated for user $uid")
                    return@withContext false
                }

                val newServerTime = if (response.serverTimestamp > 0) response.serverTimestamp else System.currentTimeMillis()
                prefs.edit().putLong("last_sync_time_$uid", newServerTime).apply()
                appPrefs.edit().putLong("last_cloud_sync_time_$uid", newServerTime).apply()

                // Refresh pending sync count in SharedPreferences so UI displays real remaining unsynced records
                val remainingDirty = getActualPendingDirtyCount(context)
                appPrefs.edit().putInt("pending_sync_count_$uid", remainingDirty).apply()

                true
            } else {
                Log.w(TAG, "Sync to Hosting failed: ${response.message}")
                if (isSessionValid(context, uid)) {
                    val errMsg = response.message ?: "Unknown server error"
                    context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
                        .edit()
                        .putString("last_sync_error_$uid", errMsg)
                        .apply()
                }
                false
            }
        } catch (e: retrofit2.HttpException) {
            val code = e.code()
            val errorBody = e.response()?.errorBody()?.string() ?: "No error body"
            Log.e(TAG, "HTTP Exception syncing to hosting (status $code): $errorBody", e)
            if (isSessionValid(context, uid)) {
                val errMsg = "HTTP $code: $errorBody"
                context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_sync_error_$uid", errMsg)
                    .apply()
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to hosting: ${e.message}", e)
            if (isSessionValid(context, uid)) {
                val errMsg = e.localizedMessage ?: e.message ?: "Unknown exception"
                context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_sync_error_$uid", errMsg)
                    .apply()
            }
            false
        } finally {
            _isSyncingFlow.value = false
            appPrefs.edit().putBoolean("is_syncing", false).apply()
            syncMutex.unlock()
        }
    }

    /**
     * Pulls remote delta from Hosting API since last sync timestamp and applies non-conflicting changes to Room.
     */
    suspend fun pullDeltaFromHosting(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank() || !isSessionValid(context, uid) || !isNetworkAvailable(context)) {
            return@withContext false
        }

        try {
            val db = IspDatabase.getDatabase(context)
            val prefs = context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
            val lastSyncTime = prefs.getLong("last_sync_time_$uid", 0L)

            // Before network transmission: verify session
            if (!isSessionValid(context, uid)) {
                Log.w(TAG, "Pull delta aborted before request: session invalidated for user $uid")
                return@withContext false
            }

            val response = ApiClient.apiService.getDelta(uid, lastSyncTime)

            // After network response: verify session
            if (!isSessionValid(context, uid)) {
                Log.w(TAG, "Pull delta response discarded: session invalidated for user $uid")
                return@withContext false
            }

            if (response.status && response.data != null) {
                // Immediately before applyDeltaToRoom: verify session
                if (!isSessionValid(context, uid)) {
                    Log.w(TAG, "Pull delta application aborted: session invalidated for user $uid")
                    return@withContext false
                }
                applyDeltaToRoom(context, db, response.data, uid)

                // Immediately before preference write: verify session
                if (!isSessionValid(context, uid)) {
                    Log.w(TAG, "Pull delta preference write aborted: session invalidated for user $uid")
                    return@withContext false
                }

                val newServerTime = if (response.serverTimestamp > 0) response.serverTimestamp else System.currentTimeMillis()
                prefs.edit().putLong("last_sync_time_$uid", newServerTime).apply()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling delta from hosting: ${e.message}")
            false
        }
    }

    private suspend fun applyDeltaToRoom(context: Context, db: IspDatabase, delta: SyncDeltaData, operationUserId: String) {
        if (!isSessionValid(context, operationUserId)) {
            Log.w(TAG, "applyDeltaToRoom aborted: session invalidated for user $operationUserId")
            return
        }
        // Collect local dirty IDs to protect unsaved local changes from remote overwrites
        val dirtyCustomerIds: Set<Long> = db.customerDao().getDirtyCustomers().map { it.id }.toSet()
        val dirtyPackageIds: Set<Long> = db.packageDao().getDirtyPackages().map { it.id }.toSet()
        val dirtyBillIds: Set<Long> = db.billDao().getDirtyBills().map { it.id }.toSet()
        val dirtyPaymentIds: Set<Long> = db.paymentDao().getDirtyPayments().map { it.id }.toSet()
        val dirtyExpenseIds: Set<Long> = db.expenseDao().getDirtyExpenses().map { it.id }.toSet()
        val dirtyCategoryIds: Set<Long> = db.expenseDao().getDirtyCategories().map { it.id }.toSet()
        val dirtySpecificAdvanceIds: Set<Long> = db.specificAdvanceDao().getDirtySpecificAdvances().map { it.id }.toSet()
        val dirtyBandwidthMonths: Set<String> = db.bandwidthBillDao().getDirtyBandwidthBills().map { it.billingMonth }.toSet()
        val isSettingsDirty: Boolean = (db.settingsDao().getDirtySettings() != null)

        // 1. Process Deleted Records tombstones
        if (!isSessionValid(context, operationUserId)) return
        delta.deletedRecords?.forEach { del ->
            when (del.collectionName) {
                "payments" -> del.recordId.toLongOrNull()?.let { pmid ->
                    if (!dirtyPaymentIds.contains(pmid)) {
                        db.paymentDao().deletePaymentById(pmid)
                    }
                }
            }
        }

        // 2. Customers
        if (!isSessionValid(context, operationUserId)) return
        delta.customers?.forEach { c ->
            val cid = c.id.toLongOrNull() ?: 0L
            if (cid > 0 && !dirtyCustomerIds.contains(cid)) {
                val entity = CustomerEntity(
                    id = cid,
                    customerCode = c.customerCode ?: "CUST-$cid",
                    name = c.name,
                    phone = c.phone ?: "",
                    address = c.address ?: "",
                    pppoeUsername = c.pppoeUsername ?: "",
                    ipAddress = c.ipAddress ?: "",
                    packageId = c.packageId?.toLongOrNull() ?: 0L,
                    packageName = "",
                    monthlyFee = 0.0,
                    status = c.status,
                    joiningDate = c.joiningDate ?: "",
                    updatedAt = c.updatedAt,
                    syncStatus = 0
                )
                db.customerDao().insertCustomer(entity)
            }
        }

        // 3. Packages
        if (!isSessionValid(context, operationUserId)) return
        delta.packages?.forEach { p ->
            val pid = p.id.toLongOrNull() ?: 0L
            if (pid > 0 && !dirtyPackageIds.contains(pid)) {
                val speedInt = p.speed?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 10
                val entity = IspPackageEntity(
                    id = pid,
                    name = p.name,
                    speedMbps = speedInt,
                    monthlyPrice = p.price,
                    updatedAt = p.updatedAt,
                    syncStatus = 0
                )
                db.packageDao().insertPackage(entity)
            }
        }

        // 4. Bills
        if (!isSessionValid(context, operationUserId)) return
        delta.bills?.forEach { b ->
            if (!dirtyBillIds.contains(b.id)) {
                val entity = BillEntity(
                    id = b.id,
                    billNumber = b.billNumber ?: "BILL-${b.id}",
                    customerId = b.customerId,
                    customerName = b.customerName ?: "",
                    customerCode = b.customerCode ?: "",
                    billingMonth = b.month,
                    amount = b.amount,
                    paidAmount = b.paidAmount,
                    dueAmount = b.dueAmount,
                    status = b.status,
                    generatedDate = b.generatedDate ?: "",
                    dueDate = b.dueDate,
                    updatedAt = b.updatedAt,
                    syncStatus = 0
                )
                db.billDao().insertBill(entity)
            }
        }

        // 5. Payments
        if (!isSessionValid(context, operationUserId)) return
        delta.payments?.forEach { pm ->
            if (!dirtyPaymentIds.contains(pm.id)) {
                val entity = PaymentEntity(
                    id = pm.id,
                    paymentReceiptNo = pm.paymentReceiptNo,
                    billId = pm.billId,
                    customerId = pm.customerId,
                    customerName = pm.customerName ?: "",
                    amount = pm.amount,
                    paymentDate = pm.paymentDate,
                    paymentMethod = pm.paymentMethod,
                    notes = pm.notes ?: "",
                    updatedAt = pm.updatedAt,
                    syncStatus = 0
                )
                db.paymentDao().insertPayment(entity)
            }
        }

        // 6. Expenses
        if (!isSessionValid(context, operationUserId)) return
        delta.expenses?.forEach { e ->
            if (!dirtyExpenseIds.contains(e.id)) {
                val entity = ExpenseEntity(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    category = e.category,
                    date = e.date,
                    paymentMethod = e.paymentMethod,
                    note = e.note ?: "",
                    receiptPath = e.receiptPath,
                    createdAt = e.createdAt,
                    updatedAt = e.updatedAt,
                    syncStatus = 0
                )
                db.expenseDao().insertExpense(entity)
            }
        }

        // 7. Expense Categories
        if (!isSessionValid(context, operationUserId)) return
        delta.expenseCategories?.forEach { ec ->
            if (!dirtyCategoryIds.contains(ec.id)) {
                val entity = ExpenseCategoryEntity(
                    id = ec.id,
                    name = ec.name,
                    updatedAt = ec.updatedAt,
                    syncStatus = 0
                )
                db.expenseDao().insertCategory(entity)
            }
        }

        // 8. Business Settings
        if (!isSessionValid(context, operationUserId)) return
        delta.settings?.let { s ->
            if (!isSettingsDirty) {
                val entity = BusinessSettingsEntity(
                    id = 1,
                    ispName = s.ispName,
                    hotline = s.hotline,
                    address = s.address ?: "",
                    currencySymbol = s.currencySymbol,
                    networkStatus = s.networkStatus,
                    themeMode = s.themeMode,
                    logoUri = s.logoUri,
                    email = s.email,
                    updatedAt = s.updatedAt,
                    syncStatus = 0
                )
                db.settingsDao().insertOrUpdateSettings(entity)
            }
        }

        // 9. Audit Logs
        if (!isSessionValid(context, operationUserId)) return
        delta.auditLogs?.forEach { al ->
            val entity = AuditLogEntity(
                id = al.id,
                action = al.action,
                actionType = al.actionType,
                details = al.details ?: "",
                userEmail = al.userEmail,
                userRole = al.userRole,
                targetEntity = al.targetEntity,
                targetId = al.targetId,
                previousState = al.previousState ?: "",
                newState = al.newState ?: "",
                status = al.status,
                timestamp = al.timestamp,
                syncStatus = 0
            )
            db.auditLogDao().insertLog(entity)
        }

        // 10. Bandwidth Bills
        if (!isSessionValid(context, operationUserId)) return
        delta.bandwidthBills?.forEach { bb ->
            if (!dirtyBandwidthMonths.contains(bb.billingMonth)) {
                val entity = BandwidthBillEntity(
                    billingMonth = bb.billingMonth,
                    amount = bb.amount,
                    updatedAt = bb.updatedAt,
                    syncStatus = 0
                )
                db.bandwidthBillDao().insertOrUpdateBandwidthBill(entity)
            }
        }

        // 11. Specific Advances
        if (!isSessionValid(context, operationUserId)) return
        delta.specificAdvances?.forEach { sa ->
            if (!dirtySpecificAdvanceIds.contains(sa.id)) {
                val entity = SpecificAdvanceEntity(
                    id = sa.id,
                    customerId = sa.customerId,
                    billingMonth = sa.billingMonth,
                    amount = sa.amount,
                    isConsumed = sa.isConsumed,
                    updatedAt = sa.updatedAt,
                    syncStatus = 0
                )
                db.specificAdvanceDao().insertSpecificAdvance(entity)
            }
        }
    }

    fun observePendingDirtyCount(context: Context, userId: String? = null): Flow<Int> {
        val actualUid = if (userId.isNullOrBlank() || userId == "guest" || userId == "authenticated_user") {
            IspApplication.getUserId(context)?.takeIf { it.isNotBlank() && it != "guest" && it != "authenticated_user" }
        } else {
            userId
        }
        if (actualUid.isNullOrBlank() || !IspApplication.isLoggedIn(context)) {
            return flowOf(0)
        }
        val db = IspDatabase.getDatabase(context, actualUid)
        return combine(
            listOf(
                db.customerDao().getDirtyCustomersCount(),
                db.packageDao().getDirtyPackagesCount(),
                db.billDao().getDirtyBillsCount(),
                db.paymentDao().getDirtyPaymentsCount(),
                db.expenseDao().getDirtyExpensesCount(),
                db.expenseDao().getDirtyCategoriesCount(),
                db.settingsDao().getDirtySettingsCount(),
                db.auditLogDao().getDirtyAuditLogsCount(),
                db.bandwidthBillDao().getDirtyBandwidthBillsCount(),
                db.specificAdvanceDao().getDirtySpecificAdvancesCount(),
                db.pendingDeletionDao().getPendingDeletionsCount()
            )
        ) { counts ->
            counts.sum()
        }
    }

    suspend fun getActualPendingDirtyCount(context: Context): Int = withContext(Dispatchers.IO) {
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank()) return@withContext 0
        return@withContext try {
            val db = IspDatabase.getDatabase(context, uid)
            val customers = db.customerDao().getDirtyCustomers().size
            val packages = db.packageDao().getDirtyPackages().size
            val bills = db.billDao().getDirtyBills().size
            val payments = db.paymentDao().getDirtyPayments().size
            val expenses = db.expenseDao().getDirtyExpenses().size
            val expenseCategories = db.expenseDao().getDirtyCategories().size
            val settings = if (db.settingsDao().getDirtySettings() != null) 1 else 0
            val auditLogs = db.auditLogDao().getDirtyAuditLogs().size
            val bandwidthBills = db.bandwidthBillDao().getDirtyBandwidthBills().size
            val specificAdvances = db.specificAdvanceDao().getDirtySpecificAdvances().size
            val pendingDeletions = db.pendingDeletionDao().getAllPendingDeletions().size
            
            customers + packages + bills + payments + expenses + expenseCategories + settings + auditLogs + bandwidthBills + specificAdvances + pendingDeletions
        } catch (e: Exception) {
            0
        }
    }
}
