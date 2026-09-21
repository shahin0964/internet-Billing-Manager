package com.example.data.repository

import android.content.Context
import android.util.Log
import java.io.File
import com.example.data.dao.AuditLogDao
import com.example.data.dao.BillDao
import com.example.data.dao.BusinessSettingsDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.IspPackageDao
import com.example.data.dao.NetworkDiagramDao
import com.example.data.dao.PaymentDao
import com.example.data.database.IspDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.ExpenseCategoryEntity
import com.example.data.model.ExpenseEntity
import com.example.data.model.IspPackageEntity
import com.example.data.model.NetworkConnectionEntity
import com.example.data.model.NetworkDiagramEntity
import com.example.data.model.NetworkNodeEntity
import com.example.data.model.PaymentEntity
import com.example.data.model.PreviousDueItem
import com.example.data.model.SpecificAdvanceEntity
import com.example.data.model.BandwidthBillEntity
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.example.data.remote.ApiClient
import com.example.data.remote.ApiService
import com.example.data.model.ApiResponse
import com.example.data.model.Customer
import com.example.data.model.PackageModel
import com.example.data.model.AddCustomerRequest
import com.example.data.model.BillModel
import com.example.data.model.BillRequest
import com.example.data.model.PaymentModel
import com.example.data.model.PaymentRequest
import com.example.data.model.ExpenseModel
import com.example.data.model.ExpenseRequest
import com.example.data.model.ExpenseCategoryModel
import com.example.data.model.ExpenseCategoryRequest
import com.example.data.model.SettingsModel
import com.example.data.model.SettingsRequest
import com.example.data.model.AuditLogModel
import com.example.data.model.AuditLogRequest
import com.example.data.model.BandwidthBillModel
import com.example.data.model.BandwidthBillRequest
import com.example.data.model.SpecificAdvanceModel
import com.example.data.model.SpecificAdvanceRequest
import com.example.util.Resource
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import retrofit2.HttpException

