package com.example.data.model

import com.google.gson.annotations.SerializedName

data class Customer(
    @SerializedName("id")
    val id: String,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("phone")
    val phone: String?,
    @SerializedName("address")
    val address: String?,
    @SerializedName("ip_address")
    val ipAddress: String?,
    @SerializedName("package_id")
    val packageId: String?,
    @SerializedName("billing_cycle_date")
    val billingCycleDate: Int,
    @SerializedName("status")
    val status: String,
    @SerializedName("pppoe_username")
    val pppoeUsername: String? = null,
    @SerializedName("customer_code")
    val customerCode: String? = null,
    @SerializedName("joining_date")
    val joiningDate: String? = null,
    @SerializedName("created_at")
    val createdAt: String? = null
)
