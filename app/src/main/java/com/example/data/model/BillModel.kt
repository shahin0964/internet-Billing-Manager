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
    val status: String,
    @SerializedName("bill_number")
    val billNumber: String? = null,
    @SerializedName("customer_name")
    val customerName: String? = null,
    @SerializedName("customer_code")
    val customerCode: String? = null,
    @SerializedName("paid_amount")
    val paidAmount: Double? = null,
    @SerializedName("due_amount")
    val dueAmount: Double? = null,
    @SerializedName("generated_date")
    val generatedDate: String? = null,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)

data class BillRequest(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("customer_id")
    val customerId: String,
    val amount: Double,
    @SerializedName("bill_month")
    val billMonth: String?,
    @SerializedName("due_date")
    val dueDate: String?,
    val status: String,
    @SerializedName("bill_number")
    val billNumber: String? = null,
    @SerializedName("customer_name")
    val customerName: String? = null,
    @SerializedName("customer_code")
    val customerCode: String? = null,
    @SerializedName("paid_amount")
    val paidAmount: Double = 0.0,
    @SerializedName("due_amount")
    val dueAmount: Double = 0.0,
    @SerializedName("generated_date")
    val generatedDate: String? = null,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)
