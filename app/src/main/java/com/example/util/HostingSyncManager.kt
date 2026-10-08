package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.IspApplication
import com.example.data.database.IspDatabase
import com.example.data.model.*
import com.example.data.remote.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

object HostingSyncManager {

    private const val TAG = "HostingSyncManager"

    private val _isSyncingFlow = MutableStateFlow(false)
    val isSyncingFlow: StateFlow<Boolean> = _isSyncingFlow.asStateFlow()

    private val syncMutex = Mutex()

    fun isSyncInProgress(): Boolean = _isSyncingFlow.value

    private var liveSyncJob: kotlinx.coroutines.Job? = null
    private val liveSyncScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    private var foregroundPollingJob: kotlinx.coroutines.Job? = null
    private val foregroundPollingScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    /**
     * Short periodic polling mechanism:
     * When the user keeps the app open in foreground, periodically checks and pulls remote changes
     * from the server every 30 to 60 seconds (default 35 seconds) without requiring logout/login.
     */
    fun startPeriodicForegroundPolling(context: Context, intervalMillis: Long = 35_000L) {
        if (foregroundPollingJob?.isActive == true) {
            return
        }
        val appCtx = context.applicationContext
        foregroundPollingJob = foregroundPollingScope.launch {
            Log.i(TAG, "Foreground periodic polling started (interval = ${intervalMillis / 1000}s)")
            while (isActive) {
                kotlinx.coroutines.delay(intervalMillis)
                try {
                    val uid = getCurrentUid(appCtx)
                    val isLoggedIn = IspApplication.isLoggedIn(appCtx)
                    if (!uid.isNullOrBlank() && isLoggedIn && isSessionValid(appCtx, uid) && isNetworkAvailable(appCtx)) {
                        Log.d(TAG, "Periodic poll tick: Auto-fetching latest server changes...")
                        syncLocalToHosting(appCtx)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Foreground periodic polling tick note: ${e.message}")
                }
            }
        }
    }

    /**
     * Pauses/stops foreground periodic polling when app is backgrounded to conserve battery/resources.
     */
    fun stopPeriodicForegroundPolling() {
        foregroundPollingJob?.cancel()
        foregroundPollingJob = null
        Log.d(TAG, "Foreground periodic polling stopped")
    }

    /**
     * Triggers a full remote data pull from the hosting server (refreshing customers, packages,
     * bills, payments, and expenses) and merging cleanly into local Room database.
     * Can be invoked on app resume, network available, or periodic poll.
     */
    suspend fun performFullRemoteDataPull(context: Context, forceDeepFallback: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val uid = getCurrentUid(appCtx)
        if (uid.isNullOrBlank() || !IspApplication.isLoggedIn(appCtx) || !isSessionValid(appCtx, uid)) {
            return@withContext false
        }
        if (!isNetworkAvailable(appCtx)) {
            Log.d(TAG, "performFullRemoteDataPull deferred: No network available right now.")
            return@withContext false
        }

        Log.i(TAG, "Starting full remote data pull for user $uid...")
        // 1. Run bidirectional sync (pushes local dirty rows first, pulls remote full dataset)
        val syncResult = syncLocalToHosting(appCtx)

        // 2. If sync failed, or deep fallback requested, or database empty, pull via individual endpoints
        val db = IspDatabase.getDatabase(appCtx, uid)
        val custCount = db.customerDao().getAllCustomersList().size
        val billCount = db.billDao().getAllBillsList().size
        if (!syncResult || forceDeepFallback || (custCount == 0 && billCount == 0)) {
            pullFromIndividualEndpoints(appCtx, uid)
        }
        true
    }

    /**
     * Instantly triggers live sync on mutation.
     * Enqueues an expedited WorkManager task to guarantee background execution
     * and simultaneously triggers an immediate in-process coroutine if network is available.
     */
    fun triggerInstantLiveSync(context: Context) {
        val uid = getCurrentUid(context)
        if (uid.isNullOrBlank() || !IspApplication.isLoggedIn(context) || isSyncInProgress()) {
            return
        }

        // 1. Immediately enqueue guaranteed expedited WorkManager sync task
        SyncWorker.enqueueSync(context, forceExpedited = true)

        // 2. Also trigger immediate in-process coroutine worker if network is active
        if (isNetworkAvailable(context)) {
            liveSyncJob?.cancel()
            liveSyncJob = liveSyncScope.launch {
                try {
                    // Short debounce (200ms) to coalesce rapid in-batch record updates
                    kotlinx.coroutines.delay(200L)
                    if (!isSyncInProgress()) {
                        syncLocalToHosting(context)
                    }
                } catch (e: Throwable) {
                    Log.d(TAG, "Instant live sync coroutine note: ${e.message}")
                }
            }
        }
    }

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
                    packageName = c.packageName,
                    monthlyFee = c.monthlyFee,
                    advanceBalance = c.advanceBalance,
                    notes = c.notes,
                    area = c.area,
                    zone = c.zone,
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
                    } else if (dirtyCustomers.isNotEmpty()) {
                        db.customerDao().markCustomersSynced(dirtyCustomers.map { it.id })
                    }

