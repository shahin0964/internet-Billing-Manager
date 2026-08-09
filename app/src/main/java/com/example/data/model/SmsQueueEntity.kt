package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_queue")
data class SmsQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val customerReferenceId: String,
    val customerName: String,
    val mobileNumber: String,
    val message: String,
    val smsType: String, // "bill_generated", "due_reminder", "overdue", "payment_confirmation", "general_notice"
    val createdTime: Long,
    val scheduledTime: Long,
    val status: String, // "PENDING", "SENDING", "SENT", "FAILED"
    val retryCount: Int = 0,
    val lastError: String? = null,
    val idempotencyKey: String? = null // e.g. "bill_12_due"
)
