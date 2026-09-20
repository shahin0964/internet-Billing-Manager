package com.example.data.model

import com.google.gson.annotations.SerializedName

data class BillModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("bill_month")
    val billMonth: String?,
    @SerializedName("due_date")
    val dueDate: String?,
    @SerializedName("status")
    val status: String
)