                    val pkgIds = synced.packages?.mapNotNull { it.toLongOrNull() }
                    if (!pkgIds.isNullOrEmpty()) {
                        db.packageDao().markPackagesSynced(pkgIds)
                    } else if (dirtyPackages.isNotEmpty()) {
                        db.packageDao().markPackagesSynced(dirtyPackages.map { it.id })
                    }

                    if (!synced.bills.isNullOrEmpty()) {
                        db.billDao().markBillsSynced(synced.bills)
                    } else if (dirtyBills.isNotEmpty()) {
                        db.billDao().markBillsSynced(dirtyBills.map { it.id })
                    }

                    if (!synced.payments.isNullOrEmpty()) {
                        db.paymentDao().markPaymentsSynced(synced.payments)
                    } else if (dirtyPayments.isNotEmpty()) {
                        db.paymentDao().markPaymentsSynced(dirtyPayments.map { it.id })
                    }

                    if (!synced.expenses.isNullOrEmpty()) {
                        db.expenseDao().markExpensesSynced(synced.expenses)
                    } else if (dirtyExpenses.isNotEmpty()) {
                        db.expenseDao().markExpensesSynced(dirtyExpenses.map { it.id })
                    }

                    if (!synced.expenseCategories.isNullOrEmpty()) {
                        db.expenseDao().markCategoriesSynced(synced.expenseCategories)
                    } else if (dirtyCategories.isNotEmpty()) {
                        db.expenseDao().markCategoriesSynced(dirtyCategories.map { it.id })
                    }

                    if (synced.settings != null || dirtySettings != null) {
                        db.settingsDao().markSettingsSynced()
                    }

                    if (!synced.auditLogs.isNullOrEmpty()) {
                        db.auditLogDao().markAuditLogsSynced(synced.auditLogs)
                    } else if (dirtyAuditLogs.isNotEmpty()) {
                        db.auditLogDao().markAuditLogsSynced(dirtyAuditLogs.map { it.id })
                    }

                    if (!synced.bandwidthBills.isNullOrEmpty()) {
                        db.bandwidthBillDao().markBandwidthBillsSynced(synced.bandwidthBills)
                    } else if (dirtyBandwidthBills.isNotEmpty()) {
                        db.bandwidthBillDao().markBandwidthBillsSynced(dirtyBandwidthBills.map { it.billingMonth })
                    }

                    if (!synced.specificAdvances.isNullOrEmpty()) {
                        db.specificAdvanceDao().markSpecificAdvancesSynced(synced.specificAdvances)
                    } else if (dirtySpecificAdvances.isNotEmpty()) {
                        db.specificAdvanceDao().markSpecificAdvancesSynced(dirtySpecificAdvances.map { it.id })
                    }

