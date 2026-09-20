package com.aistudio.ispbilling.control.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey
    val id: Long,
    val userId: Long?,
    val customerId: Long,
    val amount: Double,
    val billMonth: String,
    val dueDate: String?,
    val status: String?,
    val isSynced: Boolean = false,
    val syncAction: String = "INSERT"
)
