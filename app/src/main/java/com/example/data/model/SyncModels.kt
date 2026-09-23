package com.example.data.model

import com.google.gson.annotations.SerializedName

data class SyncPushRequest(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("customers")
    val customers: List<SyncCustomerPayload> = emptyList(),
    @SerializedName("packages")
    val packages: List<SyncPackagePayload> = emptyList(),
    @SerializedName("bills")
    val bills: List<SyncBillPayload> = emptyList(),
    @SerializedName("payments")
    val payments: List<SyncPaymentPayload> = emptyList(),
    @SerializedName("expenses")
    val expenses: List<SyncExpensePayload> = emptyList(),
    @SerializedName("expense_categories")
    val expenseCategories: List<SyncExpenseCategoryPayload> = emptyList(),
    @SerializedName("settings")
    val settings: SyncSettingsPayload? = null,
    @SerializedName("audit_logs")
    val auditLogs: List<SyncAuditLogPayload> = emptyList(),
    @SerializedName("bandwidth_bills")
    val bandwidthBills: List<SyncBandwidthBillPayload> = emptyList(),
    @SerializedName("specific_advances")
    val specificAdvances: List<SyncSpecificAdvancePayload> = emptyList(),
    @SerializedName("pending_deletions")
    val pendingDeletions: List<SyncPendingDeletionPayload> = emptyList()
)

data class SyncPendingDeletionPayload(
    @SerializedName("collection_name") val collectionName: String,
    @SerializedName("document_id") val documentId: String
)

data class SyncCustomerPayload(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("phone") val phone: String? = null,
    @SerializedName("address") val address: String? = null,
    @SerializedName("ip_address") val ipAddress: String? = null,
    @SerializedName("package_id") val packageId: String? = null,
    @SerializedName("billing_cycle_date") val billingCycleDate: Int = 1,
    @SerializedName("status") val status: String = "ACTIVE",
    @SerializedName("pppoe_username") val pppoeUsername: String? = null,
    @SerializedName("customer_code") val customerCode: String? = null,
    @SerializedName("joining_date") val joiningDate: String? = null,
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncPackagePayload(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("price") val price: Double,
    @SerializedName("speed") val speed: String? = null,
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncBillPayload(
    @SerializedName("id") val id: Long,
    @SerializedName("customer_id") val customerId: Long,
    @SerializedName("bill_number") val billNumber: String? = null,
    @SerializedName("customer_name") val customerName: String? = null,
    @SerializedName("customer_code") val customerCode: String? = null,
    @SerializedName("month") val month: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("paid_amount") val paidAmount: Double = 0.0,
    @SerializedName("due_amount") val dueAmount: Double = 0.0,
    @SerializedName("status") val status: String = "UNPAID",
    @SerializedName("due_date") val dueDate: String = "",
    @SerializedName("generated_date") val generatedDate: String? = null,
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncPaymentPayload(
    @SerializedName("id") val id: Long,
    @SerializedName("payment_receipt_no") val paymentReceiptNo: String,
    @SerializedName("bill_id") val billId: Long = 0,
    @SerializedName("customer_id") val customerId: Long,
    @SerializedName("customer_name") val customerName: String? = null,
    @SerializedName("amount") val amount: Double,
    @SerializedName("payment_date") val paymentDate: String,
    @SerializedName("payment_method") val paymentMethod: String = "Cash",
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncExpensePayload(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("category") val category: String = "General",
    @SerializedName("date") val date: String,
    @SerializedName("payment_method") val paymentMethod: String = "Cash",
    @SerializedName("note") val note: String? = null,
    @SerializedName("receipt_path") val receiptPath: String? = null,
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncExpenseCategoryPayload(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("color") val color: String = "#6750A4",
    @SerializedName("created_at") val createdAt: Long = System.currentTimeMillis(),
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncSettingsPayload(
    @SerializedName("isp_name") val ispName: String = "",
    @SerializedName("hotline") val hotline: String = "",
    @SerializedName("address") val address: String? = null,
    @SerializedName("currency_symbol") val currencySymbol: String = "৳",
    @SerializedName("network_status") val networkStatus: String = "Operational",
    @SerializedName("theme_mode") val themeMode: String = "SYSTEM",
    @SerializedName("logo_uri") val logoUri: String? = null,
    @SerializedName("email") val email: String = "",
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncAuditLogPayload(
    @SerializedName("id") val id: Long,
    @SerializedName("action") val action: String,
    @SerializedName("action_type") val actionType: String = "",
    @SerializedName("details") val details: String? = null,
    @SerializedName("user_email") val userEmail: String = "",
    @SerializedName("user_role") val userRole: String = "Admin",
    @SerializedName("target_entity") val targetEntity: String = "",
    @SerializedName("target_id") val targetId: String = "",
    @SerializedName("previous_state") val previousState: String? = null,
    @SerializedName("new_state") val newState: String? = null,
    @SerializedName("status") val status: String = "SUCCESS",
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)

data class SyncBandwidthBillPayload(
    @SerializedName("billing_month") val billingMonth: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncSpecificAdvancePayload(
    @SerializedName("id") val id: Long,
    @SerializedName("customer_id") val customerId: Long,
    @SerializedName("billing_month") val billingMonth: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("is_consumed") val isConsumed: Boolean = false,
    @SerializedName("updated_at") val updatedAt: Long = System.currentTimeMillis()
)

data class SyncResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String? = null,
    @SerializedName("server_timestamp") val serverTimestamp: Long = 0L,
    @SerializedName("synced_ids") val syncedIds: SyncSyncedIds? = null,
    @SerializedName("data") val data: SyncFullData? = null
)

data class SyncSyncedIds(
    @SerializedName("customers") val customers: List<String>? = null,
    @SerializedName("packages") val packages: List<String>? = null,
    @SerializedName("bills") val bills: List<Long>? = null,
    @SerializedName("payments") val payments: List<Long>? = null,
    @SerializedName("expenses") val expenses: List<Long>? = null,
    @SerializedName("expense_categories") val expenseCategories: List<Long>? = null,
    @SerializedName("settings") val settings: Int? = null,
    @SerializedName("audit_logs") val auditLogs: List<Long>? = null,
    @SerializedName("bandwidth_bills") val bandwidthBills: List<String>? = null,
    @SerializedName("specific_advances") val specificAdvances: List<Long>? = null,
    @SerializedName("pending_deletions") val pendingDeletions: List<String>? = null
)

data class SyncFullData(
    @SerializedName("customers") val customers: List<SyncCustomerPayload>? = null,
    @SerializedName("packages") val packages: List<SyncPackagePayload>? = null,
    @SerializedName("bills") val bills: List<SyncBillPayload>? = null,
    @SerializedName("payments") val payments: List<SyncPaymentPayload>? = null,
    @SerializedName("expenses") val expenses: List<SyncExpensePayload>? = null,
    @SerializedName("expense_categories") val expenseCategories: List<SyncExpenseCategoryPayload>? = null,
    @SerializedName("settings") val settings: SyncSettingsPayload? = null,
    @SerializedName("audit_logs") val auditLogs: List<SyncAuditLogPayload>? = null,
    @SerializedName("bandwidth_bills") val bandwidthBills: List<SyncBandwidthBillPayload>? = null,
    @SerializedName("specific_advances") val specificAdvances: List<SyncSpecificAdvancePayload>? = null
)
