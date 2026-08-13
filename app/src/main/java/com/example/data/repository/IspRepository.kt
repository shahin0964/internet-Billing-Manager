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
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
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
    val customers: Flow<List<CustomerEntity>> = customerDao.getAllCustomers()
    val packages: Flow<List<IspPackageEntity>> = packageDao.getAllPackages()
    val bills: Flow<List<BillEntity>> = billDao.getAllBills()
    val payments: Flow<List<PaymentEntity>> = paymentDao.getAllPayments()
    val settings: Flow<BusinessSettingsEntity?> = settingsDao.getSettings()
    val expenses: Flow<List<ExpenseEntity>> = expenseDao.getAllExpenses()
    val expenseCategories: Flow<List<ExpenseCategoryEntity>> = expenseDao.getAllCategories()
    val diagrams: Flow<List<NetworkDiagramEntity>> = networkDiagramDao.getAllDiagrams()
    val auditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAllAuditLogs()

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
                timestamp = System.currentTimeMillis()
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
        val result = expenseDao.insertExpense(expense)
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
        expenseDao.updateExpense(expense)
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
        context?.let { com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(it, "expenses", expense.id.toString()) }
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
        val result = expenseDao.insertCategory(ExpenseCategoryEntity(name = categoryName.trim()))
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
        val result = customerDao.insertCustomer(customer)
        val actionName = if (isNew) "CUSTOMER_CREATE" else "CUSTOMER_EDIT"
        logActivity(
            action = actionName,
            actionType = "CUSTOMER",
            details = if (isNew) "Created customer: ${customer.name} (${customer.pppoeUsername})" else "Updated customer: ${customer.name} (${customer.pppoeUsername})",
            targetEntity = "Customer",
            targetId = if (isNew) result.toString() else customer.id.toString(),
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
        
        val newBills = sortedDues.map { item ->
            val billingMonth = "${item.month} ${item.year}"
            val billNo = "PREV-BILL-${System.currentTimeMillis().toString().takeLast(6)}-${customerId}-${item.month.take(3)}"
            BillEntity(
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
                dueDate = todayStr
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
        notifyCloudSync()
    }

    suspend fun saveCustomers(customers: List<CustomerEntity>) {
        customerDao.insertCustomers(customers)
        logActivity(
            action = "CUSTOMER_CREATE",
            actionType = "CUSTOMER",
            details = "Imported ${customers.size} customer records",
            targetEntity = "Customer"
        )
        notifyCloudSync()
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.updateCustomer(customer)
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

            bills.forEach { bill ->
                com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(ctx, "bills", bill.id.toString())
            }
            payments.forEach { payment ->
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
        val result = packageDao.insertPackage(pkg)
        logActivity(
            action = if (isNew) "PACKAGE_CREATE" else "PACKAGE_EDIT",
            actionType = "PACKAGE",
            details = if (isNew) "Created ISP package: ${pkg.name} (${pkg.speedMbps} Mbps)" else "Updated ISP package: ${pkg.name}",
            targetEntity = "IspPackage",
            targetId = if (isNew) result.toString() else pkg.id.toString(),
            newState = "Speed: ${pkg.speedMbps} Mbps, Price: ৳${pkg.monthlyPrice}"
        )
        notifyCloudSync()
        return result
    }

    suspend fun updatePackage(pkg: IspPackageEntity) {
        packageDao.updatePackage(pkg)
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
        context?.let { com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(it, "packages", pkg.id.toString()) }
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
        context?.let { com.example.util.FirestoreSyncManager.deleteDocumentFromCloud(it, "bills", bill.id.toString()) }
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
        val newDue = (bill.amount - bill.paidAmount).coerceAtLeast(0.0)
        val newStatus = when {
            newDue <= 0.0 -> "PAID"
            bill.paidAmount > 0.0 -> "PARTIAL"
            else -> "UNPAID"
        }
        val finalBill = bill.copy(dueAmount = newDue, status = newStatus)
        billDao.updateBill(finalBill)
        logActivity(
            action = "BILL_EDIT",
            actionType = "BILL",
            details = "Updated bill #${bill.billNumber} for ${bill.customerName}",
            targetEntity = "Bill",
            targetId = bill.id.toString(),
            newState = "Amount: ৳${bill.amount}, Paid: ৳${bill.paidAmount}, Due: ৳${newDue}, Status: ${newStatus}"
        )
        notifyCloudSync()
    }

    suspend fun generateMonthlyBills(
        billingMonth: String,
        dueDate: String,
        selectedCustomerIds: Set<Long>? = null
    ): Int {
        val currentCustomers = customers.first()
        val existingBills = bills.first()
        val activeCustomers = currentCustomers.filter {
            it.status == "ACTIVE" && (selectedCustomerIds == null || selectedCustomerIds.contains(it.id))
        }
        
        var generatedCount = 0
        val newBills = mutableListOf<BillEntity>()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())

        for (customer in activeCustomers) {
            val alreadyBilled = existingBills.any {
                it.customerId == customer.id && it.billingMonth.equals(billingMonth, ignoreCase = true)
            }
            if (!alreadyBilled) {
                val billNo = "BILL-${System.currentTimeMillis().toString().takeLast(6)}-${customer.id}"
                newBills.add(
                    BillEntity(
                        billNumber = billNo,
                        customerId = customer.id,
                        customerName = customer.name,
                        customerCode = customer.customerCode,
                        billingMonth = billingMonth,
                        amount = customer.monthlyFee,
                        paidAmount = 0.0,
                        dueAmount = customer.monthlyFee,
                        status = "UNPAID",
                        generatedDate = todayStr,
                        dueDate = dueDate
                    )
                )
                generatedCount++
            }
        }

        if (newBills.isNotEmpty()) {
            billDao.insertBills(newBills)
            logActivity(
                action = "BILL_EDIT",
                actionType = "BILL",
                details = "Generated $generatedCount monthly bills for $billingMonth",
                targetEntity = "Bill"
            )
            try {
                context?.let { com.example.util.AutomaticSmsManager.onBillsGenerated(it, newBills) }
            } catch (e: Exception) {
                Log.e("IspRepository", "Failed to queue billing SMS: ${e.message}")
            }
        }
        notifyCloudSync()
        return generatedCount
    }

    suspend fun recordPayment(
        billId: Long,
        customerId: Long,
        amount: Double,
        paymentMethod: String,
        notes: String
    ): Boolean {
        // Find ALL unpaid bills for this customer, sorted chronologically (assuming older generatedDate or ID is older)
        val allBills = bills.first()
        val customerUnpaidBills = allBills.filter { 
            it.customerId == customerId && it.dueAmount > 0 
        }.sortedBy { it.id } // Sort by ID to pay oldest first

        if (customerUnpaidBills.isEmpty()) return false

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = sdf.format(Date())
        val receiptNo = "PAY-${System.currentTimeMillis().toString().takeLast(6)}"

        var remainingPayment = amount

        for (bill in customerUnpaidBills) {
            if (remainingPayment <= 0) break

            val amountToApply = minOf(remainingPayment, bill.dueAmount)
            remainingPayment -= amountToApply

            val newPaid = bill.paidAmount + amountToApply
            val newDue = (bill.amount - newPaid).coerceAtLeast(0.0)
            val newStatus = when {
                newDue <= 0.0 -> "PAID"
                newPaid > 0.0 -> "PARTIAL"
                else -> "UNPAID"
            }

            val updatedBill = bill.copy(
                paidAmount = newPaid,
                dueAmount = newDue,
                status = newStatus
            )
            billDao.updateBill(updatedBill)
        }

        val custName = customerUnpaidBills.first().customerName
        val payment = PaymentEntity(
            paymentReceiptNo = receiptNo,
            billId = billId, // Use the provided billId or a reference
            customerId = customerId,
            customerName = custName,
            amount = amount,
            paymentDate = todayStr,
            paymentMethod = paymentMethod,
            notes = notes
        )
        val pId = paymentDao.insertPayment(payment)
        logActivity(
            action = "PAYMENT_ADDED",
            actionType = "PAYMENT",
            details = "Recorded payment of ৳${amount} for ${custName} via ${paymentMethod}",
            targetEntity = "Payment",
            targetId = pId.toString(),
            newState = "Amount: ৳${amount}, Method: ${paymentMethod}, Receipt: ${receiptNo}"
        )
        try {
            context?.let { com.example.util.AutomaticSmsManager.onPaymentRecorded(it, payment) }
        } catch (e: Exception) {
            Log.e("IspRepository", "Failed to queue payment SMS: ${e.message}")
        }
        notifyCloudSync()

        return true
    }

    suspend fun deletePayment(payment: PaymentEntity): Boolean {
        try {
            db.withTransaction {
                // Delete payment record locally
                paymentDao.deletePaymentById(payment.id)

                // Get all remaining payments for this customer
                val remainingPayments = paymentDao.getPaymentsListForCustomer(payment.customerId)
                var remainingAmount = remainingPayments.sumOf { it.amount }

                // Get all bills for this customer, sorted chronologically (oldest first)
                val customerBills = billDao.getBillsListForCustomer(payment.customerId).sortedBy { it.id }

                for (bill in customerBills) {
                    val amountToApply = minOf(remainingAmount, bill.amount)
                    remainingAmount = (remainingAmount - amountToApply).coerceAtLeast(0.0)

                    val newPaid = amountToApply
                    val newDue = (bill.amount - newPaid).coerceAtLeast(0.0)
                    val newStatus = when {
                        newDue <= 0.0 -> "PAID"
                        newPaid > 0.0 -> "PARTIAL"
                        else -> "UNPAID"
                    }

                    val updatedBill = bill.copy(
                        paidAmount = newPaid,
                        dueAmount = newDue,
                        status = newStatus
                    )
                    billDao.updateBill(updatedBill)
                }
            }

            // Remove document from Cloud Firestore if online sync is active
            context?.let { ctx ->
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
        settingsDao.insertOrUpdateSettings(settings)
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

        root.put("customers", custArray)
        root.put("packages", pkgArray)
        root.put("expenses", expArray)
        root.put("expenseCategories", catArray)
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
        val custs = customers.first()
        val pkgs = packages.first()
        val bls = bills.first()
        val pymts = payments.first()
        val sttngs = settings.first()
        val exps = expenses.first()
        val cats = expenseCategories.first()

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
                            notes = obj.optString("notes", "")
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
                settingsDao.deleteSettings()

                if (customerList.isNotEmpty()) customerDao.insertCustomers(customerList)
                if (packageList.isNotEmpty()) packageDao.insertPackages(packageList)
                if (billList.isNotEmpty()) billDao.insertBills(billList)
                if (paymentList.isNotEmpty()) paymentDao.insertPayments(paymentList)
                if (expenseList.isNotEmpty()) expenseDao.insertExpenses(expenseList)
                if (categoryList.isNotEmpty()) expenseDao.insertCategories(categoryList)
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
                        notes = obj.optString("notes", "")
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
            settingsDao.deleteSettings()

            if (customerList.isNotEmpty()) customerDao.insertCustomers(customerList)
            if (packageList.isNotEmpty()) packageDao.insertPackages(packageList)
            if (billList.isNotEmpty()) billDao.insertBills(billList)
            if (paymentList.isNotEmpty()) paymentDao.insertPayments(paymentList)
            if (expenseList.isNotEmpty()) expenseDao.insertExpenses(expenseList)
            if (categoryList.isNotEmpty()) expenseDao.insertCategories(categoryList)
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
            name = "Default Network Topology",
            isDefault = true
        )
        val id = networkDiagramDao.insertDiagram(defaultDiag)
        return defaultDiag.copy(id = id)
    }

    suspend fun createNewDiagram(name: String): Long {
        val diag = NetworkDiagramEntity(name = name.ifBlank { "Network Topology" })
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
        networkDiagramDao.insertNode(node)
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
        notifyCloudSync()
    }

    suspend fun deleteNode(nodeId: String) {
        networkDiagramDao.deleteConnectionsForNode(nodeId)
        networkDiagramDao.deleteNodeById(nodeId)
        context?.let {
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
        networkDiagramDao.insertConnection(connection)
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
