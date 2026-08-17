package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val actionType: String = "",
    val details: String = "",
    val userEmail: String = "",
    val userRole: String = "Admin",
    val targetEntity: String = "",
    val targetId: String = "",
    val previousState: String = "",
    val newState: String = "",
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0 // 0 = SYNCED, 1 = DIRTY
)
