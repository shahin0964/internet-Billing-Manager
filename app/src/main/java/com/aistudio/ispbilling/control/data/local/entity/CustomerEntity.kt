package com.aistudio.ispbilling.control.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey
    val id: Long,
    val userId: Long?,
    val name: String,
    val phone: String,
    val address: String?,
    val ipAddress: String?,
    val packageId: Long?,
    val billingCycleDate: String?,
    val status: String?,
    val isSynced: Boolean = false,
    val syncAction: String = "INSERT"
)
