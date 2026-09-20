package com.example.data.model

import com.google.gson.annotations.SerializedName

data class ExpenseModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("amount")
    val amount: Double,
    @SerializedName("category")
    val category: String = "General",
    @SerializedName("date")
    val date: String = "",
    @SerializedName("payment_method")
    val paymentMethod: String = "Cash",
    @SerializedName("note")
    val note: String? = "",
    @SerializedName("receipt_path")
    val receiptPath: String? = null,
    @SerializedName("created_at")
    val createdAt: Long? = null,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)

data class ExpenseRequest(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    val title: String,
    val amount: Double,
    val category: String,
    val date: String,
    @SerializedName("payment_method")
    val paymentMethod: String,
    val note: String,
    @SerializedName("receipt_path")
    val receiptPath: String?,
    @SerializedName("created_at")
    val createdAt: Long? = null,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)

data class ExpenseCategoryModel(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)

data class ExpenseCategoryRequest(
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    val name: String,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)
