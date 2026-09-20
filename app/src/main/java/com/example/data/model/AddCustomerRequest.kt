package com.example.data.model

import com.google.gson.annotations.SerializedName

data class AddCustomerRequest(
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
    val status: String
)
