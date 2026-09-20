package com.example.data.model

import com.google.gson.annotations.SerializedName

data class CloudBackupModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("backup_name")
    val backupName: String,
    @SerializedName("backup_data")
    val backupData: String? = null,
    @SerializedName("backup_size")
    val backupSize: Int = 0,
    @SerializedName("version")
    val version: Int = 1,
    @SerializedName("created_at")
    val createdAt: Long = System.currentTimeMillis()
)

data class CloudBackupRequest(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("backup_name")
    val backupName: String,
    @SerializedName("backup_data")
    val backupData: String,
    @SerializedName("version")
    val version: Int = 1
)

data class CloudBackupResponse(
    @SerializedName("status")
    val status: Boolean,
    @SerializedName("message")
    val message: String? = null,
    @SerializedName("backup_id")
    val backupId: String? = null
)
