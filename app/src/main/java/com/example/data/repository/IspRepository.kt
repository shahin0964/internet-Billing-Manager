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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        context?.let { com.example.util.FirestoreSyncManager.triggerSync(it) }
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
                ?: runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email }.getOrNull()
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
        context?.let { 
            val prefs = it.getSharedPreferences("isp_prefs", Context.MODE_PRIVATE)
            val currentCount = prefs.getInt("pending_sync_count", 0)
            prefs.edit().putInt("pending_sync_count", currentCount + 1).apply()
            com.example.util.FirestoreSyncManager.triggerSync(it) 
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
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        expenseDao.deleteExpense(expense)
        context?.let {
            com.example.util.FirestoreSyncManager.markRecordAsDeleted(it, "expenses", expense.id.toString())
            com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(it, "expenses", expense.id.toString())
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
        val result = expenseDao.insertCategory(ExpenseCategoryEntity(id = generateUniqueId(), name = categoryName.trim(), updatedAt = now, syncStatus = 1))
        logActivity(
            action = "EXPENSE_CATEGORY_ADDED",
            actionType = "EXPENSE",
            details = "Created expense category: ${categoryName.trim()}",
            targetEntity = "ExpenseCategory",
            targetId = result.toString()
        )
        notifyCloudSync()
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

            com.example.util.FirestoreSyncManager.markRecordAsDeleted(ctx, "customers", customer.id.toString())
            bills.forEach { bill ->
                markBillAsDeletedForMonth(bill.customerId, bill.customerCode, bill.billingMonth)
                com.example.util.FirestoreSyncManager.markRecordAsDeleted(ctx, "bills", bill.id.toString())
                com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(ctx, "bills", bill.id.toString())
            }
            payments.forEach { payment ->
                com.example.util.FirestoreSyncManager.markRecordAsDeleted(ctx, "payments", payment.id.toString())
                com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(ctx, "payments", payment.id.toString())
            }
            com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(ctx, "customers", customer.id.toString())
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
        customerDao.updateCustomerStatus(id, status)
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
    }

    suspend fun deletePackage(pkg: IspPackageEntity) {
        packageDao.deletePackage(pkg)
        context?.let {
            com.example.util.FirestoreSyncManager.markRecordAsDeleted(it, "packages", pkg.id.toString())
            com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(it, "packages", pkg.id.toString())
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
        context?.let {
            com.example.util.FirestoreSyncManager.markRecordAsDeleted(it, "bills", bill.id.toString())
            com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(it, "bills", bill.id.toString())
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
                customer.status == "ACTIVE" && !isFree && (selectedCustomerIds == null || selectedCustomerIds.contains(customer.id))
            }
            
            var count = 0
            val newBills = mutableListOf<BillEntity>()
            val processedCustomerIds = mutableSetOf<Long>()
            val processedCustomerCodes = mutableSetOf<String>()

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = sdf.format(Date())

            for (customer in activeCustomers) {
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
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return try {
            val date = sdf.parse(currentMonthYear) ?: return ""
            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.MONTH, 1)
            sdf.format(calendar.time)
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

            // Remove document from Cloud Firestore if online sync is active
            context?.let { ctx ->
                com.example.util.FirestoreSyncManager.markRecordAsDeleted(ctx, "payments", payment.id.toString())
                com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(
                    ctx,
                    "payments",
                    payment.id.toString()
                )
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
            root.put("settings", settObj)
        }

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
                            advanceBalance = obj.optDouble("advanceBalance", 0.0)
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
                    logoUri = obj.optString("logoUri", "").ifEmpty { null }
                )
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

                if (customerList.isNotEmpty()) customerDao.insertCustomers(customerList)
                if (packageList.isNotEmpty()) packageDao.insertPackages(packageList)
                if (billList.isNotEmpty()) billDao.insertBills(billList)
                if (paymentList.isNotEmpty()) paymentDao.insertPayments(paymentList)
                if (expenseList.isNotEmpty()) expenseDao.insertExpenses(expenseList)
                if (categoryList.isNotEmpty()) expenseDao.insertCategories(categoryList)
                if (bandwidthBillList.isNotEmpty()) db.bandwidthBillDao().insertOrUpdateBandwidthBills(bandwidthBillList)
                if (specificAdvanceList.isNotEmpty()) db.specificAdvanceDao().insertSpecificAdvances(specificAdvanceList)
                if (settingsObj != null) settingsDao.insertOrUpdateSettings(settingsObj)
            }

            if (root.has("appLanguage")) {
                val lang = root.getString("appLanguage")
                if (lang == "en" || lang == "bn") {
                    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit().putString("app_lang", lang).apply()
                }
            }

            try {
                com.example.util.FirestoreSyncManager.syncLocalToCloud(context)
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
                logoUri = obj.optString("logoUri", "").ifEmpty { null }
            )
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

            if (customerList.isNotEmpty()) customerDao.insertCustomers(customerList)
            if (packageList.isNotEmpty()) packageDao.insertPackages(packageList)
            if (billList.isNotEmpty()) billDao.insertBills(billList)
            if (paymentList.isNotEmpty()) paymentDao.insertPayments(paymentList)
            if (expenseList.isNotEmpty()) expenseDao.insertExpenses(expenseList)
            if (categoryList.isNotEmpty()) expenseDao.insertCategories(categoryList)
            if (bandwidthBillList.isNotEmpty()) db.bandwidthBillDao().insertOrUpdateBandwidthBills(bandwidthBillList)
            if (specificAdvanceList.isNotEmpty()) db.specificAdvanceDao().insertSpecificAdvances(specificAdvanceList)
            if (settingsObj != null) settingsDao.insertOrUpdateSettings(settingsObj)
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
        context?.let {
            com.example.util.FirestoreSyncManager.markRecordAsDeleted(it, "network_nodes", nodeId)
            com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(it, "network_nodes", nodeId)
        }
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
        context?.let {
            com.example.util.FirestoreSyncManager.markRecordAsDeleted(it, "network_connections", connectionId)
            com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(it, "network_connections", connectionId)
        }
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
}
