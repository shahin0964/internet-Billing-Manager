package com.aistudio.ispbilling.control.data.remote.request

import com.google.gson.annotations.SerializedName

data class BillRequest(
    @SerializedName("id")
    val id: Long,

    @SerializedName("user_id")
    val userId: Long?,

    @SerializedName("customer_id")
    val customerId: Long,

    @SerializedName("amount")
    val amount: Double,

    @SerializedName("bill_month")
    val billMonth: String,

    @SerializedName("due_date")
    val dueDate: String?,

    @SerializedName("status")
    val status: String?
)