class IspRepository(
    private val customerDao: CustomerDao,
    private val packageDao: IspPackageDao,
    private val billDao: BillDao,
    private val paymentDao: PaymentDao,
    private val settingsDao: BusinessSettingsDao,
    private val expenseDao: ExpenseDao,
    private val networkDiagramDao: NetworkDiagramDao,
    private val auditLogDao: AuditLogDao,
    private val db: IspDatabase,
    private val context: Context? = null
) {
    @Volatile private var idCounter = 0
    private fun generateUniqueId(): Long {
        val count = synchronized(this) { idCounter++ }
        return (System.currentTimeMillis() * 10000L) + (1000..8999).random() + (count % 1000)
    }

    val customers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val packages: Flow<List<IspPackageEntity>> = packageDao.getAllPackages()
    val bills: Flow<List<BillEntity>> = billDao.getAllBills()
    val payments: Flow<List<PaymentEntity>> = paymentDao.getAllPayments()
    val settings: Flow<BusinessSettingsEntity?> = settingsDao.getSettings()
    val expenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val expenseCategories: Flow<List<ExpenseCategoryEntity>> = expenseDao.getAllCategories()
    val diagrams: Flow<List<NetworkDiagramEntity>> = networkDiagramDao.getAllDiagrams()
    val auditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllAuditLogs()
    val bandwidthBills: Flow<List<BandwidthBillEntity>> = db.bandwidthBillDao().getAllBandwidthBills()

    suspend fun saveOrUpdateBandwidthBill(billingMonth: String, amount: Double) {
        val now = System.currentTimeMillis()
        db.bandwidthBillDao().insertOrUpdateBandwidthBill(
            BandwidthBillEntity(
                billingMonth = billingMonth,
                amount = amount,
                updatedAt = now,
                syncStatus = 1
            )
        )

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = BandwidthBillRequest(
                        userId = userId,
                        billingMonth = billingMonth,
                        amount = amount,
                        updatedAt = now
                    )
                    val response = ApiClient.apiService.saveBandwidthBill(request)
                    if (response.status) {
                        db.bandwidthBillDao().markBandwidthBillsSynced(listOf(billingMonth))
                    } else {
                        Log.w("IspRepository", "Server rejected bandwidth bill save: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to save bandwidth bill via Hosting API: ${e.message}")
                }
            }
        }
    }

    companion object {
        private val globalBillGenerationMutex = Mutex()
    }

    private fun markBillAsDeletedForMonth(customerId: Long, customerCode: String, billingMonth: String) {
        if (context == null || billingMonth.isBlank()) return
        try {
            val prefs = context.getSharedPreferences("isp_deleted_monthly_bills", Context.MODE_PRIVATE)
            val cleanMonth = billingMonth.trim().lowercase(Locale.ROOT)
            val editor = prefs.edit()
            if (customerId != 0L) {
                editor.putBoolean("id_${customerId}_${cleanMonth}", true)
            }
            if (customerCode.isNotBlank()) {
                editor.putBoolean("code_${customerCode.trim().lowercase(Locale.ROOT)}_${cleanMonth}", true)
            }
            editor.apply()
        } catch (e: Exception) {
            Log.w("IspRepository", "Failed to mark bill as deleted for month: ${e.message}")
        }
    }

    private fun clearBillDeletedForMonth(customerId: Long, customerCode: String, billingMonth: String) {
        if (context == null || billingMonth.isBlank()) return
        try {
            val prefs = context.getSharedPreferences("isp_deleted_monthly_bills", Context.MODE_PRIVATE)
            val cleanMonth = billingMonth.trim().lowercase(Locale.ROOT)
            val editor = prefs.edit()
            if (customerId != 0L) {
                editor.remove("id_${customerId}_${cleanMonth}")
            }
            if (customerCode.isNotBlank()) {
                editor.remove("code_${customerCode.trim().lowercase(Locale.ROOT)}_${cleanMonth}")
            }
            editor.apply()
        } catch (e: Exception) {
            Log.w("IspRepository", "Failed to clear deleted bill flag: ${e.message}")
        }
    }

    private fun isBillDeletedForMonth(customerId: Long, customerCode: String, billingMonth: String): Boolean {
        if (context == null || billingMonth.isBlank()) return false
        return try {
            val prefs = context.getSharedPreferences("isp_deleted_monthly_bills", Context.MODE_PRIVATE)
            val cleanMonth = billingMonth.trim().lowercase(Locale.ROOT)
            val keyId = "id_${customerId}_${cleanMonth}"
            val keyCode = "code_${customerCode.trim().lowercase(Locale.ROOT)}_${cleanMonth}"
            prefs.getBoolean(keyId, false) || (customerCode.isNotBlank() && prefs.getBoolean(keyCode, false))
        } catch (e: Exception) {
            false
        }
    }

    suspend fun logActivity(
        action: String,
        details: String,
        actionType: String = "",
        targetEntity: String = "",
        targetId: String = "",
        previousState: String = "",
        newState: String = "",
        status: String = "SUCCESS",
        userEmail: String? = null
    ): Long {
        return try {
            val email = userEmail?.ifBlank { null }
                ?: context?.let { com.example.IspApplication.getUserEmail(it) }
                ?: "admin@isp.com"
            val log = AuditLogEntity(
                id = generateUniqueId(),
                action = action,
                actionType = actionType,
                details = details,
                userEmail = email,
                userRole = "Admin",
                targetEntity = targetEntity,
                targetId = targetId,
                previousState = previousState,
                newState = newState,
                status = status,
                timestamp = System.currentTimeMillis(),
                syncStatus = 1
            )
            val id = auditLogDao.insertLog(log)
            notifyCloudSync()

            context?.let { ctx ->
                val userId = com.example.IspApplication.getUserId(ctx)
                if (userId != null) {
                    try {
                        val request = AuditLogRequest(
                            id = log.id.toString(),
                            userId = userId,
                            action = log.action,
                            actionType = log.actionType,
                            details = log.details,
                            userEmail = log.userEmail,
                            userRole = log.userRole,
                            targetEntity = log.targetEntity,
                            targetId = log.targetId,
                            previousState = log.previousState,
                            newState = log.newState,
                            status = log.status,
                            timestamp = log.timestamp
                        )
                        val response = ApiClient.apiService.saveAuditLog(request)
                        if (response.status) {
                            auditLogDao.markAuditLogsSynced(listOf(log.id))
                        } else {
                            Log.w("IspRepository", "Server rejected audit log save: ${response.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("IspRepository", "Failed to save audit log via Hosting API: ${e.message}")
                    }
                }
            }

            id
        } catch (e: Exception) {
            Log.e("IspRepository", "Failed to write activity log: ${e.message}", e)
            0L
        }
    }

    fun getCollectedAmountForDate(date: String): Flow<Double> {
        return paymentDao.getCollectedAmountForDate(date)
    }

    private fun notifyCloudSync() {
        context?.let { ctx ->
            val uid = com.example.IspApplication.getUserId(ctx)
            if (uid != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val actualCount = com.example.util.HostingSyncManager.getActualPendingDirtyCount(ctx)
                        val prefs = ctx.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
                        prefs.edit().putInt("pending_sync_count_$uid", actualCount).apply()
                    } catch (e: Exception) {
                        val prefs = ctx.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
                        val currentCount = prefs.getInt("pending_sync_count_$uid", 0)
                        prefs.edit().putInt("pending_sync_count_$uid", currentCount + 1).apply()
                    }
                }
            }
        }
    }

    suspend fun saveExpense(expense: ExpenseEntity): Long {
        val now = System.currentTimeMillis()
        val expenseToSave = if (expense.id == 0L) {
            expense.copy(id = generateUniqueId(), updatedAt = now, syncStatus = 1)
        } else {
            expense.copy(updatedAt = now, syncStatus = 1)
        }
        val result = expenseDao.insertExpense(expenseToSave)
        logActivity(
            action = "EXPENSE_ADDED",
            actionType = "EXPENSE",
            details = "Added expense: ${expense.title} (৳${expense.amount})",
            targetEntity = "Expense",
            targetId = result.toString(),
            newState = "Amount: ৳${expense.amount}, Category: ${expense.category}"
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = ExpenseRequest(
                        id = expenseToSave.id.toString(),
                        userId = userId,
                        title = expenseToSave.title,
                        amount = expenseToSave.amount,
                        category = expenseToSave.category,
                        date = expenseToSave.date,
                        paymentMethod = expenseToSave.paymentMethod,
                        note = expenseToSave.note,
                        receiptPath = expenseToSave.receiptPath,
                        createdAt = expenseToSave.createdAt,
                        updatedAt = expenseToSave.updatedAt
                    )
                    val response = ApiClient.apiService.saveExpense(request)
                    if (response.status) {
                        expenseDao.markExpensesSynced(listOf(expenseToSave.id))
                    } else {
                        Log.w("IspRepository", "Server rejected expense save: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to save expense via Hosting API: ${e.message}")
                }
            }
        }

        return result
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        val updated = expense.copy(updatedAt = System.currentTimeMillis(), syncStatus = 1)
        expenseDao.updateExpense(updated)
        logActivity(
            action = "EXPENSE_EDIT",
            actionType = "EXPENSE",
            details = "Updated expense: ${expense.title}",
            targetEntity = "Expense",
            targetId = expense.id.toString(),
            newState = "Amount: ৳${expense.amount}, Category: ${expense.category}"
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = ExpenseRequest(
                        id = updated.id.toString(),
                        userId = userId,
                        title = updated.title,
                        amount = updated.amount,
                        category = updated.category,
                        date = updated.date,
                        paymentMethod = updated.paymentMethod,
                        note = updated.note,
                        receiptPath = updated.receiptPath,
                        createdAt = updated.createdAt,
                        updatedAt = updated.updatedAt
                    )
                    val response = ApiClient.apiService.saveExpense(request)
                    if (response.status) {
                        expenseDao.markExpensesSynced(listOf(updated.id))
                    } else {
                        Log.w("IspRepository", "Server rejected expense update: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to update expense via Hosting API: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val response = ApiClient.apiService.deleteExpense(
                        id = expense.id.toString(),
                        userId = userId
                    )
                    if (!response.status) {
                        Log.w("IspRepository", "Server rejected expense deletion: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to delete expense via Hosting API: ${e.message}")
                }
            }
        }
        logActivity(
            action = "EXPENSE_DELETED",
            actionType = "EXPENSE",
            details = "Deleted expense: ${expense.title}",
            targetEntity = "Expense",
            targetId = expense.id.toString(),
            previousState = "Amount: ৳${expense.amount}, Category: ${expense.category}"
        )
        notifyCloudSync()
    }

    suspend fun saveExpenseCategory(categoryName: String): Long {
        val now = System.currentTimeMillis()
        val categoryToSave = ExpenseCategoryEntity(id = generateUniqueId(), name = categoryName.trim(), updatedAt = now, syncStatus = 1)
        val result = expenseDao.insertCategory(categoryToSave)
        logActivity(
            action = "EXPENSE_CATEGORY_ADDED",
            actionType = "EXPENSE",
            details = "Created expense category: ${categoryName.trim()}",
            targetEntity = "ExpenseCategory",
            targetId = result.toString()
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = ExpenseCategoryRequest(
                        id = categoryToSave.id.toString(),
                        userId = userId,
                        name = categoryToSave.name,
                        updatedAt = categoryToSave.updatedAt
                    )
                    val response = ApiClient.apiService.saveExpenseCategory(request)
                    if (response.status) {
                        expenseDao.markCategoriesSynced(listOf(categoryToSave.id))
                    } else {
                        Log.w("IspRepository", "Server rejected expense category save: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to save expense category via Hosting API: ${e.message}")
                }
            }
        }

        return result
    }

    suspend fun saveCustomer(customer: CustomerEntity): Long {
        val isNew = customer.id == 0L
        val now = System.currentTimeMillis()
        val customerToSave = if (isNew) {
            customer.copy(id = generateUniqueId(), updatedAt = now, syncStatus = 1)
        } else {
            customer.copy(updatedAt = now, syncStatus = 1)
        }
        val result = customerDao.insertCustomer(customerToSave)
        val actionName = if (isNew) "CUSTOMER_CREATE" else "CUSTOMER_EDIT"
        logActivity(
            action = actionName,
            actionType = "CUSTOMER",
            details = if (isNew) "Created customer: ${customer.name} (${customer.pppoeUsername})" else "Updated customer: ${customer.name} (${customer.pppoeUsername})",
            targetEntity = "Customer",
            targetId = if (isNew) result.toString() else customerToSave.id.toString(),
            newState = "Package: ${customer.packageName}, Fee: ৳${customer.monthlyFee}, Status: ${customer.status}"
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val savedCustomerId = if (customerToSave.id != 0L) customerToSave.id else result
                    val cycleDate = try {
                        val parts = customerToSave.joiningDate.trim().split("-", "/", ".")
                        if (parts.size >= 3) {
                            val day = if (parts[0].length == 4) parts[2].toIntOrNull() else parts[0].toIntOrNull()
                            day?.coerceIn(1, 31) ?: 1
                        } else {
                            1
                        }
                    } catch (e: Exception) {
                        1
                    }
                    val request = AddCustomerRequest(
                        id = savedCustomerId.toString(),
                        userId = userId,
                        name = customerToSave.name,
                        phone = customerToSave.phone.ifBlank { null },
                        address = customerToSave.address.ifBlank { null },
                        ipAddress = customerToSave.ipAddress.ifBlank { null },
                        packageId = if (customerToSave.packageId > 0L) customerToSave.packageId.toString() else null,
                        billingCycleDate = cycleDate,
                        status = customerToSave.status
                    )
                    val response = ApiClient.apiService.saveCustomer(request)
                    if (!response.status) {
                        Log.w("IspRepository", "Server rejected customer save: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to save customer via Hosting API: ${e.message}")
                }
            }
        }

        return result
    }

    suspend fun createPreviousDues(
        customerId: Long,
        customer: CustomerEntity,
        previousDues: List<PreviousDueItem>
    ) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())
        val monthsList = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        
        // Sort chronologically oldest first to ensure Room assigns ascending IDs to them!
        val sortedDues = previousDues.sortedWith(compareBy<PreviousDueItem> { it.year.toIntOrNull() ?: 0 }.thenBy { monthsList.indexOf(it.month) })
        
        db.withTransaction {
            val newBills = mutableListOf<BillEntity>()
            for (item in sortedDues) {
                val billingMonth = "${item.month} ${item.year}".trim()
                val existing = billDao.findBillForCustomerAndMonth(customerId, customer.customerCode ?: "", billingMonth)
                if (existing != null) {
                    continue
                }
                val billNo = "PREV-BILL-${System.currentTimeMillis().toString().takeLast(6)}-${customerId}-${item.month.take(3)}"
                val now = System.currentTimeMillis()
                newBills.add(
                    BillEntity(
                        id = generateUniqueId(),
                        billNumber = billNo,
                        customerId = customerId,
                        customerName = customer.name,
                        customerCode = customer.customerCode ?: "CUST-${customerId}",
                        billingMonth = billingMonth,
                        amount = item.amount,
                        paidAmount = 0.0,
                        dueAmount = item.amount,
                        status = "UNPAID",
                        generatedDate = todayStr,
                        dueDate = todayStr,
                        updatedAt = now,
                        syncStatus = 1
                    )
                )
            }
            if (newBills.isNotEmpty()) {
                billDao.insertBills(newBills)
                logActivity(
                    action = "BILL_EDIT",
                    actionType = "BILL",
                    details = "Created ${newBills.size} previous dues bills for customer ${customer.name}",
                    targetEntity = "Customer",
                    targetId = customerId.toString()
                )
            }
        }
        notifyCloudSync()
    }

    suspend fun saveCustomers(customers: List<CustomerEntity>) {
        val now = System.currentTimeMillis()
        val customersToSave = customers.map {
            if (it.id == 0L) it.copy(id = generateUniqueId(), updatedAt = now, syncStatus = 1)
            else it.copy(updatedAt = now, syncStatus = 1)
        }
        customerDao.insertCustomers(customersToSave)
        logActivity(
            action = "CUSTOMER_CREATE",
            actionType = "CUSTOMER",
            details = "Imported ${customers.size} customer records",
            targetEntity = "Customer"
        )
        notifyCloudSync()
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        val updated = customer.copy(updatedAt = System.currentTimeMillis(), syncStatus = 1)
        customerDao.updateCustomer(updated)
        billDao.updateCustomerNameInBills(customer.id, customer.name)
        paymentDao.updateCustomerNameInPayments(customer.id, customer.name)
        logActivity(
            action = "CUSTOMER_EDIT",
            actionType = "CUSTOMER",
            details = "Updated customer details for ${customer.name} (${customer.pppoeUsername})",
            targetEntity = "Customer",
            targetId = customer.id.toString(),
            newState = "Package: ${customer.packageName}, Fee: ৳${customer.monthlyFee}, Status: ${customer.status}"
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val cycleDate = try {
                        val parts = updated.joiningDate.trim().split("-", "/", ".")
                        if (parts.size >= 3) {
                            val day = if (parts[0].length == 4) parts[2].toIntOrNull() else parts[0].toIntOrNull()
                            day?.coerceIn(1, 31) ?: 1
                        } else {
                            1
                        }
                    } catch (e: Exception) {
                        1
                    }
                    val request = AddCustomerRequest(
                        id = updated.id.toString(),
                        userId = userId,
                        name = updated.name,
                        phone = updated.phone.ifBlank { null },
                        address = updated.address.ifBlank { null },
                        ipAddress = updated.ipAddress.ifBlank { null },
                        packageId = if (updated.packageId > 0L) updated.packageId.toString() else null,
                        billingCycleDate = cycleDate,
                        status = updated.status
                    )
                    val response = ApiClient.apiService.saveCustomer(request)
                    if (!response.status) {
                        Log.w("IspRepository", "Server rejected customer update: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to update customer via Hosting API: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        val bills = billDao.getBillsForCustomer(customer.id).first()
        val payments = paymentDao.getPaymentsForCustomer(customer.id).first()

        billDao.deleteBillsForCustomer(customer.id)
        paymentDao.deletePaymentsForCustomer(customer.id)
        customerDao.deleteCustomer(customer)

        context?.let { ctx ->
            try {
                com.example.data.database.SmsDatabase.getDatabase(ctx).smsQueueDao().deleteSmsByCustomerId(customer.id.toString())
            } catch (e: Exception) {
                Log.e("IspRepository", "Failed to delete pending SMS for customer: ${e.message}")
            }

            bills.forEach { bill ->
                markBillAsDeletedForMonth(bill.customerId, bill.customerCode, bill.billingMonth)
            }

            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val response = ApiClient.apiService.deleteCustomer(
                        id = customer.id.toString(),
                        userId = userId
                    )
                    if (!response.status) {
                        Log.w("IspRepository", "Server rejected customer deletion: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to delete customer via Hosting API: ${e.message}")
                }
            }
        }
        logActivity(
            action = "CUSTOMER_DELETE",
            actionType = "CUSTOMER",
            details = "Deleted customer ${customer.name} (${customer.pppoeUsername}) and associated billing records",
            targetEntity = "Customer",
            targetId = customer.id.toString(),
            previousState = "Name: ${customer.name}, Mobile: ${customer.phone}, Package: ${customer.packageName}"
        )
        notifyCloudSync()
    }

    suspend fun updateCustomerStatus(id: Long, status: String) {
        val now = System.currentTimeMillis()
        customerDao.updateCustomerStatus(id, status, now)
        customerDao.updateCustomerSyncStatus(id, 1)
        val actionName = when (status.uppercase()) {
            "EXPIRED", "INACTIVE", "SUSPENDED" -> "SUSPEND_CUSTOMER"
            "ACTIVE" -> "RESUME_CUSTOMER"
            else -> "CUSTOMER_EDIT"
        }
        logActivity(
            action = actionName,
            actionType = "CUSTOMER",
            details = "Changed customer #$id status to $status",
            targetEntity = "Customer",
            targetId = id.toString(),
            newState = "Status: $status"
        )
        notifyCloudSync()
    }

    suspend fun savePackage(pkg: IspPackageEntity): Long {
        val isNew = pkg.id == 0L
        val now = System.currentTimeMillis()
        val pkgToSave = if (isNew) pkg.copy(id = generateUniqueId(), updatedAt = now, syncStatus = 1) else pkg.copy(updatedAt = now, syncStatus = 1)
        val result = packageDao.insertPackage(pkgToSave)
        logActivity(
            action = if (isNew) "PACKAGE_CREATE" else "PACKAGE_EDIT",
            actionType = "PACKAGE",
            details = if (isNew) "Created ISP package: ${pkg.name} (${pkg.speedMbps} Mbps)" else "Updated ISP package: ${pkg.name}",
            targetEntity = "IspPackage",
            targetId = if (isNew) result.toString() else pkgToSave.id.toString(),
            newState = "Speed: ${pkg.speedMbps} Mbps, Price: ৳${pkg.monthlyPrice}"
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = com.example.data.remote.PackageRequest(
                        id = pkgToSave.id.toString(),
                        userId = userId,
                        name = pkgToSave.name,
                        price = pkgToSave.monthlyPrice,
                        speed = if (pkgToSave.speedMbps > 0) pkgToSave.speedMbps.toString() else null
                    )
                    val response = ApiClient.apiService.savePackage(request)
                    if (response.status) {
                        packageDao.updatePackage(pkgToSave.copy(syncStatus = 0))
                    } else {
                        Log.w("IspRepository", "Server rejected package save: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to save package via Hosting API: ${e.message}")
                }
            }
        }

        return result
    }

    suspend fun updatePackage(pkg: IspPackageEntity) {
        val updated = pkg.copy(updatedAt = System.currentTimeMillis(), syncStatus = 1)
        packageDao.updatePackage(updated)
        logActivity(
            action = "PACKAGE_EDIT",
            actionType = "PACKAGE",
            details = "Updated ISP package: ${pkg.name}",
            targetEntity = "IspPackage",
            targetId = pkg.id.toString(),
            newState = "Speed: ${pkg.speedMbps} Mbps, Price: ৳${pkg.monthlyPrice}"
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = com.example.data.remote.PackageRequest(
                        id = updated.id.toString(),
                        userId = userId,
                        name = updated.name,
                        price = updated.monthlyPrice,
                        speed = if (updated.speedMbps > 0) updated.speedMbps.toString() else null
                    )
                    val response = ApiClient.apiService.savePackage(request)
                    if (response.status) {
                        packageDao.updatePackage(updated.copy(syncStatus = 0))
                    } else {
                        Log.w("IspRepository", "Server rejected package update: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to update package via Hosting API: ${e.message}")
                }
            }
        }
    }

    suspend fun deletePackage(pkg: IspPackageEntity) {
        packageDao.deletePackage(pkg)
        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val response = ApiClient.apiService.deletePackage(
                        id = pkg.id.toString(),
                        userId = userId
                    )
                    if (!response.status) {
                        Log.w("IspRepository", "Server rejected package deletion: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to delete package via Hosting API: ${e.message}")
                }
            }
        }
        logActivity(
            action = "PACKAGE_DELETE",
            actionType = "PACKAGE",
            details = "Deleted ISP package: ${pkg.name}",
            targetEntity = "IspPackage",
            targetId = pkg.id.toString(),
            previousState = "Name: ${pkg.name}, Price: ৳${pkg.monthlyPrice}"
        )
        notifyCloudSync()
    }

    suspend fun deleteBill(bill: BillEntity) {
        billDao.deleteBill(bill)
        markBillAsDeletedForMonth(bill.customerId, bill.customerCode, bill.billingMonth)
        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val response = ApiClient.apiService.deleteBill(
                        id = bill.id.toString(),
                        userId = userId
                    )
                    if (!response.status) {
                        Log.w("IspRepository", "Server rejected bill deletion: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to delete bill via Hosting API: ${e.message}")
                }
            }
        }
        logActivity(
            action = "BILL_DELETE",
            actionType = "BILL",
            details = "Deleted bill #${bill.billNumber} for ${bill.customerName}",
            targetEntity = "Bill",
            targetId = bill.id.toString(),
            previousState = "Month: ${bill.billingMonth}, Amount: ৳${bill.amount}"
        )
        notifyCloudSync()
    }

    suspend fun saveBill(bill: BillEntity): Long {
        val now = System.currentTimeMillis()
        val billToSave = if (bill.id == 0L) {
            bill.copy(id = generateUniqueId(), updatedAt = now, syncStatus = 1)
        } else {
            bill.copy(updatedAt = now, syncStatus = 1)
        }
        val savedId = billDao.insertBill(billToSave)
        logActivity(
            action = "BILL_EDIT",
            actionType = "BILL",
            details = "Saved bill #${billToSave.billNumber} for ${billToSave.customerName}",
            targetEntity = "Bill",
            targetId = billToSave.id.toString()
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = BillRequest(
                        id = billToSave.id.toString(),
                        userId = userId,
                        customerId = billToSave.customerId.toString(),
                        amount = billToSave.amount,
                        billMonth = billToSave.billingMonth,
                        dueDate = billToSave.dueDate,
                        status = billToSave.status,
                        billNumber = billToSave.billNumber,
                        customerName = billToSave.customerName,
                        customerCode = billToSave.customerCode,
                        paidAmount = billToSave.paidAmount,
                        dueAmount = billToSave.dueAmount,
                        generatedDate = billToSave.generatedDate,
                        updatedAt = billToSave.updatedAt
                    )
                    val response = ApiClient.apiService.saveBill(request)
                    if (response.status) {
                        billDao.updateBill(billToSave.copy(syncStatus = 0))
                    } else {
                        Log.w("IspRepository", "Server rejected bill save: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to save bill via Hosting API: ${e.message}")
                }
            }
        }
        return savedId
    }

    suspend fun updateBill(bill: BillEntity) {
        val originalBillNumber = if (bill.billNumber.startsWith("BREAKDOWN|")) {
            bill.billNumber.substringAfterLast("|")
        } else {
            bill.billNumber
        }

        val previousDue = if (bill.billNumber.startsWith("BREAKDOWN|")) {
            val allBills = billDao.getBillsListForCustomer(bill.customerId)
            val unpaidOthers = allBills.filter { (it.status == "UNPAID" || it.status == "PARTIAL") && it.id != bill.id }
            unpaidOthers.sumOf { it.dueAmount }
        } else {
            0.0
        }

        val individualAmount = (bill.amount - previousDue).coerceAtLeast(0.0)
        val newDue = (individualAmount - bill.paidAmount).coerceAtLeast(0.0)
        val newStatus = when {
            newDue <= 0.0 -> "PAID"
            bill.paidAmount > 0.0 -> "PARTIAL"
            else -> "UNPAID"
        }

        val finalBill = bill.copy(
            billNumber = originalBillNumber,
            amount = individualAmount,
            dueAmount = newDue,
            status = newStatus,
            updatedAt = System.currentTimeMillis(),
            syncStatus = 1
        )
        billDao.updateBill(finalBill)
        logActivity(
            action = "BILL_EDIT",
            actionType = "BILL",
            details = "Updated bill #${originalBillNumber} for ${bill.customerName}",
            targetEntity = "Bill",
            targetId = bill.id.toString(),
            newState = "Amount: ৳${individualAmount}, Paid: ৳${bill.paidAmount}, Due: ৳${newDue}, Status: ${newStatus}"
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = BillRequest(
                        id = finalBill.id.toString(),
                        userId = userId,
                        customerId = finalBill.customerId.toString(),
                        amount = finalBill.amount,
                        billMonth = finalBill.billingMonth,
                        dueDate = finalBill.dueDate,
                        status = finalBill.status,
                        billNumber = finalBill.billNumber,
                        customerName = finalBill.customerName,
                        customerCode = finalBill.customerCode,
                        paidAmount = finalBill.paidAmount,
                        dueAmount = finalBill.dueAmount,
                        generatedDate = finalBill.generatedDate,
                        updatedAt = finalBill.updatedAt
                    )
                    val response = ApiClient.apiService.saveBill(request)
                    if (response.status) {
                        billDao.updateBill(finalBill.copy(syncStatus = 0))
                    } else {
                        Log.w("IspRepository", "Server rejected bill update: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to update bill via Hosting API: ${e.message}")
                }
            }
        }
    }

    suspend fun generateMonthlyBills(
        billingMonth: String,
        dueDate: String,
        selectedCustomerIds: Set<Long>? = null,
        isAutoGeneration: Boolean = false
    ): Int = globalBillGenerationMutex.withLock {
        val cleanMonth = billingMonth.trim()
        if (cleanMonth.isBlank()) return@withLock 0

        val generatedCount = db.withTransaction {
            val currentCustomers = customerDao.getAllCustomersList()
            val activeCustomers = currentCustomers.filter { customer ->
                val isFree = customer.packageName.contains("free", ignoreCase = true) ||
                        customer.packageName.contains("ফ্রি", ignoreCase = true)
                val statusClean = customer.status.trim().uppercase(Locale.ROOT)
                val isInactiveOrSuspended = statusClean == "INACTIVE" ||
                        statusClean == "SUSPENDED" ||
                        statusClean == "EXPIRED" ||
                        statusClean.contains("SUSPEND") ||
                        statusClean.contains("INACT")
                val isExplicitlyActive = statusClean == "ACTIVE"

                isExplicitlyActive && !isInactiveOrSuspended && !isFree && (selectedCustomerIds == null || selectedCustomerIds.contains(customer.id))
            }
            
            var count = 0
            val newBills = mutableListOf<BillEntity>()
            val processedCustomerIds = mutableSetOf<Long>()
            val processedCustomerCodes = mutableSetOf<String>()

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date())

            for (customer in activeCustomers) {
                // Strict safeguard: Under no circumstances generate a bill for an inactive or suspended line
                val custStatus = customer.status.trim().uppercase(Locale.ROOT)
                if (custStatus != "ACTIVE" || custStatus == "SUSPENDED" || custStatus == "INACTIVE" || custStatus.contains("SUSPEND") || custStatus.contains("INACT")) {
                    continue
                }

                // In-batch duplicate guard
                if (processedCustomerIds.contains(customer.id)) continue
                if (customer.customerCode.isNotBlank() && processedCustomerCodes.contains(customer.customerCode.trim().lowercase(Locale.ROOT))) continue

                // Atomic direct database existence check within transaction
                val existingBill = billDao.findBillForCustomerAndMonth(
                    customerId = customer.id,
                    customerCode = customer.customerCode,
                    billingMonth = cleanMonth
                )

                if (existingBill != null) {
                    continue
                }

                val dbCount = billDao.getBillCountForCustomerAndMonth(customer.id, customer.customerCode, cleanMonth)
                if (dbCount > 0) {
                    continue
                }

                // Cross-locale & historical month deduplication check
                val existingCustomerBills = billDao.getBillsListForCustomer(customer.id)
                if (existingCustomerBills.any { com.example.util.BillingMonthUtils.isSameMonth(it.billingMonth, cleanMonth) }) {
                    continue
                }

                if (isAutoGeneration && isBillDeletedForMonth(customer.id, customer.customerCode, cleanMonth)) {
                    continue
                }

                val billNo = "BILL-${System.currentTimeMillis().toString().takeLast(6)}-${customer.id}"
                val now = System.currentTimeMillis()

                val specificAdvance = db.specificAdvanceDao().getUnconsumedSpecificAdvance(customer.id, cleanMonth)
                val billAmount: Double
                val paidAmount: Double
                val dueAmount: Double
                val status: String
                var advanceToDeduct = 0.0

                if (specificAdvance != null && specificAdvance.amount > 0.0) {
                    val advAmt = specificAdvance.amount
                    if (advAmt >= customer.monthlyFee) {
                        billAmount = customer.monthlyFee
                        paidAmount = customer.monthlyFee
                        dueAmount = 0.0
                        status = "PAID"
                        val remainingSpecific = advAmt - customer.monthlyFee
                        if (remainingSpecific > 0.0) {
                            db.specificAdvanceDao().insertSpecificAdvance(
                                specificAdvance.copy(amount = remainingSpecific, updatedAt = now)
                            )
                        } else {
                            db.specificAdvanceDao().markConsumed(specificAdvance.id, now)
                        }
                    } else {
                        billAmount = customer.monthlyFee
                        paidAmount = advAmt
                        dueAmount = (customer.monthlyFee - advAmt).coerceAtLeast(0.0)
                        status = if (dueAmount <= 0.0) "PAID" else if (paidAmount > 0.0) "PARTIAL" else "UNPAID"
                        db.specificAdvanceDao().markConsumed(specificAdvance.id, now)
                    }
                } else {
                    val currentAdvance = customer.advanceBalance
                    if (currentAdvance >= customer.monthlyFee) {
                        billAmount = customer.monthlyFee
                        paidAmount = customer.monthlyFee
                        dueAmount = 0.0
                        status = "PAID"
                        advanceToDeduct = customer.monthlyFee
                    } else {
                        val applied = currentAdvance
                        billAmount = customer.monthlyFee
                        paidAmount = applied
                        dueAmount = (customer.monthlyFee - applied).coerceAtLeast(0.0)
                        status = if (dueAmount <= 0.0) "PAID" else if (paidAmount > 0.0) "PARTIAL" else "UNPAID"
                        advanceToDeduct = applied
                    }
                }

                if (advanceToDeduct > 0.0) {
                    val updatedCust = customer.copy(
                        advanceBalance = (customer.advanceBalance - advanceToDeduct).coerceAtLeast(0.0),
                        updatedAt = now,
                        syncStatus = 1
                    )
                    customerDao.updateCustomer(updatedCust)
                }

                newBills.add(
                    BillEntity(
                        id = generateUniqueId(),
                        billNumber = billNo,
                        customerId = customer.id,
                        customerName = customer.name,
                        customerCode = customer.customerCode,
                        billingMonth = cleanMonth,
                        amount = billAmount,
                        paidAmount = paidAmount,
                        dueAmount = dueAmount,
                        status = status,
                        generatedDate = todayStr,
                        dueDate = dueDate,
                        updatedAt = now,
                        syncStatus = 1
                    )
                )

                processedCustomerIds.add(customer.id)
                if (customer.customerCode.isNotBlank()) {
                    processedCustomerCodes.add(customer.customerCode.trim().lowercase(Locale.ROOT))
                }

                if (!isAutoGeneration) {
                    clearBillDeletedForMonth(customer.id, customer.customerCode, cleanMonth)
                }
                count++
            }

            if (newBills.isNotEmpty()) {
                billDao.insertBills(newBills)
                logActivity(
                    action = "BILL_EDIT",
                    actionType = "BILL",
                    details = "Generated $count monthly bills for $cleanMonth",
                    targetEntity = "Bill"
                )
                try {
                    context?.let { com.example.util.AutomaticSmsManager.onBillsGenerated(it, newBills) }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to queue billing SMS: ${e.message}")
                }
            }
            count
        }
        notifyCloudSync()
        return@withLock generatedCount
    }

    private fun getNextMonth(currentMonthYear: String): String {
        val sdfUs = SimpleDateFormat("MMMM yyyy", Locale.US)
        val sdfDefault = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return try {
            val date = try {
                sdfUs.parse(currentMonthYear)
            } catch (e: Exception) {
                sdfDefault.parse(currentMonthYear)
            } ?: return ""
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.MONTH, 1)
            sdfUs.format(calendar.time)
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun recordPayment(
        billId: Long,
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String,
        advanceMonths: Int = 0,
        specificAdvances: List<PreviousDueItem> = emptyList()
    ): PaymentEntity? {
        if (amount <= 0.0) return null

        return try {
            db.withTransaction {
                val allBills = billDao.getAllBillsList()

                // 1. Resolve target customer ID
                val targetCustId = if (customerId != 0L) {
                    customerId
                } else if (billId != 0L) {
                    allBills.find { it.id == billId }?.customerId ?: 0L
                } else {
                    0L
                }

                // 2. Resolve customer entity
                val customer = if (targetCustId != 0L) {
                    customerDao.getCustomerById(targetCustId).first()
                } else null

                // 3. Resolve target bill
                var targetBill: BillEntity? = if (billId != 0L) {
                    allBills.find { it.id == billId }
                } else null

                if (targetBill == null && targetCustId != 0L) {
                    val unpaidForCust = allBills.filter { it.customerId == targetCustId && it.dueAmount > 0 }.sortedBy { it.id }
                    targetBill = unpaidForCust.firstOrNull()
                        ?: allBills.filter { it.customerId == targetCustId }.maxByOrNull { it.id }
                }

                if (targetCustId == 0L && targetBill == null) {
                    return@withTransaction null
                }

                val effectiveCustId = if (targetCustId != 0L) targetCustId else (targetBill?.customerId ?: 0L)
                val now = System.currentTimeMillis()
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val todayStr = sdf.format(Date(now))
                val receiptNo = "PAY-${System.currentTimeMillis().toString().takeLast(6)}"

                var remainingPayment = amount

                // 4. Apply payment to unpaid bills for this customer (oldest to newest)
                if (effectiveCustId != 0L) {
                    val unpaidBills = allBills.filter { it.customerId == effectiveCustId && it.dueAmount > 0 }.sortedBy { it.id }
                    for (b in unpaidBills) {
                        if (remainingPayment <= 0.0) break
                        val due = b.dueAmount
                        val applyAmount = minOf(remainingPayment, due)
                        val newPaid = b.paidAmount + applyAmount
                        val newDue = (b.amount - newPaid).coerceAtLeast(0.0)
                        val newStatus = when {
                            newDue <= 0.0 -> "PAID"
                            newPaid > 0.0 -> "PARTIAL"
                            else -> "UNPAID"
                        }
                        val updated = b.copy(
                            paidAmount = newPaid,
                            dueAmount = newDue,
                            status = newStatus,
                            updatedAt = now,
                            syncStatus = 1
                        )
                        billDao.updateBill(updated)
                        remainingPayment -= applyAmount
                    }
                } else if (targetBill != null && remainingPayment > 0.0) {
                    val due = targetBill.dueAmount
                    val applyAmount = minOf(remainingPayment, due)
                    val newPaid = targetBill.paidAmount + applyAmount
                    val newDue = (targetBill.amount - newPaid).coerceAtLeast(0.0)
                    val newStatus = when {
                        newDue <= 0.0 -> "PAID"
                        newPaid > 0.0 -> "PARTIAL"
                        else -> "UNPAID"
                    }
                    val updated = targetBill.copy(
                        paidAmount = newPaid,
                        dueAmount = newDue,
                        status = newStatus,
                        updatedAt = now,
                        syncStatus = 1
                    )
                    billDao.updateBill(updated)
                    remainingPayment -= applyAmount
                }

                // 5. If there is remaining payment (excess/advance), add to customer's advance balance
                val specificTotal = specificAdvances.sumOf { it.amount }
                val genericAdvanceAmount = (remainingPayment - specificTotal).coerceAtLeast(0.0)
                if (genericAdvanceAmount > 0.0 && customer != null) {
                    val updatedCust = customer.copy(
                        advanceBalance = customer.advanceBalance + genericAdvanceAmount,
                        updatedAt = now,
                        syncStatus = 1
                    )
                    customerDao.updateCustomer(updatedCust)
                }

                // 5b. Save specific advances
                if (effectiveCustId != 0L) {
                    for (adv in specificAdvances) {
                        val entity = SpecificAdvanceEntity(
                            customerId = effectiveCustId,
                            billingMonth = "${adv.month} ${adv.year}",
                            amount = adv.amount,
                            isConsumed = false,
                            updatedAt = now,
                            syncStatus = 1
                        )
                        db.specificAdvanceDao().insertSpecificAdvance(entity)
                    }
                }

                // 6. Record payment entity
                val custName = customer?.name ?: targetBill?.customerName ?: "Customer #$effectiveCustId"
                val linkedBillId = targetBill?.id ?: 0L
                val payment = PaymentEntity(
                    id = generateUniqueId(),
                    paymentReceiptNo = receiptNo,
                    billId = linkedBillId,
                    customerId = effectiveCustId,
                    customerName = custName,
                    amount = amount,
                    paymentDate = todayStr,
                    paymentMethod = paymentMethod,
                    notes = notes,
                    updatedAt = now,
                    syncStatus = 1
                )
                val pId = paymentDao.insertPayment(payment)
                val createdPayment = payment.copy(id = pId)

                logActivity(
                    action = "PAYMENT_ADDED",
                    actionType = "PAYMENT",
                    details = "Recorded payment of ৳${amount} for ${custName} via ${paymentMethod}" + (if (advanceMonths > 0) " (Advance: $advanceMonths months)" else ""),
                    targetEntity = "Payment",
                    targetId = pId.toString(),
                    newState = "Amount: ৳${amount}, Method: ${paymentMethod}, Receipt: ${receiptNo}"
                )

                try {
                    context?.let { com.example.util.AutomaticSmsManager.onPaymentRecorded(it, createdPayment) }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to queue payment SMS: ${e.message}")
                }
                notifyCloudSync()

                context?.let { ctx ->
                    val userId = com.example.IspApplication.getUserId(ctx)
                    if (userId != null) {
                        try {
                            val request = PaymentRequest(
                                id = createdPayment.id.toString(),
                                userId = userId,
                                paymentReceiptNo = createdPayment.paymentReceiptNo,
                                billId = createdPayment.billId.toString(),
                                customerId = createdPayment.customerId.toString(),
                                customerName = createdPayment.customerName,
                                amount = createdPayment.amount,
                                paymentDate = createdPayment.paymentDate,
                                paymentMethod = createdPayment.paymentMethod,
                                notes = createdPayment.notes,
                                updatedAt = createdPayment.updatedAt
                            )
                            val response = ApiClient.apiService.savePayment(request)
                            if (response.status) {
                                paymentDao.markPaymentsSynced(listOf(createdPayment.id))
                            } else {
                                Log.w("IspRepository", "Server rejected payment save: ${response.message}")
                            }
                        } catch (e: Exception) {
                            Log.e("IspRepository", "Failed to save payment via Hosting API: ${e.message}")
                        }
                    }
                }

                createdPayment
            }
        } catch (e: Exception) {
            Log.e("IspRepository", "Error in recordPayment", e)
            null
        }
    }

    suspend fun deletePayment(payment: PaymentEntity): Boolean {
        try {
            db.withTransaction {
                // Delete payment record locally
                paymentDao.deletePaymentById(payment.id)

                val customerId = payment.customerId
                if (customerId != 0L) {
                    val bills = billDao.getBillsListForCustomer(customerId).sortedBy { it.id }
                    val remainingPayments = paymentDao.getPaymentsListForCustomer(customerId).sortedBy { it.id }

                    val totalCurrentMoneyApplied = bills.sumOf { it.paidAmount }
                    val totalPaymentsBeforeDelete = remainingPayments.sumOf { it.amount } + payment.amount
                    val totalInitialAdvances = (totalCurrentMoneyApplied - totalPaymentsBeforeDelete).coerceAtLeast(0.0)

                    var remainingAdvance = totalInitialAdvances
                    val billsWithInitialPaid = bills.map { b ->
                        val initialPaid = minOf(b.amount, remainingAdvance)
                        remainingAdvance = (remainingAdvance - initialPaid).coerceAtLeast(0.0)
                        b to initialPaid
                    }

                    val billPaidMap = billsWithInitialPaid.associate { it.first.id to it.second }.toMutableMap()

                    for (pay in remainingPayments) {
                        var remainingPaymentAmount = pay.amount
                        for (b in bills) {
                            if (remainingPaymentAmount <= 0.0) break
                            val currentPaid = billPaidMap[b.id] ?: 0.0
                            val due = (b.amount - currentPaid).coerceAtLeast(0.0)
                            if (due > 0.0) {
                                val applyAmount = minOf(remainingPaymentAmount, due)
                                billPaidMap[b.id] = currentPaid + applyAmount
                                remainingPaymentAmount -= applyAmount
                            }
                        }
                    }

                    val now = System.currentTimeMillis()
                    for (b in bills) {
                        val newPaid = billPaidMap[b.id] ?: 0.0
                        val newDue = (b.amount - newPaid).coerceAtLeast(0.0)
                        val newStatus = when {
                            newDue <= 0.0 -> "PAID"
                            newPaid > 0.0 -> "PARTIAL"
                            else -> "UNPAID"
                        }
                        val updatedBill = b.copy(
                            paidAmount = newPaid,
                            dueAmount = newDue,
                            status = newStatus,
                            updatedAt = now,
                            syncStatus = 1
                        )
                        billDao.updateBill(updatedBill)
                    }

                    // Recalculate remaining advance balance for the customer
                    val totalMoneyAvailable = totalInitialAdvances + remainingPayments.sumOf { it.amount }
                    val totalMoneySpentOnBills = bills.sumOf { billPaidMap[it.id] ?: 0.0 }
                    val remainingAdvanceBalance = (totalMoneyAvailable - totalMoneySpentOnBills).coerceAtLeast(0.0)

                    val customer = customerDao.getCustomerById(customerId).first()
                    if (customer != null) {
                        val updatedCust = customer.copy(
                            advanceBalance = remainingAdvanceBalance,
                            updatedAt = now,
                            syncStatus = 1
                        )
                        customerDao.updateCustomer(updatedCust)
                    }
                } else {
                    // Fallback to old behavior if customerId is 0 (should not happen normally)
                    val bill = billDao.getBillById(payment.billId).first()
                    if (bill != null) {
                        val newPaid = (bill.paidAmount - payment.amount).coerceAtLeast(0.0)
                        val newDue = (bill.amount - newPaid).coerceAtLeast(0.0)
                        val newStatus = when {
                            newDue <= 0.0 -> "PAID"
                            newPaid > 0.0 -> "PARTIAL"
                            else -> "UNPAID"
                        }
                        val updatedBill = bill.copy(
                            paidAmount = newPaid,
                            dueAmount = newDue,
                            status = newStatus,
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = 1
                        )
                        billDao.updateBill(updatedBill)
                    }
                }
            }

            // Sync deletion to Hosting API if online
            context?.let { ctx ->
                val userId = com.example.IspApplication.getUserId(ctx)
                if (userId != null) {
                    try {
                        val response = ApiClient.apiService.deletePayment(
                            id = payment.id.toString(),
                            userId = userId
                        )
                        if (!response.status) {
                            Log.w("IspRepository", "Server rejected payment deletion: ${response.message}")
                        }
                    } catch (e: Exception) {
                        Log.e("IspRepository", "Failed to delete payment via Hosting API: ${e.message}")
                    }
                }
            }

            logActivity(
                action = "PAYMENT_DELETED",
                actionType = "PAYMENT",
                details = "Deleted payment ৳${payment.amount} for ${payment.customerName}",
                targetEntity = "Payment",
                targetId = payment.id.toString(),
                previousState = "Receipt: ${payment.paymentReceiptNo}, Amount: ৳${payment.amount}"
            )

            notifyCloudSync()
            return true
        } catch (e: Exception) {
            Log.e("IspRepository", "Error deleting payment record: ${e.message}", e)
            return false
        }
    }

    suspend fun saveSettings(settings: BusinessSettingsEntity) {
        val updated = settings.copy(updatedAt = System.currentTimeMillis(), syncStatus = 1)
        settingsDao.insertOrUpdateSettings(updated)
        logActivity(
            action = "SETTINGS_EDIT",
            actionType = "SETTINGS",
            details = "Updated business settings for ${settings.ispName.ifBlank { "ISP Control Center" }}",
            targetEntity = "BusinessSettings",
            targetId = settings.id.toString(),
            newState = "ISP: ${settings.ispName}, Hotline: ${settings.hotline}"
        )
        notifyCloudSync()

        context?.let { ctx ->
            val userId = com.example.IspApplication.getUserId(ctx)
            if (userId != null) {
                try {
                    val request = SettingsRequest(
                        id = updated.id,
                        userId = userId,
                        ispName = updated.ispName,
                        hotline = updated.hotline,
                        address = updated.address,
                        currencySymbol = updated.currencySymbol,
                        networkStatus = updated.networkStatus,
                        themeMode = updated.themeMode,
                        logoUri = updated.logoUri,
                        email = updated.email,
                        updatedAt = updated.updatedAt
                    )
                    val response = ApiClient.apiService.saveSettings(request)
                    if (response.status) {
                        settingsDao.markSettingsSynced()
                    } else {
                        Log.w("IspRepository", "Server rejected settings save: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e("IspRepository", "Failed to save settings via Hosting API: ${e.message}")
                }
            }
        }
    }

    suspend fun exportDataJson(): String {
        val custs = customers.first()
        val pkgs = packages.first()
        val bls = bills.first()
        val pymts = payments.first()
        val sttngs = settings.first()
        val exps = expenses.first()
        val cats = expenseCategories.first()
        val bwBills = db.bandwidthBillDao().getAllBandwidthBillsList()
        val specAdvs = db.specificAdvanceDao().getAllSpecificAdvancesList()

        val root = JSONObject()
        val custArray = JSONArray()
        custs.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("customerCode", c.customerCode)
            obj.put("name", c.name)
            obj.put("phone", c.phone)
            obj.put("address", c.address)
            obj.put("pppoeUsername", c.pppoeUsername)
            obj.put("ipAddress", c.ipAddress)
            obj.put("packageId", c.packageId)
            obj.put("packageName", c.packageName)
            obj.put("monthlyFee", c.monthlyFee)
            obj.put("status", c.status)
            obj.put("joiningDate", c.joiningDate)
            obj.put("notes", c.notes)
            custArray.put(obj)
        }

        val pkgArray = JSONArray()
        pkgs.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("speedMbps", p.speedMbps)
            obj.put("monthlyPrice", p.monthlyPrice)
            obj.put("description", p.description)
            pkgArray.put(obj)
        }

        val expArray = JSONArray()
        exps.forEach { e ->
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("title", e.title)
            obj.put("amount", e.amount)
            obj.put("category", e.category)
            obj.put("date", e.date)
            obj.put("paymentMethod", e.paymentMethod)
            obj.put("note", e.note)
            obj.put("receiptPath", e.receiptPath ?: "")
            obj.put("createdAt", e.createdAt)
            obj.put("updatedAt", e.updatedAt)
            expArray.put(obj)
        }

        val catArray = JSONArray()
        cats.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            catArray.put(obj)
        }

        val bwArray = JSONArray()
        bwBills.forEach { b ->
            val obj = JSONObject()
            obj.put("billingMonth", b.billingMonth)
            obj.put("amount", b.amount)
            bwArray.put(obj)
        }

        val saArray = JSONArray()
        specAdvs.forEach { sa ->
            val obj = JSONObject()
            obj.put("id", sa.id)
            obj.put("customerId", sa.customerId)
            obj.put("billingMonth", sa.billingMonth)
            obj.put("amount", sa.amount)
            obj.put("isConsumed", sa.isConsumed)
            obj.put("updatedAt", sa.updatedAt)
            saArray.put(obj)
        }

        root.put("customers", custArray)
        root.put("packages", pkgArray)
        root.put("expenses", expArray)
        root.put("expenseCategories", catArray)
        root.put("bandwidthBills", bwArray)
        root.put("specificAdvances", saArray)
        root.put("billsCount", bls.size)
        root.put("paymentsCount", pymts.size)
        root.put("exportedAt", System.currentTimeMillis())
        return root.toString(2)
    }

    suspend fun importDataJson(jsonStr: String): Boolean {
        return try {
            val root = JSONObject(jsonStr)

            if (root.has("expenses")) {
                val expArray = root.getJSONArray("expenses")
                val expenseList = mutableListOf<ExpenseEntity>()
                for (i in 0 until expArray.length()) {
                    val obj = expArray.getJSONObject(i)
                    expenseList.add(
                        ExpenseEntity(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            title = obj.optString("title", ""),
                            amount = obj.optDouble("amount", 0.0),
                            category = obj.optString("category", "Other"),
                            date = obj.optString("date", ""),
                            paymentMethod = obj.optString("paymentMethod", "Cash"),
                            note = obj.optString("note", ""),
                            receiptPath = obj.optString("receiptPath", "").ifEmpty { null },
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (expenseList.isNotEmpty()) {
                    expenseDao.insertExpenses(expenseList)
                }
            }

            if (root.has("expenseCategories")) {
                val catArray = root.getJSONArray("expenseCategories")
                val catList = mutableListOf<ExpenseCategoryEntity>()
                for (i in 0 until catArray.length()) {
                    val obj = catArray.getJSONObject(i)
                    catList.add(
                        ExpenseCategoryEntity(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            name = obj.optString("name", "")
                        )
                    )
                }
                if (catList.isNotEmpty()) {
                    expenseDao.insertCategories(catList)
                }
            }

            if (root.has("bandwidthBills")) {
                val arr = root.getJSONArray("bandwidthBills")
                val bwList = mutableListOf<BandwidthBillEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val month = obj.optString("billingMonth", "")
                    val amount = obj.optDouble("amount", 0.0)
                    if (month.isNotBlank()) {
                        bwList.add(
                            BandwidthBillEntity(
                                billingMonth = month,
                                amount = amount
                            )
                        )
                    }
                }
                if (bwList.isNotEmpty()) {
                    db.bandwidthBillDao().insertOrUpdateBandwidthBills(bwList)
                }
            }

            if (root.has("specificAdvances")) {
                val arr = root.getJSONArray("specificAdvances")
                val saList = mutableListOf<SpecificAdvanceEntity>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    saList.add(
                        SpecificAdvanceEntity(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            customerId = obj.optLong("customerId", 0L),
                            billingMonth = obj.optString("billingMonth", ""),
                            amount = obj.optDouble("amount", 0.0),
                            isConsumed = obj.optBoolean("isConsumed", false),
                            updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                        )
                    )
                }
                if (saList.isNotEmpty()) {
                    db.specificAdvanceDao().insertSpecificAdvances(saList)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Automatically creates a persistent pre-update backup of all business data
     * (customers, bills, payments, settings, packages, expenses) before an app update is applied.
     */
    suspend fun createAutomaticPreUpdateBackup(context: Context): Result<File> {
        return try {
            val jsonStr = generateFullBackupJson(context)
            val pInfo = try {
                context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (e: Exception) { null }
            val verName = pInfo?.versionName ?: "1.0.17"
            val timestamp = System.currentTimeMillis()

            val backupDir = File(context.filesDir, "backups")
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            val versionedFile = File(backupDir, "pre_update_backup_v${verName}_${timestamp}.json")
            versionedFile.writeText(jsonStr, Charsets.UTF_8)

            val latestFile = File(backupDir, "latest_pre_update_backup.json")
            latestFile.writeText(jsonStr, Charsets.UTF_8)

            val extDir = context.getExternalFilesDir(null)
            if (extDir != null) {
                val extBackupDir = File(extDir, "backups")
                if (!extBackupDir.exists()) extBackupDir.mkdirs()
                val extFile = File(extBackupDir, "pre_update_backup_v${verName}_${timestamp}.json")
                extFile.writeText(jsonStr, Charsets.UTF_8)
            }

            Log.i("IspRepository", "Automatic pre-update backup created: ${versionedFile.absolutePath} (${versionedFile.length()} bytes)")
            Result.success(versionedFile)
        } catch (e: Exception) {
            Log.e("IspRepository", "Error creating automatic pre-update safety backup", e)
            Result.failure(e)
        }
    }

    suspend fun generateFullBackupJson(context: Context): String {
        val custs = kotlinx.coroutines.withTimeoutOrNull(5000L) { customers.first() } ?: emptyList()
        val pkgs = kotlinx.coroutines.withTimeoutOrNull(5000L) { packages.first() } ?: emptyList()
        val bls = kotlinx.coroutines.withTimeoutOrNull(5000L) { bills.first() } ?: emptyList()
        val pymts = kotlinx.coroutines.withTimeoutOrNull(5000L) { payments.first() } ?: emptyList()
        val sttngs = kotlinx.coroutines.withTimeoutOrNull(5000L) { settings.first() }
        val exps = kotlinx.coroutines.withTimeoutOrNull(5000L) { expenses.first() } ?: emptyList()
        val cats = kotlinx.coroutines.withTimeoutOrNull(5000L) { expenseCategories.first() } ?: emptyList()
        val bwBills = kotlinx.coroutines.withTimeoutOrNull(5000L) { bandwidthBills.first() } ?: emptyList()
        val specAdvs = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.specificAdvanceDao().getAllSpecificAdvancesList() } ?: emptyList()
        val networkDiagrams = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.networkDiagramDao().getAllDiagramsList() } ?: emptyList()
        val networkNodes = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.networkDiagramDao().getAllNodesList() } ?: emptyList()
        val networkConns = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.networkDiagramDao().getAllConnectionsList() } ?: emptyList()
        val auditLogsList = kotlinx.coroutines.withTimeoutOrNull(5000L) { db.auditLogDao().getAllAuditLogsList() } ?: emptyList()

        val sharedPrefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val appLang = sharedPrefs.getString("app_lang", "en") ?: "en"

        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("appLanguage", appLang)

        // Customers
        val custArray = JSONArray()
        custs.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("customerCode", c.customerCode)
            obj.put("name", c.name)
            obj.put("phone", c.phone)
            obj.put("address", c.address)
            obj.put("pppoeUsername", c.pppoeUsername)
            obj.put("ipAddress", c.ipAddress)
            obj.put("packageId", c.packageId)
            obj.put("packageName", c.packageName)
            obj.put("monthlyFee", c.monthlyFee)
            obj.put("status", c.status)
            obj.put("joiningDate", c.joiningDate)
            obj.put("notes", c.notes)
            obj.put("area", c.area)
            obj.put("zone", c.zone)
            obj.put("latitude", c.latitude)
            obj.put("longitude", c.longitude)
            obj.put("oltName", c.oltName)
            obj.put("ponPort", c.ponPort)
            obj.put("onuSerial", c.onuSerial)
            obj.put("routerName", c.routerName)
            obj.put("advanceBalance", c.advanceBalance)
            custArray.put(obj)
        }
        root.put("customers", custArray)

        // Packages
        val pkgArray = JSONArray()
        pkgs.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("speedMbps", p.speedMbps)
            obj.put("monthlyPrice", p.monthlyPrice)
            obj.put("description", p.description)
            pkgArray.put(obj)
        }
        root.put("packages", pkgArray)

        // Bills
        val billArray = JSONArray()
        bls.forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("billNumber", b.billNumber)
            obj.put("customerId", b.customerId)
            obj.put("customerName", b.customerName)
            obj.put("customerCode", b.customerCode)
            obj.put("billingMonth", b.billingMonth)
            obj.put("amount", b.amount)
            obj.put("paidAmount", b.paidAmount)
            obj.put("dueAmount", b.dueAmount)
            obj.put("status", b.status)
            obj.put("generatedDate", b.generatedDate)
            obj.put("dueDate", b.dueDate)
            billArray.put(obj)
        }
        root.put("bills", billArray)

        // Payments
        val paymentArray = JSONArray()
        pymts.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("paymentReceiptNo", p.paymentReceiptNo)
            obj.put("billId", p.billId)
            obj.put("customerId", p.customerId)
            obj.put("customerName", p.customerName)
            obj.put("amount", p.amount)
            obj.put("paymentDate", p.paymentDate)
            obj.put("paymentMethod", p.paymentMethod)
            obj.put("notes", p.notes)
            paymentArray.put(obj)
        }
        root.put("payments", paymentArray)

        // Expenses
        val expArray = JSONArray()
        exps.forEach { e ->
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("title", e.title)
            obj.put("amount", e.amount)
            obj.put("category", e.category)
            obj.put("date", e.date)
            obj.put("paymentMethod", e.paymentMethod)
            obj.put("note", e.note)
            obj.put("receiptPath", e.receiptPath ?: "")
            obj.put("createdAt", e.createdAt)
            obj.put("updatedAt", e.updatedAt)
            expArray.put(obj)
        }
        root.put("expenses", expArray)

        // Categories
        val catArray = JSONArray()
        cats.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            catArray.put(obj)
        }
        root.put("expenseCategories", catArray)

        // Bandwidth Bills
        val bwArray = JSONArray()
        bwBills.forEach { b ->
            val obj = JSONObject()
            obj.put("billingMonth", b.billingMonth)
            obj.put("amount", b.amount)
            bwArray.put(obj)
        }
        root.put("bandwidthBills", bwArray)

        // Specific Advances
        val saArray = JSONArray()
        specAdvs.forEach { sa ->
            val obj = JSONObject()
            obj.put("id", sa.id)
            obj.put("customerId", sa.customerId)
            obj.put("billingMonth", sa.billingMonth)
            obj.put("amount", sa.amount)
            obj.put("isConsumed", sa.isConsumed)
            obj.put("updatedAt", sa.updatedAt)
            saArray.put(obj)
        }
        root.put("specificAdvances", saArray)

        // Business Settings
        if (sttngs != null) {
            val settObj = JSONObject()
            settObj.put("id", sttngs.id)
            settObj.put("ispName", sttngs.ispName)
            settObj.put("hotline", sttngs.hotline)
            settObj.put("address", sttngs.address)
            settObj.put("currencySymbol", sttngs.currencySymbol)
            settObj.put("networkStatus", sttngs.networkStatus)
            settObj.put("themeMode", sttngs.themeMode)
            settObj.put("logoUri", sttngs.logoUri ?: "")
            settObj.put("email", sttngs.email)
            root.put("settings", settObj)
        }

        // Network Diagrams
        val diagArray = JSONArray()
        networkDiagrams.forEach { d ->
            val obj = JSONObject()
            obj.put("id", d.id)
            obj.put("name", d.name)
            obj.put("isDefault", d.isDefault)
            obj.put("createdAt", d.createdAt)
            obj.put("updatedAt", d.updatedAt)
            diagArray.put(obj)
        }
        root.put("networkDiagrams", diagArray)

        // Network Nodes
        val nodeArray = JSONArray()
        networkNodes.forEach { n ->
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("diagramId", n.diagramId)
            obj.put("name", n.name)
            obj.put("type", n.type)
            obj.put("ipAddress", n.ipAddress)
            obj.put("location", n.location)
            obj.put("areaZone", n.areaZone)
            obj.put("portInfo", n.portInfo)
            obj.put("customerRef", n.customerRef)
            obj.put("customerId", n.customerId)
            obj.put("notes", n.notes)
            obj.put("positionX", n.positionX.toDouble())
            obj.put("positionY", n.positionY.toDouble())
            obj.put("updatedAt", n.updatedAt)
            nodeArray.put(obj)
        }
        root.put("networkNodes", nodeArray)

        // Network Connections
        val connArray = JSONArray()
        networkConns.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("diagramId", c.diagramId)
            obj.put("fromNodeId", c.fromNodeId)
            obj.put("toNodeId", c.toNodeId)
            obj.put("label", c.label)
            obj.put("notes", c.notes)
            obj.put("updatedAt", c.updatedAt)
            connArray.put(obj)
        }
        root.put("networkConnections", connArray)

        // Audit Logs
        val logArray = JSONArray()
        auditLogsList.forEach { l ->
            val obj = JSONObject()
            obj.put("id", l.id)
            obj.put("action", l.action)
            obj.put("actionType", l.actionType)
            obj.put("details", l.details)
            obj.put("userEmail", l.userEmail)
            obj.put("userRole", l.userRole)
            obj.put("targetEntity", l.targetEntity)
            obj.put("targetId", l.targetId)
            obj.put("previousState", l.previousState)
            obj.put("newState", l.newState)
            obj.put("status", l.status)
            obj.put("timestamp", l.timestamp)
            logArray.put(obj)
        }
        root.put("auditLogs", logArray)

        return root.toString(2)
    }

    suspend fun restoreFromFullBackupJson(context: Context, jsonStr: String): Boolean {
        // Step 1: Create local safety backup string before modifying existing database
        val safetyBackupJson = generateFullBackupJson(context)
        val safetyFile = java.io.File(context.filesDir, "safety_backup_before_restore.json")
        try {
            safetyFile.writeText(safetyBackupJson, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return try {
            val root = JSONObject(jsonStr)

            fun optJsonLong(obj: JSONObject, key: String, defaultIdx: Int): Long {
                if (obj.has(key) && !obj.isNull(key)) {
                    val v = obj.get(key)
                    val parsed = when (v) {
                        is Number -> v.toLong()
                        is String -> v.toLongOrNull() ?: v.filter { it.isDigit() }.toLongOrNull()
                        else -> null
                    }
                    if (parsed != null && parsed != 0L) return parsed
                }
                return (defaultIdx + 1000).toLong()
            }

            val customerList = mutableListOf<CustomerEntity>()
            if (root.has("customers")) {
                val arr = root.getJSONArray("customers")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val custId = optJsonLong(obj, "id", i)
                    customerList.add(
                        CustomerEntity(
                            id = custId,
                            customerCode = obj.optString("customerCode", "CUST-$custId"),
                            name = obj.optString("name", ""),
                            phone = obj.optString("phone", ""),
                            address = obj.optString("address", ""),
                            pppoeUsername = obj.optString("pppoeUsername", ""),
                            ipAddress = obj.optString("ipAddress", ""),
                            packageId = optJsonLong(obj, "packageId", 0),
                            packageName = obj.optString("packageName", ""),
                            monthlyFee = obj.optDouble("monthlyFee", 0.0),
                            status = obj.optString("status", "ACTIVE"),
                            joiningDate = obj.optString("joiningDate", ""),
                            notes = obj.optString("notes", ""),
                            area = obj.optString("area", ""),
                            zone = obj.optString("zone", ""),
                            latitude = obj.optDouble("latitude", 0.0),
                            longitude = obj.optDouble("longitude", 0.0),
                            oltName = obj.optString("oltName", ""),
                            ponPort = obj.optString("ponPort", ""),
                            onuSerial = obj.optString("onuSerial", ""),
                            routerName = obj.optString("routerName", ""),
                            advanceBalance = obj.optDouble("advanceBalance", 0.0),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = 1
                        )
                    )
                }
            }

            val custCodeMap = customerList.associate { it.customerCode.trim().lowercase(java.util.Locale.ROOT) to it.id }
            val custNameMap = customerList.associate { it.name.trim().lowercase(java.util.Locale.ROOT) to it.id }

            val packageList = mutableListOf<IspPackageEntity>()
            if (root.has("packages")) {
                val arr = root.getJSONArray("packages")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    packageList.add(
                        IspPackageEntity(
                            id = optJsonLong(obj, "id", i),
                            name = obj.optString("name", ""),
                            speedMbps = obj.optInt("speedMbps", 0),
                            monthlyPrice = obj.optDouble("monthlyPrice", 0.0),
                            description = obj.optString("description", ""),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = 1
                        )
                    )
                }
            }

            val billList = mutableListOf<BillEntity>()
            if (root.has("bills")) {
                val arr = root.getJSONArray("bills")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val billId = optJsonLong(obj, "id", i)
                    val rawCustId = optJsonLong(obj, "customerId", -1)
                    val cCode = obj.optString("customerCode", "")
                    val cName = obj.optString("customerName", "")
                    val resolvedCustId = if (rawCustId > 0L && customerList.any { it.id == rawCustId }) {
                        rawCustId
                    } else {
                        custCodeMap[cCode.trim().lowercase(java.util.Locale.ROOT)]
                            ?: custNameMap[cName.trim().lowercase(java.util.Locale.ROOT)]
                            ?: if (rawCustId > 0L) rawCustId else 0L
                    }

                    billList.add(
                        BillEntity(
                            id = billId,
                            billNumber = obj.optString("billNumber", ""),
                            customerId = resolvedCustId,
                            customerName = cName,
                            customerCode = cCode,
                            billingMonth = obj.optString("billingMonth", ""),
                            amount = obj.optDouble("amount", 0.0),
                            paidAmount = obj.optDouble("paidAmount", 0.0),
                            dueAmount = obj.optDouble("dueAmount", 0.0),
                            status = obj.optString("status", "UNPAID"),
                            generatedDate = obj.optString("generatedDate", ""),
                            dueDate = obj.optString("dueDate", ""),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = 1
                        )
                    )
                }
            }

            val paymentList = mutableListOf<PaymentEntity>()
            if (root.has("payments")) {
                val arr = root.getJSONArray("payments")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val payId = optJsonLong(obj, "id", i)
                    val rawCustId = optJsonLong(obj, "customerId", -1)
                    val cName = obj.optString("customerName", "")
                    val resolvedCustId = if (rawCustId > 0L && customerList.any { it.id == rawCustId }) {
                        rawCustId
                    } else {
                        custNameMap[cName.trim().lowercase(java.util.Locale.ROOT)] ?: if (rawCustId > 0L) rawCustId else 0L
                    }

                    paymentList.add(
                        PaymentEntity(
                            id = payId,
                            paymentReceiptNo = obj.optString("paymentReceiptNo", ""),
                            billId = optJsonLong(obj, "billId", 0),
                            customerId = resolvedCustId,
                            customerName = cName,
                            amount = obj.optDouble("amount", 0.0),
                            paymentDate = obj.optString("paymentDate", ""),
                            paymentMethod = obj.optString("paymentMethod", "Cash"),
                            notes = obj.optString("notes", ""),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = 1
                        )
                    )
                }
            }

            val expenseList = mutableListOf<ExpenseEntity>()
            if (root.has("expenses")) {
                val arr = root.getJSONArray("expenses")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    expenseList.add(
                        ExpenseEntity(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            title = obj.optString("title", ""),
                            amount = obj.optDouble("amount", 0.0),
                            category = obj.optString("category", "Other"),
                            date = obj.optString("date", ""),
                            paymentMethod = obj.optString("paymentMethod", "Cash"),
                            note = obj.optString("note", ""),
                            receiptPath = obj.optString("receiptPath", "").ifEmpty { null },
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = 1
                        )
                    )
                }
            }

            val categoryList = mutableListOf<ExpenseCategoryEntity>()
            if (root.has("expenseCategories")) {
                val arr = root.getJSONArray("expenseCategories")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    categoryList.add(
                        ExpenseCategoryEntity(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            name = obj.optString("name", ""),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = 1
                        )
                    )
                }
            }

            val bandwidthBillList = mutableListOf<BandwidthBillEntity>()
            if (root.has("bandwidthBills")) {
                val arr = root.getJSONArray("bandwidthBills")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val month = obj.optString("billingMonth", "")
                    val amount = obj.optDouble("amount", 0.0)
                    if (month.isNotBlank()) {
                        bandwidthBillList.add(
                            BandwidthBillEntity(
                                billingMonth = month,
                                amount = amount,
                                updatedAt = System.currentTimeMillis(),
                                syncStatus = 1
                            )
                        )
                    }
                }
            }

            val specificAdvanceList = mutableListOf<SpecificAdvanceEntity>()
            if (root.has("specificAdvances")) {
                val arr = root.getJSONArray("specificAdvances")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    specificAdvanceList.add(
                        SpecificAdvanceEntity(
                            id = if (obj.has("id")) obj.getLong("id") else 0L,
                            customerId = obj.optLong("customerId", 0L),
                            billingMonth = obj.optString("billingMonth", ""),
                            amount = obj.optDouble("amount", 0.0),
                            isConsumed = obj.optBoolean("isConsumed", false),
                            updatedAt = System.currentTimeMillis(),
                            syncStatus = 1
                        )
                    )
                }
            }

            var settingsObj: BusinessSettingsEntity? = null
            if (root.has("settings")) {
                val obj = root.getJSONObject("settings")
                settingsObj = BusinessSettingsEntity(
                    id = if (obj.has("id")) obj.getInt("id") else 1,
                    ispName = obj.optString("ispName", ""),
                    hotline = obj.optString("hotline", ""),
                    address = obj.optString("address", ""),
                    currencySymbol = obj.optString("currencySymbol", "৳"),
                    networkStatus = obj.optString("networkStatus", "Operational"),
                    themeMode = obj.optString("themeMode", "SYSTEM"),
                    logoUri = obj.optString("logoUri", "").ifEmpty { null },
                    email = obj.optString("email", ""),
                    updatedAt = System.currentTimeMillis(),
                    syncStatus = 1
                )
            }

            val logList = mutableListOf<AuditLogEntity>()
            if (root.has("auditLogs")) {
                val arr = root.getJSONArray("auditLogs")
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    logList.add(
                        AuditLogEntity(
                            id = optJsonLong(obj, "id", i),
                            action = obj.optString("action", ""),
                            actionType = obj.optString("actionType", ""),
                            details = obj.optString("details", ""),
                            userEmail = obj.optString("userEmail", ""),
                            userRole = obj.optString("userRole", "Admin"),
                            targetEntity = obj.optString("targetEntity", ""),
                            targetId = obj.optString("targetId", ""),
                            previousState = obj.optString("previousState", ""),
                            newState = obj.optString("newState", ""),
                            status = obj.optString("status", "SUCCESS"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            syncStatus = 0
                        )
                    )
                }
            }

            db.withTransaction {
                customerDao.deleteAllCustomers()
                packageDao.deleteAllPackages()
                billDao.deleteAllBills()
                paymentDao.deleteAllPayments()
                expenseDao.deleteAllExpenses()
                expenseDao.deleteAllCategories()
                db.bandwidthBillDao().deleteAllBandwidthBills()
                db.specificAdvanceDao().deleteAllSpecificAdvances()
                settingsDao.deleteSettings()
                db.pendingDeletionDao().clearAllPendingDeletions()

                if (logList.isNotEmpty() || root.has("auditLogs")) {
                    db.auditLogDao().deleteAllLogs()
                }

                if (customerList.isNotEmpty()) customerDao.insertCustomers(customerList)
                if (packageList.isNotEmpty()) packageDao.insertPackages(packageList)
                if (billList.isNotEmpty()) billDao.insertBills(billList)
                if (paymentList.isNotEmpty()) paymentDao.insertPayments(paymentList)
                if (expenseList.isNotEmpty()) expenseDao.insertExpenses(expenseList)
                if (categoryList.isNotEmpty()) expenseDao.insertCategories(categoryList)
                if (bandwidthBillList.isNotEmpty()) db.bandwidthBillDao().insertOrUpdateBandwidthBills(bandwidthBillList)
                if (specificAdvanceList.isNotEmpty()) db.specificAdvanceDao().insertSpecificAdvances(specificAdvanceList)
                if (settingsObj != null) settingsDao.insertOrUpdateSettings(settingsObj)
                if (logList.isNotEmpty()) db.auditLogDao().insertLogs(logList)
            }

            if (root.has("appLanguage")) {
                val lang = root.getString("appLanguage")
                if (lang == "en" || lang == "bn") {
                    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit().putString("app_lang", lang).apply()
                }
            }

            try {
                if (com.example.util.HostingSyncManager.isNetworkAvailable(context)) {
                    val uid = com.example.IspApplication.getUserId(context)
                    if (!uid.isNullOrBlank()) {
                        val exportedAt = root.optLong("exportedAt", 0L)
                        if (exportedAt > 0L) {
                            context.getSharedPreferences("isp_hosting_sync", Context.MODE_PRIVATE)
                                .edit()
                                .putLong("last_sync_time_$uid", exportedAt)
                                .apply()
                        }
                    }
                    com.example.util.HostingSyncManager.syncLocalToHosting(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            if (safetyFile.exists()) {
                try {
                    val safetyJson = safetyFile.readText(Charsets.UTF_8)
                    restoreFromSafetyBackupJson(safetyJson)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            false
        }
    }

    suspend fun backupToHosting(context: Context, userId: String): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (userId.isBlank()) {
                return@withContext Pair(false, "Authentication required")
            }

            // Step 1 & 2: Sync dirty local records to active MySQL tables first
            val syncSuccess = try {
                com.example.util.HostingSyncManager.syncLocalToHosting(context)
            } catch (ex: Exception) {
                Log.e("IspRepository", "Hosting sync during backup failed: ${ex.message}")
                false
            }

            // Step 3: Check if live sync succeeded
            if (!syncSuccess) {
                return@withContext Pair(false, "Live hosting synchronization failed. Backup aborted.")
            }

            // Step 4: After confirmed live sync success, generate snapshot and upload to cloud_backups
            val jsonPayload = generateFullBackupJson(context)
            if (jsonPayload.isBlank()) {
                return@withContext Pair(false, "No local data to back up")
            }
            val timeStamp = java.text.SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", java.util.Locale.US).format(java.util.Date())
            val backupName = "ISP-Cloud-Backup-$timeStamp"
            val request = com.example.data.model.CloudBackupRequest(
                userId = userId,
                backupName = backupName,
                backupData = jsonPayload,
                version = 1
            )
            val response = ApiClient.apiService.saveCloudBackup(request)
            if (response.status) {
                // Update the last cloud sync time and pending sync count so the UI updates immediately
                try {
                    val remainingDirty = com.example.util.HostingSyncManager.getActualPendingDirtyCount(context)
                    context.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putLong("last_cloud_sync_time_$userId", System.currentTimeMillis())
                        .putInt("pending_sync_count_$userId", remainingDirty)
                        .apply()
                } catch (ex: Exception) {
                    Log.e("IspRepository", "Updating sync preferences failed: ${ex.message}")
                }

                Pair(true, response.message ?: "Cloud backup successful")
            } else {
                Pair(false, response.message ?: "Cloud backup failed")
            }
        } catch (e: Exception) {
            Log.e("IspRepository", "Backup to Hosting failed: ${e.message}", e)
            Pair(false, e.localizedMessage ?: e.message ?: "Cloud backup failed")
        }
    }

    suspend fun restoreFromHosting(context: Context, userId: String): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (userId.isBlank()) {
                return@withContext Pair(false, "Authentication required")
            }
            val response = ApiClient.apiService.getLatestCloudBackup(userId = userId, action = "latest")
            if (!response.status || response.data == null) {
                return@withContext Pair(false, response.message ?: "No cloud backup found on Hosting")
            }
            val backupData = response.data.backupData
            if (backupData.isNullOrBlank()) {
                return@withContext Pair(false, "Retrieved cloud backup payload is empty")
            }
            val restored = restoreFromFullBackupJson(context, backupData)
            if (restored) {
                Pair(true, "Cloud backup restored successfully")
            } else {
                Pair(false, "Failed to restore cloud backup data")
            }
        } catch (e: Exception) {
            Log.e("IspRepository", "Restore from Hosting failed: ${e.message}", e)
            Pair(false, e.localizedMessage ?: e.message ?: "Cloud restore failed")
        }
    }

    private suspend fun restoreFromSafetyBackupJson(jsonStr: String) {
        val root = JSONObject(jsonStr)
        val customerList = mutableListOf<CustomerEntity>()
        if (root.has("customers")) {
            val arr = root.getJSONArray("customers")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                customerList.add(
                    CustomerEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        customerCode = obj.optString("customerCode", ""),
                        name = obj.optString("name", ""),
                        phone = obj.optString("phone", ""),
                        address = obj.optString("address", ""),
                        pppoeUsername = obj.optString("pppoeUsername", ""),
                        ipAddress = obj.optString("ipAddress", ""),
                        packageId = obj.optLong("packageId", 0L),
                        packageName = obj.optString("packageName", ""),
                        monthlyFee = obj.optDouble("monthlyFee", 0.0),
                        status = obj.optString("status", "ACTIVE"),
                        joiningDate = obj.optString("joiningDate", ""),
                        notes = obj.optString("notes", ""),
                        area = obj.optString("area", ""),
                        zone = obj.optString("zone", ""),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        oltName = obj.optString("oltName", ""),
                        ponPort = obj.optString("ponPort", ""),
                        onuSerial = obj.optString("onuSerial", ""),
                        routerName = obj.optString("routerName", ""),
                        advanceBalance = obj.optDouble("advanceBalance", 0.0)
                    )
                )
            }
        }
        val packageList = mutableListOf<IspPackageEntity>()
        if (root.has("packages")) {
            val arr = root.getJSONArray("packages")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                packageList.add(
                    IspPackageEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        name = obj.optString("name", ""),
                        speedMbps = obj.optInt("speedMbps", 0),
                        monthlyPrice = obj.optDouble("monthlyPrice", 0.0),
                        description = obj.optString("description", "")
                    )
                )
            }
        }
        val billList = mutableListOf<BillEntity>()
        if (root.has("bills")) {
            val arr = root.getJSONArray("bills")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                billList.add(
                    BillEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        billNumber = obj.optString("billNumber", ""),
                        customerId = obj.optLong("customerId", 0L),
                        customerName = obj.optString("customerName", ""),
                        customerCode = obj.optString("customerCode", ""),
                        billingMonth = obj.optString("billingMonth", ""),
                        amount = obj.optDouble("amount", 0.0),
                        paidAmount = obj.optDouble("paidAmount", 0.0),
                        dueAmount = obj.optDouble("dueAmount", 0.0),
                        status = obj.optString("status", "UNPAID"),
                        generatedDate = obj.optString("generatedDate", ""),
                        dueDate = obj.optString("dueDate", "")
                    )
                )
            }
        }
        val paymentList = mutableListOf<PaymentEntity>()
        if (root.has("payments")) {
            val arr = root.getJSONArray("payments")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                paymentList.add(
                    PaymentEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        paymentReceiptNo = obj.optString("paymentReceiptNo", ""),
                        billId = obj.optLong("billId", 0L),
                        customerId = obj.optLong("customerId", 0L),
                        customerName = obj.optString("customerName", ""),
                        amount = obj.optDouble("amount", 0.0),
                        paymentDate = obj.optString("paymentDate", ""),
                        paymentMethod = obj.optString("paymentMethod", "Cash"),
                        notes = obj.optString("notes", "")
                    )
                )
            }
        }
        val expenseList = mutableListOf<ExpenseEntity>()
        if (root.has("expenses")) {
            val arr = root.getJSONArray("expenses")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                expenseList.add(
                    ExpenseEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        title = obj.optString("title", ""),
                        amount = obj.optDouble("amount", 0.0),
                        category = obj.optString("category", "Other"),
                        date = obj.optString("date", ""),
                        paymentMethod = obj.optString("paymentMethod", "Cash"),
                        note = obj.optString("note", ""),
                        receiptPath = obj.optString("receiptPath", "").ifEmpty { null },
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
        val categoryList = mutableListOf<ExpenseCategoryEntity>()
        if (root.has("expenseCategories")) {
            val arr = root.getJSONArray("expenseCategories")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                categoryList.add(
                    ExpenseCategoryEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        name = obj.optString("name", "")
                    )
                )
            }
        }
        val bandwidthBillList = mutableListOf<BandwidthBillEntity>()
        if (root.has("bandwidthBills")) {
            val arr = root.getJSONArray("bandwidthBills")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val month = obj.optString("billingMonth", "")
                val amount = obj.optDouble("amount", 0.0)
                if (month.isNotBlank()) {
                    bandwidthBillList.add(
                        BandwidthBillEntity(
                            billingMonth = month,
                            amount = amount
                        )
                    )
                }
            }
        }
        val specificAdvanceList = mutableListOf<SpecificAdvanceEntity>()
        if (root.has("specificAdvances")) {
            val arr = root.getJSONArray("specificAdvances")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                specificAdvanceList.add(
                    SpecificAdvanceEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        customerId = obj.optLong("customerId", 0L),
                        billingMonth = obj.optString("billingMonth", ""),
                        amount = obj.optDouble("amount", 0.0),
                        isConsumed = obj.optBoolean("isConsumed", false),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
        var settingsObj: BusinessSettingsEntity? = null
        if (root.has("settings")) {
            val obj = root.getJSONObject("settings")
            settingsObj = BusinessSettingsEntity(
                id = if (obj.has("id")) obj.getInt("id") else 1,
                ispName = obj.optString("ispName", ""),
                hotline = obj.optString("hotline", ""),
                address = obj.optString("address", ""),
                currencySymbol = obj.optString("currencySymbol", "৳"),
                networkStatus = obj.optString("networkStatus", "Operational"),
                themeMode = obj.optString("themeMode", "SYSTEM"),
                logoUri = obj.optString("logoUri", "").ifEmpty { null },
                email = obj.optString("email", "")
            )
        }

        val diagramList = mutableListOf<NetworkDiagramEntity>()
        if (root.has("networkDiagrams")) {
            val arr = root.getJSONArray("networkDiagrams")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                diagramList.add(
                    NetworkDiagramEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        name = obj.optString("name", ""),
                        isDefault = obj.optBoolean("isDefault", false),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                )
            }
        }

        val nodeList = mutableListOf<NetworkNodeEntity>()
        if (root.has("networkNodes")) {
            val arr = root.getJSONArray("networkNodes")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                nodeList.add(
                    NetworkNodeEntity(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        diagramId = obj.optLong("diagramId", 0L),
                        name = obj.optString("name", ""),
                        type = obj.optString("type", "MIKROTIK"),
                        ipAddress = obj.optString("ipAddress", ""),
                        location = obj.optString("location", ""),
                        areaZone = obj.optString("areaZone", ""),
                        portInfo = obj.optString("portInfo", ""),
                        customerRef = obj.optString("customerRef", ""),
                        customerId = obj.optString("customerId", ""),
                        notes = obj.optString("notes", ""),
                        positionX = obj.optDouble("positionX", 0.0).toFloat(),
                        positionY = obj.optDouble("positionY", 0.0).toFloat(),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                )
            }
        }

        val connList = mutableListOf<NetworkConnectionEntity>()
        if (root.has("networkConnections")) {
            val arr = root.getJSONArray("networkConnections")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                connList.add(
                    NetworkConnectionEntity(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        diagramId = obj.optLong("diagramId", 0L),
                        fromNodeId = obj.optString("fromNodeId", ""),
                        toNodeId = obj.optString("toNodeId", ""),
                        label = obj.optString("label", ""),
                        notes = obj.optString("notes", ""),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                )
            }
        }

        val logList = mutableListOf<AuditLogEntity>()
        if (root.has("auditLogs")) {
            val arr = root.getJSONArray("auditLogs")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                logList.add(
                    AuditLogEntity(
                        id = if (obj.has("id")) obj.getLong("id") else 0L,
                        action = obj.optString("action", ""),
                        actionType = obj.optString("actionType", ""),
                        details = obj.optString("details", ""),
                        userEmail = obj.optString("userEmail", ""),
                        userRole = obj.optString("userRole", "Admin"),
                        targetEntity = obj.optString("targetEntity", ""),
                        targetId = obj.optString("targetId", ""),
                        previousState = obj.optString("previousState", ""),
                        newState = obj.optString("newState", ""),
                        status = obj.optString("status", "SUCCESS"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        syncStatus = 0
                    )
                )
            }
        }

        db.withTransaction {
            customerDao.deleteAllCustomers()
            packageDao.deleteAllPackages()
            billDao.deleteAllBills()
            paymentDao.deleteAllPayments()
            expenseDao.deleteAllExpenses()
            expenseDao.deleteAllCategories()
            db.bandwidthBillDao().deleteAllBandwidthBills()
            db.specificAdvanceDao().deleteAllSpecificAdvances()
            settingsDao.deleteSettings()

            if (diagramList.isNotEmpty() || root.has("networkDiagrams")) {
                db.networkDiagramDao().deleteAllDiagrams()
                db.networkDiagramDao().deleteAllNodes()
                db.networkDiagramDao().deleteAllConnections()
            }
            if (logList.isNotEmpty() || root.has("auditLogs")) {
                db.auditLogDao().deleteAllLogs()
            }

            if (customerList.isNotEmpty()) customerDao.insertCustomers(customerList)
            if (packageList.isNotEmpty()) packageDao.insertPackages(packageList)
            if (billList.isNotEmpty()) billDao.insertBills(billList)
            if (paymentList.isNotEmpty()) paymentDao.insertPayments(paymentList)
            if (expenseList.isNotEmpty()) expenseDao.insertExpenses(expenseList)
            if (categoryList.isNotEmpty()) expenseDao.insertCategories(categoryList)
            if (bandwidthBillList.isNotEmpty()) db.bandwidthBillDao().insertOrUpdateBandwidthBills(bandwidthBillList)
            if (specificAdvanceList.isNotEmpty()) db.specificAdvanceDao().insertSpecificAdvances(specificAdvanceList)
            if (settingsObj != null) settingsDao.insertOrUpdateSettings(settingsObj)
            if (diagramList.isNotEmpty()) diagramList.forEach { db.networkDiagramDao().insertDiagram(it) }
            if (nodeList.isNotEmpty()) db.networkDiagramDao().insertNodes(nodeList)
            if (connList.isNotEmpty()) db.networkDiagramDao().insertConnections(connList)
            if (logList.isNotEmpty()) db.auditLogDao().insertLogs(logList)
        }
    }

    suspend fun clearAllLocalData() {
        db.withTransaction {
            customerDao.deleteAllCustomers()
            packageDao.deleteAllPackages()
            billDao.deleteAllBills()
            paymentDao.deleteAllPayments()
            expenseDao.deleteAllExpenses()
            expenseDao.deleteAllCategories()
            db.bandwidthBillDao().deleteAllBandwidthBills()
            db.specificAdvanceDao().deleteAllSpecificAdvances()
            settingsDao.deleteSettings()
            db.networkDiagramDao().deleteAllDiagrams()
            db.networkDiagramDao().deleteAllNodes()
            db.networkDiagramDao().deleteAllConnections()
            db.auditLogDao().deleteAllLogs()
        }
    }

    // Network Diagram helper methods
    fun getNodesForDiagram(diagramId: Long): Flow<List<NetworkNodeEntity>> =
        networkDiagramDao.getNodesForDiagram(diagramId)

    fun getConnectionsForDiagram(diagramId: Long): Flow<List<NetworkConnectionEntity>> =
        networkDiagramDao.getConnectionsForDiagram(diagramId)

    suspend fun getOrCreateDefaultDiagram(): NetworkDiagramEntity {
        val existing = networkDiagramDao.getAllDiagramsList()
        if (existing.isNotEmpty()) {
            return existing.first()
        }
        val defaultDiag = NetworkDiagramEntity(
            id = generateUniqueId(),
            name = "Default Network Topology",
            isDefault = true
        )
        val id = networkDiagramDao.insertDiagram(defaultDiag)
        return defaultDiag.copy(id = id)
    }

    suspend fun createNewDiagram(name: String): Long {
        val now = System.currentTimeMillis()
        val diag = NetworkDiagramEntity(id = generateUniqueId(), name = name.ifBlank { "Network Topology" }, createdAt = now, updatedAt = now, syncStatus = 1)
        val id = networkDiagramDao.insertDiagram(diag)
        logActivity(
            action = "NETWORK_DIAGRAM_CREATE",
            actionType = "NETWORK",
            details = "Created network diagram: ${diag.name}",
            targetEntity = "NetworkDiagram",
            targetId = id.toString()
        )
        notifyCloudSync()
        return id
    }

    suspend fun saveNode(node: NetworkNodeEntity) {
        val updated = node.copy(updatedAt = System.currentTimeMillis(), syncStatus = 1)
        networkDiagramDao.insertNode(updated)
        logActivity(
            action = "NETWORK_DIAGRAM_EDIT",
            actionType = "NETWORK",
            details = "Saved network device: ${node.name} (${node.type})",
            targetEntity = "NetworkNode",
            targetId = node.id
        )
        notifyCloudSync()
    }

    suspend fun updateNodePosition(nodeId: String, x: Float, y: Float) {
        networkDiagramDao.updateNodePosition(nodeId, x, y)
        networkDiagramDao.updateNodeSyncStatus(nodeId, 1)
        notifyCloudSync()
    }

    suspend fun deleteNode(nodeId: String) {
        networkDiagramDao.deleteConnectionsForNode(nodeId)
        networkDiagramDao.deleteNodeById(nodeId)
        logActivity(
            action = "NETWORK_DIAGRAM_EDIT",
            actionType = "NETWORK",
            details = "Deleted network node #$nodeId",
            targetEntity = "NetworkNode",
            targetId = nodeId
        )
        notifyCloudSync()
    }

    suspend fun saveConnection(connection: NetworkConnectionEntity) {
        val updated = connection.copy(updatedAt = System.currentTimeMillis(), syncStatus = 1)
        networkDiagramDao.insertConnection(updated)
        logActivity(
            action = "NETWORK_DIAGRAM_EDIT",
            actionType = "NETWORK",
            details = "Connected network nodes ${connection.fromNodeId} ➔ ${connection.toNodeId}",
            targetEntity = "NetworkConnection",
            targetId = connection.id
        )
        notifyCloudSync()
    }

    suspend fun deleteConnection(connectionId: String) {
        networkDiagramDao.deleteConnectionById(connectionId)
        logActivity(
            action = "NETWORK_DIAGRAM_EDIT",
            actionType = "NETWORK",
            details = "Deleted network connection #$connectionId",
            targetEntity = "NetworkConnection",
            targetId = connectionId
        )
        notifyCloudSync()
    }

    suspend fun clearDiagram(diagramId: Long) {
        networkDiagramDao.clearDiagram(diagramId)
        logActivity(
            action = "NETWORK_DIAGRAM_DELETE",
            actionType = "NETWORK",
            details = "Cleared network diagram #$diagramId topology",
            targetEntity = "NetworkDiagram",
            targetId = diagramId.toString()
        )
        notifyCloudSync()
    }

    // Reusable safe API helper for GET operations returning Flow<Resource<T>>
    private fun <T> safeApiCall(apiCall: suspend () -> ApiResponse<T>): Flow<Resource<T>> = flow {
        emit(Resource.Loading)
        try {
            val response = apiCall()
            if (response.status && response.data != null) {
                emit(Resource.Success(response.data))
            } else {
                emit(Resource.Error(response.message ?: "Failed to read data from server"))
            }
        } catch (e: IOException) {
            emit(Resource.Error("No internet connection or network failure"))
        } catch (e: HttpException) {
            emit(Resource.Error("Server returned error (HTTP ${e.code()})"))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "Unknown network communication error"))
        }
    }.flowOn(Dispatchers.IO)

    // Reusable safe API helper for POST/save operations returning Flow<Resource<Unit>>
    private fun safeSaveCall(apiCall: suspend () -> ApiResponse<Unit>): Flow<Resource<Unit>> = flow {
        emit(Resource.Loading)
        try {
            val response = apiCall()
            if (response.status) {
                emit(Resource.Success(Unit))
            } else {
                emit(Resource.Error(response.message ?: "Server rejected creation request"))
            }
        } catch (e: IOException) {
            emit(Resource.Error("No internet connection or network failure"))
        } catch (e: HttpException) {
            emit(Resource.Error("Server returned error (HTTP ${e.code()})"))
        } catch (e: Exception) {
            emit(Resource.Error(e.localizedMessage ?: "Post request failed with network error"))
        }
    }.flowOn(Dispatchers.IO)

    // REST API endpoints wrappers
    fun getCustomers(userId: String): Flow<Resource<List<Customer>>> {
        return safeApiCall { ApiClient.apiService.getCustomers(userId) }
    }

    fun addCustomer(request: AddCustomerRequest): Flow<Resource<Unit>> {
        return safeSaveCall { ApiClient.apiService.saveCustomer(request) }
    }

    fun getPackages(userId: String): Flow<Resource<List<PackageModel>>> {
        return safeApiCall { ApiClient.apiService.getPackages(userId) }
    }

    fun getBills(userId: String): Flow<Resource<List<BillModel>>> {
        return safeApiCall { ApiClient.apiService.getBills(userId) }
    }

    fun getPayments(userId: String): Flow<Resource<List<PaymentModel>>> {
        return safeApiCall { ApiClient.apiService.getPayments(userId) }
    }

    fun getExpenses(userId: String, category: String? = null): Flow<Resource<List<ExpenseModel>>> {
        return safeApiCall { ApiClient.apiService.getExpenses(userId, category) }
    }

    fun getExpenseCategories(userId: String): Flow<Resource<List<ExpenseCategoryModel>>> {
        return safeApiCall { ApiClient.apiService.getExpenseCategories(userId) }
    }

    fun getSettings(userId: String): Flow<Resource<SettingsModel>> {
        return safeApiCall { ApiClient.apiService.getSettings(userId) }
    }

    fun getAuditLogs(userId: String, actionType: String? = null, limit: Int? = null): Flow<Resource<List<AuditLogModel>>> {
        return safeApiCall { ApiClient.apiService.getAuditLogs(userId, actionType, limit) }
    }

    fun getBandwidthBills(userId: String, billingMonth: String? = null): Flow<Resource<List<BandwidthBillModel>>> {
        return safeApiCall { ApiClient.apiService.getBandwidthBills(userId, billingMonth) }
    }

    fun getSpecificAdvances(userId: String, customerId: String? = null, billingMonth: String? = null): Flow<Resource<List<SpecificAdvanceModel>>> {
        return safeApiCall { ApiClient.apiService.getSpecificAdvances(userId, customerId, billingMonth) }
    }

    suspend fun syncSpecificAdvancesFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync specific advances from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getSpecificAdvances(userId)
        } catch (e: java.io.IOException) {
            Log.e("IspRepository", "Network error while syncing specific advances from Hosting: ${e.message}")
            return@withContext false
        } catch (e: retrofit2.HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing specific advances from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing specific advances from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting specific advance API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = db.specificAdvanceDao().getAllSpecificAdvancesList()
            val existingMap = existingList.associateBy { it.id }
            val dirtyIds = db.specificAdvanceDao().getDirtySpecificAdvances().map { it.id }.toSet()
            val entitiesToPersist = mutableListOf<SpecificAdvanceEntity>()

            for (remote in response.data) {
                val numId = remote.id.toLongOrNull() ?: continue
                val custId = remote.customerId.toLongOrNull() ?: 0L

                // Never overwrite local dirty modifications
                if (dirtyIds.contains(numId)) {
                    continue
                }

                val existing = existingMap[numId]
                val updatedAt = if (remote.updatedAt > 0) remote.updatedAt else (existing?.updatedAt ?: System.currentTimeMillis())

                val entity = if (existing != null) {
                    existing.copy(
                        customerId = if (custId != 0L) custId else existing.customerId,
                        billingMonth = remote.billingMonth.ifBlank { existing.billingMonth },
                        amount = remote.amount,
                        isConsumed = remote.isConsumed,
                        updatedAt = updatedAt,
                        syncStatus = 0
                    )
                } else {
                    SpecificAdvanceEntity(
                        id = numId,
                        customerId = custId,
                        billingMonth = remote.billingMonth,
                        amount = remote.amount,
                        isConsumed = remote.isConsumed,
                        updatedAt = updatedAt,
                        syncStatus = 0
                    )
                }
                entitiesToPersist.add(entity)
            }

            if (entitiesToPersist.isNotEmpty()) {
                db.specificAdvanceDao().insertSpecificAdvances(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced ${entitiesToPersist.size} specific advances from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting specific advances to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncBandwidthBillsFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync bandwidth bills from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getBandwidthBills(userId)
        } catch (e: java.io.IOException) {
            Log.e("IspRepository", "Network error while syncing bandwidth bills from Hosting: ${e.message}")
            return@withContext false
        } catch (e: retrofit2.HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing bandwidth bills from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing bandwidth bills from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting bandwidth bill API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = db.bandwidthBillDao().getAllBandwidthBillsList()
            val existingMap = existingList.associateBy { it.billingMonth }
            val dirtyMonths = db.bandwidthBillDao().getDirtyBandwidthBills().map { it.billingMonth }.toSet()
            val entitiesToPersist = mutableListOf<BandwidthBillEntity>()

            for (remote in response.data) {
                val month = remote.billingMonth
                if (month.isBlank()) continue

                // Never overwrite local dirty modifications
                if (dirtyMonths.contains(month)) {
                    continue
                }

                val existing = existingMap[month]
                val updatedAt = if (remote.updatedAt > 0) remote.updatedAt else (existing?.updatedAt ?: System.currentTimeMillis())

                val entity = if (existing != null) {
                    existing.copy(
                        amount = remote.amount,
                        updatedAt = updatedAt,
                        syncStatus = 0
                    )
                } else {
                    BandwidthBillEntity(
                        billingMonth = month,
                        amount = remote.amount,
                        updatedAt = updatedAt,
                        syncStatus = 0
                    )
                }
                entitiesToPersist.add(entity)
            }

            if (entitiesToPersist.isNotEmpty()) {
                db.bandwidthBillDao().insertOrUpdateBandwidthBills(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced ${entitiesToPersist.size} bandwidth bills from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting bandwidth bills to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncAuditLogsFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync audit logs from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getAuditLogs(userId)
        } catch (e: java.io.IOException) {
            Log.e("IspRepository", "Network error while syncing audit logs from Hosting: ${e.message}")
            return@withContext false
        } catch (e: retrofit2.HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing audit logs from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing audit logs from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting audit log API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = auditLogDao.getAllAuditLogsList()
            val existingMap = existingList.associateBy { it.id }
            val dirtyLogIds = auditLogDao.getDirtyAuditLogs().map { it.id }.toSet()
            val entitiesToPersist = mutableListOf<AuditLogEntity>()

            for (remote in response.data) {
                val numId = remote.id.toLongOrNull()
                if (numId == null) {
                    Log.w("IspRepository", "Skipping remote audit log with non-numeric ID: ${remote.id}")
                    continue
                }

                // Never overwrite local dirty modifications
                if (dirtyLogIds.contains(numId)) {
                    continue
                }

                val existing = existingMap[numId]
                val action = remote.action.ifBlank { existing?.action ?: "ACTION" }
                val actionType = remote.actionType.ifBlank { existing?.actionType ?: "" }
                val details = remote.details ?: existing?.details ?: ""
                val userEmail = remote.userEmail.ifBlank { existing?.userEmail ?: "" }
                val userRole = remote.userRole.ifBlank { existing?.userRole ?: "Admin" }
                val targetEntity = remote.targetEntity.ifBlank { existing?.targetEntity ?: "" }
                val targetId = remote.targetId.ifBlank { existing?.targetId ?: "" }
                val previousState = remote.previousState ?: existing?.previousState ?: ""
                val newState = remote.newState ?: existing?.newState ?: ""
                val status = remote.status.ifBlank { existing?.status ?: "SUCCESS" }
                val timestamp = if (remote.timestamp > 0) remote.timestamp else (existing?.timestamp ?: System.currentTimeMillis())

                val entity = if (existing != null) {
                    existing.copy(
                        action = action,
                        actionType = actionType,
                        details = details,
                        userEmail = userEmail,
                        userRole = userRole,
                        targetEntity = targetEntity,
                        targetId = targetId,
                        previousState = previousState,
                        newState = newState,
                        status = status,
                        timestamp = timestamp,
                        syncStatus = 0
                    )
                } else {
                    AuditLogEntity(
                        id = numId,
                        action = action,
                        actionType = actionType,
                        details = details,
                        userEmail = userEmail,
                        userRole = userRole,
                        targetEntity = targetEntity,
                        targetId = targetId,
                        previousState = previousState,
                        newState = newState,
                        status = status,
                        timestamp = timestamp,
                        syncStatus = 0
                    )
                }
                entitiesToPersist.add(entity)
            }

            if (entitiesToPersist.isNotEmpty()) {
                auditLogDao.insertLogs(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced ${entitiesToPersist.size} audit logs from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting audit logs to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncSettingsFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync settings from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getSettings(userId)
        } catch (e: java.io.IOException) {
            Log.e("IspRepository", "Network error while syncing settings from Hosting: ${e.message}")
            return@withContext false
        } catch (e: retrofit2.HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing settings from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing settings from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.d("IspRepository", "No settings found on Hosting or server returned false: ${response.message}")
            return@withContext false
        }

        val remote = response.data
        try {
            val dirtySettings = settingsDao.getDirtySettings()
            if (dirtySettings != null) {
                Log.d("IspRepository", "Local settings have unpushed changes; skipping overwrite from Hosting.")
                return@withContext true
            }

            val existing = settingsDao.getSettingsSingle()
            val entity = BusinessSettingsEntity(
                id = 1,
                ispName = remote.ispName.ifBlank { existing?.ispName ?: "" },
                hotline = remote.hotline.ifBlank { existing?.hotline ?: "" },
                address = remote.address.ifBlank { existing?.address ?: "" },
                currencySymbol = remote.currencySymbol.ifBlank { existing?.currencySymbol ?: "৳" },
                networkStatus = remote.networkStatus.ifBlank { existing?.networkStatus ?: "Operational" },
                themeMode = remote.themeMode.ifBlank { existing?.themeMode ?: "SYSTEM" },
                logoUri = remote.logoUri ?: existing?.logoUri,
                email = remote.email.ifBlank { existing?.email ?: "" },
                updatedAt = remote.updatedAt ?: existing?.updatedAt ?: System.currentTimeMillis(),
                syncStatus = 0
            )
            settingsDao.insertOrUpdateSettings(entity)
            Log.d("IspRepository", "Successfully synced and updated business settings from Hosting.")
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting settings to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncExpensesFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync expenses from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getExpenses(userId)
        } catch (e: java.io.IOException) {
            Log.e("IspRepository", "Network error while syncing expenses from Hosting: ${e.message}")
            return@withContext false
        } catch (e: retrofit2.HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing expenses from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing expenses from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting expense API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = expenseDao.getAllExpensesList()
            val existingMap = existingList.associateBy { it.id }
            val dirtyExpenseIds = expenseDao.getDirtyExpenses().map { it.id }.toSet()
            val entitiesToPersist = mutableListOf<ExpenseEntity>()

            for (remote in response.data) {
                val numId = remote.id.toLongOrNull()
                if (numId == null) {
                    Log.w("IspRepository", "Skipping remote expense with non-numeric ID: ${remote.id}")
                    continue
                }

                // Never overwrite local dirty modifications
                if (dirtyExpenseIds.contains(numId)) {
                    continue
                }

                val existing = existingMap[numId]
                val title = remote.title.ifBlank { existing?.title ?: "Expense" }
                val amount = remote.amount
                val category = remote.category.ifBlank { existing?.category ?: "General" }
                val date = remote.date.ifBlank { existing?.date ?: "" }
                val paymentMethod = remote.paymentMethod.ifBlank { existing?.paymentMethod ?: "Cash" }
                val note = remote.note ?: existing?.note ?: ""
                val receiptPath = remote.receiptPath ?: existing?.receiptPath
                val createdAt = remote.createdAt ?: existing?.createdAt ?: System.currentTimeMillis()
                val updatedAt = remote.updatedAt ?: existing?.updatedAt ?: System.currentTimeMillis()

                val entity = if (existing != null) {
                    existing.copy(
                        title = title,
                        amount = amount,
                        category = category,
                        date = date,
                        paymentMethod = paymentMethod,
                        note = note,
                        receiptPath = receiptPath,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncStatus = 0
                    )
                } else {
                    ExpenseEntity(
                        id = numId,
                        title = title,
                        amount = amount,
                        category = category,
                        date = date,
                        paymentMethod = paymentMethod,
                        note = note,
                        receiptPath = receiptPath,
                        createdAt = createdAt,
                        updatedAt = updatedAt,
                        syncStatus = 0
                    )
                }
                entitiesToPersist.add(entity)
            }

            if (entitiesToPersist.isNotEmpty()) {
                expenseDao.insertExpenses(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced ${entitiesToPersist.size} expenses from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting expenses to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncExpenseCategoriesFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync expense categories from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getExpenseCategories(userId)
        } catch (e: java.io.IOException) {
            Log.e("IspRepository", "Network error while syncing expense categories from Hosting: ${e.message}")
            return@withContext false
        } catch (e: retrofit2.HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing expense categories from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing expense categories from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting expense categories API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = expenseDao.getAllCategoriesList()
            val existingMap = existingList.associateBy { it.id }
            val dirtyCategoryIds = expenseDao.getDirtyCategories().map { it.id }.toSet()
            val entitiesToPersist = mutableListOf<ExpenseCategoryEntity>()

            for (remote in response.data) {
                val numId = remote.id.toLongOrNull()
                if (numId == null) {
                    Log.w("IspRepository", "Skipping remote expense category with non-numeric ID: ${remote.id}")
                    continue
                }

                // Never overwrite local dirty modifications
                if (dirtyCategoryIds.contains(numId)) {
                    continue
                }

                val existing = existingMap[numId]
                val name = remote.name.ifBlank { existing?.name ?: "" }
                val updatedAt = remote.updatedAt ?: existing?.updatedAt ?: System.currentTimeMillis()

                val entity = if (existing != null) {
                    existing.copy(
                        name = name,
                        updatedAt = updatedAt,
                        syncStatus = 0
                    )
                } else {
                    ExpenseCategoryEntity(
                        id = numId,
                        name = name,
                        updatedAt = updatedAt,
                        syncStatus = 0
                    )
                }
                entitiesToPersist.add(entity)
            }

            if (entitiesToPersist.isNotEmpty()) {
                expenseDao.insertCategories(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced ${entitiesToPersist.size} expense categories from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting expense categories to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncPaymentsFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync payments from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getPayments(userId)
        } catch (e: java.io.IOException) {
            Log.e("IspRepository", "Network error while syncing payments from Hosting: ${e.message}")
            return@withContext false
        } catch (e: retrofit2.HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing payments from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing payments from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting payment API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = paymentDao.getAllPaymentsList()
            val existingMap = existingList.associateBy { it.id }
            val dirtyPaymentIds = paymentDao.getDirtyPayments().map { it.id }.toSet()
            val entitiesToPersist = mutableListOf<PaymentEntity>()

            for (remote in response.data) {
                val numId = remote.id.toLongOrNull()
                if (numId == null) {
                    Log.w("IspRepository", "Skipping remote payment with non-numeric ID: ${remote.id}")
                    continue
                }

                // Never overwrite local dirty modifications
                if (dirtyPaymentIds.contains(numId)) {
                    continue
                }

                val existing = existingMap[numId]
                val receiptNo = remote.paymentReceiptNo.ifBlank { existing?.paymentReceiptNo ?: "PAY-${remote.id}" }
                val bId = remote.billId?.toLongOrNull() ?: existing?.billId ?: 0L
                val cId = remote.customerId.toLongOrNull() ?: existing?.customerId ?: 0L
                val cName = remote.customerName ?: existing?.customerName ?: ""
                val amt = remote.amount
                val pDate = remote.paymentDate ?: existing?.paymentDate ?: ""
                val pMethod = remote.paymentMethod.ifBlank { existing?.paymentMethod ?: "Cash" }
                val pNotes = remote.notes ?: existing?.notes ?: ""
                val upAt = remote.updatedAt ?: existing?.updatedAt ?: System.currentTimeMillis()

                val entity = if (existing != null) {
                    existing.copy(
                        paymentReceiptNo = receiptNo,
                        billId = bId,
                        customerId = cId,
                        customerName = cName,
                        amount = amt,
                        paymentDate = pDate,
                        paymentMethod = pMethod,
                        notes = pNotes,
                        updatedAt = upAt,
                        syncStatus = 0
                    )
                } else {
                    PaymentEntity(
                        id = numId,
                        paymentReceiptNo = receiptNo,
                        billId = bId,
                        customerId = cId,
                        customerName = cName,
                        amount = amt,
                        paymentDate = pDate,
                        paymentMethod = pMethod,
                        notes = pNotes,
                        updatedAt = upAt,
                        syncStatus = 0
                    )
                }
                entitiesToPersist.add(entity)
            }

            if (entitiesToPersist.isNotEmpty()) {
                paymentDao.insertPayments(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced ${entitiesToPersist.size} payments from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting payments to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncBillsFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync bills from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getBills(userId)
        } catch (e: java.io.IOException) {
            Log.e("IspRepository", "Network error while syncing bills from Hosting: ${e.message}")
            return@withContext false
        } catch (e: retrofit2.HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing bills from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing bills from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting bill API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = billDao.getAllBillsList()
            val existingMap = existingList.associateBy { it.id }
            val dirtyBillIds = billDao.getDirtyBills().map { it.id }.toSet()
            val entitiesToPersist = mutableListOf<BillEntity>()

            for (remote in response.data) {
                val numId = remote.id.toLongOrNull()
                if (numId == null) {
                    Log.w("IspRepository", "Skipping remote bill with non-numeric ID: ${remote.id}")
                    continue
                }

                // Never overwrite local dirty modifications
                if (dirtyBillIds.contains(numId)) {
                    continue
                }

                val existing = existingMap[numId]
                val custId = remote.customerId.toLongOrNull() ?: existing?.customerId ?: 0L
                val billNo = remote.billNumber ?: existing?.billNumber ?: "BILL-${remote.id}"
                val custName = remote.customerName ?: existing?.customerName ?: ""
                val custCode = remote.customerCode ?: existing?.customerCode ?: ""
                val month = remote.billMonth ?: existing?.billingMonth ?: ""
                val amt = remote.amount
                val paid = remote.paidAmount ?: existing?.paidAmount ?: (if (remote.status.equals("PAID", ignoreCase = true)) amt else 0.0)
                val due = remote.dueAmount ?: existing?.dueAmount ?: (if (remote.status.equals("PAID", ignoreCase = true)) 0.0 else amt)
                val st = remote.status.uppercase(Locale.ROOT)
                val genDate = remote.generatedDate ?: existing?.generatedDate ?: ""
                val dDate = remote.dueDate ?: existing?.dueDate ?: ""
                val upAt = remote.updatedAt ?: existing?.updatedAt ?: System.currentTimeMillis()

                val entity = if (existing != null) {
                    existing.copy(
                        billNumber = billNo,
                        customerId = custId,
                        customerName = custName,
                        customerCode = custCode,
                        billingMonth = month,
                        amount = amt,
                        paidAmount = paid,
                        dueAmount = due,
                        status = st,
                        generatedDate = genDate,
                        dueDate = dDate,
                        updatedAt = upAt,
                        syncStatus = 0
                    )
                } else {
                    BillEntity(
                        id = numId,
                        billNumber = billNo,
                        customerId = custId,
                        customerName = custName,
                        customerCode = custCode,
                        billingMonth = month,
                        amount = amt,
                        paidAmount = paid,
                        dueAmount = due,
                        status = st,
                        generatedDate = genDate,
                        dueDate = dDate,
                        updatedAt = upAt,
                        syncStatus = 0
                    )
                }
                entitiesToPersist.add(entity)
            }

            if (entitiesToPersist.isNotEmpty()) {
                billDao.insertBills(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced ${entitiesToPersist.size} bills from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting bills to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncPackagesFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync packages from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        val response = try {
            ApiClient.apiService.getPackages(userId)
        } catch (e: IOException) {
            Log.e("IspRepository", "Network error while syncing packages from Hosting: ${e.message}")
            return@withContext false
        } catch (e: HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing packages from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing packages from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting package API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = packageDao.getAllPackagesList()
            val existingMap = existingList.associateBy { it.id }
            val entitiesToPersist = mutableListOf<IspPackageEntity>()

            for (remote in response.data) {
                val numId = remote.id.toLongOrNull()
                if (numId == null) {
                    Log.w("IspRepository", "Skipping remote package with non-numeric ID: ${remote.id}")
                    continue
                }

                val existing = existingMap[numId]
                val speedInt = remote.speed?.toIntOrNull() ?: existing?.speedMbps ?: 0

                val entity = if (existing != null) {
                    existing.copy(
                        name = remote.name,
                        speedMbps = speedInt,
                        monthlyPrice = remote.price,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = 0
                    )
                } else {
                    IspPackageEntity(
                        id = numId,
                        name = remote.name,
                        speedMbps = speedInt,
                        monthlyPrice = remote.price,
                        description = "",
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = 0
                    )
                }
                entitiesToPersist.add(entity)
            }

            if (entitiesToPersist.isNotEmpty()) {
                packageDao.insertPackages(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced ${entitiesToPersist.size} packages from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting packages to Room: ${e.message}", e)
            false
        }
    }

    suspend fun syncCustomersFromHosting(userIdOverride: String? = null): Boolean = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val ctx = context
        val userId = userIdOverride?.ifBlank { null } ?: if (ctx != null) {
            com.example.IspApplication.getUserId(ctx)
        } else {
            null
        }

        if (userId.isNullOrBlank()) {
            Log.w("IspRepository", "Cannot sync customers from Hosting: unauthenticated (user ID is null)")
            return@withContext false
        }

        // Package sync must run before customer sync so packageDao is populated
        try {
            syncPackagesFromHosting(userIdOverride)
        } catch (e: Throwable) {
            Log.w("IspRepository", "Pre-customer package sync failed (continuing with existing local packages): ${e.message}")
        }

        val response = try {
            ApiClient.apiService.getCustomers(userId)
        } catch (e: IOException) {
            Log.e("IspRepository", "Network error while syncing customers from Hosting: ${e.message}")
            return@withContext false
        } catch (e: HttpException) {
            Log.e("IspRepository", "HTTP error ${e.code()} while syncing customers from Hosting: ${e.message}")
            return@withContext false
        } catch (e: Exception) {
            Log.e("IspRepository", "Unexpected error while syncing customers from Hosting: ${e.message}")
            return@withContext false
        }

        if (!response.status || response.data == null) {
            Log.w("IspRepository", "Hosting API returned status=false or null data: ${response.message}")
            return@withContext false
        }

        try {
            val existingList = customerDao.getAllCustomersList()
            val existingMap = existingList.associateBy { it.id }
            val packageList = packageDao.getAllPackagesList()
            val packageMap = packageList.associateBy { it.id }
            val entitiesToPersist = mutableListOf<CustomerEntity>()

            for (remote in response.data) {
                val numId = remote.id.toLongOrNull()
                if (numId == null) {
                    Log.w("IspRepository", "Skipping remote customer with non-numeric ID: ${remote.id}")
                    continue
                }

                val existing = existingMap[numId]
                if (existing != null) {
                    val targetPackageId = remote.packageId?.toLongOrNull() ?: existing.packageId
                    val matchedPkg = packageMap[targetPackageId]
                    val updated = existing.copy(
                        name = remote.name,
                        phone = remote.phone ?: existing.phone,
                        address = remote.address ?: existing.address,
                        ipAddress = remote.ipAddress ?: existing.ipAddress,
                        pppoeUsername = remote.pppoeUsername ?: existing.pppoeUsername,
                        customerCode = remote.customerCode ?: existing.customerCode,
                        joiningDate = remote.joiningDate ?: existing.joiningDate,
                        packageId = targetPackageId,
                        packageName = matchedPkg?.name ?: existing.packageName,
                        monthlyFee = matchedPkg?.monthlyPrice ?: existing.monthlyFee,
                        status = remote.status,
                        updatedAt = System.currentTimeMillis(),
                        syncStatus = 0
                    )
                    entitiesToPersist.add(updated)
                } else {
                    // New remote customer
                    val custCode = remote.customerCode?.trim()
                    val pppoeUser = remote.pppoeUsername?.trim()
                    val joinDate = remote.joiningDate?.trim()
                    val targetPackageId = remote.packageId?.toLongOrNull()
                    val matchedPkg = if (targetPackageId != null) packageMap[targetPackageId] else null

                    if (custCode.isNullOrBlank()) {
                        Log.w("IspRepository", "Skipping new remote customer $numId (${remote.name}): missing required customer_code")
                        continue
                    }
                    if (pppoeUser.isNullOrBlank()) {
                        Log.w("IspRepository", "Skipping new remote customer $numId (${remote.name}): missing required pppoe_username")
                        continue
                    }
                    if (joinDate.isNullOrBlank()) {
                        Log.w("IspRepository", "Skipping new remote customer $numId (${remote.name}): missing required joining_date")
                        continue
                    }
                    if (targetPackageId == null || matchedPkg == null) {
                        Log.w("IspRepository", "Skipping new remote customer $numId (${remote.name}): package_id ($targetPackageId) could not be resolved in package database")
                        continue
                    }

                    val newCustomer = CustomerEntity(
                        id = numId,
                        customerCode = custCode,
                        name = remote.name,
                        phone = remote.phone ?: "",
                        address = remote.address ?: "",
                        pppoeUsername = pppoeUser,
                        ipAddress = remote.ipAddress ?: "",
                        packageId = targetPackageId,
                        packageName = matchedPkg.name,
                        monthlyFee = matchedPkg.monthlyPrice,
                        status = remote.status,
                        joiningDate = joinDate,
                        syncStatus = 0
                    )
                    entitiesToPersist.add(newCustomer)
                }
            }

            if (entitiesToPersist.isNotEmpty()) {
                customerDao.insertCustomers(entitiesToPersist)
                Log.d("IspRepository", "Successfully synced and persisted ${entitiesToPersist.size} customers from Hosting.")
            }
            true
        } catch (e: Exception) {
            Log.e("IspRepository", "Database error while persisting Hosting customers to Room: ${e.message}", e)
            false
        }
    }
}
