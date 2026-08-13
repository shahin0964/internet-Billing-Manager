package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerCode: String,
    val name: String,
    val phone: String,
    val address: String,
    val pppoeUsername: String,
    val ipAddress: String = "",
    val packageId: Long,
    val packageName: String,
    val monthlyFee: Double,
    val status: String = "ACTIVE", // ACTIVE, INACTIVE, SUSPENDED
    val joiningDate: String,
    val notes: String = "",
    val area: String = "",
    val zone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val oltName: String = "",
    val ponPort: String = "",
    val onuSerial: String = "",
    val routerName: String = ""
)

@Entity(tableName = "packages")
data class IspPackageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val speedMbps: Int,
    val monthlyPrice: Double,
    val description: String = ""
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val billNumber: String,
    val customerId: Long,
    val customerName: String,
    val customerCode: String,
    val billingMonth: String, // e.g. "2026-08" or "August 2026"
    val amount: Double,
    val paidAmount: Double = 0.0,
    val dueAmount: Double,
    val status: String = "UNPAID", // PAID, PARTIAL, UNPAID
    val generatedDate: String,
    val dueDate: String
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentReceiptNo: String,
    val billId: Long,
    val customerId: Long,
    val customerName: String,
    val amount: Double,
    val paymentDate: String,
    val paymentMethod: String = "Cash", // Cash, bKash, Card, Bank, Online
    val notes: String = ""
)

@Entity(tableName = "business_settings")
data class BusinessSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val ispName: String = "",
    val hotline: String = "",
    val address: String = "",
    val currencySymbol: String = "৳",
    val networkStatus: String = "Operational",
    val themeMode: String = "SYSTEM", // SYSTEM, DARK, LIGHT
    val logoUri: String? = null,
    val email: String = ""
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val category: String,
    val date: String, // e.g., "yyyy-MM-dd"
    val paymentMethod: String = "Cash", // Cash, Bank, Mobile Banking, Card, Other
    val note: String = "",
    val receiptPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "expense_categories")
data class ExpenseCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

fun BillEntity.getDisplayBillNumber(): String {
    if (billNumber.startsWith("BREAKDOWN|")) {
        val parts = billNumber.split("|")
        if (parts.size >= 4) {
            return parts[3]
        }
    }
    return billNumber
}

data class PreviousDueItem(
    val month: String,
    val year: String,
    val amount: Double
)
