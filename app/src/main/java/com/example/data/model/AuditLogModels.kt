package com.example.data.model

import com.google.gson.annotations.SerializedName

data class AuditLogModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("action")
    val action: String,
    @SerializedName("action_type")
    val actionType: String = "",
    @SerializedName("details")
    val details: String? = "",
    @SerializedName("user_email")
    val userEmail: String = "",
    @SerializedName("user_role")
    val userRole: String = "Admin",
    @SerializedName("target_entity")
    val targetEntity: String = "",
    @SerializedName("target_id")
    val targetId: String = "",
    @SerializedName("previous_state")
    val previousState: String? = "",
    @SerializedName("new_state")
    val newState: String? = "",
    @SerializedName("status")
    val status: String = "SUCCESS",
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

data class AuditLogRequest(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    val action: String,
    @SerializedName("action_type")
    val actionType: String = "",
    val details: String = "",
    @SerializedName("user_email")
    val userEmail: String = "",
    @SerializedName("user_role")
    val userRole: String = "Admin",
    @SerializedName("target_entity")
    val targetEntity: String = "",
    @SerializedName("target_id")
    val targetId: String = "",
    @SerializedName("previous_state")
    val previousState: String = "",
    @SerializedName("new_state")
    val newState: String = "",
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis()
)
