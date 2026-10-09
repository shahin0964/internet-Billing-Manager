package com.aistudio.ispbilling.control.data.model

import com.google.gson.annotations.SerializedName

data class BillModel(
    @SerializedName("id")
    val id: Long = 0L,
    @SerializedName("user_id")
    val userId: Long? = null,
    @SerializedName("customer_id")
    val customerId: Long = 0L,
    @SerializedName("amount")
    val amount: Double = 0.0,
    @SerializedName("bill_month")
    val billMonth: String? = null,
    @SerializedName("month")
    val month: String? = null,
    @SerializedName("due_date")
    val dueDate: String? = null,
    @SerializedName("status")
    val status: String? = "UNPAID",
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
