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
     * Performs Simple Full Sync with the user's dedicated hosting database.
     * Uploads local un-synced dirty records and pending deletions,
     * receives the complete user database dataset from the server,
     * and reconciles it with Room while strictly protecting offline local edits.
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

            val categoryPayloads = dirtyCategories.map { cat: ExpenseCategoryEntity ->
                SyncExpenseCategoryPayload(
                    id = cat.id,
                    name = cat.name,
                    color = "#6750A4",
                    createdAt = System.currentTimeMillis(),
                    updatedAt = cat.updatedAt
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
                SyncPendingDeletionPayload(
                    collectionName = del.collectionName,
                    documentId = del.documentId
                )
            }

            val pushRequest = SyncPushRequest(
                userId = uid,
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
                pendingDeletions = deletedPayloads
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
                Log.d(TAG, "Full sync to Hosting succeeded for user $uid")

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
                    if (!synced.pendingDeletions.isNullOrEmpty()) {
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

                // Immediately before reconciliation: verify session
                if (!isSessionValid(context, uid)) {
                    Log.w(TAG, "Reconciliation aborted: session invalidated for user $uid")
                    return@withContext false
                }

                // Step 4: Reconcile complete remote dataset with Room safely
                if (response.data != null) {
                    reconcileFullDataWithRoom(context, db, response.data, uid)
                }

                // Immediately before preference writes: verify session
                if (!isSessionValid(context, uid)) {
                    Log.w(TAG, "Preference updates aborted: session invalidated for user $uid")
                    return@withContext false
                }

                val syncTimestamp = if (response.serverTimestamp > 0) response.serverTimestamp else System.currentTimeMillis()
                appPrefs.edit().putLong("last_cloud_sync_time_$uid", syncTimestamp).apply()

                // Refresh pending sync count in SharedPreferences so UI displays real remaining unsynced records
                val remainingDirty = getActualPendingDirtyCount(context)
                appPrefs.edit().putInt("pending_sync_count_$uid", remainingDirty).apply()

                // Clear any previous sync error
                val syncPrefs = context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
                syncPrefs.edit().remove("last_sync_error_$uid").apply()

                true
            } else {
                val errorMsg = response.message ?: "Unknown sync error from Hosting API"
                Log.e(TAG, "Sync to Hosting failed: $errorMsg")
                val syncPrefs = context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
                syncPrefs.edit().putString("last_sync_error_$uid", errorMsg).apply()
                false
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Network or server connection exception"
            Log.e(TAG, "Sync to Hosting exception: $errorMsg", e)
            val syncPrefs = context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
            syncPrefs.edit().putString("last_sync_error_$uid", errorMsg).apply()
            false
        } finally {
            _isSyncingFlow.value = false
            appPrefs.edit().putBoolean("is_syncing", false).apply()
            syncMutex.unlock()
        }
    }

    private suspend fun reconcileFullDataWithRoom(
        context: Context,
        db: IspDatabase,
        fullData: SyncFullData,
        operationUserId: String
    ) {
        if (!isSessionValid(context, operationUserId)) {
            Log.w(TAG, "reconcileFullDataWithRoom aborted: session invalidated for user $operationUserId")
            return
        }

        // Collect local dirty IDs and pending deletions to protect local offline work
        val dirtyCustomerIds = db.customerDao().getDirtyCustomers().map { it.id }.toSet()
        val dirtyPackageIds = db.packageDao().getDirtyPackages().map { it.id }.toSet()
        val dirtyBillIds = db.billDao().getDirtyBills().map { it.id }.toSet()
        val dirtyPaymentIds = db.paymentDao().getDirtyPayments().map { it.id }.toSet()
        val dirtyExpenseIds = db.expenseDao().getDirtyExpenses().map { it.id }.toSet()
        val dirtyCategoryIds = db.expenseDao().getDirtyCategories().map { it.id }.toSet()
        val dirtySpecificAdvanceIds = db.specificAdvanceDao().getDirtySpecificAdvances().map { it.id }.toSet()
        val dirtyBandwidthMonths = db.bandwidthBillDao().getDirtyBandwidthBills().map { it.billingMonth }.toSet()
        val isSettingsDirty = (db.settingsDao().getDirtySettings() != null)

        val pendingDeletions = db.pendingDeletionDao().getAllPendingDeletions()
        val pendingCustomerDeletions = pendingDeletions.filter { it.collectionName == "customers" }.map { it.documentId }.toSet()
        val pendingPackageDeletions = pendingDeletions.filter { it.collectionName == "packages" }.map { it.documentId }.toSet()
        val pendingBillDeletions = pendingDeletions.filter { it.collectionName == "bills" }.map { it.documentId }.toSet()
        val pendingPaymentDeletions = pendingDeletions.filter { it.collectionName == "payments" }.map { it.documentId }.toSet()
        val pendingExpenseDeletions = pendingDeletions.filter { it.collectionName == "expenses" }.map { it.documentId }.toSet()

        // 1. Customers Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverCustomers = fullData.customers.orEmpty()
        val serverCustomerMap = mutableMapOf<Long, SyncCustomerPayload>()
        serverCustomers.forEach { c ->
            val cid = c.id.toLongOrNull() ?: 0L
            if (cid > 0) {
                serverCustomerMap[cid] = c
                if (!dirtyCustomerIds.contains(cid) && !pendingCustomerDeletions.contains(c.id)) {
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
        }
        val allLocalCustomers = db.customerDao().getAllCustomersList()
        allLocalCustomers.forEach { localCust ->
            if (localCust.syncStatus == 0 && !serverCustomerMap.containsKey(localCust.id)) {
                db.customerDao().deleteCustomer(localCust)
            }
        }

        // 2. Packages Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverPackages = fullData.packages.orEmpty()
        val serverPackageMap = mutableMapOf<Long, SyncPackagePayload>()
        serverPackages.forEach { p ->
            val pid = p.id.toLongOrNull() ?: 0L
            if (pid > 0) {
                serverPackageMap[pid] = p
                if (!dirtyPackageIds.contains(pid) && !pendingPackageDeletions.contains(p.id)) {
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
        }
        val allLocalPackages = db.packageDao().getAllPackagesList()
        allLocalPackages.forEach { localPkg ->
            if (localPkg.syncStatus == 0 && !serverPackageMap.containsKey(localPkg.id)) {
                db.packageDao().deletePackage(localPkg)
            }
        }

        // 3. Bills Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverBills = fullData.bills.orEmpty()
        val serverBillMap = mutableMapOf<Long, SyncBillPayload>()
        serverBills.forEach { b ->
            serverBillMap[b.id] = b
            if (!dirtyBillIds.contains(b.id) && !pendingBillDeletions.contains(b.id.toString())) {
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
        val allLocalBills = db.billDao().getAllBillsList()
        allLocalBills.forEach { localBill ->
            if (localBill.syncStatus == 0 && !serverBillMap.containsKey(localBill.id)) {
                db.billDao().deleteBill(localBill)
            }
        }

        // 4. Payments Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverPayments = fullData.payments.orEmpty()
        val serverPaymentMap = mutableMapOf<Long, SyncPaymentPayload>()
        serverPayments.forEach { pm ->
            serverPaymentMap[pm.id] = pm
            if (!dirtyPaymentIds.contains(pm.id) && !pendingPaymentDeletions.contains(pm.id.toString())) {
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
        val allLocalPayments = db.paymentDao().getAllPaymentsList()
        allLocalPayments.forEach { localPayment ->
            if (localPayment.syncStatus == 0 && !serverPaymentMap.containsKey(localPayment.id)) {
                db.paymentDao().deletePayment(localPayment)
            }
        }

        // 5. Expenses Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverExpenses = fullData.expenses.orEmpty()
        val serverExpenseMap = mutableMapOf<Long, SyncExpensePayload>()
        serverExpenses.forEach { e ->
            serverExpenseMap[e.id] = e
            if (!dirtyExpenseIds.contains(e.id) && !pendingExpenseDeletions.contains(e.id.toString())) {
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
        val allLocalExpenses = db.expenseDao().getAllExpensesList()
        allLocalExpenses.forEach { localExpense ->
            if (localExpense.syncStatus == 0 && !serverExpenseMap.containsKey(localExpense.id)) {
                db.expenseDao().deleteExpense(localExpense)
            }
        }

        // 6. Expense Categories Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverCategories = fullData.expenseCategories.orEmpty()
        serverCategories.forEach { ec ->
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

        // 7. Business Settings Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        fullData.settings?.let { s ->
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

        // 8. Audit Logs
        if (!isSessionValid(context, operationUserId)) return
        fullData.auditLogs?.forEach { al ->
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

        // 9. Bandwidth Bills Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        fullData.bandwidthBills?.forEach { bb ->
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

        // 10. Specific Advances Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverAdvances = fullData.specificAdvances.orEmpty()
        serverAdvances.forEach { sa ->
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
