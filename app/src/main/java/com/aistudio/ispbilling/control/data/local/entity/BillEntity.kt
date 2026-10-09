package com.aistudio.ispbilling.control.data.local.entity

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey
    val id: Long = 0L,
    val userId: Long? = null,
    val billNumber: String = "",
    val customerId: Long = 0L,
    val customerName: String = "",
    val customerCode: String = "",
    val billMonth: String = "",
    val amount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val dueAmount: Double = 0.0,
    val status: String? = "UNPAID",
    val generatedDate: String = "",
    val dueDate: String? = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0,
    val isSynced: Boolean = true,
    val syncAction: String = "INSERT"
) {
    @get:Ignore
    val billingMonth: String get() = billMonth
}
