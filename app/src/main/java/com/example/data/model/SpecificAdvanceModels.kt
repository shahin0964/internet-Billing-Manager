package com.example.data.model

import com.google.gson.annotations.SerializedName

data class SpecificAdvanceModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("billing_month")
    val billingMonth: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("is_consumed")
    val isConsumed: Boolean = false,
    @SerializedName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)

data class SpecificAdvanceRequest(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("billing_month")
    val billingMonth: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("is_consumed")
    val isConsumed: Boolean = false,
    @SerializedName("updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
