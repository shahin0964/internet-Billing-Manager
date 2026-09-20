package com.example.data.model

import com.google.gson.annotations.SerializedName

data class SettingsModel(
    @SerializedName("id")
    val id: Int = 1,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("isp_name")
    val ispName: String = "",
    @SerializedName("hotline")
    val hotline: String = "",
    @SerializedName("address")
    val address: String = "",
    @SerializedName("currency_symbol")
    val currencySymbol: String = "৳",
    @SerializedName("network_status")
    val networkStatus: String = "Operational",
    @SerializedName("theme_mode")
    val themeMode: String = "SYSTEM",
    @SerializedName("logo_uri")
    val logoUri: String? = null,
    @SerializedName("email")
    val email: String = "",
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)

data class SettingsRequest(
    val id: Int = 1,
    @SerializedName("user_id")
    val userId: String,
    @SerializedName("isp_name")
    val ispName: String,
    val hotline: String,
    val address: String,
    @SerializedName("currency_symbol")
    val currencySymbol: String,
    @SerializedName("network_status")
    val networkStatus: String,
    @SerializedName("theme_mode")
    val themeMode: String,
    @SerializedName("logo_uri")
    val logoUri: String?,
    val email: String,
    @SerializedName("updated_at")
    val updatedAt: Long? = null
)
