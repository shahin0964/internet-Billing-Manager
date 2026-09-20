package com.example.data.model

import com.google.gson.annotations.SerializedName

data class PaymentModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("payment_receipt_no")
    val paymentReceiptNo: String,
    @SerializedName("bill_id")
    val billId: String?,
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("customer_name")
    val customerName: String?,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("payment_date")
    val paymentDate: String?,
    @SerializedName("payment_method")
    val paymentMethod: String = "Cash",
    @SerializedName("notes")
    val notes: String? = null,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)

data class PaymentRequest(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("payment_receipt_no")
    val paymentReceiptNo: String,
    @SerializedName("bill_id")
    val billId: String,
    @SerializedName("customer_id")
    val customerId: String,
    @SerializedName("customer_name")
    val customerName: String,
    val amount: Double,
    @SerializedName("payment_date")
    val paymentDate: String,
    @SerializedName("payment_method")
    val paymentMethod: String,
    val notes: String,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)
