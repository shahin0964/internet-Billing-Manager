package com.example.data.model

import com.google.gson.annotations.SerializedName

data class BandwidthBillModel(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("billing_month")
    val billingMonth: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

data class BandwidthBillRequest(
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("billing_month")
    val billingMonth: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