                    if (pendingDeletions.isNotEmpty()) {
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
                if (response.data != null && (response.data.customers != null || response.data.bills != null || response.data.payments != null)) {
                    reconcileFullDataWithRoom(context, db, response.data, uid)
                } else {
                    // Fallback to individual endpoints if api/sync.php did not return data
                    pullFromIndividualEndpoints(context, uid)
                }

                // Immediately before preference writes: verify session
                if (!isSessionValid(context, uid)) {
                    Log.w(TAG, "Preference updates aborted: session invalidated for user $uid")
                    return@withContext false
                }

                val syncTimestamp = if (response.serverTimestamp > 0) response.serverTimestamp else System.currentTimeMillis()
                appPrefs.edit()
                    .putLong("last_cloud_sync_time_$uid", syncTimestamp)
                    .putLong("last_cloud_sync_time", syncTimestamp)
                    .apply()

                // Refresh pending sync count in SharedPreferences so UI displays real remaining unsynced records
                val remainingDirty = getActualPendingDirtyCount(context)
                appPrefs.edit()
                    .putInt("pending_sync_count_$uid", remainingDirty)
                    .putInt("pending_sync_count", remainingDirty)
                    .apply()

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

    suspend fun pullFromIndividualEndpoints(context: Context, operationUserId: String) {
        if (!isSessionValid(context, operationUserId)) return
        val repo = com.example.data.repository.IspRepository.create(context, operationUserId)
        try {
            repo.syncPackagesFromHosting(operationUserId)
            repo.syncCustomersFromHosting(operationUserId)
            repo.syncBillsFromHosting(operationUserId)
            repo.syncPaymentsFromHosting(operationUserId)
            repo.syncExpensesFromHosting(operationUserId)
            repo.syncExpenseCategoriesFromHosting(operationUserId)
            repo.syncSettingsFromHosting(operationUserId)
            repo.syncAuditLogsFromHosting(operationUserId)
            repo.syncBandwidthBillsFromHosting(operationUserId)
            repo.syncSpecificAdvancesFromHosting(operationUserId)
        } catch (e: Throwable) {
            Log.w(TAG, "pullFromIndividualEndpoints note: ${e.message}")
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

        // 1. Packages Reconciliation (run first so customer package names & fees resolve)
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
        val localPackageMap = allLocalPackages.associateBy { it.id }
        if (serverPackages.isNotEmpty()) {
            allLocalPackages.forEach { localPkg ->
                if (localPkg.syncStatus == 0 && !serverPackageMap.containsKey(localPkg.id)) {
                    db.packageDao().deletePackage(localPkg)
                }
            }
        }

        // 2. Customers Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverCustomers = fullData.customers.orEmpty()
        val serverCustomerMap = mutableMapOf<Long, SyncCustomerPayload>()
        val existingCustomerMap = db.customerDao().getAllCustomersList().associateBy { it.id }
        serverCustomers.forEach { c ->
            val cid = c.id.toLongOrNull() ?: 0L
            if (cid > 0) {
                serverCustomerMap[cid] = c
                if (!dirtyCustomerIds.contains(cid) && !pendingCustomerDeletions.contains(c.id)) {
                    val existing = existingCustomerMap[cid]
                    val pkgId = c.packageId?.toLongOrNull() ?: existing?.packageId ?: 0L
                    val matchedPkg = localPackageMap[pkgId] ?: serverPackageMap[pkgId]?.let {
                        val speedInt = it.speed?.replace(Regex("[^0-9]"), "")?.toIntOrNull() ?: 10
                        IspPackageEntity(id = pkgId, name = it.name, speedMbps = speedInt, monthlyPrice = it.price)
                    }
                    val resolvedPkgName = c.packageName?.takeIf { it.isNotBlank() }
                        ?: matchedPkg?.name
                        ?: existing?.packageName
                        ?: ""
                    val resolvedFee = c.monthlyFee?.takeIf { it > 0.0 }
                        ?: matchedPkg?.monthlyPrice
                        ?: existing?.monthlyFee
                        ?: 0.0
                    val resolvedAdvance = c.advanceBalance ?: existing?.advanceBalance ?: 0.0

                    val entity = CustomerEntity(
                        id = cid,
                        customerCode = c.customerCode?.takeIf { it.isNotBlank() } ?: existing?.customerCode ?: "CUST-$cid",
                        name = c.name,
                        phone = c.phone ?: existing?.phone ?: "",
                        address = c.address ?: existing?.address ?: "",
                        pppoeUsername = c.pppoeUsername ?: existing?.pppoeUsername ?: "",
                        ipAddress = c.ipAddress ?: existing?.ipAddress ?: "",
                        packageId = pkgId,
                        packageName = resolvedPkgName,
                        monthlyFee = resolvedFee,
                        status = c.status,
                        joiningDate = c.joiningDate ?: existing?.joiningDate ?: "",
                        notes = c.notes ?: existing?.notes ?: "",
                        area = c.area ?: existing?.area ?: "",
                        zone = c.zone ?: existing?.zone ?: "",
                        advanceBalance = resolvedAdvance,
                        updatedAt = c.updatedAt,
                        syncStatus = 0
                    )
                    db.customerDao().insertCustomer(entity)
                }
            }
        }
        if (serverCustomers.isNotEmpty()) {
            val allLocalCustomers = db.customerDao().getAllCustomersList()
            allLocalCustomers.forEach { localCust ->
                if (localCust.syncStatus == 0 && !serverCustomerMap.containsKey(localCust.id)) {
                    db.customerDao().deleteCustomer(localCust)
                }
            }
        }

        // 3. Bills Reconciliation (Device B sees payments, edits, and status changes made by Device A)
        if (!isSessionValid(context, operationUserId)) return
        val serverBills = fullData.bills.orEmpty()
        val serverBillMap = mutableMapOf<Long, SyncBillPayload>()
        val existingBillMap = db.billDao().getAllBillsList().associateBy { it.id }
        serverBills.forEach { b ->
            serverBillMap[b.id] = b
            if (!dirtyBillIds.contains(b.id) && !pendingBillDeletions.contains(b.id.toString())) {
                val existing = existingBillMap[b.id]
                val resolvedName = b.customerName?.takeIf { it.isNotBlank() }
                    ?: existing?.customerName
                    ?: serverCustomerMap[b.customerId]?.name
                    ?: existingCustomerMap[b.customerId]?.name
                    ?: ""
                val resolvedCode = b.customerCode?.takeIf { it.isNotBlank() }
                    ?: existing?.customerCode
                    ?: serverCustomerMap[b.customerId]?.customerCode
                    ?: existingCustomerMap[b.customerId]?.customerCode
                    ?: ""
                val resolvedMonth = b.month.takeIf { it.isNotBlank() }
                    ?: b.billMonth?.takeIf { it.isNotBlank() }
                    ?: existing?.billingMonth
                    ?: ""
                val resolvedGenDate = b.generatedDate?.takeIf { it.isNotBlank() }
                    ?: existing?.generatedDate
                    ?: ""
                val resolvedDueDate = b.dueDate.takeIf { it.isNotBlank() }
                    ?: existing?.dueDate
                    ?: ""

                val entity = BillEntity(
                    id = b.id,
                    billNumber = b.billNumber?.takeIf { it.isNotBlank() } ?: existing?.billNumber ?: "BILL-${b.id}",
                    customerId = b.customerId,
                    customerName = resolvedName,
                    customerCode = resolvedCode,
                    billingMonth = resolvedMonth,
                    amount = b.amount,
                    paidAmount = b.paidAmount,
                    dueAmount = b.dueAmount,
                    status = b.status.uppercase(java.util.Locale.ROOT),
                    generatedDate = resolvedGenDate,
                    dueDate = resolvedDueDate,
                    updatedAt = if (b.updatedAt > 0) b.updatedAt else (existing?.updatedAt ?: System.currentTimeMillis()),
                    syncStatus = 0
                )
                db.billDao().insertBill(entity)
            }
        }
        if (serverBills.isNotEmpty()) {
            val allLocalBills = db.billDao().getAllBillsList()
            allLocalBills.forEach { localBill ->
                if (localBill.syncStatus == 0 && !serverBillMap.containsKey(localBill.id)) {
                    db.billDao().deleteBill(localBill)
                }
            }
        }

        // 4. Payments Reconciliation (Device B sees all payments recorded by Device A)
        if (!isSessionValid(context, operationUserId)) return
        val serverPayments = fullData.payments.orEmpty()
        val serverPaymentMap = mutableMapOf<Long, SyncPaymentPayload>()
        val existingPaymentMap = db.paymentDao().getAllPaymentsList().associateBy { it.id }
        serverPayments.forEach { pm ->
            serverPaymentMap[pm.id] = pm
            if (!dirtyPaymentIds.contains(pm.id) && !pendingPaymentDeletions.contains(pm.id.toString())) {
                val existing = existingPaymentMap[pm.id]
                val resolvedName = pm.customerName?.takeIf { it.isNotBlank() }
                    ?: existing?.customerName
                    ?: serverCustomerMap[pm.customerId]?.name
                    ?: existingCustomerMap[pm.customerId]?.name
                    ?: ""
                val entity = PaymentEntity(
                    id = pm.id,
                    paymentReceiptNo = pm.paymentReceiptNo.takeIf { it.isNotBlank() } ?: existing?.paymentReceiptNo ?: "REC-${pm.id}",
                    billId = pm.billId,
                    customerId = pm.customerId,
                    customerName = resolvedName,
                    amount = pm.amount,
                    paymentDate = pm.paymentDate.takeIf { it.isNotBlank() } ?: existing?.paymentDate ?: "",
                    paymentMethod = pm.paymentMethod.takeIf { it.isNotBlank() } ?: existing?.paymentMethod ?: "Cash",
                    notes = pm.notes ?: existing?.notes ?: "",
                    updatedAt = if (pm.updatedAt > 0) pm.updatedAt else (existing?.updatedAt ?: System.currentTimeMillis()),
                    syncStatus = 0
                )
                db.paymentDao().insertPayment(entity)
            }
        }
        if (serverPayments.isNotEmpty()) {
            val allLocalPayments = db.paymentDao().getAllPaymentsList()
            allLocalPayments.forEach { localPayment ->
                if (localPayment.syncStatus == 0 && !serverPaymentMap.containsKey(localPayment.id)) {
                    db.paymentDao().deletePayment(localPayment)
                }
            }
        }

        // 5. Expenses Reconciliation
        if (!isSessionValid(context, operationUserId)) return
        val serverExpenses = fullData.expenses.orEmpty()
        val serverExpenseMap = mutableMapOf<Long, SyncExpensePayload>()
        val existingExpenseMap = db.expenseDao().getAllExpensesList().associateBy { it.id }
        serverExpenses.forEach { e ->
            serverExpenseMap[e.id] = e
            if (!dirtyExpenseIds.contains(e.id) && !pendingExpenseDeletions.contains(e.id.toString())) {
                val existing = existingExpenseMap[e.id]
                val entity = ExpenseEntity(
                    id = e.id,
                    title = e.title,
                    amount = e.amount,
                    category = e.category,
                    date = e.date,
                    paymentMethod = e.paymentMethod,
                    note = e.note ?: existing?.note ?: "",
                    receiptPath = e.receiptPath ?: existing?.receiptPath,
                    createdAt = e.createdAt,
                    updatedAt = e.updatedAt,
                    syncStatus = 0
                )
                db.expenseDao().insertExpense(entity)
            }
        }
        if (serverExpenses.isNotEmpty()) {
            val allLocalExpenses = db.expenseDao().getAllExpensesList()
            allLocalExpenses.forEach { localExpense ->
                if (localExpense.syncStatus == 0 && !serverExpenseMap.containsKey(localExpense.id)) {
                    db.expenseDao().deleteExpense(localExpense)
                }
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
            val existing = db.settingsDao().getSettingsSingle()
            val isLocalDefaultOrBlank = existing == null || existing.ispName.isBlank()
            if (!isSettingsDirty || isLocalDefaultOrBlank || (s.updatedAt ?: 0L) >= (existing?.updatedAt ?: 0L)) {
                val entity = BusinessSettingsEntity(
                    id = 1,
                    ispName = s.ispName.ifBlank { existing?.ispName ?: "" },
                    hotline = s.hotline.ifBlank { existing?.hotline ?: "" },
                    address = s.address ?: existing?.address ?: "",
                    currencySymbol = s.currencySymbol.ifBlank { existing?.currencySymbol ?: "৳" },
                    networkStatus = s.networkStatus.ifBlank { existing?.networkStatus ?: "Operational" },
                    themeMode = s.themeMode.ifBlank { existing?.themeMode ?: "SYSTEM" },
                    logoUri = s.logoUri ?: existing?.logoUri,
                    email = s.email.ifBlank { existing?.email ?: "" },
                    updatedAt = s.updatedAt ?: System.currentTimeMillis(),
                    syncStatus = 0
                )
                db.settingsDao().insertOrUpdateSettings(entity)

                try {
                    val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("cached_isp_name_$operationUserId", entity.ispName)
                        .putString("cached_hotline_$operationUserId", entity.hotline)
                        .putString("cached_address_$operationUserId", entity.address)
                        .putString("cached_currency_$operationUserId", entity.currencySymbol)
                        .putString("cached_logo_$operationUserId", entity.logoUri ?: "")
                        .putString("cached_email_$operationUserId", entity.email)
                        .apply()
                } catch (e: Exception) {
                    // Ignore prefs cache errors
                }
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
            val bandwidthBills = db.bandwidthBillDao().getDirtyBandwidthBills().size
            val specificAdvances = db.specificAdvanceDao().getDirtySpecificAdvances().size
            val pendingDeletions = db.pendingDeletionDao().getAllPendingDeletions().size
            
            customers + packages + bills + payments + expenses + expenseCategories + settings + bandwidthBills + specificAdvances + pendingDeletions
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Robust Session & Cloud Restoration:
     * Pulls down all user data, bills, payments, expenses, settings, and history
     * from the server database based on the authenticated user's account ID.
     * Prevents data loss and stale states on re-login and fresh app installs.
     */
    suspend fun restoreOrSyncSession(context: Context, operationUserId: String): Boolean = withContext(Dispatchers.IO) {
        if (operationUserId.isBlank()) return@withContext false
        if (!isSessionValid(context, operationUserId)) {
            Log.w(TAG, "restoreOrSyncSession skipped: session not valid for $operationUserId")
            return@withContext false
        }

        Log.i(TAG, "Starting robust session & cloud restoration for user $operationUserId...")

        val token = IspApplication.getAuthToken(context)
        if (!token.isNullOrBlank()) {
            ApiClient.authToken = token
        }

        if (!isNetworkAvailable(context)) {
            Log.w(TAG, "restoreOrSyncSession: No network available right now. Scheduling expedited sync worker.")
            SyncWorker.enqueueSync(context, forceExpedited = true)
            return@withContext false
        }

        // Step 1: Perform full bidirectional sync to pull down complete dataset and push any local records
        val syncResult = syncLocalToHosting(context)

        // Step 2: Check whether local database has data. If still empty, pull via individual endpoints or latest cloud backup
        val db = IspDatabase.getDatabase(context, operationUserId)
        val custCount = db.customerDao().getAllCustomersList().size
        val billCount = db.billDao().getAllBillsList().size

        if (custCount == 0 && billCount == 0) {
            Log.i(TAG, "Local database empty after sync endpoint. Performing deep restoration from individual endpoints...")
            pullFromIndividualEndpoints(context, operationUserId)

            val custCountAfterEndpoints = db.customerDao().getAllCustomersList().size
            if (custCountAfterEndpoints == 0) {
                Log.i(TAG, "Attempting restoration from latest cloud backup snapshot...")
                try {
                    val repo = com.example.data.repository.IspRepository.create(context, operationUserId)
                    repo.restoreFromHosting(context, operationUserId)
                } catch (e: Throwable) {
                    Log.w(TAG, "Latest cloud backup restore note: ${e.message}")
                }
            }
        }

        // Step 3: Enqueue expedited sync worker to keep background sync healthy
        SyncWorker.enqueueSync(context, forceExpedited = true)

        // Step 4: Update sync preferences
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
        val remainingDirty = getActualPendingDirtyCount(context)
        prefs.edit()
            .putLong("last_cloud_sync_time_$operationUserId", now)
            .putLong("last_cloud_sync_time", now)
            .putInt("pending_sync_count_$operationUserId", remainingDirty)
            .putInt("pending_sync_count", remainingDirty)
            .apply()

        Log.i(TAG, "Session restoration completed for user $operationUserId (syncResult=$syncResult)")
        true
    }
}
