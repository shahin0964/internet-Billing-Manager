package com.example.ui.viewmodel

import com.example.ui.components.formatAmount
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.IspDatabase
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.IspPackageEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.PreviousDueItem
import com.example.data.model.BandwidthBillEntity
import com.example.data.repository.IspRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IspViewModel(application: Application) : AndroidViewModel(application) {

    private val _activeRepository = MutableStateFlow(
        IspRepository.create(application, com.example.IspApplication.getUserId(application))
    )
    val repository: IspRepository get() = _activeRepository.value

    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerEntity>> = _activeRepository
        .flatMapLatest { it.customers }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val packages: StateFlow<List<IspPackageEntity>> = _activeRepository
        .flatMapLatest { it.packages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val bills: StateFlow<List<BillEntity>> = _activeRepository
        .flatMapLatest { it.bills }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val payments: StateFlow<List<PaymentEntity>> = _activeRepository
        .flatMapLatest { it.payments }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: StateFlow<BusinessSettingsEntity> = _activeRepository
        .flatMapLatest { repo ->
            repo.settings.map { s ->
                val cur = s ?: BusinessSettingsEntity(
                    id = 1,
                    ispName = "",
                    hotline = "",
                    address = "",
                    currencySymbol = "৳",
                    networkStatus = "Operational",
                    themeMode = "SYSTEM"
                )
                val cleanIspName = if (cur.ispName in listOf("Global Fiber ISP", "FastNet Broadband", "Broadband ISP")) "" else cur.ispName
                val cleanHotline = if (cur.hotline == "+1 (800) 555-0199") "" else cur.hotline
                val cleanAddress = if (cur.address in listOf("Central NOC, Tech City", "Main NOC, Plaza Suite 10")) "" else cur.address
                if (cleanIspName != cur.ispName || cleanHotline != cur.hotline || cleanAddress != cur.address) {
                    cur.copy(ispName = cleanIspName, hotline = cleanHotline, address = cleanAddress)
                } else {
                    cur
                }
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            BusinessSettingsEntity(ispName = "", hotline = "", address = "")
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<ExpenseEntity>> = _activeRepository
        .flatMapLatest { it.expenses }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val expenseCategories: StateFlow<List<ExpenseCategoryEntity>> = _activeRepository
        .flatMapLatest { it.expenseCategories }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val bandwidthBills: StateFlow<List<BandwidthBillEntity>> = _activeRepository
        .flatMapLatest { it.bandwidthBills }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val auditLogs: StateFlow<List<com.example.data.model.AuditLogEntity>> = _activeRepository
        .flatMapLatest { it.auditLogs }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val todayCollectionAmount: StateFlow<Double> = _activeRepository
        .flatMapLatest { repo ->
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayStr = sdf.format(java.util.Date())
            repo.getCollectedAmountForDate(todayStr)
        }.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0
        )

    // UI state filters & queries
    val customerSearchQuery = MutableStateFlow("")
    val customerStatusFilter = MutableStateFlow("ALL") // ALL, ACTIVE, INACTIVE, SUSPENDED

    val billSearchQuery = MutableStateFlow("")

    val collectionSearchQuery = MutableStateFlow("")
    val dueSortOption = MutableStateFlow("DUE_DESC") // DUE_DESC, DUE_ASC, NAME

    val selectedCustomerForDetail = MutableStateFlow<CustomerEntity?>(null)

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _isAppInitializing = MutableStateFlow(true)
    val isAppInitializing: StateFlow<Boolean> = _isAppInitializing.asStateFlow()

    private val activeSyncJobs = java.util.Collections.synchronizedSet(java.util.HashSet<kotlinx.coroutines.Job>())

    private fun trackSyncJob(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit): kotlinx.coroutines.Job {
        val job = viewModelScope.launch {
            block()
        }
        activeSyncJobs.add(job)
        job.invokeOnCompletion {
            activeSyncJobs.remove(job)
        }
        return job
    }

    fun cancelAllSyncOperations() {
        synchronized(activeSyncJobs) {
            val iterator = activeSyncJobs.iterator()
            while (iterator.hasNext()) {
                val job = iterator.next()
                if (job.isActive) {
                    job.cancel()
                }
                iterator.remove()
            }
        }
    }

    fun switchUserSession(newUserId: String?) {
        cancelAllSyncOperations()
        selectedCustomerForDetail.value = null
        val newRepo = IspRepository.create(getApplication(), newUserId)
        _activeRepository.value = newRepo
        seedDefaultPackagesAndSettingsIfNeeded()
        autoGenerateCurrentMonthBills()
        if (!newUserId.isNullOrBlank() && newUserId != "guest" && newUserId != "authenticated_user") {
            triggerCloudSyncOnLogin()
        }
    }

    init {
        seedDefaultPackagesAndSettingsIfNeeded()
        autoGenerateCurrentMonthBills()

        if (com.example.IspApplication.isLoggedIn(application)) {
            trackSyncJob {
                try {
                    repository.syncCustomersFromHosting()
                    repository.syncBillsFromHosting()
                    repository.syncPaymentsFromHosting()
                    repository.syncExpensesFromHosting()
                    repository.syncExpenseCategoriesFromHosting()
                    repository.syncSettingsFromHosting()
                    repository.syncAuditLogsFromHosting()
                    repository.syncBandwidthBillsFromHosting()
                    repository.syncSpecificAdvancesFromHosting()
                } catch (e: Throwable) {
                    android.util.Log.w("IspViewModel", "Hosting customer/bill/payment/expense/settings/audit_logs/bandwidth_bills/specific_advances sync on init note: ${e.message}")
                }
            }
        }

        seedDefaultPackagesAndSettingsIfNeeded()
        autoGenerateCurrentMonthBills()

        viewModelScope.launch {
            try {
                repository.customers.first()
                repository.settings.first()
            } catch (e: Throwable) {
                // Ignore initialization read errors
            } finally {
                _isAppInitializing.value = false
            }
        }
    }

    fun saveOrUpdateBandwidthBill(billingMonth: String, amount: Double) {
        viewModelScope.launch {
            repository.saveOrUpdateBandwidthBill(billingMonth, amount)
        }
    }


    val billingScreenBills: StateFlow<List<BillEntity>> = bills.map { rawBills ->
        val unpaidBills = rawBills.filter { it.status == "UNPAID" || it.status == "PARTIAL" }
        val billsByCustomer = unpaidBills.groupBy { it.customerId }
        val displayBills = mutableListOf<BillEntity>()

        for ((custId, cBills) in billsByCustomer) {
            val sorted = cBills.sortedByDescending { it.id }
            val currentBill = sorted.first()
            val previousBills = sorted.drop(1)
            val previousDue = previousBills.sumOf { it.dueAmount }

            if (previousDue > 0) {
                val totalDue = currentBill.dueAmount + previousDue
                
                // Formulate BREAKDOWN structure: BREAKDOWN|previous_dues|current_bill|original_bill_number
                val prevList = previousBills.sortedBy { it.id }.map { "${it.billingMonth}:${it.dueAmount}" }.joinToString(",")
                val breakdownString = "BREAKDOWN|$prevList|${currentBill.billingMonth}:${currentBill.dueAmount}|${currentBill.billNumber}"

                val virtualBill = currentBill.copy(
                    billNumber = breakdownString,
                    amount = totalDue,
                    dueAmount = totalDue,
                    paidAmount = 0.0 // Representing remaining aggregate
                )
                displayBills.add(virtualBill)
            } else {
                displayBills.add(currentBill)
            }
        }
        
        // Also add paid bills just in case they are needed? BillingScreen filters by UNPAID/PARTIAL
        val paidBills = rawBills.filter { it.status == "PAID" }
        displayBills.addAll(paidBills)
        
        displayBills.sortedWith { b1, b2 ->
            com.example.util.CustomerSortUtils.compareCustomerNames(b1.customerName, b2.customerName)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun autoGenerateCurrentMonthBills() {
        viewModelScope.launch {
            val currentMonth = com.example.util.BillingMonthUtils.formatStandardMonth()
            val dueDate = com.example.util.BillingMonthUtils.formatStandardDueDate()
            repository.generateMonthlyBills(currentMonth, dueDate, isAutoGeneration = true)
        }
    }

    private fun seedDefaultPackagesAndSettingsIfNeeded() {
        viewModelScope.launch {
            // Do not seed default or dummy packages. Packages should only be created when the user adds them.
            // Clean up any previously auto-seeded default dummy packages if they exist and are not used by any customer.
            val dummyPackageNames = setOf(
                "10 Mbps Starter Fiber",
                "25 Mbps Standard Fiber",
                "50 Mbps Ultra Fiber",
                "100 Mbps Enterprise"
            )
            val currentPkgs = repository.packages.first()
            val customers = repository.customers.first()
            val usedPackageNames = customers.map { it.packageName }.toSet()
            val usedPackageIds = customers.map { it.packageId }.toSet()

            currentPkgs.forEach { pkg ->
                if (pkg.name in dummyPackageNames && pkg.id !in usedPackageIds && pkg.name !in usedPackageNames) {
                    repository.deletePackage(pkg)
                }
            }

            val currentSettings = repository.settings.first()
            if (currentSettings == null) {
                // If cloud settings exist for this user in SharedPreferences, restore them first
                val app = getApplication<Application>()
                val uid = com.example.IspApplication.getUserId(app)
                val userIspName = if (uid != null) {
                    val prefs = app.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
                    prefs.getString("cached_isp_name_$uid", "") ?: ""
                } else ""
                val userHotline = if (uid != null) {
                    val prefs = app.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
                    prefs.getString("cached_hotline_$uid", "") ?: ""
                } else ""
                val userAddress = if (uid != null) {
                    val prefs = app.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
                    prefs.getString("cached_address_$uid", "") ?: ""
                } else ""

                repository.saveSettings(
                    BusinessSettingsEntity(
                        id = 1,
                        ispName = userIspName,
                        hotline = userHotline,
                        address = userAddress,
                        currencySymbol = "৳",
                        networkStatus = "Operational",
                        themeMode = "SYSTEM"
                    )
                )
            } else {
                val cleanIspName = if (currentSettings.ispName in listOf("Global Fiber ISP", "FastNet Broadband", "Broadband ISP")) "" else currentSettings.ispName
                val cleanHotline = if (currentSettings.hotline == "+1 (800) 555-0199") "" else currentSettings.hotline
                val cleanAddress = if (currentSettings.address in listOf("Central NOC, Tech City", "Main NOC, Plaza Suite 10")) "" else currentSettings.address
                val cleanSymbol = if (currentSettings.currencySymbol == "$") "৳" else currentSettings.currencySymbol
                if (cleanIspName != currentSettings.ispName || cleanHotline != currentSettings.hotline || cleanAddress != currentSettings.address || cleanSymbol != currentSettings.currencySymbol) {
                    repository.saveSettings(
                        currentSettings.copy(
                            ispName = cleanIspName,
                            hotline = cleanHotline,
                            address = cleanAddress,
                            currencySymbol = cleanSymbol
                        )
                    )
                }
            }
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    suspend fun createPreUpdateBackup(context: Context): Result<java.io.File> {
        return repository.createAutomaticPreUpdateBackup(context)
    }

    fun triggerCloudSyncOnLogin() {
        trackSyncJob {
            try {
                repository.syncCustomersFromHosting()
                repository.syncBillsFromHosting()
                repository.syncPaymentsFromHosting()
                repository.syncExpensesFromHosting()
                repository.syncExpenseCategoriesFromHosting()
                repository.syncSettingsFromHosting()
                repository.syncAuditLogsFromHosting()
                repository.syncBandwidthBillsFromHosting()
                repository.syncSpecificAdvancesFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Hosting sync on login note: ${e.message}")
            }
        }
    }

    fun syncCustomersFromHosting() {
        trackSyncJob {
            try {
                repository.syncCustomersFromHosting()
                repository.syncBillsFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting sync note: ${e.message}")
            }
        }
    }

    fun syncBillsFromHosting() {
        trackSyncJob {
            try {
                repository.syncBillsFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting bill sync note: ${e.message}")
            }
        }
    }

    fun syncPaymentsFromHosting() {
        trackSyncJob {
            try {
                repository.syncPaymentsFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting payment sync note: ${e.message}")
            }
        }
    }

    fun syncExpensesFromHosting() {
        trackSyncJob {
            try {
                repository.syncExpensesFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting expense sync note: ${e.message}")
            }
        }
    }

    fun syncExpenseCategoriesFromHosting() {
        trackSyncJob {
            try {
                repository.syncExpenseCategoriesFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting expense categories sync note: ${e.message}")
            }
        }
    }

    fun syncSettingsFromHosting() {
        trackSyncJob {
            try {
                repository.syncSettingsFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting settings sync note: ${e.message}")
            }
        }
    }

    fun syncAuditLogsFromHosting() {
        trackSyncJob {
            try {
                repository.syncAuditLogsFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting audit logs sync note: ${e.message}")
            }
        }
    }

    fun syncBandwidthBillsFromHosting() {
        trackSyncJob {
            try {
                repository.syncBandwidthBillsFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting bandwidth bills sync note: ${e.message}")
            }
        }
    }

    fun syncSpecificAdvancesFromHosting() {
        trackSyncJob {
            try {
                repository.syncSpecificAdvancesFromHosting()
            } catch (e: Throwable) {
                android.util.Log.w("IspViewModel", "Manual Hosting specific advances sync note: ${e.message}")
            }
        }
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun saveCustomer(customer: CustomerEntity, previousDues: List<PreviousDueItem> = emptyList()) {
        viewModelScope.launch {
            val newNameTrimmed = customer.name.trim().lowercase(java.util.Locale.ROOT)
            val existingList = repository.customers.first()
            val isDuplicateActive = existingList.any { existing ->
                existing.status.equals("ACTIVE", ignoreCase = true) &&
                existing.name.trim().lowercase(java.util.Locale.ROOT) == newNameTrimmed
            }
            if (isDuplicateActive) {
                _toastMessage.value = "Already Active"
                return@launch
            }

            val insertedId = repository.saveCustomer(customer)
            if (previousDues.isNotEmpty()) {
                repository.createPreviousDues(insertedId, customer, previousDues)
            }
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_customer_saved)
        }
    }

    fun importCustomers(
        candidates: List<CustomerEntity>,
        overwriteDuplicates: Boolean,
        onComplete: (importedCount: Int, updatedCount: Int, skippedCount: Int) -> Unit
    ) {
        viewModelScope.launch {
            try {
                var importedCount = 0
                var updatedCount = 0
                var skippedCount = 0

                val existingList = repository.customers.first()
                val existingByCode = existingList.associateBy { it.customerCode.trim().lowercase(java.util.Locale.ROOT) }
                val existingByPppoe = existingList.filter { it.pppoeUsername.isNotBlank() }
                    .associateBy { it.pppoeUsername.trim().lowercase(java.util.Locale.ROOT) }

                val newToInsert = mutableListOf<CustomerEntity>()

                for (candidate in candidates) {
                    val codeKey = candidate.customerCode.trim().lowercase(java.util.Locale.ROOT)
                    val pppoeKey = candidate.pppoeUsername.trim().lowercase(java.util.Locale.ROOT)

                    val matchedExisting = existingByCode[codeKey]
                        ?: (if (pppoeKey.isNotEmpty()) existingByPppoe[pppoeKey] else null)

                    if (matchedExisting != null) {
                        if (overwriteDuplicates) {
                            // If candidate status is default ACTIVE and existing is SUSPENDED or INACTIVE, preserve existing
                            val finalStatus = if (candidate.status == "SUSPENDED" || candidate.status == "INACTIVE") {
                                candidate.status
                            } else {
                                matchedExisting.status
                            }
                            val updatedEntity = candidate.copy(id = matchedExisting.id, status = finalStatus)
                            repository.updateCustomer(updatedEntity)
                            updatedCount++
                        } else {
                            skippedCount++
                        }
                    } else {
                        newToInsert.add(candidate)
                        importedCount++
                    }
                }

                if (newToInsert.isNotEmpty()) {
                    repository.saveCustomers(newToInsert)
                }

                onComplete(importedCount, updatedCount, skippedCount)
            } catch (e: Exception) {
                android.util.Log.e("IspViewModel", "Failed to import customers: ${e.message}", e)
                onComplete(0, 0, candidates.size)
            }
        }
    }

    fun updateCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_customer_updated)
            if (selectedCustomerForDetail.value?.id == customer.id) {
                selectedCustomerForDetail.value = customer
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_customer_removed)
            if (selectedCustomerForDetail.value?.id == customer.id) {
                selectedCustomerForDetail.value = null
            }
        }
    }

    fun toggleCustomerStatus(customer: CustomerEntity) {
        val newStatus = when (customer.status) {
            "ACTIVE" -> "SUSPENDED"
            "SUSPENDED" -> "INACTIVE"
            else -> "ACTIVE"
        }
        viewModelScope.launch {
            repository.updateCustomerStatus(customer.id, newStatus)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_customer_status, newStatus)
            selectedCustomerForDetail.value?.let {
                if (it.id == customer.id) {
                    selectedCustomerForDetail.value = it.copy(status = newStatus)
                }
            }
        }
    }

    fun setCustomerStatus(customer: CustomerEntity, newStatus: String) {
        viewModelScope.launch {
            repository.updateCustomerStatus(customer.id, newStatus)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_customer_status, newStatus)
            selectedCustomerForDetail.value?.let {
                if (it.id == customer.id) {
                    selectedCustomerForDetail.value = it.copy(status = newStatus)
                }
            }
        }
    }

    fun savePackage(pkg: IspPackageEntity) {
        viewModelScope.launch {
            repository.savePackage(pkg)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_package_saved)
        }
    }

    fun updatePackage(pkg: IspPackageEntity) {
        viewModelScope.launch {
            repository.updatePackage(pkg)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_package_updated)
        }
    }

    fun deletePackage(pkg: IspPackageEntity) {
        viewModelScope.launch {
            repository.deletePackage(pkg)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_package_removed)
        }
    }

    fun generateMonthlyBills(billingMonth: String, dueDate: String, selectedCustomerIds: Set<Long>? = null) {
        viewModelScope.launch {
            val count = repository.generateMonthlyBills(billingMonth, dueDate, selectedCustomerIds, isAutoGeneration = false)
            if (count > 0) {
                _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_generated_bills, count, billingMonth)
            } else {
                _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_no_active_customers_bill)
            }
        }
    }

    fun recordPayment(
        billId: Long,
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String,
        advanceMonths: Int = 0,
        specificAdvances: List<PreviousDueItem> = emptyList(),
        onSuccess: ((PaymentEntity) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val payment = repository.recordPayment(billId, customerId, amount, paymentMethod, notes, advanceMonths, specificAdvances)
            if (payment != null) {
                _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_payment_recorded, settings.value.currencySymbol, amount.formatAmount())
                onSuccess?.invoke(payment)
            } else {
                _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_error_payment)
            }
        }
    }

    fun deletePayment(payment: PaymentEntity) {
        viewModelScope.launch {
            val success = repository.deletePayment(payment)
            if (success) {
                _toastMessage.value = getApplication<Application>().getString(com.example.R.string.delete_collection_success)
            } else {
                _toastMessage.value = getApplication<Application>().getString(com.example.R.string.delete_collection_error)
            }
        }
    }

    fun updateSettings(newSettings: BusinessSettingsEntity) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_business_updated)
        }
    }

    fun exportBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val json = repository.exportDataJson()
            onResult(json)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_backup_ready)
        }
    }

    fun saveExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.saveExpense(expense)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_expense_added)
        }
    }

    fun updateExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.updateExpense(expense)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_expense_updated)
        }
    }

    fun deleteExpense(expense: ExpenseEntity) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_expense_deleted)
        }
    }

    fun addCustomCategory(categoryName: String) {
        if (categoryName.isBlank()) return
        viewModelScope.launch {
            repository.saveExpenseCategory(categoryName)
            _toastMessage.value = getApplication<Application>().getString(com.example.R.string.msg_category_added)
        }
    }

    fun importBackup(jsonString: String) {
        viewModelScope.launch {
            val success = repository.importDataJson(jsonString)
            if (success) {
                _toastMessage.value = "Backup restored successfully"
            }
        }
    }

    fun createEncryptedBackup(password: String, onComplete: (java.io.File?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = getApplication<Application>()
            var resultFile: java.io.File? = null
            try {
                val jsonPayload = repository.generateFullBackupJson(app)
                if (jsonPayload.isBlank()) {
                    throw IllegalStateException("Generated backup payload is empty")
                }
                val encryptedBytes = com.example.util.BackupEncryptionManager.encryptPayload(jsonPayload, password)
                if (encryptedBytes.isEmpty()) {
                    throw IllegalStateException("Encrypted payload is empty")
                }

                val backupDir = java.io.File(app.filesDir, "backups")
                if (!backupDir.exists()) backupDir.mkdirs()

                val timeStamp = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm", java.util.Locale.US).format(java.util.Date())
                val backupFile = java.io.File(backupDir, "ISP-Billing-Backup-$timeStamp.ispbackup")
                backupFile.writeBytes(encryptedBytes)

                if (!backupFile.exists() || backupFile.length() == 0L) {
                    throw IllegalStateException("Backup file write failed")
                }

                resultFile = backupFile
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _toastMessage.value = app.getString(com.example.R.string.backup_created_success)
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _toastMessage.value = app.getString(com.example.R.string.msg_backup_failed)
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onComplete(resultFile)
                }
            }
        }
    }

    fun restoreEncryptedBackupFromUri(
        context: android.content.Context,
        uri: android.net.Uri,
        password: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = getApplication<Application>()
            var isSuccess = false
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Cannot open file")
                val bytes = inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) {
                    throw IllegalArgumentException("File is empty")
                }

                val jsonPayload = try {
                    com.example.util.BackupEncryptionManager.decryptPayload(bytes, password)
                } catch (e: javax.crypto.AEADBadTagException) {
                    throw IllegalArgumentException("INCORRECT_PASSWORD")
                } catch (e: javax.crypto.BadPaddingException) {
                    throw IllegalArgumentException("INCORRECT_PASSWORD")
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("Tag mismatch", ignoreCase = true) || msg.contains("cipher", ignoreCase = true) || msg.contains("BadPadding", ignoreCase = true)) {
                        throw IllegalArgumentException("INCORRECT_PASSWORD")
                    } else if (msg.contains("header", ignoreCase = true) || msg.contains("too small", ignoreCase = true) || msg.contains("ciphertext", ignoreCase = true)) {
                        throw IllegalArgumentException("CORRUPTED_FILE")
                    }
                    throw e
                }

                isSuccess = repository.restoreFromFullBackupJson(context, jsonPayload)

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (isSuccess) {
                        _toastMessage.value = app.getString(com.example.R.string.backup_restored_success)
                    } else {
                        _toastMessage.value = app.getString(com.example.R.string.msg_restore_failed)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                val errorMsg = when (e.message) {
                    "INCORRECT_PASSWORD" -> app.getString(com.example.R.string.msg_incorrect_password)
                    "CORRUPTED_FILE" -> app.getString(com.example.R.string.msg_invalid_or_corrupted_backup)
                    else -> app.getString(com.example.R.string.msg_restore_failed)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _toastMessage.value = errorMsg
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(isSuccess)
                }
            }
        }
    }

    fun restoreEncryptedBackupFromFile(
        context: android.content.Context,
        file: java.io.File,
        password: String,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val app = getApplication<Application>()
            var isSuccess = false
            try {
                if (!file.exists() || file.length() == 0L) {
                    throw IllegalArgumentException("CORRUPTED_FILE")
                }
                val bytes = file.readBytes()
                if (bytes.isEmpty()) {
                    throw IllegalArgumentException("CORRUPTED_FILE")
                }

                val jsonPayload = try {
                    com.example.util.BackupEncryptionManager.decryptPayload(bytes, password)
                } catch (e: javax.crypto.AEADBadTagException) {
                    throw IllegalArgumentException("INCORRECT_PASSWORD")
                } catch (e: javax.crypto.BadPaddingException) {
                    throw IllegalArgumentException("INCORRECT_PASSWORD")
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("Tag mismatch", ignoreCase = true) || msg.contains("cipher", ignoreCase = true) || msg.contains("BadPadding", ignoreCase = true)) {
                        throw IllegalArgumentException("INCORRECT_PASSWORD")
                    } else if (msg.contains("header", ignoreCase = true) || msg.contains("too small", ignoreCase = true) || msg.contains("ciphertext", ignoreCase = true)) {
                        throw IllegalArgumentException("CORRUPTED_FILE")
                    }
                    throw e
                }

                isSuccess = repository.restoreFromFullBackupJson(context, jsonPayload)

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (isSuccess) {
                        _toastMessage.value = app.getString(com.example.R.string.backup_restored_success)
                    } else {
                        _toastMessage.value = app.getString(com.example.R.string.msg_restore_failed)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                val errorMsg = when (e.message) {
                    "INCORRECT_PASSWORD" -> app.getString(com.example.R.string.msg_incorrect_password)
                    "CORRUPTED_FILE" -> app.getString(com.example.R.string.msg_invalid_or_corrupted_backup)
                    else -> app.getString(com.example.R.string.msg_restore_failed)
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    _toastMessage.value = errorMsg
                }
            } finally {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(isSuccess)
                }
            }
        }
    }

    fun getLocalBackupFiles(): List<java.io.File> {
        val app = getApplication<Application>()
        val backupDir = java.io.File(app.filesDir, "backups")
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles()?.filter { it.extension == "ispbackup" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    fun deleteLocalBackupFile(file: java.io.File): Boolean {
        return try {
            if (file.exists()) file.delete() else false
        } catch (e: Exception) {
            false
        }
    }

    private val cloudBackupMutex = kotlinx.coroutines.sync.Mutex()
    private val cloudRestoreMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun backupToHosting(context: android.content.Context, userId: String): Pair<Boolean, String> {
        if (!cloudBackupMutex.tryLock()) {
            val msg = "Cloud backup is already in progress"
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _toastMessage.value = msg
            }
            return Pair(false, msg)
        }
        try {
            val result = repository.backupToHosting(context, userId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _toastMessage.value = result.second
            }
            return result
        } finally {
            cloudBackupMutex.unlock()
        }
    }

    suspend fun restoreFromHosting(context: android.content.Context, userId: String): Pair<Boolean, String> {
        if (!cloudRestoreMutex.tryLock()) {
            val msg = "Cloud restore is already in progress"
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _toastMessage.value = msg
            }
            return Pair(false, msg)
        }
        try {
            val result = repository.restoreFromHosting(context, userId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                _toastMessage.value = result.second
            }
            return result
        } finally {
            cloudRestoreMutex.unlock()
        }
    }

    fun saveBill(bill: com.example.data.model.BillEntity) {
        viewModelScope.launch {
            repository.saveBill(bill)
        }
    }

    fun updateBill(bill: com.example.data.model.BillEntity) {
        viewModelScope.launch {
            repository.updateBill(bill)
        }
    }

    fun deleteBill(bill: com.example.data.model.BillEntity) {
        viewModelScope.launch {
            repository.deleteBill(bill)
            _toastMessage.value = "Billing record deleted successfully"
        }
    }

    suspend fun clearAllLocalData(): Boolean {
        return try {
            repository.clearAllLocalData()
            true
        } catch (e: Throwable) {
            android.util.Log.e("IspViewModel", "Failed to clear all local data safely: ${e.message}", e)
            false
        }
    }

    fun logActivity(
        action: String,
        details: String,
        actionType: String = "",
        targetEntity: String = "",
        targetId: String = "",
        previousState: String = "",
        newState: String = "",
        status: String = "SUCCESS",
        userEmail: String? = null
    ) {
        viewModelScope.launch {
            repository.logActivity(
                action = action,
                details = details,
                actionType = actionType,
                targetEntity = targetEntity,
                targetId = targetId,
                previousState = previousState,
                newState = newState,
                status = status,
                userEmail = userEmail
            )
        }
    }
}
