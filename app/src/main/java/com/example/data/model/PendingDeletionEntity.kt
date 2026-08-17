package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_deletions")
data class PendingDeletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val collectionName: String,
    val documentId: String,
    val timestamp: Long = System.currentTimeMillis()
)
