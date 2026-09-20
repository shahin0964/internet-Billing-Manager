package com.aistudio.ispbilling.control.data.model

import com.google.gson.annotations.SerializedName

data class Customer(
    @SerializedName("id")
    val id: Long,
    @SerializedName("user_id")
    val userId: Long?,
    @SerializedName("name")
    val name: String,
    @SerializedName("phone")
    val phone: String,
    @SerializedName("address")
    val address: String?,
    @SerializedName("ip_address")
    val ipAddress: String?,
    @SerializedName("package_id")
    val packageId: Long?,
    @SerializedName("billing_cycle_date")
    val billingCycleDate: String?,
    @SerializedName("status")
    val status: String?
)
